<div align="center">

# 🔋 NOBG (No Background) — v3.0.0

**Ứng dụng quản lý app ngầm, ép hạ xung CPU & tiết kiệm pin tối đa trên Android (Không cần Root)**

[![Việt Nam](https://img.shields.io/badge/Ng%C3%B4n_ng%E1%BB%AF-Ti%E1%BA%BFng_Vi%E1%BB%87t-blue?style=for-the-badge)](#-tiếng-việt---nobg-v300)
[![English](https://img.shields.io/badge/Language-English-red?style=for-the-badge)](#-english---nobg-v300)

<br/>

[![Views](https://profile-counter.glitch.me/quyetbkhoa_NOBG/count.svg)](https://github.com/quyetbkhoa/NOBG)
![GitHub release](https://img.shields.io/github/v/release/quyetbkhoa/NOBG?color=brightgreen)
![GitHub stars](https://img.shields.io/github/stars/quyetbkhoa/NOBG?style=social)
![License](https://img.shields.io/github/license/quyetbkhoa/NOBG)

</div>

---

## 🇻🇳 TIẾNG VIỆT — NOBG v3.0.0

**NOBG (No Background)** là ứng dụng Android chuyên dụng giúp tối ưu hóa hệ thống, quản lý và kiểm soát triệt để các ứng dụng chạy ngầm nhằm tiết kiệm tối đa tài nguyên RAM, CPU và kéo dài thời lượng sử dụng Pin.

Điểm đặc biệt của **NOBG** là khả năng can thiệp sâu vào hệ thống Android qua **Shizuku API** (sử dụng quyền ADB) mà **KHÔNG CẦN ROOT** thiết bị.

---

### 🌟 Tính Năng Mới Nổi Bật (Version 3.0.0)

#### 1. ⚡ Ép PowerHAL Hạ Xung CPU & Tiết kiệm Pin (CPU Underclocking)
* **Giới hạn xung nhịp CPU ngầm:** Thực thi lệnh Shizuku PowerHAL (`cmd power set-mode 1` & `settings put global low_power 1`) để tự động giới hạn tần số xung nhịp tối đa (Cap Max Frequency) của các nhân CPU hiệu năng cao (Big Cores).
* **Mát máy & Tiết kiệm pin:** Giảm từ 20% - 40% xung nhịp tối đa, giữ nhiệt độ máy luôn mát mẻ mà không ảnh hưởng tới trải nghiệm.
* **Hiển thị trên thông báo hệ thống:** Trạng thái hạ xung CPU được cập nhật thời gian thực trên thanh thông báo `MonitorService`.

#### 2. 📈 Biểu Đồ Xung Nhịp CPU 2 Giờ Gần Nhất & Thống Kê Chi Tiết
* **Đồ thị thời gian thực (2-Hour Clock Chart):** Biểu đồ dạng đường Canvas theo dõi mốc tần số GHz của CPU trong 120 phút qua.
* **Thống kê so sánh:** 
  * Xung trung bình lúc **BẬT** hạ xung (Ví dụ: `1.25 GHz`).
  * Xung trung bình khi **TẮT** hạ xung (Ví dụ: `1.84 GHz`).
  * Mức tiết kiệm phần trăm xung nhịp thực tế (Ví dụ: `📉 -32.9%`).

#### 3. ⏱️ Theo Dõi Tài Nguyên NOBG & Đồng Hồ Thời Gian Đếm (Live Uptime Counter)
* **Đồng hồ Uptime thời gian thực:** Đếm thời gian dịch vụ NOBG đã hoạt động liên tục kể từ khi khởi động (Ví dụ: `02h 45m 12s`).
* **Theo dõi mức chiếm dụng:** Đo lường chính xác bộ nhớ RAM tiêu thụ (`~16.5 MB`) và mức tải CPU ngầm (`<0.1%`).

#### 4. ☁️ Sao Lưu & Đồng Bộ Cấu Hình Đa Thiết Bị (Portable JSON Backup)
* **Xuất / Nhập cấu hình (Export / Import JSON):** Lưu toàn bộ cài đặt ứng dụng thành file `nobg_config_backup.json`.
* **Khớp app linh hoạt:** Khi chuyển sang điện thoại mới hoặc reset máy, chỉ cần chọn file JSON là toàn bộ cấu hình hạn chế app được khôi phục ngay lập tức.

#### 5. 🧊 "Kệ Đóng Băng" (App Widget) & Shortcut Mở Nhanh (Smart Unfreeze-Launch)
* **HomeScreen App Widget:** Widget hiển thị danh sách các ứng dụng đang ở chế độ Đóng băng (`Disable-Enable`).
* **Phím tắt mở mượt như app thường:** Bấm vào Shortcut/Widget -> NOBG tự động xả đóng băng app -> Mở app -> Khi bạn thoát app ra, NOBG sẽ tự động đóng băng app lại lập tức.

#### 6. 📘 Bảng Tra Cứu Thuật Toán & Lệnh Hệ Thống (Algorithm Knowledge Base)
* **Minh bạch thuật toán:** Tra cứu chi tiết nguyên lý hoạt động, công thức toán học và tất cả các lệnh Shell/ADB/Shizuku thực thi cho từng tính năng trong app (`appops set`, `pm disable-user`, `am force-stop`, `dumpsys`).

#### 7. 🧹 Dọn Dẹp Giao Diện Dashboard (Dashboard UI Cleanup)
* Ẩn hoàn toàn tag thừa "NOBG Tắt" và tag mặc định "Tối ưu hóa" (Optimized) giúp màn hình danh sách app sạch sẽ và trực quan.

#### 8. 🚀 Tự Động CI/CD & Cập Nhật Trực Tiếp Trong App (In-App OTA & Telegram Bot)
* **Build tự động qua GitHub Actions:** Tự động biên dịch bản Release APK đã ký chữ ký chuẩn mỗi khi push code lên `main`.
* **Gửi APK qua Telegram Bot:** Tự động gửi file APK mới nhất về Telegram channel `@quyet_channel`.
* **Tải & Cài đặt 1 chạm trong app (In-App OTA):** Mở app bấm *Kiểm tra bản cập nhật* để tải về và cài đặt trực tiếp bản build mới nhất!

---

### 🛡️ 3 Chế Độ Quản Lý App Ngầm
1. **Chế độ Tiêu chuẩn (Standard Mode)**: Áp dụng các hạn chế ngầm mặc định của Android đối với ứng dụng.
2. **Chế độ Mạnh mẽ (Aggressive Mode)**: Tự động buộc dừng (`am force-stop`) ứng dụng ngầm sau khoảng thời gian đếm ngược (delaySeconds) khi rời khỏi app.
3. **Chế độ Đóng băng (Disable-Enable Mode)**: Vô hiệu hóa hoàn toàn package (`pm disable-user`) khi app xuống nền và kích hoạt lại (`pm enable`) khi người dùng mở lại.

---

## 🇬🇧 ENGLISH — NOBG v3.0.0

**NOBG (No Background)** is an advanced Android system optimization tool designed to strictly manage background applications, prevent battery drain, free up RAM/CPU resources, and extend battery life.

**NOBG** interacts deeply with Android OS via **Shizuku API** (using ADB permissions) **WITHOUT REQUIRING ROOT ACCESS**.

---

### 🌟 Key New Features (Version 3.0.0)

#### 1. ⚡ PowerHAL CPU Underclocking & Battery Saver
* **Cap Max CPU Frequency:** Executes Shizuku PowerHAL commands (`cmd power set-mode 1` & `settings put global low_power 1`) to cap maximum frequencies on high-performance Big Cores.
* **Cooling & Power Saving:** Reduces peak CPU frequency by 20% - 40%, keeping the phone cool and saving battery under heavy workloads.
* **Live System Notification:** Active CPU underclocking status is rendered live on `MonitorService` foreground notification.

#### 2. 📈 2-Hour Real-Time CPU Frequency Chart & Statistics
* **Live Canvas Line Chart:** Tracks CPU GHz frequency over the past 120 minutes in real-time.
* **Statistical Insights:**
  * Average frequency when Underclock is **ON** (e.g., `1.25 GHz`).
  * Average frequency when Underclock is **OFF** (e.g., `1.84 GHz`).
  * Realized frequency reduction percentage (e.g., `📉 -32.9%`).

#### 3. ⏱️ NOBG Self-Resource Tracking & Live Uptime Counter
* **Live Service Uptime Counter:** Tracks continuous running duration of NOBG service (e.g., `02h 45m 12s`).
* **Resource Consumption Monitoring:** Accurately measures RAM usage (`~16.5 MB`) and background CPU load (`<0.1%`).

#### 4. ☁️ Portable Multi-Device JSON Backup & Restore
* **Export / Import JSON:** Backup all app configurations to `nobg_config_backup.json`.
* **Cross-Device Migration:** Easily migrate configs to a new phone or restore after a factory reset in seconds.

#### 5. 🧊 "App Freeze Shelf" Widget & Smart Unfreeze-Launch Shortcuts
* **HomeScreen App Widget:** View and launch frozen (`Disable-Enable`) apps directly from your Home Screen.
* **Seamless Launch Flow:** 1-tap unfreeze & launch -> automatically re-freezes package when leaving the app.

#### 6. 📘 Algorithm & Shell Commands Knowledge Base
* **Transparent Knowledge Base:** Interactive UI detailing algorithms, mathematical formulas, and exact Shell/ADB/Shizuku commands (`appops set`, `pm disable-user`, `am force-stop`, `dumpsys`).

#### 7. 🚀 Automated CI/CD & In-App OTA Updates
* **GitHub Actions CI/CD:** Auto-builds signed Release APKs on `main` push.
* **Telegram Bot Integration:** Sends latest APK automatically to `@quyet_channel`.
* **1-Tap In-App OTA Update:** Check for updates within NOBG to download and install the latest build directly.

---

## 🛠️ Tech Stack

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Database**: Room Database (App configs, battery logs, charging sessions, CPU logs)
- **Asynchronous**: Kotlin Coroutines & StateFlow
- **System APIs & Permissions**:
  - **Shizuku API** (`WRITE_SECURE_SETTINGS`, `pm`, `am`, `dumpsys`, `appops`)
  - **UsageStatsManager** & **UsageEvents** (Real-time app transition events)
  - **NetworkStatsManager** (Network data tracking per app)
  - **Foreground Service** (`MonitorService` polling & reconciliation)

---

## 🛠️ System Requirements & Setup

1. **Android OS**: Android 8.0 (API level 26) or higher.
2. **Shizuku App**: Installed & running (Activated via Wireless ADB or PC).
3. **Required Permissions**:
   - **Shizuku Permission**: To execute force-stop and disable package commands.
   - **Usage Access (Usage Stats)**: To detect foreground/background app transitions.
   - **Ignore Battery Optimizations**: Keeps `MonitorService` active.
   - **Post Notifications** (Android 13+): For battery alarms & service status.

---

## 📜 License

Distributed under the MIT License. Copyright (c) 2026 **NOBG Project**.
