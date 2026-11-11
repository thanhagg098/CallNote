# BÁO CÁO KẾT QUẢ THỰC HIỆN THÁNG 10/2026

**Dự án:** Ghi chú cuộc gọi (CallNote) – app Android chép lời cuộc gọi offline bằng whisper.cpp
**Kỳ báo cáo:** 01/10/2026 – 31/10/2026
**Nguồn số liệu:** lịch sử commit Git của repo (11 commit, 29/10/2026, 07:11 – 16:41)

---

## 1. Tóm tắt

Trong tháng 10, dự án đi từ con số 0 đến bản app dùng được: chép lời cuộc gọi Samsung và Zalo hoàn toàn offline trên máy, gom theo ngày, gửi bản tổng hợp cho Hermes (Telegram) hoặc Claude để lập danh sách việc cần làm. Có hai lần đổi hướng thiết kế (chép theo yêu cầu, rồi tổng hợp theo ngày) nhằm đơn giản thao tác và giảm tải máy.

| Chỉ số | Kết quả |
|---|---|
| Số commit | 11 |
| Số ngày làm việc có commit | 1 (29/10) |
| Tổng thay đổi mã | ~2.800 dòng thêm, ~680 dòng xoá |
| Cột mốc chính | Bản đầu → tổng hợp theo ngày → nâng độ chính xác chép lời |

## 2. Kết quả theo hạng mục

### 2.1 Nền tảng (hoàn thành)
- Dựng app Kotlin + Jetpack Compose (minSdk 29, arm64-v8a), tích hợp whisper.cpp v1.7.6 qua JNI, tối ưu armv8.2 fp16 + dotprod.
- Lưu dữ liệu SQLite cục bộ; khoá ký cố định để bản mới cài đè bản cũ.
- CI GitHub Actions tự build APK, đã chuyển sang dùng Android SDK có sẵn trên runner; có workflow tạo mô hình PhoWhisper.

### 2.2 Chép lời
- Mặc định dùng mô hình **PhoWhisper tiếng Việt** (VinAI, ~190 MB).
- Nâng độ chính xác: beam search, lọc chống méo khi đổi tần số lấy mẫu, chuẩn hoá âm lượng, cắt cụm từ lặp.
- Chọn nhanh mô hình small/medium trong cài đặt.
- Bỏ qua cuộc ngắn hơn 20 giây; cuộc lỗi được thử lại ở lần tổng hợp sau.

### 2.3 Hỗ trợ cuộc gọi Zalo (Cube ACR)
- Chép lời cuộc gọi Zalo từ thư mục Cube ACR; chỉ lấy file của Zalo để không trùng với cuộc gọi thường.
- Tự bật Cube ACR Helper khi có cuộc gọi Zalo, tự tắt 15 giây sau khi cúp máy – tránh app ngân hàng/chứng khoán cảnh báo quyền trợ năng.
- Thêm nhật ký Cube ACR để kiểm tra việc tự bật/tắt; luôn tắt Helper khi không có cuộc gọi Zalo.

### 2.4 Trải nghiệm và luồng làm việc
- Đổi từ chép nền sang **chép theo yêu cầu** (app không chạy nền, không quét định kỳ).
- Đổi tiếp sang **tổng hợp theo ngày**: màn hình hiện file ghi âm của ngày chọn; một nút **Tổng hợp ngày** tự chép các file chưa có lời rồi mở bảng gửi.
- Bản tổng hợp gồm: tổng số cuộc/phút, người gọi nhiều, danh sách cuộc theo giờ kèm lời từng cuộc, và lời dặn lập danh sách việc cần làm.
- Nhắc 21h mỗi tối "Hôm nay có N cuộc" (tuỳ chọn); sửa lỗi nút 30 ngày bị dựng đứng.
- Cam kết quyền riêng tư: file ghi âm không rời điện thoại, chỉ gửi văn bản khi bấm "Gửi tổng hợp".

## 3. Đối chiếu tiến độ

| Hạng mục | Trạng thái |
|---|---|
| Chép lời offline trên máy | Hoàn thành |
| Mô hình tiếng Việt mặc định | Hoàn thành |
| Hỗ trợ Zalo qua Cube ACR + tự bật/tắt | Hoàn thành (cần cấp quyền WRITE_SECURE_SETTINGS qua adb) |
| Tổng hợp theo ngày và gửi Telegram/Claude | Hoàn thành |
| Tự động build APK | Hoàn thành |

## 4. Tồn tại và rủi ro

- Chưa có số liệu đo độ chính xác chép lời (tỷ lệ lỗi từ) và thời gian chép trên máy thật; các cải tiến ở 2.2 chưa được định lượng.
- Chưa có kiểm thử tự động trong repo; kiểm tra hiện chủ yếu thủ công trên S24 Ultra.
- Tự bật Cube ACR phụ thuộc quyền đặc biệt cấp bằng adb và quyền đọc thông báo; Android có thể chặn "cài đặt bị hạn chế".
- Mô hình ~190 MB phải tải qua Wi‑Fi, phụ thuộc link ở Releases.

## 5. Kế hoạch tháng 11/2026

1. Đo thực tế: độ chính xác và thời gian chép trên một bộ cuộc gọi mẫu, ghi lại làm mốc so sánh.
2. Theo dõi nhật ký Cube ACR vài tuần để xác nhận tự bật/tắt ổn định.
3. Thêm kiểm thử cho phần gom bản tổng hợp và làm sạch văn bản.
4. Cân nhắc cải tiến theo phản hồi sử dụng thực tế (lọc người gọi, định dạng bản tổng hợp).

---
*Ghi chú: báo cáo chỉ dựa trên lịch sử Git. Số liệu vận hành (số cuộc gọi đã chép, số phút, tỷ lệ lỗi) chưa có trong repo; nếu bạn cung cấp, tôi sẽ bổ sung.*
