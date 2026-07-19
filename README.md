# Shopee Affiliate Link Finder 🚀

Ứng dụng tự động hóa cào dữ liệu, đối sánh hình ảnh bằng Trí tuệ nhân tạo (Gemini VLM) và trích xuất liên kết Affiliate từ video review sản phẩm lên hệ thống Shopee Affiliate.

---

## 🌟 Các tính năng nổi bật

1. **CDP Mode (Chrome DevTools Protocol) & 1-Click Bat Launcher**:
   - Sử dụng tệp tiện ích `Mo_Chrome_Debug.bat` tự động phát hiện và mở Chrome thật dưới dạng tiến trình độc lập, giúp vượt qua **100% cơ chế chống robot và chặn captcha** của Shopee/Akamai.
2. **Đối sánh hình ảnh bằng AI (Gemini 2.5 Flash VLM - Batch Mode)**:
   - Tự động trích xuất các khung hình chính từ video review sản phẩm (`.mp4`) bằng thư viện JavaCV.
   - Gửi gộp song song (Batch Mode) toàn bộ ảnh trích xuất và ảnh sản phẩm ứng cử viên trên Shopee lên Gemini API để phân tích các chi tiết vật lý (bố cục bàn phím, cổng kết nối, logo, hình dáng vỏ hộp...) để đưa ra kết luận trùng khớp chính xác nhất.
3. **Lọc lệch phụ kiện thông minh (Accessory Filter)**:
   - Tự động phát hiện và loại bỏ các sản phẩm là phụ kiện (như sạc, cáp, túi chống sốc, ốp lưng, dán màn hình...) khi truy vấn gốc của bạn là sản phẩm chính (như laptop, máy ảnh, điện thoại...).
4. **Hỗ trợ thu thập sản phẩm nhiều trang (Pagination)**:
   - Tự động click chuyển trang kết quả tìm kiếm trên Shopee Affiliate (lên tới 3 trang) để tìm kiếm đầy đủ và tối ưu nhất, đồng thời tự động lọc trùng lặp sản phẩm giữa các trang.
5. **Giao diện thân thiện & Tự lưu cấu hình (Auto-save State)**:
   - Giao diện tối giản với công nghệ FlatLaf Dark Mode.
   - Tự động lưu lại mọi lựa chọn cấu hình gần nhất (Thư mục đầu vào, API Key, số link, số ảnh cắt...) vào tệp `config.properties` để bạn không cần thiết lập lại ở những lần chạy sau.

---

## 📋 Yêu cầu hệ thống

