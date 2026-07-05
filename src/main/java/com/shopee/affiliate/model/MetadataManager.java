package com.shopee.affiliate.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý đọc và ghi file metadata.txt cho từng sản phẩm.
 */
public class MetadataManager {

    /**
     * Đọc file metadata.txt và phân tích các thông tin sản phẩm.
     *
     * @param folder Thư mục sản phẩm
     * @return ProductMetadata Đối tượng chứa thông tin sản phẩm
     * @throws IOException Nếu xảy ra lỗi đọc file
     */
    public static ProductMetadata readMetadata(File folder) throws IOException {
        File metadataFile = new File(folder, "metadata.txt");
        if (!metadataFile.exists()) {
            throw new IOException("Không tìm thấy file metadata.txt trong thư mục: " + folder.getAbsolutePath());
        }

        ProductMetadata metadata = new ProductMetadata(folder);
        metadata.setMetadataFile(metadataFile);

        // Tìm file video (.mp4) trong thư mục
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files != null && files.length > 0) {
            metadata.setVideoFile(files[0]);
        } else {
            System.err.println("Cảnh báo: Không tìm thấy file video .mp4 nào trong thư mục: " + folder.getName());
        }

        List<String> lines = Files.readAllLines(metadataFile.toPath(), StandardCharsets.UTF_8);
        String description = "";
        List<String> affiliateLinks = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Description:")) {
                description = trimmed.substring("Description:".length()).trim();
                metadata.setDescription(description);
                metadata.setProductName(extractProductName(description));
            } else if (trimmed.startsWith("Link Affiliate:")) {
                String linksContent = trimmed.substring("Link Affiliate:".length()).trim();
                if (!linksContent.isEmpty()) {
                    // Tách các link đã có sẵn (cách nhau bởi dấu phẩy)
                    String[] links = linksContent.split(",");
                    for (String link : links) {
                        String cleanLink = link.trim();
                        // Loại bỏ dấu phẩy thừa ở cuối link
                        if (cleanLink.endsWith(",")) {
                            cleanLink = cleanLink.substring(0, cleanLink.length() - 1).trim();
                        }
                        if (!cleanLink.isEmpty() && cleanLink.startsWith("http")) {
                            affiliateLinks.add(cleanLink);
                        }
                    }
                }
            }
        }

        metadata.setAffiliateLinks(affiliateLinks);
        return metadata;
    }

    /**
     * Ghi lại thông tin metadata.txt kèm danh sách link affiliate mới.
     *
     * @param metadata Đối tượng sản phẩm chứa danh sách link affiliate mới
     * @throws IOException Nếu xảy ra lỗi ghi file
     */
    public static void writeMetadata(ProductMetadata metadata) throws IOException {
        File metadataFile = metadata.getMetadataFile();
        if (metadataFile == null) {
            metadataFile = new File(metadata.getFolder(), "metadata.txt");
        }

        List<String> lines = new ArrayList<>();
        // Ghi lại dòng Description gốc
        lines.add("Description: " + metadata.getDescription());

        // Định dạng danh sách link affiliate: cách nhau bởi ", " và kết thúc có dấu phẩy nếu bạn muốn
        // Ở đây chúng ta ghi theo định dạng yêu cầu: cách nhau bằng ", "
        StringBuilder linksBuilder = new StringBuilder("Link Affiliate: ");
        List<String> links = metadata.getAffiliateLinks();
        for (int i = 0; i < links.size(); i++) {
            linksBuilder.append(links.get(i));
            if (i < links.size() - 1) {
                linksBuilder.append(", ");
            } else {
                linksBuilder.append(","); // Giữ dấu phẩy ở cuối dòng như file mẫu
            }
        }
        lines.add(linksBuilder.toString());
        lines.add(""); // Dòng trống cuối file

        Files.write(metadataFile.toPath(), lines, StandardCharsets.UTF_8);
        System.out.println("Đã cập nhật file metadata.txt tại: " + metadataFile.getAbsolutePath());
    }

    /**
     * Trích xuất tên sản phẩm từ dòng mô tả (loại bỏ các hashtag #).
     */
    private static String extractProductName(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        // Tìm vị trí của hashtag đầu tiên
        int hashIdx = description.indexOf("#");
        String name;
        if (hashIdx != -1) {
            name = description.substring(0, hashIdx).trim();
        } else {
            name = description.trim();
        }
        return name;
    }
}
