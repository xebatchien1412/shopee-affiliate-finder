package com.shopee.affiliate.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.shopee.affiliate.config.AppConfig;
import com.shopee.affiliate.matcher.GeminiVlmComparator;
import com.shopee.affiliate.matcher.TextMatcher;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Điều khiển trình duyệt Chrome qua Playwright để tự động hóa thao tác trên Shopee Affiliate.
 */
public class ShopeeAutomation implements AutoCloseable {

    private Playwright playwright;
    private Browser browser;
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

        boolean isCdp = AppConfig.isCdp();
        
        if (isCdp) {
            System.out.println("Kết nối tới Chrome thật đang mở qua cổng CDP (http://localhost:9222)...");
            try {
                browser = playwright.chromium().connectOverCDP("http://localhost:9222");
            } catch (Exception e) {
                System.err.println("Lỗi kết nối CDP: " + e.getMessage());
                throw new RuntimeException("Không thể kết nối đến Chrome đang mở (cổng 9222).\n" +
                        "👉 Hướng dẫn: Bạn chỉ cần đúp click vào file 'Mo_Chrome_Debug.bat' trong thư mục dự án để mở trình duyệt, sau đó nhấn chạy lại App!");
            }

            if (!browser.contexts().isEmpty()) {
                context = browser.contexts().get(0);
            } else {
                context = browser.newContext();
            }
            
            System.out.println("Danh sách các trang/tab đang mở trên Chrome:");
            for (int i = 0; i < context.pages().size(); i++) {
                System.out.println("  Tab " + i + ": " + context.pages().get(i).url());
            }

            // Tìm kiếm chính xác tab đang mở trang Shopee
            page = null;
            for (Page p : context.pages()) {
                if (p.url().contains("shopee.vn")) {
                    page = p;
                    System.out.println("=> [CDP] Đã phát hiện và kết nối thành công vào Tab Shopee: " + p.url());
                    break;
                }
            }

            if (page == null) {
                // Nếu không thấy tab Shopee, tìm tab đầu tiên mở trang web (bắt đầu bằng http/https)
                for (Page p : context.pages()) {
                    if (p.url().startsWith("http")) {
                        page = p;
                        System.out.println("=> [CDP] Không có tab Shopee, kết nối vào Tab hoạt động đầu tiên: " + p.url());
                        break;
                    }
                }
            }

            if (page == null) {
                // Nếu vẫn không có tab nào hợp lệ, tiến hành mở tab mới
                page = context.newPage();
                System.out.println("=> [CDP] Không tìm thấy tab khả dụng. Đã tự động tạo Tab mới.");
            }
        } else {
            String userDataDir = AppConfig.getChromeUserDataDir();
            System.out.println("Sử dụng Chrome Profile tại: " + userDataDir);

            // Cấu hình khởi chạy Chrome ở chế độ ẩn danh tự động hóa (Stealth Mode)
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false) // Cần hiện giao diện để đăng nhập và xử lý trực quan
                    .setChannel("chrome") // Chạy Chrome thực tế trên máy
                    .setViewportSize(1280, 800)
                    .setIgnoreDefaultArgs(List.of("--enable-automation")) // Bỏ qua cờ mặc định thông báo tự động hóa của Chrome
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setLocale("vi-VN")
                    .setArgs(List.of(
                            "--disable-blink-features=AutomationControlled", // Ẩn cờ tự động hóa Blink
                            "--start-maximized",
                            "--disable-infobars"
                    ));

