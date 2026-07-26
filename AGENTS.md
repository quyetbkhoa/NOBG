# NOBG (No Background) - Agent Guidelines & Workflow Rules

Tài liệu này quy định chi tiết cách AI Agent cần xử lý các yêu cầu lập trình, cải tiến giao diện và bảo trì dự án **NOBG (No Background on Shizuku)**.

---

## 1. Tổng quan dự án (Project Overview)
- **Tên dự án**: NOBG (No Background)
- **Mục tiêu**: Quản lý ứng dụng chạy ngầm, đóng băng (Freeze/Disable), ép dừng (Force Stop) ứng dụng để tối ưu dung lượng RAM và tiết kiệm pin hệ thống.
- **Điểm đặc biệt**: Sử dụng **Shizuku API** hoặc **ADB Direct Shell** để can thiệp hệ thống mà **KHÔNG cần Root**.
- **Tech Stack**:
  - **Language**: Kotlin (100%)
  - **UI**: Jetpack Compose (Material 3) & RemoteViews (Android AppWidget)
  - **Database**: Room Database
  - **Async/Concurrency**: Kotlin Coroutines & StateFlow
  - **System Interaction**: Shizuku API, UsageStatsManager, Foreground Service (`MonitorService`)

---

## 2. Quy trình xử lý yêu cầu chuẩn của Agent (Agent Workflow)

Mỗi khi nhận yêu cầu từ người dùng, Agent **BẮT BUỘC** thực hiện theo các bước sau:

1. **Phân tích kỹ lưỡng & Kiểm tra tác động**:
   - Đọc các file nguồn liên quan trước khi chỉnh sửa. Không tự suy đoán tên biến hay hàm.
   - Giữ nguyên các comment và tài liệu hiện có không liên quan.

2. **Lập Kế hoạch Thực thi (Implementation Plan)**:
   - Tạo file `implementation_plan.md` trong thư mục Artifacts khi có tính năng mới hoặc thay đổi kiến trúc lớn.

3. **Chỉnh sửa Code chuẩn Kotlin & Compose**:
   - Tuân thủ kiến trúc MVVM (`Repository` -> `ViewModel` -> `Compose UI`).
   - Sử dụng `StateFlow` cho UI State.

4. **Kiểm tra Biên dịch cục bộ (Local Build Verification)**:
   - Chạy lệnh Gradle build trước khi commit:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`
   - Đảm bảo **0 lỗi biên dịch** trước khi tiến hành commit.

5. **Commit, Push & Kiểm tra GitHub Actions Build**:
   - Thực hiện `git add .`, `git commit` và `git push origin main`.
   - **BẮT BUỘC**: Sử dụng API GitHub Actions (`https://api.github.com/repos/quyetbkhoa/NOBG/actions/runs`) để kiểm tra trạng thái build CI/CD cho đến khi đạt `conclusion: "success"`. Báo cáo kết quả build cho người dùng.

---

## 3. Các Quy tắc Tính năng & Thiết kế Chi tiết (Feature & UI Rules)

### 🧊 A. Quản lý Widget Kệ Đóng Bằng (AppWidget Rules)
1. **Tiêu đề Widget**: Phải hiển thị ngắn gọn là **`"NOBG"`** (không kèm dải chữ mô tả thừa).
2. **Hành vi Bấm vào App trên Widget**:
   - Bấm trực tiếp vào icon ứng dụng -> Tự động unfreeze/enable và nhảy thẳng vào ứng dụng đó (thông qua `UnfreezeAndLaunchActivity`).
3. **Hành vi Bấm vào Vùng trống / Header Widget**:
   - Bấm vào header, tiêu đề hoặc khoảng trắng trên Widget -> Mở ứng dụng NOBG trực tiếp vào trang **Kệ Đóng Bằng (`FREEZER_SHELF`)**.
4. **Cập nhật Widget Tức thì (Realtime Update)**:
   - Bất kỳ thao tác thêm/bớt ứng dụng vào Kệ Đóng Bằng hoặc bấm Ép dừng/Rã đông trên App/Dashboard đều phải phát broadcast `FrozenAppsWidgetProvider.updateAllWidgets(context)` để Widget cập nhật ngay lập tức.
