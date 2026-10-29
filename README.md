# Ghi chú cuộc gọi

App Android tự chép lời các cuộc gọi đã ghi âm trên Samsung, **chạy offline ngay trên máy** (whisper.cpp), rồi gom thành bảng để gửi cho Hermes (Telegram) hoặc Claude lập danh sách việc cần làm.

File ghi âm **không bao giờ rời khỏi điện thoại**. Chỉ phần văn bản được gửi đi, và chỉ khi bạn bấm "Gửi tổng hợp".

## Cách hoạt động

App **không chạy nền, không quét định kỳ** — chỉ làm việc khi bạn bấm.

1. Samsung tự ghi âm cuộc gọi vào `Recordings/Call` như bình thường.
2. Mở app → màn hình hiện các file ghi âm **của ngày đang chọn** (mặc định hôm nay; đổi ngày bằng mũi tên ‹ › hoặc lịch).
3. Bấm **Chép lời N cuộc** → app chép lời tiếng Việt bằng mô hình whisper ngay trên máy (bỏ qua cuộc ngắn hơn 20 giây). Có thể thoát app, xong sẽ có thông báo.
4. Bấm **Gửi tổng hợp** (hoặc bấm vào thông báo "Đã tổng hợp…") → chọn Telegram gửi cho bot Hermes, hoặc dán vào Claude. Bản tổng hợp gồm: tổng số cuộc/số phút, người gọi nhiều, danh sách cuộc theo giờ và lời từng cuộc, kèm lời dặn "tổng hợp tình hình trong ngày + lập danh sách việc cần làm".
5. Tuỳ chọn: 21h mỗi tối app nhắc "Hôm nay có N cuộc" (chỉ đếm file, không chép lời).

## Build APK (GitHub tự build)

Đẩy mã lên nhánh `main` → tab **Actions** chạy "Build APK" (~10 phút) → file `GhiChuCuocGoi.apk` nằm ở mục **Releases → app-latest**.

## Mô hình chép lời

- Mặc định: PhoWhisper-small (VinAI, chuyên tiếng Việt, ~190 MB), lấy từ Releases → `model`.
- Dự phòng: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin` (whisper đa ngôn ngữ).
- Tốt hơn cho tiếng Việt: chạy workflow **"Tạo mô hình PhoWhisper"** trong tab Actions (chọn `small`), xong thì copy link file `.bin` ở Releases → `model`, dán vào app: Cài đặt → Link tải mô hình → Tải mô hình.

## Cài đặt trên điện thoại (S24 Ultra)

1. Tải `GhiChuCuocGoi.apk`, mở file → cho phép "Cài ứng dụng không rõ nguồn".
2. Mở app → cấp quyền **Âm thanh** và **Thông báo**.
3. Cài đặt → **Tải mô hình** (dùng Wi‑Fi) → chờ tải xong.
4. Không cần cho phép chạy nền nữa (bản cũ cần). Nếu trước đây đã thêm app vào "Ứng dụng không bao giờ ngủ" thì có thể gỡ ra.

## Tự bật Cube ACR khi gọi Zalo

App ngân hàng/chứng khoán (VPS SmartOne…) cảnh báo khi Cube ACR Helper bật quyền trợ năng. App này giữ Cube **tắt** bình thường, **tự bật** khi có cuộc gọi Zalo và tự tắt 15 giây sau khi cúp máy.

Thiết lập 1 lần:
1. Cài đặt app → mục "Tự bật Cube ACR khi gọi Zalo" → **Cấp quyền đọc thông báo** → bật "Ghi chú cuộc gọi". (Nếu bị chặn "cài đặt bị hạn chế": Cài đặt máy → Ứng dụng → Ghi chú cuộc gọi → ⋮ → Cho phép cài đặt bị hạn chế.)
2. Cấp quyền đặc biệt bằng máy tính (bật Gỡ lỗi USB, cắm cáp):
   `adb shell pm grant vn.thanh.callnote android.permission.WRITE_SECURE_SETTINGS`
3. Bật công tắc **Tự bật khi có cuộc gọi Zalo**.

## Ghi chú kỹ thuật

- Kotlin + Jetpack Compose, minSdk 29, chỉ build arm64-v8a.
- whisper.cpp v1.7.6 qua JNI (`app/src/main/cpp`), tối ưu armv8.2 fp16 + dotprod.
- Dữ liệu lưu SQLite trong máy (`callnote.db`).
- Khoá ký `keystore/callnote.jks` cố định để bản mới cài đè được bản cũ.
