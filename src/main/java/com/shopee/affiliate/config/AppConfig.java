package com.shopee.affiliate.config;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Quản lý và cung cấp các giá trị cấu hình cho ứng dụng.
 */
public class AppConfig {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Cảnh báo: Không tìm thấy file config.properties trong resources!");
            } else {
                properties.load(input);
            }
        } catch (Exception ex) {
            System.err.println("Lỗi khi đọc file config.properties: " + ex.getMessage());
        }

        // Tải ghi đè từ file config.properties ở thư mục gốc (nếu có)
        java.io.File localConfig = new java.io.File("config.properties");
        if (localConfig.exists()) {
            try (java.io.InputStream input = new java.io.FileInputStream(localConfig)) {
                properties.load(input);
                System.out.println("Đã tải cấu hình ghi đè từ file config.properties cục bộ.");
            } catch (Exception ex) {
                System.err.println("Lỗi khi đọc file config.properties cục bộ: " + ex.getMessage());
            }
        }
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String getInputDir() {
        return getProperty("input.dir", "C:\\Users\\minhh\\Desktop\\drive-download-20260703T135848Z-3-001");
    }

    public static String getGeminiApiKey() {
        // Hỗ trợ lấy từ env trước, nếu không có thì lấy trong file config
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = getProperty("gemini.api.key", "");
        }
        return apiKey;
    }

    public static String getGeminiModel() {
        String model = System.getProperty("gui.gemini.model");
        if (model == null || model.trim().isEmpty()) {
            model = getProperty("gemini.model", "gemini-1.5-pro");
        }
        return model;
    }

    public static boolean isCdp() {
        String cdp = System.getProperty("gui.browser.cdp");
        if (cdp == null || cdp.trim().isEmpty()) {
            cdp = getProperty("browser.cdp", "false");
        }
        return Boolean.parseBoolean(cdp);
    }

    public static String getChromeUserDataDir() {
        String path = getProperty("chrome.user.data.dir", "chrome-profile");
        // Nếu là path tương đối, chuyển thành tuyệt đối dựa vào thư mục chạy dự án
        if (!Paths.get(path).isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), path).toAbsolutePath().toString();
        }
        return path;
    }

    public static int getExtractFramesCount() {
        try {
            return Integer.parseInt(getProperty("video.extract.frames", "4"));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    public static int getMaxAffiliateLinks() {
        try {
            return Integer.parseInt(getProperty("max.affiliate.links", "6"));
        } catch (NumberFormatException e) {
            return 6;
        }
    }

    public static void saveProperties(String inputDir, String apiKey, String model, int maxLinks, int extractFrames, boolean cdp) {
        properties.setProperty("input.dir", inputDir);
        properties.setProperty("gemini.api.key", apiKey);
        properties.setProperty("gemini.model", model);
        properties.setProperty("max.affiliate.links", String.valueOf(maxLinks));
        properties.setProperty("video.extract.frames", String.valueOf(extractFrames));
        properties.setProperty("browser.cdp", String.valueOf(cdp));

        try (java.io.OutputStream output = new java.io.FileOutputStream("config.properties")) {
            properties.store(output, "Saved by Shopee Affiliate Finder GUI");
            System.out.println("Đã tự động lưu cấu hình phiên mới vào file config.properties ở thư mục gốc.");
        } catch (Exception ex) {
            System.err.println("Lỗi khi lưu file config.properties cục bộ: " + ex.getMessage());
        }
    }
}
