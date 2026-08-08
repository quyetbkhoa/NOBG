<div align="center">

# 🔋 NOBG (No Background) — v3.0.0

**Ứng dụng quản lý app ngầm, ép hạ xung CPU, đóng băng app & tiết kiệm pin tối đa trên Android (Không cần Root)**

[![Việt Nam](https://img.shields.io/badge/Ng%C3%B4n_ng%E1%BB%AF-Ti%E1%BA%BFng_Vi%E1%BB%87t-blue?style=for-the-badge)](#vietnamese)
[![English](https://img.shields.io/badge/Language-English-red?style=for-the-badge)](#english)

<br/>

![Views](https://komarev.com/ghpvc/?username=quyetbkhoa-nobg&label=Views&color=007ec6&style=flat-square)
![GitHub release](https://img.shields.io/github/v/release/quyetbkhoa/NOBG?color=brightgreen)
![GitHub stars](https://img.shields.io/github/stars/quyetbkhoa/NOBG?style=social)
![License](https://img.shields.io/github/license/quyetbkhoa/NOBG)

</div>

---

<a name="vietnamese"></a>

## 🇻🇳 TIẾNG VIỆT — NOBG v3.0.0

**NOBG (No Background)** là ứng dụng Android chuyên dụng giúp tối ưu hóa hệ thống, quản lý và kiểm soát triệt để các ứng dụng chạy ngầm nhằm tiết kiệm tối đa RAM, CPU và kéo dài thời lượng Pin.

Điểm đặc biệt: NOBG can thiệp sâu vào hệ thống qua **Shizuku API** / **ADB** (ép dừng, vô hiệu hóa package, hạ xung CPU, đọc dumpsys) mà **KHÔNG CẦN ROOT**. App còn tích hợp **AI Trợ lý** — chat với AI để đọc dữ liệu thật trên máy và điều khiển tính năng bằng ngôn ngữ tự nhiên.

---

### ✨ Tính Năng Nổi Bật

#### 1. 🤖 AI Trợ lý (AI Chat với Function Calling)
* **Đọc dữ liệu THẬT trên máy:** Hỏi "Pin còn bao nhiêu?", "RAM trống bao nhiêu?", "Tôi dùng app nào nhiều nhất?"... AI tự gọi công cụ để lấy kết quả thực tế trên thiết bị (**hãng máy, mức pin, nhiệt độ, trạng thái sạc, RAM/bộ nhớ trong, app dùng nhiều hôm nay, trạng thái NOBG, cài đặt, lịch sử pin 24h, phiên sạc, thống kê CPU, chi tiết từng app**).
* **Tổng hợp 1 lần gọi**: Tool `get_overall_stats` gộp pin + RAM + bộ nhớ + top app + trạng thái NOBG, kèm cache thông minh — tiết kiệm quota API tối đa.
* **Ra lệnh đổi cài đặt bằng ngôn ngữ tự nhiên**: "Bật tóm tắt thông báo", "Tắt âm báo pin đầy", "Bật chủ đề tối"... — AI sẽ hỏi bạn **xác nhận** trước khi áp dụng (không tự ý thay đổi).
* **Đa nhà cung cấp**: Hỗ trợ **Gemini (Google)**, **Groq (siêu nhanh, free không cần thẻ)**, **OpenRouter (nhiều model free)**.
* **Xử lý quyền mềm dẻo**: Nếu thiết bị chưa cấp Usage Access / Shizuku, AI nói rõ và hướng dẫn cách bật trong Cài đặt — không bao giờ trả lời "không có quyền" cho những thứ đủ quyền.
* **Trí nhớ dài**: Hội thoại dài tự được tóm tắt để giữ ngữ cảnh mà không tốn quota API.

#### 2. 🎙️ Đọc Thông Báo Bằng Giọng Nói (Notification Reader)
* **TTS Việt hóa**: Đọc to thông báo của từng app (Zalo, Messenger, Telegram...) bằng TTS hệ thống.
* **Chỉ đọc khi kết nối Bluetooth** (ví dụ: khi đeo tai nghe/loa xe) để tránh đọc lúc nơi làm việc.
* **Ducking**: tự giảm âm lượng nhạc phát khi đọc.
* **AI tóm tắt & lọc thông báo**: (nếu bật) AI rút gọn tin nhắn dài đọc ngắn gọn, lọc bỏ thông báo rác/spam, chỉ đọc tin quan trọng.
* **Chế độ Fail-open**: AI lỗi/chậm cũng không làm bỏ lỡ tin — vẫn đọc text gốc.

#### 3. 🧊 "Kệ Đóng Băng" — Widget màn hình chính
* **App Widget tự do**: hiện danh sách app đang bị đóng băng trên Home Screen.
* **Widget tùy biến cao**: nền Đen/Trắng, màu chữ (theo hệ thống/trắng/đen/xanh lam), độ mờ 0–100%, 2–4 cột, icon 36–56dp, bo góc 12–24dp.
* **1 chạm Rã đông & Mở**: Bấm icon app → tự `pm enable` → mở app → khi bạn thoát, NOBG tự tái đóng băng.
* **Bấm header/khoảng trống**: mở thẳng **Kệ Đóng Bằng** trong app.

#### 4. ⚡ Ép Hạ Xung CPU (PowerHAL Underclocking)
* **Giới hạn xung CPU ngầm**: `cmd power set-mode 1` & `settings put global low_power 1` để giới hạn tần số tối đa các nhân Big.
* **Giữ máy mát & tiết kiệm pin**: giảm 20%–40% xung đỉnh, xem được trên thanh thông báo trong thời gian thực.
* **Biểu đồ CPU 2h**: Canvas theo dõi GHz trong 120 phút + thống kê xung TB khi BẬT/TẮT (ví dụ `1.25→1.84 GHz, -32.9%`).

#### 5. 🔋 Pin & Phiên Sạc (Battery Intelligence)
* **Theo dõi mức tụt pin**: thời lượng On-Screen/Off-Screen, mức tiêu thụ từng app.
* **Phiên sạc chi tiết**: biểu đồ từng phiên (trục Oy cố định 0–100%), tốc độ **% / giờ** & **phút / 1%**.
* **Đọc dumpsys pin** (qua Shizuku): dòng điện (mA) khi sạc/xả, đo chính xác tốc độ sạc thực tế.
* **Dự đoán thời gian sạc đầy** bằng ChargingPredictor; báo hiệu khi sạc đầy pin.
* **Reset độc lập từng Tab**: reset app tiêu thụ / chỉ số pin chung / phiên sạc riêng biệt.

#### 6. ⏱️ Hẹn Giờ Thông Minh (Smart Timer)
* **Hẹn giờ sự kiện hệ thống**: tắt màn hình, chế độ tiết kiệm pin, hẹn giờ mở/đóng app — chạy đúng giờ đã đặt.
* **Widget hẹn giờ**: xem & bấm nhanh từ màn hình chính.
* Minh bạch lệnh hệ thống: hiện đúng Shell/ADB/Shizuku sẽ chạy.

#### 7. 📘 Bảng Tra Cứu Thuật Toán & Lệnh Hệ Thống (Algorithm Knowledge Base)
* Tra cứu nguyên lý hoạt động, công thức toán học và toàn bộ lệnh `appops set`, `pm disable-user`, `am force-stop`, `dumpsys` mà NOBG thực thi — **minh bạch tuyệt đối**.

#### 8. 🔄 Sao Lưu, CI/CD & Cập Nhật Trong App
* **Sao lưu cấu hình**: Export/Import `nobg_config_backup.json` dễ mang sang máy mới.
* **CI/CD tự động**: GitHub Actions xây dựng Release APK đã ký mỗi lần push `main`.
* **Cập nhật tức thì**: Build được gửi lên Telegram channel + **tải/cài 1 chạm ngay trong app** (In-App OTA Update).

---

## 🛡️ 3 Chế Độ Quản Lý App Ngầm
1. **Chế độ Tiêu chuẩn (Standard)**: áp dụng hạn chế ngầm mặc định của Android.
2. **Chế độ Mạnh mẽ (Aggressive)**: tự `am force-stop` app sau khi rời khỏi đúng delaySeconds.
3. **Chế độ Đóng băng (Disable-Enable)**: `pm disable-user` khi xuống nền, `pm enable` khi mở lại.

> Mọi thao tác **Ép dừng / Đóng băng / Rã đông** từ App, Dashboard hay Widget đều kiểm tra quyền Shizuku trước và cập nhật Widget ngay lập tức.

---

## 📄 Cấu Hình & Quyền (Setup)
1. **Android OS**: 8.0 (API 26) trở lên.
2. **Shizuku App**: đã chạy (kích hoạt qua Wireless ADB hoặc PC) — bắt buộc cho Force-Stop / Disable / hạ xung CPU / đọc dumpsys.
3. **Quyền cần thiết**:
   - Shizuku (`pm grant WRITE_SECURE_SETTINGS`, `appops set WRITE_SETTINGS`)
   - Usage Access (theo dõi thời lượng dùng app)
   - Bỏ tối ưu pin (Ignore Battery Optimizations) để `MonitorService` ổn định
   - Notification Access (Android 8+) dùng cho Đọc thông báo & Đọc qua AI
   - Post Notifications (Android 13+)

---

## 🛠️ Tech Stack
- **Language**: 100% Kotlin
- **UI**: Jetpack Compose (Material 3) + RemoteViews (AppWidget)
- **DB**: Room (app configs, battery log, charging sessions, CPU freq log, notification read config, Bluetooth device)
- **Nền tảng bất đồng bộ**: Kotlin Coroutines & StateFlow
- **Hệ thống**: Shizuku API / ADB (pm, am, appops, dumpsys), PowerHAL (`cmd power set-mode 1`), UsageStatsManager, NetworkStatsManager, Foreground Service (`MonitorService`)
- **AI**: Function Calling đa provider — Gemini (`/v1beta`), Groq & OpenRouter (OpenAI-compatible), TTS đọc thông báo, OkHttp

---

<a name="english"></a>

## 🇬🇧 ENGLISH — NOBG v3.0.0

**NOBG (No Background)** is an advanced Android system optimizer that strictly manages background apps, prevents battery drain, frees RAM/CPU, caps CPU frequency and extends battery life.

**NOBG** interacts deeply with Android via **Shizuku API** (ADB permissions) **WITHOUT REQUIRING ROOT**.

---

## ✨ Top Features

#### 1. 🤖 AI Assistant (Chat with Function Calling)
* **Reads REAL device data**: ask *"Battery level?"*, *"Free RAM?"*, *"Top apps today?"* and AI calls local tools to fetch live data (model, battery %, temperature, voltage, charging state, RAM, storage, usage, NOBG status, settings, 24h battery history, charging sessions, CPU stats, per-app detail).
* **One-shot aggregated tool**: `get_overall_stats` merges battery+RAM+storage+top apps+NOBG status and all tools are smart-cached to save API quota.
* **Natural-language control**: *"turn on notification summary"*, *"disable full battery sound"* — changes are **confirmed by you** before being applied.
* **Multi-provider**: **Gemini**, **Groq** (fast, free tier) and **OpenRouter**, all free-friendly.
* **Graceful permission handling**: missing Usage Access/Shizuku → AI explains and guides instead of refusing.
* **Long-term memory**: conversations > 36 messages are auto-summarized to keep context cheap.

#### 2. 🎙️ Notification Aloud (Vietnamese TTS)
* Speak notifications from chosen apps via system TTS; toggle **Bluetooth-only mode** (e.g. in car), **audio ducking**.
* Optional AI **summarization & spam filtering** (only important notifications are spoken); fail-open fallback to raw text.

#### 3. 🧊 Freeze Shelf & Home Screen Widget
* Widget showing your frozen apps; highly customizable (dark/light theme, text color (system/white/black/blue), opacity 0–100%, 2–4 columns, icon size 36–56dp, corner radius 12–24dp).
* Tap an app icon → auto `enable` + launch + re-freeze on exit; tap blank space → open NOBG **Freezer Shelf**.

#### 4. ⚡ PowerHAL CPU Underclocking
* Caps max CPU frequency (`cmd power set-mode 1` + `settings put global low_power 1`) → **cooler phone, 20–40% less peak power**.
* **Live 2h frequency chart** + avg-freq comparison when underclock is ON/OFF (e.g. `1.25 GHz → 1.84 GHz, -32.9%`).

#### 5. 🔋 Battery & Charging Analytics
- On/off-screen battery drain stats, per-app consumption.
- Detailed **charging sessions** chart (Y axis fixed 0–100%) with **%/hour speed & minutes per 1%**; dumpsys current draw (mA) via Shizuku; charge-complete predictor & **low/full battery alarm**.
- **Per-tab independent reset**.

#### 6. ⏱️ Smart Timer
- Schedule system actions (screen off, power save, open app...) with a Home Screen widget; every underlying shell command is shown transparently.

#### 7. 📘 Algorithm & Shell Command Knowledge Base
- Full transparency: algorithms, math formulas, exact `appops set` / `pm disable-user` / `am force-stop` / `dumpsys` commands used by the app.

#### 8. 🔄 Backup, CI/CD & In-App OTA
- JSON portable backup/restore; GitHub Actions builds signed Release APK on each push; automatic Telegram deliver; **1-tap in-app update installer**.

---

## 🛡️ 3 Background App Management Modes
1. **Standard** — default Android background restrictions.
2. **Aggressive** — auto `am force-stop` after a delay.
3. **Disable–Enable (Freeze)** — `pm disable-user` on background, `pm enable` on relaunch.

---

## 📄 Requirements & Permissions
1. **Android 8.0 (API 26)+**
2. **Shizuku** running (via Wireless ADB or PC) for force-stop, freeze, CPU cap and dumpsys.
3. **Permissions**: Shizuku (`WRITE_SECURE_SETTINGS` + `WRITE_SETTINGS`), Usage Access, Ignore Battery Optimization, Notification Access (Android 13+ notifications).

---

## 🛠️ Tech Stack
- **100% Kotlin**, Jetpack Compose (M3) + RemoteViews widgets
- **Room** database, **Coroutines & StateFlow**
- **Shizuku/ADB**, **PowerHAL**, **UsageStatsManager**, **NetworkStatsManager**, `MonitorService` Foreground Service
- **AI:** Gemini / Groq / OpenRouter (function calling), TTS

---

## 📜 License

Distributed under the MIT License. Copyright (c) 2026 **NOBG Project**.