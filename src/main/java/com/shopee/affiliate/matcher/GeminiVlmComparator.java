package com.shopee.affiliate.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.shopee.affiliate.config.AppConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

/**
 * Đối sánh hình ảnh sử dụng Gemini 2.5 Flash API để kết luận trùng khớp.
 */
public class GeminiVlmComparator {

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public GeminiVlmComparator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.gson = new Gson();
        this.apiKey = AppConfig.getGeminiApiKey();
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

    /**
     * So sánh các ảnh cắt từ video với ảnh cào được từ Shopee để xác nhận sản phẩm khớp.
     *
     * @param videoFrames Danh sách ảnh trích xuất từ video
     * @param shopeeImageUrl URL ảnh sản phẩm trên Shopee
     * @param queryName Tên sản phẩm cần tìm kiếm
     * @param shopeeTitle Tên sản phẩm trên Shopee
     * @return MatchResult Kết quả so khớp từ Gemini
     */
    public MatchResult compareImages(List<File> videoFrames, String shopeeImageUrl, String queryName, String shopeeTitle) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            System.err.println("CẢNH BÁO: Chưa cấu hình Gemini API Key. Bỏ qua bước đối sánh ảnh (mặc định không khớp).");
            return new MatchResult(false, 0.0, "Chưa cấu hình Gemini API Key");
        }

        if (videoFrames == null || videoFrames.isEmpty()) {
            return new MatchResult(false, 0.0, "Không có khung hình video để đối sánh");
        }

        try {
            System.out.println("  [Đối sánh hình ảnh] Đang tải ảnh Shopee về bộ nhớ tạm...");
            byte[] shopeeImageBytes = downloadImage(shopeeImageUrl);
            String shopeeImageBase64 = Base64.getEncoder().encodeToString(shopeeImageBytes);
            String shopeeMimeType = detectMimeType(shopeeImageUrl, shopeeImageBytes);

            // Xây dựng JSON payload cho Gemini API
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> contentMap = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            // 1. Thêm Prompt hướng dẫn đối sánh chi tiết
            Map<String, Object> textPart = new HashMap<>();
            String prompt = "You are a precise product matching expert.\n" +
                    "We need to verify if the physical product shown in the video frames is EXACTLY the same model as the product in the Shopee store image.\n\n" +
                    "Product Query: \"" + queryName + "\"\n" +
                    "Shopee Product Title: \"" + shopeeTitle + "\"\n\n" +
                    "Review instructions:\n" +
                    "1. Analyze physical characteristics: ports layout, keyboard layout, case design, logo placement, screen bezels, and shape.\n" +
                    "2. Different versions/models/generations (e.g., HP EliteBook 850 G7 vs G8, or different screen sizes) are NOT a match. They must be the exact same model.\n" +
                    "3. Respond ONLY in JSON format containing the following fields:\n" +
                    "   - \"match\": true (if it is exactly the same model) or false (if different model or not sure)\n" +
                    "   - \"confidence\": score from 0.0 to 1.0\n" +
                    "   - \"reason\": brief explanation in Vietnamese stating what matched or mismatched.";
            textPart.put("text", prompt);
            parts.add(textPart);

            // 2. Thêm các ảnh từ video
            // Để tiết kiệm token và tránh quá tải, ta chỉ gửi tối đa 2 ảnh đặc trưng (ảnh số 1 và số 3 chẳng hạn)
            int count = 0;
            for (File frame : videoFrames) {
                if (count >= 2) break; // Gửi tối đa 2 ảnh video
                byte[] frameBytes = Files.readAllBytes(frame.toPath());
                String frameBase64 = Base64.getEncoder().encodeToString(frameBytes);
                
                Map<String, Object> imagePart = new HashMap<>();
                Map<String, String> inlineData = new HashMap<>();
                inlineData.put("mimeType", "image/png");
                inlineData.put("data", frameBase64);
                imagePart.put("inlineData", inlineData);
                parts.add(imagePart);
                count++;
            }

            // 3. Thêm ảnh từ Shopee
            Map<String, Object> shopeePart = new HashMap<>();
            Map<String, String> inlineData = new HashMap<>();
            inlineData.put("mimeType", shopeeMimeType);
            inlineData.put("data", shopeeImageBase64);
            shopeePart.put("inlineData", inlineData);
            parts.add(shopeePart);

            contentMap.put("parts", parts);
            contents.add(contentMap);
            requestBody.put("contents", contents);

            // Cấu hình định dạng phản hồi JSON
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            String jsonPayload = gson.toJson(requestBody);

            // Gửi request tới Gemini 2.5 Flash
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            System.out.println("  [Đối sánh hình ảnh] Đang gửi yêu cầu tới Gemini API...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                return parseGeminiResponse(responseBody);
            } else {
                System.err.println("Lỗi Gemini API (HTTP " + response.statusCode() + "): " + response.body());
                return new MatchResult(false, 0.0, "Lỗi Gemini API: HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình đối sánh ảnh: " + e.getMessage());
            e.printStackTrace();
            return new MatchResult(false, 0.0, "Lỗi xử lý: " + e.getMessage());
        }
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
