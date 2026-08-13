# Implementation Plan: Chặn đọc theo ứng dụng + keyword

## 1. Mục tiêu
- Không tắt đọc toàn bộ ứng dụng khi người dùng chọn một notification trong lịch sử.
- Quy tắc chặn phải gồm đúng `packageName + userId + keyword`.
- Ví dụ: Messenger + `đang kiểm tra tin nhắn mới` chỉ bỏ qua trạng thái này; tin nhắn Messenger khác vẫn đọc.

## 2. Dữ liệu và xử lý service
- Thêm bảng Room `notification_block_rules` và migration database 9 → 10.
- Mỗi rule lưu package, không gian người dùng, keyword, tên app và thời điểm tạo.
- Service ghép tiêu đề/nội dung notification, so khớp keyword không phân biệt hoa thường.
- Chỉ bỏ qua TTS khi notification đến từ đúng app/không gian và chứa keyword của rule.
- Giữ nguyên cơ chế bỏ qua notification im lặng và lịch sử tối đa 200 mục.

## 3. UI/UX
- Chạm một mục lịch sử để mở hộp thoại tạo rule.
- Tự điền keyword từ nội dung notification nhưng cho phép người dùng sửa trước khi lưu.
- Hiển thị rõ rule theo dạng `Tên ứng dụng + keyword` và có nút xóa từng rule.
- Bỏ bộ lọc `Đã chặn` theo ứng dụng vì không còn đúng mô hình dữ liệu.
- Đổi nhãn keyword trong cấu hình app thành `Chỉ đọc khi có từ khóa` để phân biệt với keyword chặn.

## 4. Kiểm chứng
- Kiểm tra migration Room 9 → 10 và schema composite primary key.
- Kiểm tra cùng keyword ở app khác không bị chặn; cùng app nhưng nội dung khác vẫn được đọc.
- Kiểm tra thêm/xóa rule phản ánh tức thì trên UI.
- Chạy `assembleDebug`, unit test và `lintDebug`.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
