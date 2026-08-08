# Implementation Plan: Tích hợp Gemini API (Free Tier) + Rà soát Error Handling

## 1. Mục tiêu
- Tích hợp Google Gemini API (free tier) vào NOBG với 3 tính năng:
  1. **AI tóm tắt thông báo** trước khi TTS đọc (đọc ngắn gọn, đủ ý).
  2. **AI lọc ưu tiên thông báo** (bỏ qua spam/rác, chỉ đọc tin quan trọng).
  3. **Chat AI tổng quát** trong app.
- Xử lý **đầy đủ mọi case lỗi API**: thiếu key, key sai (401/403), rate limit (429), model không tồn tại (404), nội dung bị chặn (safety), server lỗi (5xx), network, timeout, response rỗng/parse lỗi, JSON mode lỗi.
- Rà soát toàn bộ codebase về error/exception/case handling và sửa các lỗi HIGH/MEDIUM quan trọng.

## 2. Kiến trúc

```
┌─ GeminiApiClient.kt (data) ─ OkHttp ─ generativelanguage.googleapis.com
│   - GeminiResult sealed: Success / Error(type, message)
│   - generateContent(prompt, system?, jsonMode?, timeoutMs)
│   - Retry: 429/5xx/network, exponential backoff, tôn trọng Retry-After
├─ NobgRepository: prefs AI (enabled, apiKey, model, summaryEnabled, filterEnabled)
├─ NotificationReaderService: gọi AI với withTimeoutOrNull -> fail-open về text gốc
├─ ChatViewModel + ChatScreen: chat session, lịch sử 20 tin gần nhất
└─ SettingsScreen + NotificationReadScreen: UI cấu hình
```

## 3. Case handling của Gemini API

| Case | Xử lý |
|---|---|
| Chưa nhập key | Trả NO_API_KEY, UI hiện hướng dẫn |
| 400 | BAD_REQUEST, lấy error.message |
| 401/403 | INVALID_API_KEY (hướng dẫn lấy key từ Google AI Studio) |
| 404 | MODEL_NOT_FOUND -> tự fallback model `gemini-1.5-flash` |
| 429 | RATE_LIMITED -> retry 2 lần, backoff, tôn trọng Retry-After |
| 5xx | SERVER_ERROR -> retry 2 lần backoff |
| Network/DNS/SSL | NETWORK -> retry |
| Timeout | TIMEOUT (connect 10s, read 25s) |
| promptFeedback.blockReason / finishReason SAFETY | BLOCKED |
| candidates rỗng / thiếu text | EMPTY_RESPONSE |
| JSON hỏng khi jsonMode | PARSE_ERROR -> retry 1 lần với prompt ép JSON |
| Ký tự không hợp lệ | Trim, sanitize |

**Nguyên tắc fail-open**: với đọc thông báo, AI lỗi/chậm -> VẪN đọc text gốc (không bỏ lỡ tin nhắn). Chỉ AI lọc trả về "không quan trọng" mới bỏ qua.

## 4. Files thay đổi
- `app/build.gradle.kts`: + okhttp 4.12.0
- MỚI `data/GeminiApiClient.kt`, `ui/ChatViewModel.kt`, `ui/ChatScreen.kt`
- `service/NotificationReaderService.kt`: AI summary + filter
- `data/NobgRepository.kt`: prefs AI
- `ui/SettingsScreen.kt`: mục AI
- `ui/NotificationReadScreen.kt`: card AI
- `MainActivity.kt` + `ui/AppListScreen.kt`: entry AI Chat
- Fix audit: ShizukuExecutor, UserService (timeout), SmartTimerService, SmartTimerViewModel, BatteryStatsViewModel, MainViewModel, FrozenAppsWidgetProvider, NotificationReaderService (BT permission, utterance listener), icon bitmap cap

## 5. Kiểm chứng
- `.\gradlew.bat assembleDebug` 0 lỗi
- Commit + push + kiểm tra GitHub Actions (đang gặp hạn chế quota phía GitHub)
