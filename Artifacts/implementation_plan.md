# Implementation Plan: Sửa phát hiện thiết bị Bluetooth đang kết nối

## 1. Nguyên nhân
- UI và `NotificationReaderService` đang gọi `BluetoothManager.getConnectedDevices()` với profile A2DP/HEADSET.
- `BluetoothManager` chỉ hỗ trợ truy vấn trực tiếp một số profile GATT; trên nhiều thiết bị, A2DP/HEADSET ném lỗi và code hiện tại nuốt exception nên danh sách kết nối luôn rỗng.
- Vì UI và service cùng dùng logic lỗi, thiết bị đã chọn vẫn bị báo `Chưa kết nối` và TTS bị tạm dừng.

## 2. Bộ dò dùng chung
- Tạo `BluetoothAudioDeviceDetector` dùng `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` để lấy các audio output đang kết nối.
- Hỗ trợ Bluetooth A2DP, SCO, BLE Headset, BLE Speaker và Hearing Aid theo phiên bản Android.
- Ghép thiết bị theo địa chỉ MAC đã lưu; fallback theo tên khi Android/OEM không cung cấp địa chỉ audio route.
- Chuẩn hóa địa chỉ và tên để không lỗi do hoa/thường hoặc định dạng dấu `:`.

## 3. Tích hợp
- `NotificationReadViewModel` dùng detector để đánh dấu từng thiết bị `Đang kết nối` và tính trạng thái tổng.
- Đăng ký `AudioDeviceCallback` để UI cập nhật ngay khi audio route được thêm/bớt, bên cạnh broadcast Bluetooth hiện có.
- `NotificationReaderService` dùng cùng detector trước mỗi lần đọc, đảm bảo điều kiện thực thi khớp trạng thái UI.
- Khi thiếu quyền, Bluetooth tắt hoặc lỗi truy vấn, reset trạng thái kết nối rõ ràng thay vì giữ state cũ.

## 4. Kiểm chứng
- Biên dịch trên minSdk 26 / compileSdk 34.
- Chạy `assembleDebug`, unit test và `lintDebug`.
- Commit, push `main` và theo dõi GitHub Actions đến `SUCCESS`.
