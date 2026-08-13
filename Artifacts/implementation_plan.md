# Implementation Plan: Tối ưu Widget Kệ Đóng Băng và khả năng khám phá AI

## 1. Mục tiêu Widget
- Widget chỉ hiển thị các ứng dụng trong Kệ Đóng Băng, không còn header, tiêu đề, số lượng hay nút `+/-` phía trên.
- Ô Cài đặt có hình thức giống một ứng dụng và luôn nằm ở vị trí cuối cùng trong lưới.
- Thêm/xóa ứng dụng khỏi Kệ được thực hiện trong màn hình Cài đặt Widget, không dùng trạng thái xóa nhanh lưu trong preferences.

## 2. Luồng Widget mới
1. `widget_frozen_apps.xml` chỉ còn `GridView` phủ toàn bộ vùng nội dung.
2. `FreezerWidgetFactory` trả về danh sách ứng dụng và thêm một item đặc biệt “Cài đặt” ở cuối.
3. Bấm ứng dụng vẫn rã đông và mở ứng dụng như hiện tại.
4. Bấm ô Cài đặt chuyển qua `UnfreezeAndLaunchActivity` để mở `WidgetConfigActivity` đúng widget ID.
5. Bấm vùng trống ngoài item vẫn mở Kệ Đóng Băng theo quy tắc hiện tại.
6. Xóa hoàn toàn `widget_delete_mode`, broadcast toggle và badge xóa nhanh.

## 3. Quản lý ứng dụng trong Cài đặt Widget
- Thêm thẻ “Ứng dụng trên Kệ” ở đầu màn hình cấu hình.
- Nút “Thêm ứng dụng” mở bộ chọn ứng dụng hiện có.
- Danh sách ứng dụng đang nằm trên Kệ có nút xóa trực tiếp từng app.
- Mọi thao tác thêm/xóa cập nhật Room và widget ngay, không phụ thuộc nút lưu giao diện.
- Preview được sửa theo thiết kế mới: chỉ app và ô Cài đặt cuối lưới.

## 4. Rà soát AI và cải thiện UX
- Giữ kiến trúc hiện có: Gemini/Groq/OpenRouter, function calling, đọc dữ liệu thật, thay đổi cài đặt có xác nhận.
- Sửa tiêu đề Chat hiển thị đúng provider đang dùng thay vì cố định “Gemini”.
- Biến các use case hữu ích thành nút hỏi nhanh: tổng quan máy, hao pin, phiên sạc, app dùng nhiều, trạng thái NOBG và đổi chủ đề.
- Cập nhật tài liệu use case theo đúng năng lực tool thực tế.

## 5. Kiểm chứng
- Không còn tham chiếu đến title/header/nút `+/-`/delete mode của widget.
- `assembleDebug`, unit test và `lintDebug` thành công.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