- **Hệ điều hành**: Windows (đã được cấu hình tối ưu sẵn các đường dẫn).
- **Java**: JDK 17 trở lên.
- **Maven**: Đã được cài đặt và cấu hình biến môi trường (`path`).
- **Trình duyệt**: Google Chrome bản mới nhất.
- **Gemini API Key**: Nhận miễn phí tại [Google AI Studio](https://aistudio.google.com/).

---

## 🛠️ Hướng dẫn sử dụng chi tiết (Step-by-Step)

### Bước 1: Chuẩn bị trình duyệt Chrome sạch (Debug Mode)
1. **Tắt hoàn toàn** tất cả các cửa sổ trình duyệt Google Chrome thông thường đang mở trên máy tính của bạn (để giải phóng cổng debug `9222`).
2. Vào thư mục dự án và click đúp vào file **`Mo_Chrome_Debug.bat`**.
3. Cửa sổ Chrome thật sẽ tự động mở lên. Tiến hành đăng nhập vào trang [Shopee Affiliate](https://affiliate.shopee.vn/) và di chuyển đến trang **Tìm kiếm Sản phẩm** (Product Offer).

### Bước 2: Chuẩn bị dữ liệu sản phẩm đầu vào
Cấu trúc thư mục đầu vào phải có dạng như sau:
```
Thư mục chính chứa sản phẩm/
├── Sản phẩm 1/
│   ├── metadata.txt (Chứa tên sản phẩm và các link affiliate đã có)
│   └── video_review.mp4 (Video review thực tế của sản phẩm)
├── Sản phẩm 2/
│   ├── metadata.txt
│   └── video_review.mp4
```
*Lưu ý: Tệp `metadata.txt` cần có định dạng:*
```txt
productName=Tên sản phẩm cụ thể muốn tìm kiếm
affiliateLinks=
```

### Bước 3: Khởi chạy ứng dụng Java
Mở PowerShell hoặc Command Prompt tại thư mục dự án và chạy lệnh sau để khởi động giao diện đồ họa (GUI):
```powershell
mvn exec:java
```

### Bước 4: Thiết lập cấu hình và Chạy
1. **Thư mục sản phẩm**: Nhấn nút **Chọn thư mục...** và dẫn đến Thư mục chính chứa các thư mục sản phẩm con của bạn.
2. **Gemini API Key**: Dán mã API Key của bạn vào ô nhập liệu.
3. **Thiết lập nâng cao**:
   - *Số link lấy tối đa*: Số link affiliate bạn muốn App cào được cho mỗi sản phẩm (Mặc định là 6).
   - *Số ảnh cắt từ video*: Số khung hình trích xuất từ video gửi lên AI đối sánh (Khuyên dùng: 4 - 5 ảnh để đảm bảo bắt trọn các góc sản phẩm).
4. Nhấn nút **Bắt đầu xử lý**.
5. Quay lại cửa sổ Chrome xem tiến trình tìm kiếm tự động. Khi App tìm kiếm xong, một hộp thoại pop-up sẽ hiện lên trên Java App yêu cầu bạn xác nhận:
   - Nếu bạn đã đăng nhập và Chrome đang hiển thị trang kết quả tìm kiếm đúng sản phẩm, nhấn **YES** trên hộp thoại.
   - App sẽ tự động thực hiện trích xuất ảnh video, tải ảnh Shopee, gọi Gemini AI đối sánh và tự click lấy link affiliate ngắn dán ngược lại vào tệp `metadata.txt` của sản phẩm đó.

---

## ❓ Xử lý sự cố thường gặp (Troubleshooting)

#### 1. Lỗi: "Không thể kết nối đến Chrome đang mở (cổng 9222)"
*   **Nguyên nhân**: Cổng debug `9222` đang bị chiếm bởi một phiên bản Chrome thông thường chưa tắt hẳn hoặc do file `.bat` chưa được chạy.
*   **Cách xử lý**: Tắt toàn bộ cửa sổ Chrome, mở Task Manager (nhấn `Ctrl + Shift + Esc`), tìm các tiến trình `Google Chrome` chạy ngầm và nhấn **End Task** để tắt sạch. Sau đó chạy lại file `Mo_Chrome_Debug.bat`.

#### 2. Lỗi anti-bot xuất hiện (Trang xác minh captcha)
*   **Nguyên nhân**: Shopee phát hiện các hành động điều hướng quá nhanh hoặc do trình duyệt được khởi động trực tiếp từ mã nguồn Java.
*   **Cách xử lý**: Đảm bảo bạn **không** khởi chạy Chrome từ code. Hãy luôn mở trình duyệt thông qua việc click đúp tệp `Mo_Chrome_Debug.bat`. Trong quá trình App chạy, không tự ý click chuột hoặc reload trang Chrome để tránh làm nhiễu tiến trình tự động hóa.

#### 3. AI đánh giá không trùng khớp (Kết quả khớp: false)
*   **Nguyên nhân**: Video review chỉ hiển thị vỏ hộp, hoặc góc quay quá tối/mờ không rõ đặc trưng sản phẩm. Hoặc ảnh đại diện của shop Shopee đăng lên chỉ là logo hãng/ảnh không liên quan.
*   **Cách xử lý**: Bạn có thể tăng số lượng ảnh trích xuất từ video lên `5` hoặc `6` trong cấu hình để tăng khả năng chụp trúng chi tiết sản phẩm.
