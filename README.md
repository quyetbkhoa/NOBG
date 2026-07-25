# 🔋 NOBG (No Background) — v2.1.0

**NOBG (No Background)** là ứng dụng Android chuyên dụng giúp tối ưu hóa hệ thống, quản lý và kiểm soát triệt me các ứng dụng chạy ngầm nhằm tiết kiệm tối đa tài nguyên RAM, CPU và kéo dài thời lượng sử dụng Pin.

Điểm đặc biệt của **NOBG** là khả năng can thiệp sâu vào hệ thống Android qua **Shizuku API** (sử dụng quyền ADB) mà **KHÔNG CẦN ROOT** thiết bị.

---

## 🌟 Tính Năng Nổi Bật (Version 2.1.0)

### 1. 🛡️ 3 Chế Độ Quản Lý App Ngầm (Optimization Modes)
- **Chế độ Tiêu chuẩn (Standard Mode)**: Áp dụng các hạn chế ngầm mặc định của Android đối với ứng dụng được chọn.
- **Chế độ Mạnh mẽ (Aggressive Mode - Force Stop)**: Tự động buộc dừng (`am force-stop`) ứng dụng qua Shizuku ngay khi người dùng thoát ứng dụng (sau khoảng thời gian đếm ngược tùy chỉnh).
- **Chế độ Đóng băng (Disable-Enable Mode)**: Vô hiệu hóa hoàn toàn package (`pm disable-user`) qua Shizuku khi ứng dụng xuống nền, loại bỏ 100% khả năng tự chạy ngầm/chạy ngầm trái phép. Tự động kích hoạt lại (`pm enable`) khi người dùng mở ứng dụng.

### 2. ⚡ Lịch Sử Các Phiên Sạc & Biểu Đồ Tốc Độ Sạc Phi Tuyến Tính (`ChargingSessionsTab`)
- **Lưu vết từng phiên sạc riêng biệt**: Tự động lưu tất cả các lần cắm sạc vào Room Database (thời gian bắt đầu/kết thúc, % pin ban đầu, % kết thúc, tổng thời gian).
- **Biểu đồ Tốc độ Sạc từng phiên (Session Curve)**: Bấm vào bất kỳ phiên sạc nào trong lịch sử để xem biểu đồ đường sạc tương ứng với **Trục Ox: % Pin** và **Trục Oy: Thời gian sạc**.
- **Thuật toán dự đoán sạc phi tuyến tính**: Tính toán trung bình từng nấc 1% pin dựa trên các phiên sạc lịch sử, từ đó đưa ra dự đoán thời gian sạc đầy 100% chính xác (tính đến hiện tượng sạc chậm dần từ 80% -> 100%).
- **🔊 Cảnh báo âm thanh khi pin đầy 100%**: Tự động phát âm thanh thông báo/chuông cảnh báo khi pin được sạc tới 100% để nhắc người dùng rút sạc.

### 3. 🛡️ Nâng Cấp Thông Báo Giám Sát Hệ Thống (Rich Dashboard Notification)
- Nâng cấp thông báo giám sát thường trực thành dashboard thông báo tiếng Việt hiện đại.
- Hiển thị thông số thời gian thực: Số ứng dụng đang được tối ưu, số lượt dọn ngầm, phần trăm pin và tốc độ sạc/xả.
- Tích hợp các nút thao tác nhanh: **`⚙️ Quản lý App`** và **`📊 Thống kê Pin`**.

### 4. 📊 Thống Kê Pin & Dòng Thời Gian Sự Kiện Chi Tiết (Detailed Battery & Event Timeline)
- **Dòng thời gian sự kiện (Event Timeline Canvas)**:
  - Biểu đồ thời gian trực quan phân định rõ các khoảng thời gian **Tiền cảnh (Foreground)** và **Dịch vụ nền (Background Service)**.
  - Hiển thị đầy đủ mốc thời gian, số phiên tiền cảnh, tổng thời gian hiển thị, phiên dài nhất, số lần chạy dịch vụ nền và số lần tương tác.
