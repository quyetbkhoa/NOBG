# Implementation Plan: Cân chỉnh kích thước Timer Widget và lề Kệ Đóng Băng

## 1. Mục tiêu
- Timer Widget 1x1 có kích thước thị giác tương đương icon ứng dụng trên launcher, không còn icon nhỏ nằm trong một khung lớn.
- Widget Kệ Đóng Băng có khoảng thở đều giữa nội dung và mép nền, không để icon dính sát lề.
- Giữ nguyên toàn bộ hành vi bấm và cấu hình đã triển khai.

## 2. Timer Widget
- Bỏ lớp nền vuông phụ và nhãn `NOBG` trùng với chữ đã có trong icon launcher.
- Hiển thị trực tiếp icon NOBG kích thước 56dp, tương đương icon ứng dụng Android.
- Đặt trạng thái ngắn bên dưới như nhãn launcher (`1h · 2p`, `Còn 15p`, `Đang đếm`).
- Dùng chữ trắng có bóng tối để đọc được trên cả wallpaper sáng và tối.
- Giữ khai báo 1x1 và thao tác bấm để bật/dừng chế độ nhanh.

## 3. Widget Kệ Đóng Băng
- Giữ khoảng cách ngoài hiện tại để nền không chạm biên cell.
- Thêm padding nội dung động theo số cột:
  - 2 cột: lề ngang 20dp.
  - 3 cột: lề ngang 14dp.
  - 4 cột: lề ngang 10dp.
- Thêm lề trên/dưới 16dp, tăng spacing giữa các item và tắt scrollbar.
- Giới hạn tên ứng dụng theo bề rộng cột để không tràn hoặc dạt layout.

## 4. Kiểm chứng
- `assembleDebug`, unit test và `lintDebug` thành công.
- Kiểm tra không đưa ảnh tham chiếu `.codex-remote-attachments` vào commit.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
