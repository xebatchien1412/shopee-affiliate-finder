package com.shopee.affiliate.matcher;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiền lọc các sản phẩm dựa trên độ tương đồng tiêu đề bằng văn bản.
 */
public class TextMatcher {

    /**
     * Kiểm tra xem tên sản phẩm Shopee có khớp tương đối với tên sản phẩm cần tìm hay không.
     *
     * @param query Tên gốc cần tìm (ví dụ: "HP EliteBook 850 G7")
     * @param candidate Tên sản phẩm trên Shopee (ví dụ: "Laptop HP EliteBook 850 G7 Giá Rẻ")
     * @return true nếu thỏa mãn điều kiện khớp từ khóa tối thiểu
     */
    public static boolean isTextMatch(String query, String candidate) {
        if (query == null || candidate == null) {
            return false;
        }

        String q = query.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", " ");
        String c = candidate.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", " ");

        // Tách các từ riêng lẻ
        String[] qWords = q.split("\\s+");
        String[] cWords = c.split("\\s+");

        Set<String> cWordSet = new HashSet<>();
        for (String w : cWords) {
            if (!w.trim().isEmpty()) {
                cWordSet.add(w);
            }
        }

        // Đếm số lượng từ khóa quan trọng của query có mặt trong candidate
        int importantWordsMatched = 0;
        int importantWordsTotal = 0;

        for (String word : qWords) {
            String cleanWord = word.trim();
            if (cleanWord.isEmpty() || isStopWord(cleanWord)) {
                continue; // Bỏ qua từ dừng (như: laptop, may, tinh...)
            }

            importantWordsTotal++;
            if (cWordSet.contains(cleanWord)) {
                importantWordsMatched++;
            }
        }

        if (importantWordsTotal == 0) {
            // Nếu không có từ khóa đặc trưng, so sánh độ tương đồng Jaccard tổng quát
            return calculateJaccardSimilarity(q, c) > 0.4;
        }

        // Yêu cầu khớp tối thiểu 70% số từ khóa quan trọng (ví dụ "850" và "g7")
        double matchRatio = (double) importantWordsMatched / importantWordsTotal;
        System.out.println("  [Lọc văn bản] '" + candidate + "' vs '" + query + "' -> Tỷ lệ khớp từ khóa: " + 
                String.format("%.2f", matchRatio * 100) + "%");
        
        return matchRatio >= 0.7;
    }

    /**
     * Kiểm tra xem từ có phải là từ dừng phổ biến (không mang tính định danh sản phẩm) hay không.
     */
    private static boolean isStopWord(String word) {
        String w = word.toLowerCase();
        return w.equals("laptop") || w.equals("máy") || w.equals("tính") || w.equals("xách") || 
               w.equals("tay") || w.equals("chính") || w.equals("hãng") || w.equals("giá") || 
               w.equals("rẻ") || w.equals("đẹp") || w.equals("full") || w.equals("box") ||
               w.equals("new") || w.equals("cũ") || w.equals("likenew") || w.equals("sản") || 
               w.equals("phẩm") || w.equals("điện") || w.equals("thoại") || w.equals("phụ") || 
               w.equals("kiện");
    }

    /**
     * Tính toán chỉ số tương đồng Jaccard giữa hai chuỗi.
     */
    private static double calculateJaccardSimilarity(String s1, String s2) {
        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>();

        for (String w : s1.split("\\s+")) { if (!w.isEmpty()) set1.add(w); }
        for (String w : s2.split("\\s+")) { if (!w.isEmpty()) set2.add(w); }

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}
