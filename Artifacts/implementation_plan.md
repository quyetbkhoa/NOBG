# Implementation Plan: Lọc notification im lặng và lịch sử đọc thông báo

## 1. Mục tiêu
- NOBG không phát TTS đối với notification thuộc kênh im lặng/độ ưu tiên thấp.
- Lưu lịch sử notification đã nhận để người dùng kiểm tra nguồn và nội dung gần đây.
- Cho phép chọn một hoặc nhiều mục lịch sử rồi chặn NOBG đọc ứng dụng tương ứng.

## 2. Xử lý tại NotificationListenerService
- Xác định notification im lặng từ Ranking/NotificationChannel: importance thấp hoặc kênh không có âm thanh và rung.
- Lưu lịch sử trước bước lọc TTS để notification im lặng vẫn xuất hiện cho người dùng kiểm tra.
- Không đọc notification ongoing, notification của NOBG và notification im lặng.
- Giới hạn lịch sử ở 200 mục gần nhất; nội dung được cắt độ dài để tránh tăng database không kiểm soát.

## 3. Dữ liệu và UI
- Thêm bảng Room `notification_history` và migration database 8 → 9.
- Màn Đọc thông báo có thẻ `Lịch sử thông báo`; bấm vào mở danh sách gần nhất.
- Mỗi mục hiển thị app, thời gian, tiêu đề/nội dung và nhãn `Im lặng` nếu có.
- Hỗ trợ chọn nhiều mục và thao tác `Chặn NOBG đọc`; giữ nguyên mode/từ khóa đã cấu hình của app.
- Danh sách ứng dụng có bộ lọc `Tất cả`, `Đang đọc`, `Đã chặn`.

## 4. Kiểm chứng
- Kiểm tra migration Room, nhận/lưu lịch sử và lọc notification im lặng.
- Kiểm tra chọn lịch sử → app chuyển sang trạng thái chặn đúng package/userId.
- Chạy `assembleDebug`, unit test và `lintDebug`.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
