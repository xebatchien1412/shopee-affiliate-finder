package com.shopee.affiliate;

import com.shopee.affiliate.browser.ShopeeAutomation;
import com.shopee.affiliate.browser.ShopeeAutomation.ShopeeProduct;
import com.shopee.affiliate.config.AppConfig;
import com.shopee.affiliate.matcher.GeminiVlmComparator;
import com.shopee.affiliate.matcher.GeminiVlmComparator.MatchResult;
import com.shopee.affiliate.matcher.TextMatcher;
import com.shopee.affiliate.model.MetadataManager;
import com.shopee.affiliate.model.ProductMetadata;
import com.shopee.affiliate.video.VideoFrameExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lớp chạy chính điều phối toàn bộ quy trình tự động hóa.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--cli")) {
            System.out.println("=== KHỞI ĐỘNG HỆ THỐNG SHOPEE AFFILIATE FINDER (CLI) ===");
            runCli();
        } else {
            System.out.println("=== KHỞI ĐỘNG GIAO DIỆN SHOPEE AFFILIATE FINDER (GUI) ===");
            com.shopee.affiliate.ui.AppFrame.launch();
        }
    }

    public static void runCli() {
        // 1. Kiểm tra cấu hình thư mục đầu vào
        String inputDirStr = AppConfig.getInputDir();
        File inputDir = new File(inputDirStr);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            System.err.println("Lỗi: Thư mục đầu vào không tồn tại hoặc không phải thư mục: " + inputDirStr);
            System.exit(1);
        }

        // 2. Tìm tất cả các thư mục con sản phẩm
        File[] productFolders = inputDir.listFiles(File::isDirectory);
        if (productFolders == null || productFolders.length == 0) {
            System.out.println("Không tìm thấy thư mục sản phẩm nào tại: " + inputDirStr);
            System.exit(0);
        }

        System.out.println("Tìm thấy " + productFolders.length + " sản phẩm cần xử lý.");

        // 3. Khởi tạo Automation và VLM Comparator
        ShopeeAutomation automation = new ShopeeAutomation();
        GeminiVlmComparator vlmComparator = new GeminiVlmComparator();
        
        // Khởi tạo thư mục tạm để lưu ảnh trích xuất
        File tempDir = new File("temp_frames");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        try {
            // Khởi chạy trình duyệt và kiểm tra đăng nhập
            automation.init();
            automation.checkLoginAndNavigate();

            int maxLinks = AppConfig.getMaxAffiliateLinks();
            int frameCount = AppConfig.getExtractFramesCount();

            // Lặp qua từng thư mục sản phẩm
            for (File folder : productFolders) {
                System.out.println("\n------------------------------------------------------------");
                System.out.println("ĐANG XỬ LÝ SẢN PHẨM: " + folder.getName());
                
                ProductMetadata metadata;
                try {
                    metadata = MetadataManager.readMetadata(folder);
                } catch (IOException e) {
                    System.err.println("Lỗi khi đọc metadata của " + folder.getName() + ": " + e.getMessage());
                    continue;
                }

                System.out.println("  -> Tên sản phẩm cần tìm: " + metadata.getProductName());

                // Nếu đã đủ 6 link rồi thì bỏ qua không tìm tiếp
                if (metadata.getAffiliateLinks().size() >= maxLinks) {
                    System.out.println("  -> Sản phẩm đã có đủ " + metadata.getAffiliateLinks().size() + " links affiliate. Bỏ qua.");
                    continue;
                }

                // Kiểm tra file video
                if (metadata.getVideoFile() == null) {
                    System.err.println("  -> Bỏ qua vì không tìm thấy file video MP4.");
                    continue;
                }

                // Bước 1: Trích xuất các khung hình từ video
                File productTempDir = new File(tempDir, folder.getName());
                List<File> extractedFrames = new ArrayList<>();
                try {
                    extractedFrames = VideoFrameExtractor.extractFrames(
                            metadata.getVideoFile(), productTempDir, frameCount
                    );
                } catch (Exception e) {
                    System.err.println("  -> Lỗi trích xuất video: " + e.getMessage());
                    cleanDirectory(productTempDir);
                    continue;
                }

                if (extractedFrames.isEmpty()) {
                    System.err.println("  -> Lỗi: Không trích xuất được khung hình nào từ video.");
                    cleanDirectory(productTempDir);
                    continue;
                }

                // Bước 2: Tìm kiếm, đối sánh và lấy link trực tiếp theo từng trang (tránh lỗi stale element của Playwright)
                List<String> updatedLinks = automation.searchAndProcessAffiliateLinks(
                        metadata.getProductName(),
                        vlmComparator,
                        extractedFrames,
                        maxLinks,
                        metadata.getAffiliateLinks(),
                        () -> false
                );

                if (updatedLinks.size() > metadata.getAffiliateLinks().size()) {
                    metadata.setAffiliateLinks(updatedLinks);
                    try {
                        MetadataManager.writeMetadata(metadata);
                        System.out.println("  => Cập nhật thành công! Tổng số link hiện tại: " + updatedLinks.size());
                    } catch (IOException e) {
                        System.err.println("  => Lỗi khi ghi đè metadata.txt: " + e.getMessage());
                    }
                } else {
                    System.out.println("  => Không tìm thấy thêm sản phẩm trùng khớp trên Shopee hoặc không lấy được thêm link.");
                }

                // Xóa thư mục ảnh tạm sau khi hoàn tất đối sánh sản phẩm này
                cleanDirectory(productTempDir);
            }

        } catch (Exception e) {
            System.err.println("Lỗi hệ thống nghiêm trọng: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Đóng trình duyệt sạch sẽ
            automation.close();
            // Xóa thư mục tạm lớn
            cleanDirectory(tempDir);
            System.out.println("\n=== HỆ THỐNG ĐÃ HOÀN THÀNH XỬ LÝ TOÀN BỘ ===");
        }
    }

    /**
     * Helper xóa đệ quy thư mục tạm.
     */
    private static void cleanDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Không thể xóa thư mục tạm: " + dir.getAbsolutePath() + " - " + e.getMessage());
        }
    }
}