5. **Cách ly Task Stack của Activity Tùy chỉnh Widget (`WidgetConfigActivity`)**:
   - Khai báo trong `AndroidManifest.xml` bắt buộc phải có:
     ```xml
     android:taskAffinity=""
     android:excludeFromRecents="true"
     android:launchMode="singleInstance"
     ```
   - *Lý do*: Đảm bảo khi mở màn hình Tùy chỉnh Widget từ Widget hoặc từ Cài đặt, sau đó bấm Home rồi mở lại NOBG từ Launcher thì màn hình chính `MainActivity` vẫn hiển thị bình thường chứ không bị đè bởi màn hình tùy chỉnh.
6. **Các Tùy chọn Giao diện Widget**:
   - **Chủ đề**: Nền Đen (`DARK`) / Nền Trắng (`LIGHT`).
   - **Màu chữ**: Theo hệ thống (`SYSTEM`), Trắng (`WHITE`), Đen (`BLACK`), Xanh lam (`ACCENT`).
   - **Độ mờ nền**: 0% đến 100%.
   - **Số cột**: 2 cột, 3 cột, 4 cột.
   - **Kích thước Icon**: Nhỏ (36dp), Vừa (48dp), Lớn (56dp).
   - **Bo góc Icon**: Vừa (12dp), Bo Tròn (18dp), Tròn hẳn (24dp / Circle).

---

### 🔋 B. Thống kê Sử dụng & Pin (Battery & Charging Stats Rules)
1. **Trục Oy Biểu đồ Phiên Sạc**:
   - Trục Oy của biểu đồ chi tiết phiên sạc trong `ChargingSessionsTab` **BẮT BUỘC** cố định nguyên dải từ **0% đến 100%** (chia mốc 0%, 25%, 50%, 75%, 100%), tuyệt đối không cắt đầu cắt cuối.
2. **Tính toán Tốc độ Sạc Thực tế**:
   - Hiển thị tốc độ sạc thực tế dưới dạng `% / giờ` và `phút / 1%`.
3. **Nút Reset Thống kê Pin Độc lập (Per-Tab Reset)**:
   - Các nút Reset chỉ số thống kê pin trong `BatteryStatsScreen` phải hoạt động độc lập cho từng Tab:
     - **Tab 0 (App tiêu thụ)**: Chỉ reset thời gian On-Screen/Off-Screen và mức tiêu thụ app.
     - **Tab 1 (Chỉ số pin chung)**: Chỉ reset tổng mức tụt pin và dòng điện.
     - **Tab 2 (Tốc độ sạc)**: Chỉ reset lịch sử các phiên sạc.

---

### ⚡ C. Quản lý Quyền & Hệ thống (Shizuku & ADB Rules)
1. **Kiểm tra Quyền Shizuku**:
   - Luôn kiểm tra `ShizukuManager.isShizukuRunning()` và `ShizukuManager.hasPermission()` trước khi thực hiện Force Stop hoặc Disable/Enable package.
2. **Cấp Quyền Hệ Thống**:
   - Quyền `WRITE_SECURE_SETTINGS` cấp qua Shizuku (`pm grant ...`).
   - Quyền `WRITE_SETTINGS` cấp qua AppOps (`appops set ...`).
3. **Hiệu năng Monitoring Service**:
   - `MonitorService` lắng nghe `UsageStatsManager` mỗi 1.5s. Tuyệt đối không đặt logic xử lý I/O nặng hoặc query Room Database đồng bộ vào luồng này.

---

## 4. Bảng kiểm tra trước khi bàn giao (Agent Checklist)

- [ ] Code Kotlin biên dịch sạch sẽ không có lỗi/cảnh báo nghiêm trọng.
- [ ] Đã chạy `.\gradlew.bat assembleDebug` thành công.
- [ ] Đã `git push origin main`.
- [ ] Đã kiểm tra GitHub Actions build CI/CD đạt kết quả `SUCCESS`.
- [ ] Đã tóm tắt ngắn gọn công việc cho người dùng bằng tiếng Việt.
