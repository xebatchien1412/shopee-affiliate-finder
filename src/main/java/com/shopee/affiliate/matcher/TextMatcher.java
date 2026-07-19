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

        // Kiểm tra lọc lệch phụ kiện (Accessory Check)
        // Nếu tên sản phẩm tìm thấy chứa từ khóa phụ kiện (sạc, túi, ốp, dán...) nhưng tên thư mục gốc không có, loại bỏ luôn.
        String[] accessoryKeywords = {
            "sạc", "sac", "cáp", "cap", "túi", "tui", "bao", "ốp", "op", "dán", "dan", "skin", "phím", "phim", "chuột", "chuot", 
            "lót", "lot", "pad", "đế", "de", "tản", "tan", "nhiệt", "nhiet", "linh", "kiện", "kien", "thay", "thế", "the", 
            "sửa", "sua", "chữa", "chua", "chống", "chong", "sốc", "soc", "cường", "cuong", "lực", "luc", "case", "sleeve", 
            "charger", "adapter", "cable", "keyboard", "cover", "decal", "protector", "screen", "film", "tai", "nghe", "loa", 
            "balo", "quạt", "quat"
        };
        for (String acc : accessoryKeywords) {
            // Bao gồm so khớp cả có dấu và không dấu
            boolean qHas = q.contains(acc);
            boolean cHas = c.contains(acc);
            if (cHas && !qHas) {
                System.out.println("  [Lọc phụ kiện] Loại bỏ '" + candidate + "' vì chứa từ khóa phụ kiện '" + acc + "' mà truy vấn gốc không yêu cầu.");
                return false;
            }
        }

        // Tách các từ riêng lẻ
        String[] qWords = q.split("\\s+");
        String[] cWords = c.split("\\s+");

        Set<String> cWordSet = new HashSet<>();
        for (String w : cWords) {
            if (!w.trim().isEmpty()) {
                cWordSet.add(w);
            }
        }

        // Lọc ra tập hợp các từ khóa quan trọng thực sự của query và candidate
        Set<String> qImportantWords = new HashSet<>();
        for (String word : qWords) {
            String clean = word.trim();
            if (!clean.isEmpty() && !isStopWord(clean)) {
                qImportantWords.add(clean);
            }
        }

        Set<String> cImportantWords = new HashSet<>();
        for (String word : cWords) {
            String clean = word.trim();
            if (!clean.isEmpty() && !isStopWord(clean)) {
                cImportantWords.add(clean);
            }
        }

        // Tìm phần giao các từ khóa quan trọng trùng nhau
        Set<String> matchedWords = new HashSet<>(qImportantWords);
        matchedWords.retainAll(cImportantWords);
        int matchedCount = matchedWords.size();

        int totalQueryImportant = qImportantWords.size();
        int totalCandidateImportant = cImportantWords.size();
        int denom = Math.min(totalQueryImportant, totalCandidateImportant);

        if (denom == 0) {
            // Nếu một trong hai không có từ khóa đặc trưng, so sánh Jaccard tổng quát
            return calculateJaccardSimilarity(q, c) > 0.4;
        }

        // Tỷ lệ khớp tính trên số từ khóa của chuỗi ngắn hơn (tránh lệch khi tên một bên quá dài)
        double matchRatio = (double) matchedCount / denom;
        System.out.println("  [Lọc văn bản] '" + candidate + "' vs '" + query + "' -> Khớp từ quan trọng: " + 
                matchedCount + "/" + denom + " (" + String.format("%.2f", matchRatio * 100) + "%)");
        
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
