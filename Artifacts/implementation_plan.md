# Implementation Plan: Nâng cấp AI Trợ lý - "Khôn hơn" với Groq Free Tier

## 1. Mục tiêu
- AI trả lời thông minh, chính xác hơn mà vẫn nằm trong giới hạn **Groq free tier** (30 RPM, TPM theo model).
- Giảm số lượt gọi tool lặp lại (tiết kiệm quota), giữ "trí nhớ" cho hội thoại dài.
- Tận dụng dữ liệu thật đã thu thập (battery_log, charging_sessions, cpu_freq_log).

## 2. Bối cảnh Groq Free Tier (đã verify 2026)
- `llama-3.3-70b-versatile`: 30 RPM / 1.000 req/ngày, hỗ trợ tool-calling tốt → giữ làm default.
- `qwen/qwen3-32b`: 60 RPM (ít bị giới hạn hơn) - thêm vào suggested models.
- `llama-3.1-8b-instant`: 14.400 req/ngày - dùng cho tóm tắt không quan trọng.
- DeepSeek R1 Distill: suy luận tốt nhưng tool-calling hạn chế.

## 3. Các thay đổi

### P0 - Tham số & Tool nền tảng
1. `OpenAiCompatClient.kt`: `MAX_TOOL_ROUNDS` 3 → **6** (multi-hop), `MAX_TOKENS` 1024 → **2048**.
2. `DeviceTools.kt`:
   - Tool mới **`get_overall_stats`**: gộp pin + RAM + storage + top apps + NOBG status trong 1 gọi (thay cho chuỗi get_device_info + get_battery_info + get_app_usage_today + get_nobg_status).
   - Tool mới **`get_battery_history`** (battery_log): min/max level, 60 mốc mẫu, số mẫu.
   - Tool mới **`get_charging_sessions`** (charging_sessions): start/end, duration, from→to level, tốc độ %/giờ.
   - Tool mới **`get_cpu_stats`** (cpu_freq_log): min/max/avg freq, underclock ratio.
   - **Cache ngắn hạn** theo tool (3s pin → 60s installed apps) qua `cached()` để AI gọi lặp không tốn quota.
3. `NobgRepository.kt`: + `getCpuLogsSince(time)` (wrapper CpuLogDao).
4. `AiClient.kt`: cập nhật danh sách model Groq (thêm Qwen 3 32B, Llama 4 Scout, cảnh báo DeepSeek R1).

### P1 - Zero-shot context
5. `ChatViewModel.kt`: mỗi lượt chat tự gọi `get_overall_stats` (đã cache 3s) và chèn "NGỮ CẢNH HIỆN TẠI" vào system prompt → AI trả lời ngay, không cần 1-2 lượt tool lặp lại cho câu hỏi thường (pin/RAM/máy).

### P1 - Tóm tắt hội thoại dài
6. `ChatViewModel.kt`: khi > 36 tin (non-error), tự gọi AI tóm tắt phần đầu (dropLast 20), giữ `conversationSummary` chèn vào system prompt các lượt sau; chỉ tóm tắt lại sau mỗi +10 tin → không bùng quota.

### P2 - Tool lịch sử pin / sạc / CPU
- Đã gộp chung vào mục 2 (get_battery_history, get_charging_sessions, get_cpu_stats).

## 4. Files thay đổi
- `app/src/main/java/com/nobg/app/data/OpenAiCompatClient.kt`
- `app/src/main/java/com/nobg/app/data/DeviceTools.kt`
- `app/src/main/java/com/nobg/app/data/NobgRepository.kt`
- `app/src/main/java/com/nobg/app/data/AiClient.kt`
- `app/src/main/java/com/nobg/app/ui/ChatViewModel.kt`

## 5. Kiểm chứng
- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug` → 0 lỗi.
- Commit + push + kiểm tra GitHub Actions đạt SUCCESS.