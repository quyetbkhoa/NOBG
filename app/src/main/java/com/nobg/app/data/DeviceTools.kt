package com.nobg.app.data

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import com.nobg.app.shizuku.ShizukuManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Bộ công cụ (function calling) cho AI Trợ lý đọc dữ liệu TRÊN MÁY.
 * Tất cả các công cụ đều CHỈ ĐỌC (read-only), không thực hiện bất kỳ thay đổi nào
 * trên hệ thống để đảm bảo an toàn.
 */
object DeviceTools {

    // ID thuộc tính pin (hằng số của BatteryManager, dùng số nguyên để tương thích mọi compileSdk)
    private const val PROP_STATUS = 6
    private const val PROP_HEALTH = 7
    private const val PROP_VOLTAGE = 8
    private const val PROP_TEMPERATURE = 9

    /** Cache kết quả tool ngắn hạn để AI không gọi đi gọi lại cùng dữ liệu (giảm round-trip & quota) */
    private data class CacheEntry(val result: String, val expiresAt: Long)
    private val cache = HashMap<String, CacheEntry>()

    /** TTL cache theo từng tool (ms) - chỉ cache dữ liệu thay đổi chậm hoặc không cần quá tươi */
    private val CACHE_TTL_MS: Map<String, Long> = mapOf(
        "get_device_info" to 10_000L,
        "get_battery_info" to 3_000L,
        "get_app_usage_today" to 30_000L,
        "get_nobg_status" to 5_000L,
        "get_nobg_settings" to 5_000L,
        "get_installed_apps" to 60_000L,
        "get_app_info" to 30_000L,
        "get_overall_stats" to 3_000L,
        "get_battery_history" to 30_000L,
        "get_charging_sessions" to 30_000L,
        "get_cpu_stats" to 30_000L
    )

    private suspend fun cached(name: String, args: JSONObject, ttlMs: Long, block: suspend () -> String): String {
        val key = "$name|$args"
        val now = SystemClock.elapsedRealtime()
        synchronized(cache) {
            cache[key]?.let { if (it.expiresAt > now) return it.result }
        }
        return block().also { value ->
            synchronized(cache) {
                cache[key] = CacheEntry(value, now + ttlMs)
                if (cache.size > 64) {
                    val expired = cache.filterValues { it.expiresAt <= now }
                    expired.keys.forEach(cache::remove)
                }
            }
        }
    }

