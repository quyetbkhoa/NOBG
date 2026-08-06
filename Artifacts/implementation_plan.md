# Implementation Plan: Modernize & Unify App UI

## Mục tiêu
Làm giao diện toàn bộ app hiện đại, đẹp, thống nhất và linh hoạt theo hệ thống (Material You / Dynamic Color).

## Phạm vi & Thay đổi

### 1. Theme.kt (nền tảng - ảnh hưởng toàn app)
- Bật **Dynamic Color** (Material You): `dynamicLightColorScheme` / `dynamicDarkColorScheme` trên Android 12+ (SDK 31+).
- Thiết bị cũ / không hỗ trợ: fallback bảng màu xanh lá NOBG hiện tại (đã tinh chỉnh cho cân bằng).
- Bổ sung `Typography` tùy chỉnh và `Shapes` mềm mại (bo góc 12/16/24dp) dùng chung toàn app.
- Hỗ trợ dark theme tự động theo hệ thống (đã có `isSystemInDarkTheme`).

### 2. MainActivity.kt
- Bật **edge-to-edge** (`enableEdgeToEdge`) để trạng thái thanh điều hướng hiện đại, icon tự đổi màu theo dark/light.
- Surface nền sử dụng `MaterialTheme.colorScheme.background` (mặc định của Material3).

### 3. AppListScreen.kt (màn hình chính)
- Top bar: thay emoji `⏱️`/`🧊` bằng icon Material chuẩn (`Timer`, `AcUnit`) - đồng bộ style.
- Thanh tìm kiếm: dùng dạng tonal `SearchBar`-like, bo góc đều hơn.
- Hàng app (`AppRow`): chuyển sang Card gọn gàng thay vì Row + Divider, badge bỏ emoji thừa, dùng màu theme.

### 4. SettingsScreen.kt
- Thống nhất card: cùng một kiểu container (`surfaceContainerLow`) thay vì lẫn lộn `surfaceVariant`/`secondaryContainer`/`tertiaryContainer` với alpha khác nhau.
- Tiêu đề card: bỏ style ALL-CAPS + emoji dài, dùng tiêu đề thường + icon Material.
- Đồng bộ button style.

### 5. FreezerShelfScreen.kt
- Card grid item: giữ nguyên cấu trúc, làm mượt màu trạng thái (frozen/unfrozen) theo theme.
- Thống nhất `RoundedCornerShape` với theme shapes.

### 6. BatteryStatsScreen.kt
- `TabRow` mặc định → `PrimaryTabRow` (Material3) hiện đại hơn.
- Card chỉ số: dùng `surfaceContainerHigh`/`surfaceContainerLow` theo chuẩn M3.
- Bỏ màu hardcode `Color(0xFF4CAF50)`/`0xFFF44336` → dùng `primary`/`error`.

### 7. NotificationReadScreen.kt & SmartTimerScreen.kt
- Thống nhất card container theo chuẩn M3 mới.
- SmartTimer đã được cải tiến trước đó, chỉ căn chỉnh nhẹ.

### 8. Dialogs (AppManagementDialog, AppDetailDialog, FilterBottomSheet, PermissionOnboardingDialog, AlgorithmScreen, ChargingSessionsTab)
- Đã dùng `MaterialTheme.colorScheme` - sẽ tự nhận dynamic color. Chỉ sửa nếu có màu hardcode đáng kể (AppDetailDialog dùng 2 màu đồ thị cố định → chuyển sang màu theme).

## Kiểm chứng
- `.\gradlew.bat assembleDebug` thành công 0 lỗi.
- Push main + kiểm tra GitHub Actions.

## Rủi ro
- Dynamic color có thể đổi màu chủ đạo sang bất kỳ màu nào (theo wallpaper) - chấp nhận được, đúng yêu cầu "linh hoạt theo hệ thống". Fallback xanh lá cho máy cũ.