- **Chỉ số Sử dụng Chi tiết (Usage Stats)**:
  - Thời gian bật màn hình (Screen-on time), lượt mở ứng dụng, thời gian dịch vụ tiền cảnh.
  - Điện năng tiêu thụ tính theo **mAh**, tỉ lệ **% Pin** tiêu thụ và tốc độ hao pin (**%/giờ**).
  - Phân loại **Nhóm chờ Android (Standby Bucket)**: *Hoạt động*, *Tập làm việc*, *Thường xuyên*, *Hiếm khi*, *Hạn chế*, *Ngoại lệ*.
- **Thống kê Mạng (Network Usage)**:
  - Đo lường dung lượng dữ liệu truyền nhận qua **Wi-Fi** và **Dữ liệu di động** của từng app.
- **Tổng quan Pin & Biểu đồ tốc độ sạc**:
  - Biểu đồ tốc độ sạc (Charging Curve) theo từng % pin.
  - Tốc độ hao pin trung bình khi màn hình sáng và khi màn hình tắt.
  - Tự động dự đoán thời gian sạc đầy pin thông qua thông báo hệ thống.

### 3. ⚡ Nút Tắt Nhanh (Quick Settings Tiles - QS Integration)
Tích hợp sẵn các Tile tiện ích vào bảng Cài đặt nhanh của Android:
- **USB Debugging Tile**: Bật/tắt nhanh chế độ Gỡ lỗi USB.
- **Wireless ADB Tile**: Bật/tắt nhanh Gỡ lỗi không dây.
- **Screen Timeout Tile**: Chuyển đổi nhanh thời gian sáng màn hình (15s, 30s, 1m, 5m, 10m, Never).
- **App Shortcut & Custom Intent Tile**: Mở nhanh ứng dụng hoặc kích hoạt Intent tùy chỉnh từ Quick Settings.

### 4. 🔍 Bộ Lọc & Tra Cứu Nâng Cao
- Lọc ứng dụng linh hoạt theo: *App Người dùng / App Hệ thống*, *Trạng thái Đã cấu hình / Chưa cấu hình*, *Trạng thái Tiết kiệm pin hệ thống (Restricted / Optimized / Unrestricted)*, *App đang bị vắng mặt / disable*.
- **Hỗ trợ Tra cứu AI**: Tích hợp menu tra cứu nhanh thông tin và tác dụng của ứng dụng qua **ChatGPT**, **Google Gemini** và **Google Search**.

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

- **Ngôn ngữ**: Kotlin (100%)
- **Giao diện (UI)**: Jetpack Compose (Material Design 3)
- **Database**: Room Database (Lưu trữ lịch sử pin & cấu hình app)
- **Bất đồng bộ**: Kotlin Coroutines & StateFlow
- **Quyền & Can thiệp Hệ thống**:
  - **Shizuku API** (Thực thi quyền `WRITE_SECURE_SETTINGS`, `pm`, `am`, `dumpsys`)
  - **UsageStatsManager** & **UsageEvents** (Theo dõi sự kiện foreground/background)
  - **NetworkStatsManager** (Đo lường lưu lượng mạng)
  - **Foreground Service** (`MonitorService` tự động lắng nghe trạng thái app theo thời gian thực)

---

## 🚀 Hướng Dẫn Cài Đặt & Sử Dụng

### 1. Yêu cầu hệ thống
- Android 8.0 (API level 26) trở lên.
- Đã cài đặt và khởi chạy ứng dụng **Shizuku** (Cấp quyền ADB qua PC hoặc Wireless ADB).

### 2. Cấp quyền ứng dụng
Để NOBG hoạt động đầy đủ tính năng, ứng dụng sẽ yêu cầu các quyền sau:
1. **Quyền Shizuku**: Cho phép NOBG ép dừng hoặc đóng băng app ngầm.
2. **Quyền Truy cập dữ liệu sử dụng (Usage Stats)**: Để đếm thời gian dùng app và sự kiện tiền cảnh/hậu cảnh.
3. **Quyền Hiển thị trên ứng dụng khác (Draw over apps)** *(Dùng cho chế độ Disable-Enable)*.
4. **Quyền Tắt tối ưu hóa pin (Ignore Battery Optimization)**.

---

## 📜 Giấy Phép (License)

Dự án được phát triển dưới giấy phép MIT. Toàn bộ mã nguồn thuộc quyền sở hữu của dự án **NOBG**.
