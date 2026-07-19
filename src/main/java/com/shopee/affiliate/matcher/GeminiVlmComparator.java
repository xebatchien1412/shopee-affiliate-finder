package com.shopee.affiliate.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.shopee.affiliate.config.AppConfig;
import com.shopee.affiliate.browser.ShopeeAutomation.ShopeeProduct;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Đối sánh hình ảnh sử dụng Gemini API để kết luận trùng khớp.
 */
public class GeminiVlmComparator {

    private final HttpClient httpClient;
    private final Gson gson;
    private final List<String> apiKeys;
    private int currentKeyIndex;

    public GeminiVlmComparator() {
        this(AppConfig.getGeminiApiKey());
    }

    public GeminiVlmComparator(String apiKeysString) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.gson = new Gson();
        this.apiKeys = parseApiKeys(apiKeysString);
        this.currentKeyIndex = 0;
    }

    private List<String> parseApiKeys(String apiKeysString) {
        List<String> list = new ArrayList<>();
        if (apiKeysString != null && !apiKeysString.trim().isEmpty()) {
            String[] parts = apiKeysString.split(",");
            for (String part : parts) {
                String clean = part.trim();
                if (!clean.isEmpty()) {
                    list.add(clean);
                }
            }
        }
        return list;
    }

    /**
     * Dữ liệu kết quả phản hồi từ Gemini API.
     */
    public static class MatchResult {
        private boolean match;
        private double confidence;
        private String reason;

        public MatchResult(boolean match, double confidence, String reason) {
            this.match = match;
            this.confidence = confidence;
            this.reason = reason;
        }

        public boolean isMatch() {
            return match;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "MatchResult{match=" + match + ", confidence=" + confidence + ", reason='" + reason + "'}";
        }
    }

    public MatchResult compareImages(List<File> videoFrames, String shopeeImageUrl, String queryName, String shopeeTitle) {
        ShopeeProduct candidate = new ShopeeProduct(shopeeTitle, shopeeImageUrl, null);
        Map<Integer, MatchResult> batchResult = compareImagesBatch(videoFrames, Collections.singletonList(candidate), queryName);
        return batchResult.getOrDefault(1, new MatchResult(false, 0.0, "Không có kết quả đối sánh"));
    }

    /**
     * So sánh danh sách các sản phẩm Shopee ứng viên với các khung hình video theo lô (Batch).
     *
     * @param videoFrames Danh sách ảnh trích xuất từ video
     * @param candidates Danh sách sản phẩm Shopee ứng viên
     * @param queryName Tên sản phẩm cần tìm kiếm
     * @return Map ánh xạ từ chỉ số của candidate (1-indexed, từ 1 đến N) sang MatchResult tương ứng
     */
    public Map<Integer, MatchResult> compareImagesBatch(List<File> videoFrames, List<ShopeeProduct> candidates, String queryName) {
        Map<Integer, MatchResult> results = new HashMap<>();
        
        if (apiKeys.isEmpty() || (apiKeys.size() == 1 && apiKeys.get(0).equals("YOUR_GEMINI_API_KEY"))) {
            System.err.println("CẢNH BÁO: Chưa cấu hình Gemini API Key. Bỏ qua bước đối sánh ảnh.");
            for (int i = 0; i < candidates.size(); i++) {
                results.put(i + 1, new MatchResult(false, 0.0, "Chưa cấu hình Gemini API Key"));
            }
            return results;
        }

        if (videoFrames == null || videoFrames.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                results.put(i + 1, new MatchResult(false, 0.0, "Không có khung hình video để đối sánh"));
            }
            return results;
        }

        if (candidates == null || candidates.isEmpty()) {
            return results;
        }

        try {
            System.out.println("  [Đối sánh hình ảnh] Đang tải song song " + candidates.size() + " ảnh Shopee...");
            
            List<CompletableFuture<byte[]>> futures = new ArrayList<>();
            for (ShopeeProduct candidate : candidates) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return downloadImage(candidate.getImageUrl());
                    } catch (Exception e) {
                        System.err.println("Lỗi tải ảnh của candidate: " + candidate.getTitle() + " - " + e.getMessage());
                        return new byte[0];
                    }
                }));
            }

            List<byte[]> imagesBytes = new ArrayList<>();
            for (CompletableFuture<byte[]> future : futures) {
                imagesBytes.add(future.join());
            }

            // Xây dựng JSON payload cho Gemini API
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> contentMap = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            // 1. Thêm Prompt hướng dẫn đối sánh chi tiết theo lô
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("You are a precise product matching expert.\n");
            promptBuilder.append("We have a target physical product shown in the video frames.\n");
            promptBuilder.append("We need to verify which of the candidate products (from a store search) matches the target product EXACTLY.\n\n");
            promptBuilder.append("Product Query: \"").append(queryName).append("\"\n\n");
            promptBuilder.append("Candidate Products to evaluate against the video frames:\n");
            for (int i = 0; i < candidates.size(); i++) {
                promptBuilder.append("Candidate ").append(i + 1).append(":\n");
                promptBuilder.append("  - Title: \"").append(candidates.get(i).getTitle()).append("\"\n");
            }
            promptBuilder.append("\nReview instructions:\n");
            promptBuilder.append("1. For each candidate (from 1 to ").append(candidates.size()).append("), analyze its physical characteristics (ports layout, keyboard layout, case design, logo placement, screen bezels, shape) shown in the corresponding candidate image, and compare it with the target product in the video frames.\n");
            promptBuilder.append("2. Different versions/models/generations (e.g., HP EliteBook 850 G7 vs G8, or different screen sizes) are NOT a match. They must be the exact same model.\n");
            promptBuilder.append("3. CRITICAL: Distinguish between the actual main product (e.g., the phone itself, the camera itself, the laptop itself) and its accessories (e.g., phone cases, protective covers, screen protectors, mounts, chargers, straps). Even if a phone case is shown with the exact shape, cutouts, or brand of the target phone, it is NOT a match for the phone itself. The candidate product must be the actual main product, not an accessory for it. Scan both candidate titles and images carefully for indications of accessories.\n");
            promptBuilder.append("4. Respond ONLY with a JSON array where each element is an object for each candidate. The array must contain exactly one object per candidate in order:\n");
            promptBuilder.append("   - \"index\": integer (from 1 to ").append(candidates.size()).append(")\n");
            promptBuilder.append("   - \"match\": true (if it is exactly the same model) or false (if different model or not sure)\n");
            promptBuilder.append("   - \"confidence\": score from 0.0 to 1.0\n");
            promptBuilder.append("   - \"reason\": brief explanation in Vietnamese stating what matched or mismatched.\n\n");
            promptBuilder.append("Note on Video Frames: Some video frames may show packaging (boxes), accessories (stands, chargers, selfie sticks), or reviewer's face. Scan through ALL provided video frames to find the main product body. Perform physical comparison using the main product body frames. Only fail/mark match as false if the actual main product is never shown in any of the video frames, or if the physical characteristics of the main product itself do not match.\n");

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", promptBuilder.toString());
            parts.add(textPart);

            // 2. Thêm các ảnh từ video
            Map<String, Object> targetHeaderPart = new HashMap<>();
            targetHeaderPart.put("text", "\n--- TARGET PRODUCT VIDEO FRAMES ---");
            parts.add(targetHeaderPart);

            int frameCount = 0;
            for (File frame : videoFrames) {
                if (frameCount >= 5) break; // Gửi tối đa 5 ảnh video để bao phủ toàn bộ nội dung review
                byte[] frameBytes = Files.readAllBytes(frame.toPath());
                String frameBase64 = Base64.getEncoder().encodeToString(frameBytes);
                
                Map<String, Object> imagePart = new HashMap<>();
                Map<String, String> inlineData = new HashMap<>();
                inlineData.put("mimeType", "image/png");
                inlineData.put("data", frameBase64);
                imagePart.put("inlineData", inlineData);
                parts.add(imagePart);
                frameCount++;
            }

            // 3. Thêm các ảnh ứng viên Shopee
            for (int i = 0; i < candidates.size(); i++) {
                byte[] imgBytes = imagesBytes.get(i);
                if (imgBytes == null || imgBytes.length == 0) {
                    // Nếu lỗi tải ảnh, đánh dấu thất bại sẵn
                    results.put(i + 1, new MatchResult(false, 0.0, "Không thể tải ảnh sản phẩm từ Shopee"));
                    continue;
                }
                String imgBase64 = Base64.getEncoder().encodeToString(imgBytes);
                String mimeType = detectMimeType(candidates.get(i).getImageUrl(), imgBytes);

                Map<String, Object> candidateLabelPart = new HashMap<>();
                candidateLabelPart.put("text", "\n--- CANDIDATE " + (i + 1) + " IMAGE ---");
                parts.add(candidateLabelPart);

                Map<String, Object> imagePart = new HashMap<>();
                Map<String, String> inlineData = new HashMap<>();
                inlineData.put("mimeType", mimeType);
                inlineData.put("data", imgBase64);
                imagePart.put("inlineData", inlineData);
                parts.add(imagePart);
            }

            contentMap.put("parts", parts);
            contents.add(contentMap);
            requestBody.put("contents", contents);

            // Cấu hình định dạng phản hồi JSON
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            String jsonPayload = gson.toJson(requestBody);

            // Gửi request tới Gemini API (với cơ chế xoay vòng key khi quá giới hạn)
            String model = AppConfig.getGeminiModel();
            HttpResponse<String> response = null;
            int attempts = 0;
            int maxAttempts = apiKeys.size();
            
            while (attempts < maxAttempts) {
                String currentKey = apiKeys.get(currentKeyIndex);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + currentKey;
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                System.out.println("  [Đối sánh hình ảnh] Đang gửi yêu cầu lô tới Gemini API (" + model + ") sử dụng API Key thứ " + (currentKeyIndex + 1) + "...");
                
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        break; // Gọi thành công! Thoát vòng lặp để xử lý phản hồi
                    } else if (response.statusCode() == 429 || response.statusCode() == 400 || response.statusCode() == 403) {
                        System.err.println("  [Cảnh báo VLM] API Key thứ " + (currentKeyIndex + 1) + " bị lỗi (HTTP " + response.statusCode() + "). Đang thử xoay vòng sang key tiếp theo...");
                        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
                        attempts++;
                        Thread.sleep(1000); // Đợi 1 giây trước khi thử lại
                    } else {
                        // Các mã lỗi khác không xoay vòng (ví dụ lỗi máy chủ hoặc cấu hình sai)
                        System.err.println("  [Cảnh báo VLM] Lỗi HTTP " + response.statusCode() + " từ Gemini API. Dừng thử lại.");
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("  [Cảnh báo VLM] Lỗi kết nối mạng khi dùng key thứ " + (currentKeyIndex + 1) + ": " + e.getMessage() + ". Đang thử xoay vòng key...");
                    currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
                    attempts++;
                    Thread.sleep(1000);
                }
            }

            if (response != null && response.statusCode() == 200) {
                String responseBody = response.body();
                JsonObject root = gson.fromJson(responseBody, JsonObject.class);
                String rawText = root.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();

                String cleanText = rawText.trim();
                if (cleanText.startsWith("```")) {
                    cleanText = cleanText.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
                }

                JsonArray jsonArray = gson.fromJson(cleanText, JsonArray.class);
                for (JsonElement elem : jsonArray) {
                    JsonObject obj = elem.getAsJsonObject();
                    int index = obj.get("index").getAsInt();
                    boolean match = obj.get("match").getAsBoolean();
                    double confidence = obj.get("confidence").getAsDouble();
                    String reason = obj.get("reason").getAsString();
                    
                    results.put(index, new MatchResult(match, confidence, reason));
                }
                
                // Điền thêm các kết quả còn thiếu nếu AI bỏ sót
                for (int i = 0; i < candidates.size(); i++) {
                    if (!results.containsKey(i + 1)) {
                        results.put(i + 1, new MatchResult(false, 0.0, "Gemini không phản hồi kết quả cho ứng viên này"));
                    }
                }
                
            } else {
                String errorMsg = (response != null) ? "HTTP " + response.statusCode() : "Không thể kết nối đến Gemini API sau khi thử tất cả các Key";
                System.err.println("Lỗi đối sánh hình ảnh (toàn bộ API Key đều thất bại): " + errorMsg);
                for (int i = 0; i < candidates.size(); i++) {
                    if (!results.containsKey(i + 1)) {
                        results.put(i + 1, new MatchResult(false, 0.0, "Lỗi Gemini API: " + errorMsg));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình đối sánh ảnh theo lô: " + e.getMessage());
            e.printStackTrace();
            for (int i = 0; i < candidates.size(); i++) {
                if (!results.containsKey(i + 1)) {
                    results.put(i + 1, new MatchResult(false, 0.0, "Lỗi xử lý: " + e.getMessage()));
                }
            }
        }
        
        return results;
    }

    /**
     * Tải hình ảnh từ URL về byte array.
     */
    private byte[] downloadImage(String urlString) throws IOException, InterruptedException {
        // Đảm bảo URL hợp lệ (Shopee ảnh thường bắt đầu bằng //, cần thêm https:)
        if (urlString.startsWith("//")) {
            urlString = "https:" + urlString;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Không thể tải ảnh, HTTP Status: " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Nhận diện MimeType của ảnh.
     */
    private String detectMimeType(String url, byte[] data) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".webp") || (data.length > 12 && new String(data, 8, 4).equals("WEBP"))) {
            return "image/webp";
        } else if (lowerUrl.contains(".png") || (data.length > 8 && data[0] == (byte) 0x89 && data[1] == (byte) 0x50)) {
            return "image/png";
        }
        return "image/jpeg"; // Mặc định là jpeg
    }

    /**
     * Phân tích kết quả JSON trả về từ Gemini API.
     */
    private MatchResult parseGeminiResponse(String responseJson) {
        try {
            JsonObject root = gson.fromJson(responseJson, JsonObject.class);
            String rawText = root.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            // Phân tích text JSON nhận được từ prompt của Gemini
            JsonObject resultJson = gson.fromJson(rawText.trim(), JsonObject.class);
            boolean match = resultJson.get("match").getAsBoolean();
            double confidence = resultJson.get("confidence").getAsDouble();
            String reason = resultJson.get("reason").getAsString();

            return new MatchResult(match, confidence, reason);
        } catch (Exception e) {
            System.err.println("Lỗi phân tích cú pháp phản hồi từ Gemini: " + e.getMessage());
            System.err.println("Nội dung gốc phản hồi: " + responseJson);
            return new MatchResult(false, 0.0, "Lỗi cú pháp phản hồi AI: " + e.getMessage());
        }
    }
}
