package com.nobg.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val publishedAt: String,
    val apkUrl: String,
    val htmlUrl: String,
    val body: String
)

sealed class UpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    object AlreadyLatest : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object GitHubUpdater {
    private const val GITHUB_RELEASES_ALL_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases"
    private const val GITHUB_RELEASE_LATEST_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases/latest"
    private const val GITHUB_RELEASE_TAG_API = "https://api.github.com/repos/quyetbkhoa/NOBG/releases/tags/latest"

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val jsonStr = fetchJson(GITHUB_RELEASES_ALL_API)
                ?: fetchJson(GITHUB_RELEASE_LATEST_API)
                ?: fetchJson(GITHUB_RELEASE_TAG_API)

            if (jsonStr.isNullOrBlank()) {
                return@withContext UpdateResult.Error("Không thể kết nối tới GitHub Releases.")
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

            val tagName = json.optString("tag_name", "latest")
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
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                apkUrl = htmlUrl
            }

            val info = UpdateInfo(
                tagName = tagName,
                publishedAt = publishedAt,
                apkUrl = apkUrl,
                htmlUrl = htmlUrl,
                body = body
            )

            return@withContext UpdateResult.UpdateAvailable(info)
        } catch (e: Exception) {
            return@withContext UpdateResult.Error("Lỗi kiểm tra bản cập nhật: ${e.message}")
        }
    }

    private fun fetchJson(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NOBG-Android-App")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
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
