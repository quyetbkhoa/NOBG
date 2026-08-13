# Implementation Plan: Premium Android System UI redesign

## 1. Mục tiêu thiết kế
- Chuẩn hóa toàn bộ Compose UI theo ba tầng: canvas xám trắng, group card trắng, nội dung trung tính.
- Card phẳng, không border/shadow, bán kính 26dp; row cao tối thiểu 76dp và divider thụt sau icon.
- Typography nhẹ, title trang 34sp, title row 18sp, secondary text 14–15sp.
- Accent chỉ dành cho icon và trạng thái có ý nghĩa; loại bỏ cảm giác Material 3 mặc định.

## 2. Design system dùng chung
- Xây dựng palette sáng/tối tĩnh, typography, shape và spacing token trong `ui/theme`.
- Tạo component dùng chung: adaptive content container, large page header, flat group card,
  grouped navigation row, inset divider, pill search và section label.
- Mọi card Compose đi qua wrapper elevation 0 để đảm bảo không xuất hiện shadow trên các OEM khác nhau.

## 3. Responsive / foldable
- Giới hạn chiều rộng nội dung đọc được và căn giữa trên màn hình lớn; gutter đổi từ 20dp lên 28–32dp.
- Dashboard đổi từ một cột grouped rows sang hai pane khi cửa sổ đủ rộng.
- Danh sách/grid dùng số cột dựa trên chiều rộng khả dụng, không dựa vào model máy hoặc orientation.
- Giữ touch target tối thiểu 48dp và hỗ trợ text wrap/font scaling trên màn ngoài Find N3.

## 4. Phạm vi màn hình
- Trang chủ, quản lý ứng dụng, kệ đóng băng, thống kê pin/sạc.
- Đếm giờ, đọc thông báo, AI/trò chuyện, giải thuật, danh sách hệ thống, cài đặt.
- Dialog, bottom sheet, onboarding và màn cấu hình widget cũng dùng palette/shape/spacing mới.

## 5. Kiểm chứng
- Tìm và loại bỏ card elevation, border và gradient UI không phù hợp.
- Build `assembleDebug`, chạy test/lint phù hợp và sửa lỗi biên dịch.
- Commit, push `main`, theo dõi GitHub Actions tới khi `SUCCESS`.