            context = playwright.chromium().launchPersistentContext(Paths.get(userDataDir), options);
        }

        // Tiêm mã giả lập: Xóa toàn bộ các dấu vết tự động hóa để tránh bị Shopee chặn captcha
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});\n" +
            "Object.defineProperty(navigator, 'languages', {get: () => ['vi-VN', 'vi', 'en-US', 'en']});\n" +
            "Object.defineProperty(navigator, 'plugins', {get: () => [\n" +
            "    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },\n" +
            "    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }\n" +
            "]});\n" +
            "window.chrome = {\n" +
            "    app: { isInstalled: false },\n" +
            "    runtime: { connect: () => {}, sendMessage: () => {} }\n" +
            "};\n" +
            "const originalQuery = window.navigator.permissions.query;\n" +
            "window.navigator.permissions.query = (parameters) => (\n" +
            "    parameters.name === 'notifications' ?\n" +
            "        Promise.resolve({ state: Notification.permission }) :\n" +
            "        originalQuery(parameters)\n" +
            ");\n" +
            "try {\n" +
            "    for (const key in document) {\n" +
            "        if (key.startsWith('$cdc_') || key.includes('cdc_') || key.includes('cdc')) {\n" +
            "            delete document[key];\n" +
            "        }\n" +
            "    }\n" +
            "} catch (e) {}\n"
        );
        
        // Chỉ khởi tạo page nếu page chưa được gắn (CDP có thể đã có sẵn tab)
        if (page == null) {
            page = context.newPage();
        }
        
        // Thiết lập timeout mặc định là 30 giây
        page.setDefaultTimeout(30000);
    }



    /**
     * Kiểm tra trạng thái đăng nhập và yêu cầu người dùng đăng nhập nếu cần.
     */
    public void checkLoginAndNavigate() {
        // Chờ 500ms để Playwright đồng bộ hóa trạng thái URL của trang CDP
        try {
            page.waitForTimeout(500);
        } catch (Exception ignored) {}

        String currentUrl = page.url();
        System.out.println("[Debug CDP] Tab URL hiện tại: " + currentUrl);
        
        if (currentUrl != null && currentUrl.contains("offer/product_offer")) {
            System.out.println("Bạn đã ở sẵn trang Tìm kiếm sản phẩm. Bỏ qua bước điều hướng tự động để tránh kích hoạt anti-bot.");
        } else {
            System.out.println("Đang điều hướng đến trang Shopee Affiliate Offer...");
            try {
                page.navigate("https://affiliate.shopee.vn/offer/product_offer");
                page.waitForTimeout(3000);
            } catch (Exception e) {
                System.err.println("Cảnh báo điều hướng: " + e.getMessage());
            }
        }

        boolean isNotReady = true;
        while (isNotReady) {
            // Kiểm tra xem đã ở đúng trang sản phẩm chưa
            isNotReady = !page.url().contains("offer/product_offer");

            if (!isNotReady) {
                System.out.println("Đã nhận diện phiên đăng nhập và trang Tìm kiếm sản phẩm thành công.");
                break;
            }

            System.out.println("\n============================================================");
            System.out.println("CẢNH BÁO: Bạn chưa đăng nhập hoặc chưa vào trang tìm kiếm sản phẩm!");
            System.out.println("Vui lòng thực hiện đăng nhập và đi tới trang Tìm kiếm sản phẩm.");
            
            if (System.getProperty("gui.input.dir") != null) {
                System.out.println("Vui lòng xác nhận qua hộp thoại pop-up trên màn hình...");
                System.out.println("============================================================\n");
                
                int choice = javax.swing.JOptionPane.showConfirmDialog(null,
                        "Bạn chưa đăng nhập hoặc chưa vào trang tìm kiếm sản phẩm Shopee Affiliate!\n\n" +
                        "1. Vui lòng thực hiện đăng nhập trên trình duyệt Chrome vừa mở.\n" +
                        "2. Đảm bảo trình duyệt đang ở trang Tìm kiếm Sản phẩm (Product Offer).\n" +
                        "3. Sau đó quay lại đây và nhấn [YES] để tiếp tục quy trình tự động.\n" +
                        "   (Nhấn [NO] hoặc [CANCEL] để dừng chương trình).",
                        "Yêu cầu Đăng nhập & Vào trang Tìm kiếm",
                        javax.swing.JOptionPane.YES_NO_OPTION,
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                        
                if (choice != javax.swing.JOptionPane.YES_OPTION) {
                    throw new RuntimeException("Đã dừng tiến trình tự động hóa theo yêu cầu người dùng do chưa đăng nhập hoặc chưa đúng trang.");
                }
            } else {
                System.out.println("Sau khi đăng nhập thành công và thấy giao diện Tìm kiếm Sản phẩm,");
                System.out.println("vui lòng quay lại đây và nhấn phím ENTER để tiếp tục...");
                System.out.println("============================================================\n");

                Scanner scanner = new Scanner(System.in);
                scanner.nextLine();
            }
            
            // Tải lại trang sau khi người dùng xác nhận đã đăng nhập để kiểm tra lại
            System.out.println("Đang kiểm tra lại phiên đăng nhập...");
            if (!page.url().contains("offer/product_offer")) {
                page.navigate("https://affiliate.shopee.vn/offer/product_offer");
                page.waitForTimeout(3000);
            }
        }
    }

    /**
     * Kiểm tra xem ô tìm kiếm sản phẩm có tồn tại và hiển thị trên trang không.
     */
    private boolean isSearchInputPresent() {
        String[] inputSelectors = {
                "input[placeholder*='tên sản phẩm']",
                "input[placeholder*='Tìm kiếm']",
                "input[placeholder*='link']",
                "input[placeholder*='offer']",
                "input.ant-input",
                "input[type='text']"
        };
        for (String selector : inputSelectors) {
            try {
                Locator loc = page.locator(selector).first();
                if (loc.count() > 0 && loc.isVisible()) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
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
            // Đợi bảng dữ liệu tải xong (thường 3 giây)
            page.waitForTimeout(3000);

            // Chẩn đoán DOM để tìm cấu trúc chứa thẻ "Get Link" hoặc "Lấy Link"
            try {
                String diagnostic = (String) page.evaluate(
                    "() => {\n" +
                    "    const elements = Array.from(document.querySelectorAll('*'));\n" +
                    "    const getLinkElements = elements.filter(el => el.textContent && (el.textContent.trim().includes('Get Link') || el.textContent.trim().includes('Lấy Link') || el.textContent.trim().includes('Lấy link')));\n" +
                    "    let result = 'Found Link elements: ' + getLinkElements.length + '\\n';\n" +
                    "    getLinkElements.slice(0, 3).forEach((el, index) => {\n" +
                    "        let parent = el.parentElement;\n" +
                    "        let paths = [];\n" +
                    "        while (parent && parent !== document.body) {\n" +
                    "            paths.push(parent.tagName + (parent.className ? '.' + parent.className.split(' ').join('.') : ''));\n" +
                    "            parent = parent.parentElement;\n" +
                    "        }\n" +
                    "        result += `Path ${index}: ` + paths.slice(0, 5).join(' < ') + '\\n';\n" +
                    "    });\n" +
                    "    return result;\n" +
                    "}"
                );
                System.out.println("[Diagnose DOM] " + diagnostic);
            } catch (Exception e) {
                System.out.println("[Diagnose DOM] Lỗi chẩn đoán: " + e.getMessage());
            }

            int currentPage = 1;
            int maxPages = 3; // Giới hạn tối đa 3 trang kết quả để tránh quét quá nhiều

            while (currentPage <= maxPages) {
                // Bắt đầu cào dữ liệu từ bảng kết quả trang hiện tại
                Locator rows = page.locator("tr.ant-table-row");
                int rowCount = rows.count();
                
                if (rowCount == 0) {
                    // Thử tìm theo cấu trúc div nếu Shopee đổi giao diện dạng card
                    rows = page.locator(".ant-list-item");
                    rowCount = rows.count();
                }

                if (rowCount == 0) {
                    // Tìm các cột/thẻ ant-col hoặc thẻ card chứa nút lấy link (Cực kỳ tổng quát)
                    rows = page.locator("//div[(contains(@class, 'ant-col') or contains(@class, 'ant-list-item') or contains(@class, 'card') or contains(@class, 'item')) and .//*[contains(text(), 'Link') or contains(text(), 'Lấy')]]");
                    rowCount = rows.count();
                }

                System.out.println("Trang " + currentPage + ": Tìm thấy " + rowCount + " dòng/ô kết quả hiển thị trên trang.");

                for (int i = 0; i < rowCount; i++) {
                    Locator row = rows.nth(i);
                    
                    // Trích xuất hình ảnh
                    Locator imgLocator = row.locator("img").first();
                    String imageUrl = "";
                    if (imgLocator.count() > 0) {
                        imageUrl = imgLocator.getAttribute("src");
                    }

                    // Trích xuất tiêu đề sản phẩm
                    String rowText = row.innerText();
                    String title = parseProductTitle(rowText);

                    // Tìm nút "Lấy Link" hoặc "Get Link" (chấp nhận cả button, a, [role='button'], .ant-btn có chứa text chính xác)
                    Locator btn = row.locator("button, a, [role='button'], .ant-btn")
                            .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^(Get Link|Lấy Link|Lấy link)$", java.util.regex.Pattern.CASE_INSENSITIVE)))
                            .first();

                    if (btn.count() == 0) {
                        // Dự phòng nếu text có chứa khoảng trắng thừa hoặc cấu trúc lồng nhau phức tạp
                        btn = row.locator("button, a, [role='button'], .ant-btn")
                                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile(".*(Get Link|Lấy Link|Lấy link).*", java.util.regex.Pattern.CASE_INSENSITIVE)))
                                .first();
                    }

                    if (btn.count() == 0) {
                        // Dự phòng cuối cùng: Tìm bất kỳ span nào có chứa chữ và nằm trong cấu trúc nút
                        btn = row.locator("button span, a span, .ant-btn span")
                                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile(".*(Link|Lấy).*", java.util.regex.Pattern.CASE_INSENSITIVE)))
                                .first();
                    }
                    
                    if (btn.count() == 0) {
                        btn = row.locator("button, a").first(); // Fallback nút/link đầu tiên của dòng
                    }

                    if (btn.count() > 0 && !imageUrl.isEmpty() && !title.isEmpty()) {
                        // Kiểm tra tránh trùng lặp sản phẩm giữa các trang
                        boolean alreadyExist = false;
                        for (ShopeeProduct existing : products) {
                            if (existing.getTitle().equalsIgnoreCase(title)) {
                                alreadyExist = true;
                                break;
                            }
                        }
                        if (!alreadyExist) {
                            products.add(new ShopeeProduct(title, imageUrl, btn));
                        }
                    }
                }

                // Tìm nút trang tiếp theo bằng cách duyệt qua toàn bộ ứng viên trên DOM để tìm nút hiển thị và hoạt động
                Locator nextBtn = null;
                Locator nextBtnCandidates = page.locator("li.ant-pagination-next button, li.ant-pagination-next a, button.ant-pagination-next");
                int candidateCount = nextBtnCandidates.count();
                for (int c = 0; c < candidateCount; c++) {
                    Locator cand = nextBtnCandidates.nth(c);
                    if (cand.isVisible()) {
                        String parentClass = "";
                        try {
                            parentClass = (String) cand.evaluate("el => el.parentElement ? el.parentElement.className : ''");
                        } catch (Exception e) {}
                        
                        boolean parentDisabled = parentClass.contains("ant-pagination-disabled");
                        boolean selfDisabled = cand.getAttribute("disabled") != null || 
                                               (cand.getAttribute("class") != null && cand.getAttribute("class").contains("disabled"));
                        
                        if (!parentDisabled && !selfDisabled) {
                            nextBtn = cand;
                            break;
                        }
                    }
                }

                if (nextBtn != null) {
                    System.out.println("  -> Phát hiện còn trang kết quả tiếp theo. Đang bấm chuyển sang trang " + (currentPage + 1) + "...");
                    try {
                        nextBtn.click();
                        page.waitForTimeout(3000); // Tăng thời gian chờ tải trang mới lên 3s để tải bảng kết quả
                        currentPage++;
                    } catch (Exception e) {
                        System.err.println("  -> Không thể click chuyển trang tiếp theo: " + e.getMessage());
                        break;
                    }
                } else {
                    System.out.println("  -> Không còn trang kết quả tiếp theo (nút Next bị vô hiệu hóa hoặc không tìm thấy). Kết thúc cào tìm kiếm.");
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Lỗi khi tìm kiếm sản phẩm: " + e.getMessage());
            e.printStackTrace();
        }

        return products;
    }

    /**
     * Tìm kiếm sản phẩm trên Shopee Affiliate, phân trang và đối sánh/lấy link trực tiếp từng trang.
     * Cách tiếp cận này giúp tránh lỗi stale element của Playwright khi chuyển trang.
     */
    public List<String> searchAndProcessAffiliateLinks(
            String query, 
            GeminiVlmComparator vlmComparator, 
            List<File> extractedFrames, 
            int maxLinks, 
            List<String> existingLinks,
            java.util.function.BooleanSupplier isCancelled
    ) {
        List<String> resultsLinks = new ArrayList<>(existingLinks);
        
        try {
            // Nhập ô tìm kiếm và Enter
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
                return resultsLinks;
            }

            searchInput.focus();
            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
            page.waitForTimeout(200);
            
            searchInput.fill(query);
            page.waitForTimeout(300);
            page.keyboard().press("Enter");

            System.out.println("Đã gửi lệnh tìm kiếm, chờ tải dữ liệu kết quả...");
            page.waitForTimeout(3000);

            // Tự động sắp xếp theo Giá từ Cao đến Thấp (Price: High to Low) để ưu tiên sản phẩm chính giá trị cao
            try {
                Locator priceSortTrigger = page.locator("label.ant-radio-button-wrapper .ant-select, .ant-radio-button-wrapper .ant-select-selection").first();
                if (priceSortTrigger.count() > 0 && priceSortTrigger.isVisible()) {
                    System.out.println("Đang click mở menu sắp xếp giá...");
                    priceSortTrigger.click();
                    page.waitForTimeout(1000); // Chờ menu dropdown xuất hiện

                    // Tìm tùy chọn thứ 2 ("High to Low" / "Cao đến Thấp")
                    Locator highToLowOpt = page.locator("li.ant-select-dropdown-menu-item:has-text('High to Low'), li.ant-select-dropdown-menu-item:has-text('Cao đến Thấp'), li.ant-select-dropdown-menu-item:nth-child(2)").first();
                    if (highToLowOpt.count() > 0 && highToLowOpt.isVisible()) {
                        System.out.println("Đang chọn chế độ sắp xếp: Giá từ Cao đến Thấp (Price: High to Low)...");
                        highToLowOpt.click();
                        page.waitForTimeout(3000); // Chờ tải lại danh sách đã sắp xếp
                    }
                }
            } catch (Exception e) {
                System.err.println("Cảnh báo: Không thể thực hiện sắp xếp giá (mục sắp xếp có thể không hiển thị hoặc cấu trúc thay đổi): " + e.getMessage());
            }

            int currentPage = 1;
            int maxPages = 3;

            while (currentPage <= maxPages) {
                if (isCancelled.getAsBoolean()) {
                    System.out.println("Tiến trình bị dừng bởi người dùng.");
                    break;
                }

                if (resultsLinks.size() >= maxLinks) {
                    System.out.println("Đã lấy đủ số link yêu cầu (" + maxLinks + "). Dừng quét trang.");
                    break;
                }

                // Cào dữ liệu từ bảng kết quả trang hiện tại
                Locator rows = page.locator("tr.ant-table-row");
                int rowCount = rows.count();
                
                if (rowCount == 0) {
                    rows = page.locator(".ant-list-item");
                    rowCount = rows.count();
                }

                if (rowCount == 0) {
                    rows = page.locator("//div[(contains(@class, 'ant-col') or contains(@class, 'ant-list-item') or contains(@class, 'card') or contains(@class, 'item')) and .//*[contains(text(), 'Link') or contains(text(), 'Lấy')]]");
                    rowCount = rows.count();
                }

                System.out.println("Trang " + currentPage + ": Tìm thấy " + rowCount + " dòng/ô kết quả hiển thị trên trang.");

                List<ShopeeProduct> pageCandidates = new ArrayList<>();

                for (int i = 0; i < rowCount; i++) {
                    if (isCancelled.getAsBoolean()) break;
                    
                    Locator row = rows.nth(i);
                    
                    Locator imgLocator = row.locator("img").first();
                    String imageUrl = "";
                    if (imgLocator.count() > 0) {
                        imageUrl = imgLocator.getAttribute("src");
                    }

                    String rowText = row.innerText();
                    String title = parseProductTitle(rowText);

                    Locator btn = row.locator("button, a, [role='button'], .ant-btn")
                            .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^(Get Link|Lấy Link|Lấy link)$", java.util.regex.Pattern.CASE_INSENSITIVE)))
                            .first();

                    if (btn.count() == 0) {
                        btn = row.locator("button, a, [role='button'], .ant-btn")
                                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile(".*(Get Link|Lấy Link|Lấy link).*", java.util.regex.Pattern.CASE_INSENSITIVE)))
                                .first();
                    }

                    if (btn.count() == 0) {
                        btn = row.locator("button span, a span, .ant-btn span")
                                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile(".*(Link|Lấy).*", java.util.regex.Pattern.CASE_INSENSITIVE)))
                                .first();
                    }
                    
                    if (btn.count() == 0) {
                        btn = row.locator("button, a").first();
                    }

                    if (btn.count() > 0 && !imageUrl.isEmpty() && !title.isEmpty()) {
                        System.out.println("  * Tiền lọc văn bản: " + title);
                        if (TextMatcher.isTextMatch(query, title)) {
                            pageCandidates.add(new ShopeeProduct(title, imageUrl, btn));
                            System.out.println("    => [OK] Đạt tiêu chuẩn lọc từ khóa.");
                        } else {
                            System.out.println("    => [LOẠI] Không khớp từ khóa.");
                        }
                    }
                }

                // Nếu trang hiện tại có ứng viên đạt tiêu chuẩn lọc từ khóa, tiến hành so sánh ảnh AI ngay tại trang này
                if (!pageCandidates.isEmpty() && !isCancelled.getAsBoolean()) {
                    System.out.println("Tiến hành đối sánh hình ảnh bằng AI cho " + pageCandidates.size() + " ứng viên trang " + currentPage + "...");
                    Map<Integer, GeminiVlmComparator.MatchResult> batchResults = vlmComparator.compareImagesBatch(
                            extractedFrames, pageCandidates, query
                    );

                    for (int i = 0; i < pageCandidates.size(); i++) {
                        if (isCancelled.getAsBoolean()) break;
                        if (resultsLinks.size() >= maxLinks) {
                            break;
                        }

                        ShopeeProduct candidate = pageCandidates.get(i);
                        GeminiVlmComparator.MatchResult matchResult = batchResults.getOrDefault(i + 1, new GeminiVlmComparator.MatchResult(false, 0.0, "Không có kết quả"));

                        System.out.println("  * Kết quả đối sánh AI cho: " + candidate.getTitle());
                        System.out.println("    -> Khớp: " + matchResult.isMatch() + 
                                " (Độ tin cậy: " + String.format("%.2f", matchResult.getConfidence()) + ")");
                        System.out.println("    -> Lý do: " + matchResult.getReason());

                        if (matchResult.isMatch() && matchResult.getConfidence() >= 0.75) {
                            System.out.println("    => [CHẤP NHẬN] Sản phẩm trùng khớp! Tiến hành lấy link affiliate...");
                            String newLink = getAffiliateLink(candidate.getGetLinkButton());
                            if (newLink != null) {
                                String cleanNew = cleanShopeeLink(newLink);
                                boolean isDuplicate = false;
                                for (String existing : resultsLinks) {
                                    if (cleanShopeeLink(existing).equalsIgnoreCase(cleanNew)) {
                                        isDuplicate = true;
                                        break;
                                    }
                                }
                                if (!isDuplicate) {
                                    resultsLinks.add(newLink);
                                } else {
                                    System.out.println("      -> [BỎ QUA] Link đã tồn tại trong file metadata.txt (trùng mã liên kết).");
                                }
                            }
                        } else {
                            System.out.println("    => [LOẠI] AI kết luận không khớp hoặc độ tin cậy thấp.");
                        }
                    }
                }

                if (resultsLinks.size() >= maxLinks) {
                    System.out.println("Đã lấy đủ số link yêu cầu (" + maxLinks + "). Kết thúc quét trang.");
                    break;
                }

                // Tìm nút trang tiếp theo bằng cách duyệt qua toàn bộ ứng viên trên DOM để tìm nút hiển thị và hoạt động
                Locator nextBtn = null;
                Locator nextBtnCandidates = page.locator("li.ant-pagination-next button, li.ant-pagination-next a, button.ant-pagination-next");
                int candidateCount = nextBtnCandidates.count();
                for (int c = 0; c < candidateCount; c++) {
                    Locator cand = nextBtnCandidates.nth(c);
                    if (cand.isVisible()) {
                        String parentClass = "";
                        try {
                            parentClass = (String) cand.evaluate("el => el.parentElement ? el.parentElement.className : ''");
                        } catch (Exception e) {}
                        
                        boolean parentDisabled = parentClass.contains("ant-pagination-disabled");
                        boolean selfDisabled = cand.getAttribute("disabled") != null || 
                                               (cand.getAttribute("class") != null && cand.getAttribute("class").contains("disabled"));
                        
                        if (!parentDisabled && !selfDisabled) {
                            nextBtn = cand;
                            break;
                        }
                    }
                }

                if (nextBtn != null) {
                    System.out.println("  -> Phát hiện còn trang kết quả tiếp theo. Đang bấm chuyển sang trang " + (currentPage + 1) + "...");
                    try {
                        nextBtn.click();
                        page.waitForTimeout(3000); // Tăng thời gian chờ tải trang mới lên 3s để tải bảng kết quả
                        currentPage++;
                    } catch (Exception e) {
                        System.err.println("  -> Không thể click chuyển trang tiếp theo: " + e.getMessage());
                        break;
                    }
                } else {
                    System.out.println("  -> Không còn trang kết quả tiếp theo (nút Next bị vô hiệu hóa hoặc không tìm thấy). Kết thúc cào tìm kiếm.");
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Lỗi khi tìm kiếm và xử lý link: " + e.getMessage());
            e.printStackTrace();
        }

        return resultsLinks;
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
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi đóng Playwright: " + e.getMessage());
        }
    }

    /**
     * Chuẩn hóa và làm sạch link affiliate Shopee bằng cách loại bỏ các tham số tracking tùy chọn (từ dấu '?' trở đi).
     */
    private String cleanShopeeLink(String link) {
        if (link == null) return "";
        String trimmed = link.trim();
        int qMarkIdx = trimmed.indexOf('?');
        if (qMarkIdx != -1) {
            return trimmed.substring(0, qMarkIdx).trim();
        }
        return trimmed;
    }
}
