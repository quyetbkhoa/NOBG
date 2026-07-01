# NOBG (No Background) - Project Context & Rules

## Tổng quan dự án (Project Overview)
NOBG là một ứng dụng Android với mục đích quản lý các ứng dụng chạy ngầm nhằm tiết kiệm pin và tài nguyên hệ thống. Điểm đặc biệt của dự án này là sử dụng **Shizuku** để can thiệp sâu vào hệ thống (Force Stop, Disable/Enable ứng dụng) mà không yêu cầu thiết bị phải Root (chỉ cần quyền ADB).

## Tech Stack
- **Ngôn ngữ**: Kotlin
- **Giao diện**: Jetpack Compose (Material 3)
- **Database**: Room Database
- **Bất đồng bộ & Đa luồng**: Kotlin Coroutines
- **Quản lý quyền/Hệ thống**: Shizuku API, UsageStatsManager, Foreground Service.

## Cấu trúc thư mục (`app/src/main/java/com/nobg/app/`)
- `ui/`: Các màn hình bằng Jetpack Compose (`AppListScreen`, `ConnectScreen`, `SettingsScreen`, `QuickSettingsScreen`, `BatteryStatsScreen`) và `MainViewModel`.
- `data/`: Cấu hình Room database (`AppDatabase`, `AppEntity`, `Daos`) và `NobgRepository` dùng để quản lý trạng thái, cài đặt cho từng ứng dụng.
- `service/`: Chứa `MonitorService`, một Foreground Service liên tục lắng nghe `UsageStatsManager` (mỗi 1.5s) để nhận biết app nào đang mở/đóng.
- `shizuku/`: Chứa `ShizukuManager` và `UserService` đóng vai trò giao tiếp với Shizuku service trên thiết bị để thực hiện các lệnh quản trị cao.
- `qs/`: Chứa các `TileService` (USB Debug, Wireless ADB, Screen Timeout, Shortcut, Custom Intent) để tích hợp vào thanh Quick Settings của Android.

## Cơ chế hoạt động (Modes)
Dự án có 3 chế độ xử lý app ngầm khi chúng rời khỏi Foreground (bị người dùng thoát ra):
1. **Standard Mode**: Hạn chế ngầm mặc định.
2. **Aggressive Mode**: Ép dừng (Force Stop) bằng Shizuku sau một khoảng thời gian (delay) được thiết lập.
3. **Disable-Enable Mode**: Vô hiệu hóa (Disable) hoàn toàn package qua Shizuku để tiết kiệm tối đa.

## Các tính năng mở rộng
- **Quản lý Quick Settings (QS Tiles)**: Cung cấp các nút tắt/bật nhanh các tính năng hệ thống (ADB, Thời gian sáng màn hình) và cấu hình mở nhanh App/Intent tùy chỉnh.
- **Thống kê sử dụng & Pin**: Hiển thị thời gian On-Screen, Off-Screen, thời gian sử dụng từng app bằng cách lấy dữ liệu từ hệ thống (sẽ kết hợp với `MonitorService`).

## Các quy tắc lập trình cho Agent
1. **Shizuku & ADB Permissions**: 
   - Luôn kiểm tra quyền Shizuku trước khi thực thi lệnh (Force Stop, Disable).
   - Quyền `WRITE_SECURE_SETTINGS` được cấp qua Shizuku (`pm grant ...`).
   - Quyền `WRITE_SETTINGS` được cấp qua ADB AppOps (`appops set ...`).
2. **Quick Settings Tiles**: Các TileServices (`qs/`) giao tiếp với cấu hình hệ thống bằng cách ghi vào `Settings.Global` hoặc `Settings.System`. Cần khai báo `ContentObserver` để lắng nghe thay đổi nếu bị thay đổi từ Cài đặt gốc. Gọi `startActivityAndCollapse` phải dùng `PendingIntent` từ Android 14 trở lên.
3. **UI & State**: Dùng StateFlow trong `MainViewModel` cho các UI Jetpack Compose.
4. **Performance**: Chú ý đến `MonitorService` vì hàm polling chạy liên tục mỗi 1.5s, không được bỏ logic nặng vào đó.
5. **Build System**: Dự án dùng Gradle KTS, nếu thêm thư viện hãy sử dụng version catalog nếu có, hoặc thêm thẳng vào `build.gradle.kts`.
