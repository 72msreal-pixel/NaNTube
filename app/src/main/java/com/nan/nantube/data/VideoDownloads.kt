package com.nan.nantube.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object VideoDownloads {
    private const val CHUNK_BYTES = 10L * 1024 * 1024
    private const val MAX_HEIGHT = 1080
    private const val MAX_ATTEMPTS = 4

    private class Stream(val url: String, val size: Long, val mime: String, val height: Int, val bitrate: Int)

    private class Plan(val video: Stream, val audio: Stream?, val userAgent: String)

    /** googlevideo từ chối link (403 / 404 / 410): link hết hạn hoặc client lấy link này không được phục vụ. */
    private class Refused(val code: Int) : IOException("Máy chủ video từ chối tải (HTTP $code)")

    /** Một cách lấy link /player; [userAgent] phải trùng với client đã lấy link khi tải. */
    private class Source(val name: String, val userAgent: String, val load: suspend () -> JSONObject)

    /**
     * Thứ tự theo meetube (PhoneSourcePlanner): VISIONOS đứng đầu (link trực tiếp, không cần mã hóa chữ ký hay PO token),
     * rồi ANDROID_VR. Client của tài khoản (TV / WEB) để cuối cùng: meetube ghi rõ link của TV 7.x trả 403 và WEB thường
     * chỉ có SABR, nên trước đây chọn nó đầu tiên là nguyên nhân tải bị từ chối.
     */
    private fun sources(account: Account?, id: String): List<Source> = listOf(
        Source("VISIONOS", InnerTube.VISION_UA) { InnerTube.directPlayer(id, vision = true) },
        Source("ANDROID_VR", InnerTube.VR_UA) { InnerTube.directPlayer(id, vision = false) },
        Source("ACCOUNT", InnerTube.callUserAgent(account)) {
            InnerTube.call("player", JSONObject().put("videoId", id), account)
        }
    )

    suspend fun download(context: Context, account: Account?, video: VideoItem, onProgress: (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            var failure: Exception? = null
            // Như DownloadJob của meetube: link bị từ chối thì đổi client, lấy link mới, tải lại; không thử lại đúng link cũ.
            for (source in sources(account, video.id)) {
                val plan = readPlan(source) ?: continue
                try {
                    probePlan(plan)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e
                    continue
                }
                val work = File(context.cacheDir, "download-${UUID.randomUUID()}").apply { mkdirs() }
                try {
                    val total = plan.video.size + (plan.audio?.size ?: 0L)
                    val videoPart = File(work, "video.part")
                    fetch(plan.video, videoPart, plan.userAgent) { onProgress(percent(it, total)) }
                    val result = if (plan.audio == null) {
                        videoPart
                    } else {
                        val audioPart = File(work, "audio.part")
                        fetch(plan.audio, audioPart, plan.userAgent) { onProgress(percent(plan.video.size + it, total)) }
                        File(work, "joined.mp4").also { MediaJoiner.join(videoPart, audioPart, it) }
                    }
                    val name = fileName(video)
                    publish(context, result, name)
                    return@withContext name
                } catch (e: Refused) {
                    failure = e
                } finally {
                    work.deleteRecursively()
                }
            }
            val last = failure
            if (last is Refused) {
                throw IOException("YouTube từ chối link tải của video này (HTTP ${last.code}). Thử lại sau hoặc chọn video khác.")
            }
            throw last ?: IllegalStateException("YouTube không cung cấp bản MP4 tải trực tiếp cho video này")
        }

    /** Hỏi thử 2 byte đầu của luồng video và âm thanh: link bị từ chối thì bỏ plan này ngay, khỏi tải dở rồi mới biết. */
    private fun probePlan(plan: Plan) {
        probe(plan.video.url, plan.userAgent)
        plan.audio?.let { probe(it.url, plan.userAgent) }
    }

    private fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)

    private fun fileName(video: VideoItem): String {
        val clean = video.title.replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "_").trim().take(55).ifBlank { "Video" }
        return "$clean-${video.id}.mp4"
    }

    private suspend fun readPlan(source: Source): Plan? {
        val json = try {
            source.load()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (json.dig("videoDetails", "isLive") == true || json.dig("videoDetails", "isUpcoming") == true) {
            error("Không tải được video đang hoặc sắp phát trực tiếp")
        }
        if (json.dig("playabilityStatus", "status") != "OK") return null
        val data = json.optJSONObject("streamingData") ?: return null
        val adaptive = streams(data.optJSONArray("adaptiveFormats"))
        val video = adaptive.filter { it.mime.startsWith("video/mp4") && "avc1" in it.mime && it.height <= MAX_HEIGHT }
            .maxByOrNull { it.height }
        val audio = adaptive.filter { it.mime.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
        if (video != null && audio != null) return Plan(video, audio, source.userAgent)
        val muxed = streams(data.optJSONArray("formats"))
            .filter { it.mime.startsWith("video/mp4") }
            .maxByOrNull { it.height }
        return muxed?.let { Plan(it, null, source.userAgent) }
    }

    private fun streams(formats: JSONArray?): List<Stream> {
        if (formats == null) return emptyList()
        return (0 until formats.length()).mapNotNull { index ->
            val format = formats.optJSONObject(index) ?: return@mapNotNull null
            val url = format.optString("url").ifBlank { null } ?: return@mapNotNull null
            val uri = Uri.parse(url)
            val host = uri.host ?: return@mapNotNull null
            val trusted = uri.scheme == "https" && (host == "googlevideo.com" || host.endsWith(".googlevideo.com"))
            val size = format.optString("contentLength").toLongOrNull() ?: uri.getQueryParameter("clen")?.toLongOrNull()
            if (!trusted || size == null || size <= 0) return@mapNotNull null
            Stream(url, size, format.optString("mimeType"), format.optInt("height"), format.optInt("bitrate"))
        }
    }

    private fun fetch(stream: Stream, target: File, userAgent: String, onBytes: (Long) -> Unit) {
        target.outputStream().use { out ->
            var offset = 0L
            while (offset < stream.size) {
                val end = minOf(offset + CHUNK_BYTES, stream.size) - 1
                val bytes = readRange(stream.url, offset, end, userAgent)
                if (bytes.isEmpty()) throw IOException("Máy chủ video trả dữ liệu rỗng")
                out.write(bytes)
                offset += bytes.size
                onBytes(offset)
            }
        }
    }

    /** Lỗi mạng thoáng qua thì thử lại đúng đoạn đó (có chờ); link bị từ chối thì dừng để đổi client. */
    private fun readRange(url: String, start: Long, end: Long, userAgent: String): ByteArray {
        var failure: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return requestRange(url, start, end, userAgent)
            } catch (e: Refused) {
                throw e
            } catch (e: IOException) {
                failure = e
                try {
                    Thread.sleep(1_000L shl attempt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
        throw failure ?: IOException("Không tải được dữ liệu video")
    }

    /** Dùng đúng tiêu đề Range như StreamFetcher của meetube (không dùng tham số range= trên URL). */
    private fun requestRange(url: String, start: Long, end: Long, userAgent: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Range", "bytes=$start-$end")
            val code = conn.responseCode
            if (code == 403 || code == 404 || code == 410) throw Refused(code)
            if (code != 200 && code != 206) throw IOException("Máy chủ video trả HTTP $code")
            if (code == 200) {
                // Máy chủ bỏ qua Range: chỉ chấp nhận khi đây là đoạn đầu và cả luồng còn nhỏ, tránh đọc cả tệp lớn vào RAM.
                if (start > 0 || conn.contentLengthLong > CHUNK_BYTES * 2) throw IOException("Máy chủ bỏ qua yêu cầu tải theo đoạn")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun probe(url: String, userAgent: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Range", "bytes=0-1")
            val code = conn.responseCode
            if (code == 403 || code == 404 || code == 410) throw Refused(code)
            if (code != 200 && code != 206) throw IOException("Máy chủ video trả HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun publish(context: Context, source: File, name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Không tạo được tệp trong thư mục Tải xuống")
            val out = resolver.openOutputStream(uri) ?: error("Không ghi được tệp trong thư mục Tải xuống")
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("Máy không có bộ nhớ để lưu video")
            source.copyTo(File(folder, name), overwrite = true)
        }
    }
}
