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
- `ui/`: Các màn hình bằng Jetpack Compose (`AppListScreen`, `ConnectScreen`, `SettingsScreen`) và `MainViewModel`.
- `data/`: Cấu hình Room database (`AppDatabase`, `AppEntity`, `Daos`) và `NobgRepository` dùng để quản lý trạng thái, cài đặt cho từng ứng dụng.
- `service/`: Chứa `MonitorService`, một Foreground Service liên tục lắng nghe `UsageStatsManager` (mỗi 1.5s) để nhận biết app nào đang mở/đóng.
- `shizuku/`: Chứa `ShizukuManager` và `UserService` đóng vai trò giao tiếp với Shizuku service trên thiết bị để thực hiện các lệnh quản trị cao.

## Cơ chế hoạt động (Modes)
Dự án có 3 chế độ xử lý app ngầm khi chúng rời khỏi Foreground (bị người dùng thoát ra):
1. **Standard Mode**: Hạn chế ngầm mặc định.
2. **Aggressive Mode**: Ép dừng (Force Stop) bằng Shizuku sau một khoảng thời gian (delay) được thiết lập.
3. **Disable-Enable Mode**: Vô hiệu hóa (Disable) hoàn toàn package qua Shizuku để tiết kiệm tối đa.

## Các quy tắc lập trình cho Agent
1. **Shizuku API**: Luôn kiểm tra quyền Shizuku trước khi thực hiện các hàm force-stop hay disable để tránh crash ứng dụng.
2. **UI & State**: Dùng StateFlow trong `MainViewModel` cho các UI Jetpack Compose.
3. **Performance**: Chú ý đến `MonitorService` vì hàm polling chạy liên tục mỗi 1.5s, không được bỏ logic nặng vào đó.
4. **Build System**: Dự án dùng Gradle KTS, nếu thêm thư viện hãy sử dụng version catalog nếu có, hoặc thêm thẳng vào `build.gradle.kts`.
