package com.shopee.affiliate.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Model chứa thông tin của một sản phẩm từ thư mục đầu vào.
 */
public class ProductMetadata {
    private File folder;
    private File videoFile;
    private File metadataFile;
    private String description;
    private String productName;
    private List<String> affiliateLinks = new ArrayList<>();

    public ProductMetadata(File folder) {
        this.folder = folder;
    }

    public File getFolder() {
        return folder;
    }

    public void setFolder(File folder) {
        this.folder = folder;
    }

    public File getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(File videoFile) {
        this.videoFile = videoFile;
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    public void setMetadataFile(File metadataFile) {
        this.metadataFile = metadataFile;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<String> getAffiliateLinks() {
        return affiliateLinks;
    }

    public void setAffiliateLinks(List<String> affiliateLinks) {
        this.affiliateLinks = affiliateLinks;
    }

    @Override
    public String toString() {
        return "ProductMetadata{" +
                "folderName=" + (folder != null ? folder.getName() : "null") +
                ", productName='" + productName + '\'' +
                ", hasVideo=" + (videoFile != null) +
                ", linksCount=" + affiliateLinks.size() +
                '}';
    }
}
