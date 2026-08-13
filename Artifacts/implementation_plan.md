# Implementation Plan: Timer Widget 1x1 và cấu hình chế độ nhanh

## 1. Mục tiêu
- Giữ Timer Widget ở đúng kích thước lưới 1x1 và hiển thị thành một ô vuông bo góc, không kéo nền thành hình chữ nhật theo kích thước host.
- Cho phép cấu hình riêng thao tác nhanh của widget ngay trong màn hình Đếm giờ thông minh.
- Loại bỏ hoàn toàn tùy chọn và hành vi tự tắt máy khi hết thời gian.
- Rà soát, sửa các lỗi tràn/chật bố cục trên màn hình hẹp.

## 2. Thay đổi dữ liệu và luồng xử lý
1. Thêm `SmartTimerQuickConfig` độc lập với cấu hình phiên Timer đang dùng, gồm:
   - Loại giờ đọc: giờ thực tế hoặc thời gian đã trôi qua.
   - Tổng thời lượng chạy.
   - Chu kỳ phát thông báo bằng giọng đọc.
2. Lưu cấu hình nhanh trong `SharedPreferences` qua `NobgRepository`.
3. `SmartTimerViewModel` cung cấp `StateFlow` và các hàm cập nhật cấu hình nhanh cho Compose UI.
4. Khi bấm widget, `SmartTimerService` đọc cấu hình nhanh đã lưu thay cho preset hard-code.
5. Widget được cập nhật tức thì khi cấu hình nhanh thay đổi.

## 3. Thay đổi giao diện
1. `widget_smart_timer.xml`: dùng lớp ngoài trong suốt và một ô nội dung vuông 64dp nằm giữa, nền bo góc lớn.
2. `smart_timer_widget_info.xml`: khai báo rõ kích thước 1x1, không cho resize.
3. `SmartTimerScreen`:
   - Thêm thẻ “Cấu hình chế độ nhanh của Widget”.
   - Bỏ thẻ “Hẹn giờ tắt máy khi hết giờ”.
   - Thay preset tắt máy bằng preset đọc thời gian thông thường.
   - Chia lại các hàng chip/nút để không nhét quá nhiều phần tử vào một dòng.
4. Chỉnh mô tả Timer trên Dashboard và README cho đúng chức năng thực tế.

## 4. Loại bỏ tính năng tắt máy
- Xóa trường `autoShutdown`, preference cũ, action preset tắt máy và lệnh shell shutdown trong service.
- Khi lưu cấu hình mới, dọn khóa preference cũ để không còn trạng thái thừa sau nâng cấp.

## 5. Kiểm chứng
- Tìm toàn dự án để bảo đảm không còn UI/logic `autoShutdown` hoặc lệnh tắt máy của Timer.
- Chạy `assembleDebug` với JDK của Android Studio và bảo đảm 0 lỗi biên dịch.
- Kiểm tra diff, commit, push `main`, sau đó theo dõi GitHub Actions đến khi `SUCCESS`.
