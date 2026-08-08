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
            name = "get_app_usage_today",
            description = "Đọc danh sách 10 ứng dụng được dùng nhiều nhất hôm nay (từ 00:00) kèm thời gian sử dụng trên màn hình.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
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
        )
    )

    /** Nhãn tiếng Việt ngắn gọn cho từng công cụ (hiển thị khi AI đang dùng) */
    fun labelOf(name: String): String = when (name) {
        "get_device_info" -> "thông tin máy"
        "get_battery_info" -> "thông tin pin"
        "get_app_usage_today" -> "thời gian dùng app"
        "get_nobg_status" -> "trạng thái NOBG"
        "get_installed_apps" -> "danh sách app"
        "get_app_info" -> "chi tiết app"
        else -> name
    }

    /** Thực thi công cụ; KHÔNG BAO GIỜ ném exception - luôn trả về chuỗi JSON */
    suspend fun execute(name: String, args: JSONObject, context: Context, repo: NobgRepository): String {
        return try {
            val result = when (name) {
                "get_device_info" -> deviceInfo(context)
                "get_battery_info" -> batteryInfo(context)
                "get_app_usage_today" -> appUsageToday(context)
                "get_nobg_status" -> nobgStatus(repo)
                "get_installed_apps" -> installedApps(context, args)
                "get_app_info" -> appInfo(context, args)
                else -> JSONObject().put("error", "Công cụ không tồn tại: $name")
            }
            result.toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString()
        }
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
            return JSONObject().put("error", "Không có quyền xem thời gian sử dụng (Usage Access).")
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
            return JSONObject().put("error", "Không có quyền xem thời gian sử dụng (Usage Access).")
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
        val disabled = try {
            if (ShizukuManager.isShizukuRunning()) ShizukuManager.getDisabledPackages() else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        return JSONObject()
            .put("managed_app_count", managed.size)
            .put("frozen_shelf_count", shelf.size)
            .put("disabled_system_app_count", disabled.size)
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
            .put("last_used", if (lastUsed > 0) formatTime(lastUsed) else "chưa từng dùng")
    }

    private fun lastTimeUsed(context: Context, pkg: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, 0, System.currentTimeMillis())
                .filter { it.packageName == pkg }
                .maxOfOrNull { it.lastTimeUsed } ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    private suspend fun standbyBucket(pkg: String): String {
        return try {
            val out = ShizukuManager.exec("am get-standby-bucket $pkg").trim()
            out.lines().lastOrNull()?.trim() ?: out
        } catch (_: Exception) {
            "không xác định"
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