    val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = "get_device_info",
            description = "Đọc thông tin tổng quan của máy: hãng, model, phiên bản Android, RAM tổng/đang trống, dung lượng bộ nhớ trong tổng/trống, màn hình đang bật hay tắt, thời gian máy đã bật.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "get_battery_info",
            description = "Đọc thông tin pin hiện tại: phần trăm pin, nhiệt độ, điện áp, trạng thái sạc (đang sạc/xả/đầy), nguồn sạc (AC/USB/wireless), tình trạng pin.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "get_overall_stats",
            description = "Đọc TỔNG HỢP tình trạng máy trong 1 lần gọi: pin (phần trăm, nhiệt độ, trạng thái sạc), RAM tổng/trống, bộ nhớ trong tổng/trống, 5 app dùng nhiều nhất hôm nay, trạng thái NOBG (số app quản lý, đóng băng, shell). Dùng tool này thay vì gọi lần lượt get_device_info + get_battery_info + get_app_usage_today + get_nobg_status khi người dùng hỏi câu mang tính tổng quan như \"máy tôi thế nào\", \"sức khỏe máy\", \"tình trạng máy\".",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "get_app_usage_today",
            description = "Đọc danh sách 10 ứng dụng được dùng nhiều nhất hôm nay (từ 00:00) kèm thời gian sử dụng trên màn hình.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "get_battery_history",
            description = "Đọc LỊCH SỬ PIN trong khoảng thời gian (mặc định 24 giờ gần nhất): mức pin thấp nhất/cao nhất trong khoảng, các mốc thời gian ghi nhận mức pin. " +
                "Số liệu chỉ mang tính gần đúng (lấy mẫu mỗi khi pin/màn hình thay đổi).",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("hours", JSONObject().put("type", "integer").put("description", "Số giờ nhìn lại (1-168), mặc định 24 (tùy chọn)"))
                )
        ),
        AiToolDefinition(
            name = "get_charging_sessions",
            description = "Đọc các PHIÊN SẠC đã hoàn thành trong lịch sử: thời gian bắt đầu/kết thúc, thời gian sạc, mức pin đầu -> cuối, sạc có đầy 100% không. Tính tần suất sạc của người dùng.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("limit", JSONObject().put("type", "integer").put("description", "Số phiên mới nhất trả về, mặc định 10 (tùy chọn)"))
                )
        ),
        AiToolDefinition(
            name = "get_cpu_stats",
            description = "Đọc thống kê CPU: tần số min/max/trung bình, thời gian chạy underclock, số lần ghi nhận trong khoảng giờ (mặc định 1 giờ). Giúp đánh giá máy có bị tụt hiệu năng không.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("hours", JSONObject().put("type", "integer").put("description", "Số giờ nhìn (1-24), mặc định 1 (tùy chọn)"))
                )
        ),
        AiToolDefinition(
            name = "get_nobg_status",
            description = "Đọc trạng thái hoạt động của NOBG: số app đang được quản lý, số app trong Kệ Đóng Bằng, số app đang bị vô hiệu hóa trên hệ thống, nhà cung cấp AI đang dùng, tính năng đọc thông báo có bật không.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "get_installed_apps",
            description = "Liệt kê các ứng dụng đã cài trên máy kèm package name. Có thể lọc theo từ khóa trong tên hoặc package.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("keyword", JSONObject().put("type", "string").put("description", "Từ khóa lọc tên hoặc package (tùy chọn)"))
                        .put("limit", JSONObject().put("type", "integer").put("description", "Số lượng tối đa trả về, mặc định 30 (tùy chọn)"))
                )
        ),
        AiToolDefinition(
            name = "get_app_info",
            description = "Đọc chi tiết một ứng dụng theo package name: tên hiển thị, phiên bản, ngày cài đặt, trạng thái bật/tắt, standby bucket, thời điểm dùng lần cuối.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("package", JSONObject().put("type", "string").put("description", "Package name của ứng dụng (bắt buộc)")))
                .put("required", JSONArray().put("package"))
        ),
        AiToolDefinition(
            name = "get_nobg_settings",
            description = "Đọc toàn bộ cài đặt của app NOBG: AI Trợ lý (bật/tắt, provider, model, tóm tắt, lọc rác), Đọc thông báo (bật/tắt, chỉ Bluetooth, ducking), TTS (tốc độ, âm lượng, cao độ), âm thanh báo pin đầy, chủ đề giao diện.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
        ),
        AiToolDefinition(
            name = "set_nobg_setting",
            description = "THAY ĐỔI cài đặt của app NOBG theo lệnh người dùng. Chỉ dùng khi người dùng yêu cầu, và mọi thay đổi đều phải được người dùng xác nhận. " +
                "Tham số \"changes\" là object dạng {\"tên_cài_đặt\": giá_trị}. Các cài đặt hợp lệ: " +
                "ai_enabled (boolean), ai_provider (string: gemini|groq|openrouter), ai_model (string), " +
                "ai_summary_enabled (boolean), ai_filter_enabled (boolean), ai_filter_strictness (số 0-1), " +
                "notif_read_enabled (boolean), notif_read_only_bt (boolean), notif_read_ducking (boolean), " +
                "tts_speech_rate (số, mặc định 1.0), tts_volume (số 0-1), tts_pitch (số, mặc định 1.0), " +
                "full_battery_sound (boolean), theme_mode (string: SYSTEM|LIGHT|DARK).",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "changes",
                        JSONObject()
                            .put("type", "object")
                            .put("description", "Các cài đặt cần thay đổi, ví dụ: {\"ai_summary_enabled\": true, \"tts_volume\": 0.8}")
                    )
                )
                .put("required", JSONArray().put("changes")),
            requiresApproval = true
        )
    )

    /** Mô tả một cài đặt NOBG để thực thi (whitelist - AI chỉ được đụng những cài đặt này) */
    private data class SettingSpec(
        val key: String,
        val type: String, // "bool" | "string" | "number"
        val label: String,
        val validValues: Set<String> = emptySet(),
        val read: (NobgRepository) -> Any,
        val write: (NobgRepository, Any) -> Unit
    )

    private val settingCatalog = listOf(
        SettingSpec("ai_enabled", "bool", "AI Trợ lý", read = { it.isAiEnabled() }, write = { r, v -> r.setAiEnabled(v as Boolean) }),
        SettingSpec("ai_provider", "string", "Nhà cung cấp AI", validValues = setOf("gemini", "groq", "openrouter"), read = { it.getAiProvider() }, write = { r, v -> r.setAiProvider(v as String) }),
        SettingSpec("ai_model", "string", "Model AI", read = { it.getAiModel() }, write = { r, v -> r.setAiModel(v as String) }),
        SettingSpec("ai_summary_enabled", "bool", "Tóm tắt thông báo bằng AI", read = { it.isAiSummaryEnabled() }, write = { r, v -> r.setAiSummaryEnabled(v as Boolean) }),
        SettingSpec("ai_filter_enabled", "bool", "Lọc thông báo rác bằng AI", read = { it.isAiFilterEnabled() }, write = { r, v -> r.setAiFilterEnabled(v as Boolean) }),
        SettingSpec("ai_filter_strictness", "number", "Độ gắt lọc thông báo AI", read = { it.getAiFilterStrictness() }, write = { r, v -> r.setAiFilterStrictness((v as Number).toFloat()) }),
        SettingSpec("notif_read_enabled", "bool", "Đọc thông báo", read = { it.isNotifReadGlobalEnabled() }, write = { r, v -> r.setNotifReadGlobalEnabled(v as Boolean) }),
        SettingSpec("notif_read_only_bt", "bool", "Chỉ đọc khi kết nối Bluetooth", read = { it.isNotifReadOnlySelectedBt() }, write = { r, v -> r.setNotifReadOnlySelectedBt(v as Boolean) }),
        SettingSpec("notif_read_ducking", "bool", "Giảm âm lượng khi đọc thông báo", read = { it.isNotifReadDuckingEnabled() }, write = { r, v -> r.setNotifReadDuckingEnabled(v as Boolean) }),
        SettingSpec("tts_speech_rate", "number", "Tốc độ đọc TTS", read = { it.getTtsSpeechRate() }, write = { r, v -> r.setTtsSpeechRate((v as Number).toFloat()) }),
        SettingSpec("tts_volume", "number", "Âm lượng đọc TTS", read = { it.getTtsVolume() }, write = { r, v -> r.setTtsVolume((v as Number).toFloat()) }),
        SettingSpec("tts_pitch", "number", "Cao độ giọng đọc TTS", read = { it.getTtsPitch() }, write = { r, v -> r.setTtsPitch((v as Number).toFloat()) }),
        SettingSpec("full_battery_sound", "bool", "Âm thanh báo pin đầy", read = { it.isFullBatterySoundEnabled() }, write = { r, v -> r.setFullBatterySoundEnabled(v as Boolean) }),
        SettingSpec("theme_mode", "string", "Chủ đề giao diện", validValues = setOf("SYSTEM", "LIGHT", "DARK"), read = { it.getThemeMode() }, write = { r, v -> r.setThemeMode(v as String) })
    )

    /** Nhãn tiếng Việt ngắn gọn cho từng công cụ (hiển thị khi AI đang dùng) */
    fun labelOf(name: String): String = when (name) {
        "get_device_info" -> "thông tin máy"
        "get_battery_info" -> "thông tin pin"
        "get_overall_stats" -> "tổng quan máy"
        "get_app_usage_today" -> "thời gian dùng app"
        "get_nobg_status" -> "trạng thái NOBG"
        "get_nobg_settings" -> "cài đặt NOBG"
        "get_installed_apps" -> "danh sách app"
        "get_app_info" -> "chi tiết app"
        "get_battery_history" -> "lịch sử pin (24h)"
        "get_charging_sessions" -> "phiên sạc"
        "get_cpu_stats" -> "thống kê CPU"
        "set_nobg_setting" -> "thay đổi cài đặt NOBG"
        else -> name
    }

    /** Thực thi công cụ; KHÔNG BAO GIỜ ném exception - luôn trả về chuỗi JSON. Kết quả được cache ngắn hạn theo tool. */
    suspend fun execute(name: String, args: JSONObject, context: Context, repo: NobgRepository): String {
        return try {
            val ttl = CACHE_TTL_MS[name]
            if (ttl != null) {
                return cached(name, args, ttl) {
                    compute(name, args, context, repo)
                }
            }
            compute(name, args, context, repo)
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString()
        }
    }

    private suspend fun compute(name: String, args: JSONObject, context: Context, repo: NobgRepository): String {
        val result = when (name) {
            "get_device_info" -> deviceInfo(context)
            "get_battery_info" -> batteryInfo(context)
            "get_overall_stats" -> overallStats(context, repo)
            "get_app_usage_today" -> appUsageToday(context)
            "get_nobg_status" -> nobgStatus(repo)
            "get_installed_apps" -> installedApps(context, args)
            "get_app_info" -> appInfo(context, args)
            "get_nobg_settings" -> getAllSettings(repo)
            "get_battery_history" -> batteryHistory(context, args, repo)
            "get_charging_sessions" -> chargingSessions(context, args, repo)
            "get_cpu_stats" -> cpuStats(context, args, repo)
            // set_nobg_setting phải qua xét duyệt (xem applySettings trong ChatViewModel)
            "set_nobg_setting" -> JSONObject()
                .put("error", "Công cụ thay đổi cài đặt phải được người dùng xác nhận trước.")
            else -> JSONObject().put("error", "Công cụ không tồn tại: $name")
        }
        return result.toString()
    }

    /** Đọc toàn bộ cài đặt NOBG đang có */
    private fun getAllSettings(repo: NobgRepository): JSONObject {
        val obj = JSONObject()
        settingCatalog.forEach { spec ->
            try {
                obj.put(spec.key, spec.read(repo))
            } catch (_: Exception) {
                obj.put(spec.key, JSONObject.NULL)
            }
        }
        obj.put("ai_configured", repo.isAiFullyConfigured())
        return obj
    }

    /** Tổng hợp tình trạng máy trong 1 JSON (thay cho 4 tool lẻ) */
    private suspend fun overallStats(context: Context, repo: NobgRepository): JSONObject {
        val device = deviceInfo(context)
        val battery = batteryInfo(context)
        val usage = appUsageToday(context)
        val status = nobgStatus(repo)
        val topApps = usage.optJSONArray("apps")?.let { arr ->
            JSONArray().apply {
                for (i in 0 until minOf(arr.length(), 5)) {
                    put(arr.get(i))
                }
            }
        } ?: JSONArray()
        return JSONObject()
            .put("pin", JSONObject()
                .put("percent", battery.opt("level_percent"))
                .put("temp_c", battery.opt("temperature_celsius"))
                .put("charging", battery.opt("charging_status"))
                .put("source", battery.opt("power_source")))
            .put("ram", JSONObject()
                .put("total_gb", device.opt("ram_total_gb"))
                .put("available_gb", device.opt("ram_available_gb")))
            .put("storage", JSONObject()
                .put("total_gb", device.opt("storage_total_gb"))
                .put("free_gb", device.opt("storage_free_gb")))
            .put("top_apps_today", topApps)
            .put("nobg", JSONObject()
                .put("managed", status.opt("managed_app_count"))
                .put("frozen", status.opt("frozen_shelf_count"))
                .put("shell_ready", status.opt("shell_ready")))
    }

    /** Lịch sử pin (battery_log trong DB): min/max + các mốc mức pin gần nhất */
    private suspend fun batteryHistory(context: Context, args: JSONObject, repo: NobgRepository): JSONObject {
        val hours = args.optInt("hours", 24).coerceIn(1, 168)
        val since = System.currentTimeMillis() - hours * 3600_000L
        val logs = repo.getBatteryLogsSince(since)
        if (logs.isEmpty()) {
            return JSONObject()
                .put("hours", hours)
                .put("note", "Chưa có dữ liệu pin trong khoảng này (dữ liệu chỉ được ghi khi NOBG chạy nền).")
        }
        val minLevel = logs.minOf { it.batteryLevel }
        val maxLevel = logs.maxOf { it.batteryLevel }
        // Lấy mẫu: nhiều nhất 60 mốc, trải đều
        val sampled = if (logs.size <= 60) logs else {
            val step = logs.size.toDouble() / 60.0
            (0 until 60).map { logs[minOf(logs.size - 1, (it * step).toInt())] }
        }
        val points = JSONArray()
        sampled.forEach { l ->
            points.put(
                JSONObject()
                    .put("t", SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(l.timestamp)))
                    .put("level", l.batteryLevel)
                    .put("charging", l.isCharging)
            )
        }
        return JSONObject()
            .put("hours", hours)
            .put("min_level", minLevel)
            .put("max_level", maxLevel)
            .put("sample_count", logs.size)
            .put("points", points)
    }

    /** Các phiên sạc đã ghi nhận */
    private suspend fun chargingSessions(context: Context, args: JSONObject, repo: NobgRepository): JSONObject {
        val limit = args.optInt("limit", 10).coerceIn(1, 50)
        val sessions = repo.getAllChargingSessions().take(limit)
        if (sessions.isEmpty()) {
            return JSONObject().put("note", "Chưa có phiên sạc nào được ghi nhận. Phiên sạc được lưu khi sạc từ dưới mức đầy lên (kết thúc >= 95%).")
        }
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject()
                    .put("start", SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(s.startTimeMs)))
                    .put("duration_minutes", s.totalDurationSeconds / 60)
                    .put("from_level", s.startLevel)
                    .put("to_level", s.endLevel)
                    .put("completed_to_full", s.isCompletedToFull)
                    .put("charge_gain_percent_per_hour", percentPerHour(s.startLevel, s.endLevel, s.totalDurationSeconds))
            )
        }
        return JSONObject().put("sessions", arr)
    }

    private fun percentPerHour(start: Int, end: Int, durationSeconds: Long): Double {
        if (durationSeconds <= 0) return 0.0
        val gain = (end - start).coerceAtLeast(0)
        return Math.round((gain * 3600.0 / durationSeconds) * 10.0) / 10.0
    }

    /** Thống kê CPU từ cpu_freq_log */
    private suspend fun cpuStats(context: Context, args: JSONObject, repo: NobgRepository): JSONObject {
        val hours = args.optInt("hours", 1).coerceIn(1, 24)
        val since = System.currentTimeMillis() - hours * 3600_000L
        val logs = repo.getCpuLogsSince(since)
        if (logs.isEmpty()) {
            return JSONObject()
                .put("hours", hours)
                .put("note", "Chưa có dữ liệu CPU trong khoảng này (chỉ ghi khi NOBG đang chạy nền).")
        }
        val freqs = logs.map { it.freqMhz }
        val underclockSamples = logs.count { it.isUnderclockOn }
        return JSONObject()
            .put("hours", hours)
            .put("samples", logs.size)
            .put("freq_min_mhz", freqs.minOrNull())
            .put("freq_max_mhz", freqs.maxOrNull())
            .put("freq_avg_mhz", Math.round(freqs.average()).toInt())
            .put("underclock_on_samples", underclockSamples)
            .put("underclock_ratio", Math.round(underclockSamples * 100.0 / logs.size))
    }

    /**
     * Chuyển yêu cầu thay đổi thành mô tả tiếng Việt dễ hiểu cho dialog xét duyệt.
     * Ví dụ: {"ai_summary_enabled": true, "tts_volume": 0.8} -> "Bật Tóm tắt thông báo bằng AI; Đặt Âm lượng đọc TTS = 0.8"
     */
    fun describeSettingChange(args: JSONObject): String {
        val changes = args.optJSONObject("changes") ?: args
        val parts = mutableListOf<String>()
        changes.keys().forEach { key ->
            val spec = settingCatalog.firstOrNull { it.key == key }
            if (spec == null) {
                parts.add("\"$key\" (cài đặt không được hỗ trợ)")
                return@forEach
            }
            val value = changes.opt(key)
            parts.add(
                when {
                    value is Boolean && value -> "Bật ${spec.label}"
                    value is Boolean -> "Tắt ${spec.label}"
                    else -> "Đặt ${spec.label} = $value"
                }
            )
        }
        return if (parts.isEmpty()) "Không có thay đổi nào" else parts.joinToString("; ")
    }

    /**
     * Áp dụng thay đổi cài đặt (chỉ gọi SAU KHI người dùng chấp thuận).
     * Trả về JSON: {"approved": true, "applied": [...], "errors": [...]}
     */
    suspend fun applySettings(args: JSONObject, context: Context, repo: NobgRepository): String {
        val changes = args.optJSONObject("changes") ?: args
        val applied = mutableListOf<String>()
        val errors = mutableListOf<String>()
        changes.keys().forEach { key ->
            val spec = settingCatalog.firstOrNull { it.key == key }
            if (spec == null) {
                errors.add("\"$key\" không phải cài đặt hợp lệ")
                return@forEach
            }
            val raw = changes.opt(key)
            try {
                when (spec.type) {
                    "bool" -> {
                        if (raw !is Boolean) throw IllegalArgumentException("phải là true/false")
                        spec.write(repo, raw)
                    }
                    "number" -> {
                        if (raw !is Number) throw IllegalArgumentException("phải là số")
                        spec.write(repo, raw.toDouble())
                    }
                    "string" -> {
                        val s = raw.toString()
                        if (spec.validValues.isNotEmpty() && s !in spec.validValues) {
                            throw IllegalArgumentException("phải là một trong: ${spec.validValues.joinToString("|")}")
                        }
                        spec.write(repo, s)
                    }
                }
                applied.add(spec.label)
            } catch (e: Exception) {
                errors.add("$key: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return JSONObject()
            .put("approved", true)
            .put("applied", JSONArray(applied))
            .put("errors", JSONArray(errors))
            .toString()
    }

    private fun deviceInfo(context: Context): JSONObject {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        val data = StatFs(Environment.getDataDirectory().path)
        val totalBytes = data.totalBytes
        val freeBytes = data.availableBytes
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("android_version", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("ram_total_gb", round1(mem.totalMem / (1024.0 * 1024 * 1024)))
            .put("ram_available_gb", round1(mem.availMem / (1024.0 * 1024 * 1024)))
            .put("storage_total_gb", round1(totalBytes / (1024.0 * 1024 * 1024)))
            .put("storage_free_gb", round1(freeBytes / (1024.0 * 1024 * 1024)))
            .put("screen_on", pm?.isInteractive == true)
            .put("uptime_seconds", SystemClock.elapsedRealtime() / 1000)
    }

    private fun batteryInfo(context: Context): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        // Nguồn sạc: lấy qua sticky broadcast (tương thích mọi Android)
        val plugged = try {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        } catch (_: Exception) {
            -1
        }
        return JSONObject()
            .put("level_percent", bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
            .put("temperature_celsius", bm?.getIntProperty(PROP_TEMPERATURE)?.div(10.0) ?: -1.0)
            .put("voltage_mv", bm?.getIntProperty(PROP_VOLTAGE) ?: -1)
            .put("charging_status", batteryStatusText(bm?.getIntProperty(PROP_STATUS) ?: -1))
            .put("power_source", powerSourceText(plugged))
            .put("health", batteryHealthText(bm?.getIntProperty(PROP_HEALTH) ?: -1))
    }

    private fun batteryStatusText(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "đang sạc"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "đang xả"
        BatteryManager.BATTERY_STATUS_FULL -> "đầy"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "không sạc"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "không xác định"
        else -> "không xác định"
    }

    private fun powerSourceText(plugged: Int): String = when {
        plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "sạc không dây"
        plugged == 0 -> "không cắm sạc"
        else -> "không xác định"
    }

    private fun batteryHealthText(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "tốt"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "quá nhiệt"
        BatteryManager.BATTERY_HEALTH_DEAD -> "hỏng"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "quá áp"
        BatteryManager.BATTERY_HEALTH_COLD -> "quá lạnh"
        else -> "bình thường"
    }

    private fun appUsageToday(context: Context): JSONObject {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            return JSONObject()
                .put("usage_access", false)
                .put("hint", "NOBG chưa được cấp quyền Xem mức sử dụng (Usage Access). Hướng dẫn: Cài đặt → Ứng dụng → NOBG → Quyền → Truy cập mức sử dụng → Cho phép.")
        }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val now = System.currentTimeMillis()
        val pm = context.packageManager

        // Gộp theo package (multi-user có thể xuất hiện nhiều lần)
        val usageByPkg = HashMap<String, Long>()
        val stats: List<UsageStats> = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        } catch (_: SecurityException) {
            return JSONObject()
                .put("usage_access", false)
                .put("hint", "NOBG chưa được cấp quyền Xem mức sử dụng (Usage Access). Hướng dẫn: Cài đặt → Ứng dụng → NOBG → Quyền → Truy cập mức sử dụng → Cho phép.")
        }
        stats.forEach { s ->
            usageByPkg.merge(s.packageName, s.totalTimeInForeground, Long::plus)
        }

        val apps = usageByPkg.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(10)
            .map { (pkg, ms) ->
                val label = try { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() } catch (_: Exception) { pkg }
                JSONObject()
                    .put("package", pkg)
                    .put("label", label)
                    .put("time_on_screen_seconds", ms / 1000)
            }
        return JSONObject().put("apps", JSONArray(apps))
    }

    private suspend fun nobgStatus(repo: NobgRepository): JSONObject {
        val managed = repo.getEnabledApps()
        val shelf = repo.getFrozenShelfApps()
        val shizukuReady = try { ShizukuManager.isShizukuRunning() } catch (_: Exception) { false }
        val disabled = try {
            if (shizukuReady) ShizukuManager.getDisabledPackages() else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        return JSONObject()
            .put("managed_app_count", managed.size)
            .put("frozen_shelf_count", shelf.size)
            .put("disabled_system_app_count", disabled.size)
            .put("shell_ready", shizukuReady)
            .put("shell_hint", if (shizukuReady) "Shizuku đang chạy, đầy đủ quyền." else "Shizuku/ADB chưa sẵn sàng nên chưa thể đếm app bị vô hiệu hóa. Hướng dẫn: mở màn hình chính NOBG → Cài đặt → bật Shizuku.")
            .put("ai_enabled", repo.isAiEnabled())
            .put("ai_provider", repo.getAiProvider())
            .put("notification_reader_enabled", repo.isNotifReadGlobalEnabled())
    }

    private fun installedApps(context: Context, args: JSONObject): JSONObject {
        val keyword = args.optString("keyword", "").trim().lowercase()
        val limit = args.optInt("limit", 30).coerceIn(1, 100)
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val matched = mutableListOf<JSONObject>()
        apps.forEach { info ->
            val label = try { info.loadLabel(pm).toString() } catch (_: Exception) { "" }
            if (keyword.isNotEmpty() &&
                !label.lowercase().contains(keyword) &&
                !info.packageName.lowercase().contains(keyword)
            ) return@forEach
            matched.add(
                JSONObject()
                    .put("package", info.packageName)
                    .put("label", label)
            )
        }
        return JSONObject()
            .put("total_matched", matched.size)
            .put("apps", JSONArray(matched.take(limit)))
    }

    private suspend fun appInfo(context: Context, args: JSONObject): JSONObject {
        val pkg = args.optString("package", "").trim()
        if (pkg.isBlank()) {
            return JSONObject().put("error", "Thiếu tham số package.")
        }
        val pm = context.packageManager
        val info = try { pm.getPackageInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) {
            return JSONObject().put("error", "Không tìm thấy ứng dụng có package \"$pkg\".")
        }
        val label = try { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() } catch (_: Exception) { pkg }
        val enabledSetting = pm.getApplicationEnabledSetting(pkg)
        val lastUsed = lastTimeUsed(context, pkg)
        val standbyBucket = standbyBucket(pkg)
        return JSONObject()
            .put("package", pkg)
            .put("label", label)
            .put("version", info.versionName ?: "?")
            .put("version_code", info.longVersionCode)
            .put("installed_date", formatTime(info.firstInstallTime))
            .put("enabled", enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            .put("standby_bucket", standbyBucket)
            .put(
                "last_used",
                when {
                    lastUsed > 0 -> formatTime(lastUsed)
                    lastUsed < 0 -> "không xác định (thiếu quyền Usage Access)"
                    else -> "chưa từng dùng"
                }
            )
    }

    private fun lastTimeUsed(context: Context, pkg: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, 0, System.currentTimeMillis())
                .filter { it.packageName == pkg }
                .maxOfOrNull { it.lastTimeUsed } ?: 0
        } catch (_: SecurityException) {
            -1
        }
    }

    private suspend fun standbyBucket(pkg: String): String {
        return try {
            if (!ShizukuManager.isShizukuRunning()) return "không xác định (cần bật Shizuku/ADB)"
            val out = ShizukuManager.exec("am get-standby-bucket $pkg").trim()
            out.lines().lastOrNull()?.trim() ?: out
        } catch (_: Exception) {
            "không xác định (cần bật Shizuku/ADB)"
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
