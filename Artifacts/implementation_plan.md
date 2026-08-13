# Implementation Plan: Notification xuyên suốt cho Smart Timer

## 1. Mục tiêu
- Smart Timer chỉ chạy khi ứng dụng có quyền hiển thị thông báo trên Android 13+.
- Trong toàn bộ thời gian Timer hoạt động phải có foreground notification cố định, không tự biến mất.
- Notification cung cấp đủ thông tin để người dùng kiểm tra Timer mà không cần mở ứng dụng.
- Người dùng có thể dừng Timer trực tiếp bằng nút `Dừng` trên notification.

## 2. Nội dung notification
- Hiển thị thời gian đã chạy theo `HH:mm:ss`.
- Nếu Timer có giới hạn, hiển thị thêm thời gian còn lại.
- Hiển thị chu kỳ đọc thông báo và chế độ đọc (`Thời gian đã đếm` hoặc `Giờ hiện tại`).
- Dùng giao diện mở rộng `BigTextStyle`, cập nhật định kỳ nhưng không phát âm/rung lại ở mỗi lần cập nhật.
- Đặt notification ở chế độ ongoing, category stopwatch, public visibility và foreground-immediate.

## 3. Quyền và vòng đời
- Kiểm tra `POST_NOTIFICATIONS` trong ViewModel trước khi khởi chạy service.
- Tại màn Timer, yêu cầu quyền ngay khi người dùng bấm Bắt đầu hoặc chọn preset; chỉ tiếp tục khi được cấp.
- Khi bấm Timer Widget mà chưa có quyền, mở màn Timer/onboarding để xin quyền thay vì chạy Timer ẩn.
- Service tự từ chối phiên chạy mới nếu được gọi từ luồng cũ mà quyền thông báo không còn.
- Khi hết giờ hoặc bấm Dừng, hủy foreground notification, lưu trạng thái đã dừng và cập nhật widget.

## 4. Kiểm chứng
- Kiểm tra start/stop từ màn Timer, preset và widget.
- Chạy `assembleDebug`, unit test và `lintDebug`.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
