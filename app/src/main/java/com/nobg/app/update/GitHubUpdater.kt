package com.nobg.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val publishedAt: String,
    val apkUrl: String,
    val htmlUrl: String,
    val body: String,
    val isNewer: Boolean
)

sealed class UpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    data class AlreadyLatest(val currentVersion: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object GitHubUpdater {
    private const val GITHUB_RELEASES_ALL_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases"
    private const val GITHUB_RELEASE_LATEST_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases/latest"
    private const val GITHUB_RELEASE_TAG_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases/tags/latest"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun isNewerVersion(remoteTag: String, currentVer: String): Boolean {
        if (remoteTag.equals("latest", ignoreCase = true) || remoteTag.contains("latest", ignoreCase = true)) {
            return true
        }

        val remoteClean = remoteTag.replace("[^0-9.]".toRegex(), "")
        val currentClean = currentVer.replace("[^0-9.]".toRegex(), "")

        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun fetchJson(urlString: String): Pair<Int, String?> {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NOBG-Android-App")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val code = conn.responseCode
            if (code == 200) {
                code to conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                code to null
            }
        } catch (e: Exception) {
            -1 to null
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val currentVer = getCurrentVersionName(context)
        try {
            val (code1, res1) = fetchJson(GITHUB_RELEASES_ALL_API)
            val (code2, res2) = if (res1 == null) fetchJson(GITHUB_RELEASE_LATEST_API) else (code1 to res1)
            val (code3, res3) = if (res2 == null) fetchJson(GITHUB_RELEASE_TAG_API) else (code2 to res2)

            val jsonStr = res3
            val lastCode = code3

            if (jsonStr.isNullOrBlank()) {
                val errorMsg = when (lastCode) {
                    403, 429 -> "GitHub API bị giới hạn kết nối (Rate Limit HTTP $lastCode). Vui lòng thử lại sau hoặc mở web."
                    404 -> "Không tìm thấy thông tin Release trên GitHub (HTTP 404)."
                    -1 -> "Không thể kết nối mạng tới GitHub API (Lỗi mạng hoặc hết thời gian chờ)."
                    else -> "Không thể kết nối tới GitHub Releases (Mã lỗi: $lastCode)."
                }
                return@withContext UpdateResult.Error(errorMsg)
            }

            val json: JSONObject = try {
                val trimmed = jsonStr.trim()
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    if (array.length() == 0) return@withContext UpdateResult.Error("Chưa có bản Release nào trên GitHub.")
                    array.getJSONObject(0)
                } else {
                    JSONObject(trimmed)
                }
            } catch (e: Exception) {
                return@withContext UpdateResult.Error("Lỗi đọc dữ liệu từ GitHub: ${e.message}")
            }

            var tagName = json.optString("tag_name", "latest")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "https://github.com/quyetbkhoa/NOBG/releases")
            val body = json.optString("body", "Bản build Release mới nhất từ GitHub Actions.")

            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        if (tagName.equals("latest", ignoreCase = true) && name.contains("v", ignoreCase = true)) {
                            tagName = name.substringBefore(".apk")
                        }
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                apkUrl = htmlUrl
            }

            val isNewer = isNewerVersion(tagName, currentVer)

            val info = UpdateInfo(
                tagName = tagName,
                publishedAt = publishedAt,
                apkUrl = apkUrl,
                htmlUrl = htmlUrl,
                body = body,
                isNewer = isNewer
            )

            if (isNewer) {
                return@withContext UpdateResult.UpdateAvailable(info)
            } else {
                return@withContext UpdateResult.AlreadyLatest(currentVer)
            }
        } catch (e: Exception) {
            return@withContext UpdateResult.Error("Lỗi kiểm tra bản cập nhật: ${e.message}")
        }
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long, progressPct: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "NOBG-Android-App")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) conn.contentLengthLong else conn.contentLength.toLong()

            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val destFile = File(destDir, "nobg_update.apk")
            if (destFile.exists()) destFile.delete()

            conn.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    var lastProgress = -1
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        val pct = if (totalBytes > 0) ((totalDownloaded * 100) / totalBytes).toInt() else 0
                        if (pct != lastProgress) {
                            lastProgress = pct
                            onProgress(totalDownloaded, totalBytes, pct)
                        }
                    }
                }
            }
            return@withContext destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun installApk(context: Context, apkFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Try Shizuku silent install first if available
            if (ShizukuManager.isShizukuInstalled()) {
                val out = ShizukuManager.exec("pm install -r \"${apkFile.absolutePath}\"")
                if (out.contains("Success", ignoreCase = true)) {
                    return@withContext true
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to standard Android PackageInstaller via FileProvider
        try {
            val apkUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun openDownloadLink(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}


