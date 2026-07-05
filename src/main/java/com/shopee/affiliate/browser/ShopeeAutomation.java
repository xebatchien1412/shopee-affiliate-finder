package com.shopee.affiliate.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.shopee.affiliate.config.AppConfig;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Điều khiển trình duyệt Chrome qua Playwright để tự động hóa thao tác trên Shopee Affiliate.
 */
public class ShopeeAutomation implements AutoCloseable {

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    /**
     * Dữ liệu đại diện cho một sản phẩm tìm thấy trên Shopee Affiliate.
     */
    public static class ShopeeProduct {
        private String title;
        private String imageUrl;
        private Locator getLinkButton;

        public ShopeeProduct(String title, String imageUrl, Locator getLinkButton) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.getLinkButton = getLinkButton;
        }

        public String getTitle() {
            return title;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public Locator getGetLinkButton() {
            return getLinkButton;
        }

        @Override
        public String toString() {
            return "ShopeeProduct{title='" + title + "', imageUrl='" + imageUrl + "'}";
        }
    }

    /**
     * Khởi tạo trình duyệt Playwright với Profile riêng biệt.
     */
    public void init() {
        System.out.println("Đang khởi tạo Playwright...");
        playwright = Playwright.create();

        String userDataDir = AppConfig.getChromeUserDataDir();
        System.out.println("Sử dụng Chrome Profile tại: " + userDataDir);

        // Cấu hình khởi chạy Chrome
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false) // Cần hiện giao diện để đăng nhập và xử lý trực quan
                .setChannel("chrome") // Chạy Chrome thực tế trên máy
                .setViewportSize(1280, 800)
                .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled", // Ẩn cờ tự động hóa
                        "--start-maximized"
                ));

        context = playwright.chromium().launchPersistentContext(Paths.get(userDataDir), options);
        page = context.newPage();
        
        // Thiết lập timeout mặc định là 30 giây
        page.setDefaultTimeout(30000);
    }

    /**
     * Kiểm tra trạng thái đăng nhập và yêu cầu người dùng đăng nhập nếu cần.
     */
    public void checkLoginAndNavigate() {
        System.out.println("Đang điều hướng đến trang Shopee Affiliate Offer...");
        page.navigate("https://affiliate.shopee.vn/offer/product_offer");

        // Đợi một chút để trang tải hoặc chuyển hướng
        page.waitForTimeout(3000);

        // Kiểm tra xem có đang ở trang đăng nhập hoặc có ô đăng nhập không
        boolean isLoginPage = page.url().contains("login") || 
                             page.locator("input[type='password']").count() > 0 || 
                             page.locator("text=Đăng nhập").count() > 0;

        if (isLoginPage) {
            System.out.println("\n============================================================");
            System.out.println("CẢNH BÁO: Bạn chưa đăng nhập vào hệ thống Shopee Affiliate!");
            System.out.println("Vui lòng thực hiện đăng nhập trên trình duyệt Chrome vừa mở.");
            System.out.println("Sau khi đăng nhập thành công và thấy giao diện Tìm kiếm Sản phẩm,");
            System.out.println("vui lòng quay lại đây và nhấn phím ENTER để tiếp tục...");
            System.out.println("============================================================\n");

            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            
            // Tải lại trang sau khi người dùng đã đăng nhập
            System.out.println("Đang kiểm tra lại phiên đăng nhập...");
            page.navigate("https://affiliate.shopee.vn/offer/product_offer");
            page.waitForTimeout(3000);
        } else {
            System.out.println("Đã nhận diện phiên đăng nhập thành công.");
        }
    }

    /**
     * Tìm kiếm sản phẩm theo tên/từ khóa.
     *
     * @param query Từ khóa tìm kiếm
     * @return Danh sách sản phẩm kết quả cào được
     */
    public List<ShopeeProduct> searchProduct(String query) {
        System.out.println("Đang tìm kiếm sản phẩm với từ khóa: '" + query + "'");
        List<ShopeeProduct> products = new ArrayList<>();

        try {
            // Tìm ô input tìm kiếm (thử nhiều selector thông dụng trên Shopee Affiliate)
            Locator searchInput = null;
            String[] inputSelectors = {
                    "input[placeholder*='tên sản phẩm']",
                    "input[placeholder*='Tìm kiếm']",
                    "input[placeholder*='link']",
                    "input[placeholder*='offer']",
                    "input.ant-input",
                    "input[type='text']"
            };

            for (String selector : inputSelectors) {
                Locator loc = page.locator(selector).first();
                if (loc.count() > 0 && loc.isVisible()) {
                    searchInput = loc;
                    break;
                }
            }

            if (searchInput == null) {
                System.err.println("Lỗi: Không tìm thấy ô tìm kiếm trên trang!");
                return products;
            }

            // Xóa sạch nội dung cũ bằng cách chọn tất cả và nhấn Backspace
            searchInput.focus();
            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
            page.waitForTimeout(200);
            
            // Nhập từ khóa mới
            searchInput.fill(query);
            page.waitForTimeout(300);
            page.keyboard().press("Enter");

            System.out.println("Đã gửi lệnh tìm kiếm, chờ tải dữ liệu kết quả...");
            // Đợi bảng dữ liệu tải xong (Ant Design Table thường có lớp ant-table-tbody)
            page.waitForTimeout(3000); // Đợi cứng 3s để API Shopee trả dữ liệu

            // Bắt đầu cào dữ liệu từ bảng kết quả
            // Tìm các dòng trong bảng hoặc các card sản phẩm
            // Thường là thẻ tr trong tbody
            Locator rows = page.locator("tr.ant-table-row");
            int rowCount = rows.count();
            
            if (rowCount == 0) {
                // Thử tìm theo cấu trúc div nếu Shopee đổi giao diện dạng card
                rows = page.locator(".ant-list-item");
                rowCount = rows.count();
            }

            System.out.println("Tìm thấy " + rowCount + " dòng kết quả hiển thị trên trang.");

            for (int i = 0; i < rowCount; i++) {
                Locator row = rows.nth(i);
                
                // Trích xuất hình ảnh
                Locator imgLocator = row.locator("img").first();
                String imageUrl = "";
                if (imgLocator.count() > 0) {
                    imageUrl = imgLocator.getAttribute("src");
                }

                // Trích xuất tiêu đề sản phẩm
                // Tiêu đề thường nằm trong div có chữ đậm hoặc có class cụ thể, hoặc text đầu tiên dài
                String rowText = row.innerText();
                String title = parseProductTitle(rowText);

                // Tìm nút "Lấy Link" hoặc "Get Link"
                // Thử nhiều biến thể chữ viết
                Locator btn = row.locator("button:has-text('Lấy Link')").first();
                if (btn.count() == 0) {
                    btn = row.locator("button:has-text('Get Link')").first();
                }
                if (btn.count() == 0) {
                    btn = row.locator("button:has-text('Lấy link')").first();
                }
                if (btn.count() == 0) {
                    btn = row.locator("button").first(); // Fallback nút đầu tiên của dòng
                }

                if (btn.count() > 0 && !imageUrl.isEmpty() && !title.isEmpty()) {
                    products.add(new ShopeeProduct(title, imageUrl, btn));
                }
            }

        } catch (Exception e) {
            System.err.println("Lỗi khi tìm kiếm sản phẩm: " + e.getMessage());
            e.printStackTrace();
        }

        return products;
    }

    /**
     * Click vào nút lấy link và trích xuất URL affiliate từ popup.
     *
     * @param getLinkButton Locator của nút "Lấy link" tương ứng của sản phẩm
     * @return Chuỗi liên kết affiliate ngắn s.shopee.vn, hoặc null nếu lỗi
     */
    public String getAffiliateLink(Locator getLinkButton) {
        try {
            System.out.println("Đang click nút lấy link...");
            getLinkButton.click();

            // Chờ popup modal xuất hiện (thường có class ant-modal)
            page.waitForTimeout(2000);

            // Tìm ô chứa link. Thường là input hoặc textarea chứa link có s.shopee.vn hoặc shope.ee
            Locator inputs = page.locator("input, textarea");
            int inputCount = inputs.count();
            String affiliateLink = null;

            for (int i = 0; i < inputCount; i++) {
                Locator input = inputs.nth(i);
                String val = input.inputValue();
                if (val != null && (val.contains("s.shopee.vn") || val.contains("shope.ee") || val.contains("shopee.vn/universal-link"))) {
                    affiliateLink = val.trim();
                    break;
                }
            }

            // Nếu không tìm thấy qua inputValue, quét qua innerText của các phần tử trong modal
            if (affiliateLink == null) {
                Locator modal = page.locator(".ant-modal-content");
                if (modal.count() > 0) {
                    String modalText = modal.innerText();
                    Pattern pattern = Pattern.compile("https://(s\\.shopee\\.vn|shope\\.ee)/[a-zA-Z0-9]+");
                    Matcher matcher = pattern.matcher(modalText);
                    if (matcher.find()) {
                        affiliateLink = matcher.group();
                    }
                }
            }

            // Đóng popup bằng cách nhấn nút Đóng/Hủy hoặc phím Escape
            Locator closeBtn = page.locator(".ant-modal-close, button:has-text('Đóng'), button:has-text('Cancel'), button:has-text('Hủy')").first();
            if (closeBtn.count() > 0 && closeBtn.isVisible()) {
                closeBtn.click();
            } else {
                page.keyboard().press("Escape");
            }
            
            page.waitForTimeout(1000); // Chờ modal biến mất hoàn toàn
            
            if (affiliateLink != null) {
                System.out.println("  -> Đã lấy được link: " + affiliateLink);
            } else {
                System.err.println("  -> Không thể trích xuất link affiliate từ popup!");
            }
            
            return affiliateLink;

        } catch (Exception e) {
            System.err.println("Lỗi khi lấy link affiliate: " + e.getMessage());
            // Đảm bảo đóng popup bằng Escape nếu bị kẹt
            try { page.keyboard().press("Escape"); } catch (Exception ignored) {}
            return null;
        }
    }

    /**
     * Hàm phân tích lấy tiêu đề sản phẩm từ chuỗi text thô của dòng trong bảng.
     */
    private String parseProductTitle(String rowText) {
        if (rowText == null || rowText.trim().isEmpty()) {
            return "";
        }
        // Thường nội dung dòng chứa: Tên sản phẩm, Giá, Hoa hồng, Nút bấm.
        // Tên sản phẩm thường ở đầu. Ta cắt dòng theo ký tự xuống dòng (\n)
        String[] lines = rowText.split("\n");
        for (String line : lines) {
            String clean = line.trim();
            // Bỏ qua dòng trống, dòng giá tiền (chứa đ, VNĐ, %), hoặc dòng nút bấm
            if (clean.isEmpty() || clean.contains("Lấy Link") || clean.contains("Get Link") || 
                clean.matches(".*\\d+%.*") || clean.contains("₫") || clean.contains("VND")) {
                continue;
            }
            // Trả về dòng text đầu tiên hợp lệ (thường là tên sản phẩm)
            if (clean.length() > 5) {
                return clean;
            }
        }
        return lines[0].trim();
    }

    @Override
    public void close() {
        System.out.println("Đóng kết nối trình duyệt Playwright...");
        try {
            if (context != null) {
                context.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi đóng Playwright: " + e.getMessage());
        }
    }
}
