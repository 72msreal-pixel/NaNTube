package com.nan.nantube.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Chọn MP4 có sẵn cả tiếng lẫn hình từ /player, rồi giao việc tải cho Android. */
object VideoDownloads {
    private data class Media(val uri: Uri, val userAgent: String?)

    suspend fun enqueue(context: Context, account: Account?, video: VideoItem) = withContext(Dispatchers.IO) {
        val media = findMedia(account, video.id)
        val name = video.title.replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "_")
            .trim().take(55).ifBlank { "Video" }
        val filename = "$name-${video.id}-${UUID.randomUUID().toString().take(8)}.mp4"
        val request = DownloadManager.Request(media.uri)
            .setTitle(video.title)
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        media.userAgent?.let { request.addRequestHeader("User-Agent", it) }
        if (Build.VERSION.SDK_INT >= 29) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } else {
            @Suppress("DEPRECATION")
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
                .setVisibleInDownloadsUi(true)
        }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: error("Máy không có trình quản lý tải xuống")
        check(manager.enqueue(request) > 0) { "Không đưa được video vào hàng đợi tải" }
    }

    private suspend fun findMedia(account: Account?, id: String): Media {
        val body = JSONObject().put("videoId", id)
        // Ưu tiên các nguồn URL trực tiếp của Meetube, không gửi token/cookie sang client khác.
        val sources: List<Pair<String?, suspend () -> JSONObject>> = listOf(
            null to { InnerTube.call("player", body, account) },
            InnerTube.VISION_UA to { InnerTube.directPlayer(id, vision = true) },
            InnerTube.VR_UA to { InnerTube.directPlayer(id, vision = false) }
        )
        for ((ua, player) in sources) {
            val json = try {
                player()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            if (json.dig("videoDetails", "isLive") == true || json.dig("videoDetails", "isUpcoming") == true) {
                error("Không tải được video đang hoặc sắp phát trực tiếp")
            }
            if (json.dig("playabilityStatus", "status") != "OK") continue
            val formats = json.dig("streamingData", "formats") as? JSONArray ?: continue
            val direct = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
                .filter { it.optString("mimeType").startsWith("video/mp4") }
                .sortedByDescending { it.optInt("height") }
            for (format in direct) {
                val url = format.optString("url")
                if (url.isBlank()) continue
                val uri = Uri.parse(url)
                val host = uri.host ?: continue
                if (uri.scheme == "https" && (host == "googlevideo.com" || host.endsWith(".googlevideo.com"))) {
                    return Media(uri, ua)
                }
            }
        }
        error("YouTube không cung cấp bản MP4 tải trực tiếp cho video này")
    }
}

/** Luồng Lounge của Meetube: ghép mã TV → bind REMOTE_CONTROL → gửi setPlaylist. */
object TvPlayback {
    private const val ORIGIN = "https://www.youtube.com"
    private const val BIND = "$ORIGIN/api/lounge/bc/bind"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36"
    private val headers = mapOf("User-Agent" to userAgent, "Origin" to ORIGIN, "Referer" to "$ORIGIN/")

    suspend fun play(code: String, videoId: String) = withContext(Dispatchers.IO) {
        val normalized = code.filterNot { it.isWhitespace() || it == '-' }
        require(normalized.length == 12 && normalized.all { it in '0'..'9' }) { "Mã TV phải có 12 chữ số" }
        val screen = pair(normalized)
        val token = screen.optString("loungeToken")
        check(token.isNotBlank()) { "Mã TV không hợp lệ hoặc đã hết hạn" }
        val rid = (10_000..89_999).random()
        val bindUrl = url(BIND, mapOf(
            "device" to "REMOTE_CONTROL", "id" to UUID.randomUUID().toString(),
            "name" to "NaNTube", "app" to "youtube-desktop", "loungeIdToken" to token,
            "VER" to "8", "CVER" to "1", "RID" to rid.toString(),
            "zx" to UUID.randomUUID().toString().replace("-", "").take(12)
        ))
        val bind = Http.post(bindUrl, "", "application/x-www-form-urlencoded", headers)
        check(bind.code in 200..299) { "Không kết nối được với TV" }
        val (sid, gsid) = sessionIds(bind.body)
        val commandUrl = url(BIND, mapOf(
            "VER" to "8", "CVER" to "1", "RID" to (rid + 1).toString(),
            "SID" to sid, "gsessionid" to gsid, "loungeIdToken" to token
        ))
        val command = mapOf(
            "count" to "1", "ofs" to "0", "req0__sc" to "setPlaylist", "req0_videoId" to videoId,
            "req0_videoIds" to videoId, "req0_currentIndex" to "0", "req0_currentTime" to "0"
        ).entries.joinToString("&") { "${Uri.encode(it.key)}=${Uri.encode(it.value)}" }
        val result = Http.post(commandUrl, command, "application/x-www-form-urlencoded", headers)
        check(result.code in 200..299) { "TV không nhận được lệnh phát" }
    }

    private fun pair(code: String): JSONObject {
        val address = url("$ORIGIN/api/lounge/pairing/get_screen", mapOf("pairing_code" to code))
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = false
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            check(conn.responseCode in 200..299) { "Không ghép được TV. Kiểm tra mã và thử lại." }
            val data = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(data).optJSONObject("screen") ?: error("Mã TV không hợp lệ hoặc đã hết hạn")
        } finally {
            conn.disconnect()
        }
    }

    internal fun sessionIds(body: String): Pair<String, String> {
        val source = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        val data = DataInputStream(source)
        var sid: String? = null
        var gsid: String? = null
        while (source.available() > 0) {
            val line = StringBuilder()
            while (true) {
                val b = source.read()
                if (b < 0 || b == 10) break
                if (line.length > 1024) error("Phản hồi TV không hợp lệ")
                line.append(b.toChar())
            }
            val length = line.toString().trim().toIntOrNull() ?: continue
            if (length <= 0 || length > source.available()) break
            val bytes = ByteArray(length)
            data.readFully(bytes)
            val messages = JSONArray(String(bytes, Charsets.UTF_8))
            for (i in 0 until messages.length()) {
                val event = messages.optJSONArray(i)?.optJSONArray(1) ?: continue
                when (event.optString(0)) {
                    "c" -> sid = event.optString(1).ifBlank { null }
                    "S" -> gsid = event.optString(1).ifBlank { null }
                }
            }
            if (sid != null && gsid != null) return sid to gsid
        }
        error("TV không trả về phiên kết nối")
    }

    private fun url(base: String, params: Map<String, String>): String =
        Uri.parse(base).buildUpon().apply { params.forEach { (key, value) -> appendQueryParameter(key, value) } }.build().toString()
}
