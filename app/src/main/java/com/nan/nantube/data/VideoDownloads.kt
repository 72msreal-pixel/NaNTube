package com.nan.nantube.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object VideoDownloads {
    private const val CHUNK_BYTES = 10L * 1024 * 1024
    private const val MAX_HEIGHT = 1080
    private const val MAX_ATTEMPTS = 4
    private const val METADATA_PREFS = "download_metadata"
    // Android có thể thêm " (1)" vào tên nếu tải lại một video đã tồn tại.
    private val savedName = Regex("^(.+)-([A-Za-z0-9_-]{11})(?: [(][0-9]+[)])?[.]mp4$")
    private val revision = MutableStateFlow(0)

    fun changes(context: Context): Flow<Unit> {
        val writes = revision.map { Unit }
        if (Build.VERSION.SDK_INT < 29) return writes
        val media = callbackFlow<Unit> {
            val resolver = context.contentResolver
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) { trySend(Unit) }
            }
            resolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, observer)
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
        return merge(writes, media).conflate()
    }

    /** Danh sách trên thiết bị, không phụ thuộc tài khoản. Đọc cả tệp do phiên bản cũ của app ghi. */
    suspend fun page(context: Context): VideoPage = withContext(Dispatchers.IO) {
        val names = if (Build.VERSION.SDK_INT >= 29) {
            val columns = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val where = "${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ? AND " +
                "${MediaStore.Downloads.MIME_TYPE} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0 AND " +
                "${MediaStore.Downloads.SIZE} > 0"
            val args = arrayOf(context.packageName, "video/mp4")
            val order = "${MediaStore.Downloads.DATE_ADDED} DESC, ${MediaStore.Downloads._ID} DESC"
            val cursor = context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, columns, where, args, order)
                ?: error("Không đọc được thư mục Tải xuống")
            cursor.use { buildList { while (it.moveToNext()) add(it.getString(0).orEmpty()) } }
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.name }.orEmpty()
        }
        val prefs = context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
        val items = names.mapNotNull { name ->
            val match = savedName.matchEntire(name) ?: return@mapNotNull null
            val (fileTitle, id) = match.destructured
            val metadata = prefs.getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            fun field(key: String): String? = (metadata?.opt(key) as? String)?.takeIf { it.isNotBlank() }
            VideoItem(id, field("title") ?: fileTitle, field("channel"), field("duration"), null,
                metadata?.optBoolean("short") == true)
        }.distinctBy { it.id }
        VideoPage(items, null, total = "${items.size} video")
    }


    private class Stream(val url: String, val size: Long, val mime: String, val height: Int, val bitrate: Int)

    private class Plan(val video: Stream, val audio: Stream?)

    /** googlevideo từ chối link (403 / 404 / 410): link hết hạn hoặc client lấy link này không được phục vụ. */
    private class Refused(val code: Int) : IOException("Máy chủ video từ chối tải (HTTP $code)")

    /** Một cách lấy link /player. */
    private class Source(val name: String, val load: suspend () -> JSONObject)

    /**
     * Thứ tự theo meetube (PhoneSourcePlanner): VISIONOS đứng đầu (link trực tiếp, không cần mã hóa chữ ký hay PO token),
     * rồi ANDROID_VR. Client của tài khoản (TV / WEB) để cuối cùng: meetube ghi rõ link của TV 7.x trả 403 và WEB thường
     * chỉ có SABR, nên trước đây chọn nó đầu tiên là nguyên nhân tải bị từ chối.
     */
    private fun sources(account: Account?, id: String): List<Source> = listOf(
        Source("VISIONOS") { InnerTube.directPlayer(id, vision = true) },
        Source("ANDROID_VR") { InnerTube.directPlayer(id, vision = false) },
        Source("ACCOUNT") {
            InnerTube.call("player", JSONObject().put("videoId", id), account)
        }
    )

    suspend fun download(context: Context, account: Account?, video: VideoItem, onProgress: (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            var failure: Exception? = null
            var reason: String? = null
            // Như DownloadJob của meetube: link bị từ chối thì đổi client, lấy link mới, tải lại; không thử lại đúng link cũ.
            for (source in sources(account, video.id)) {
                val plan = readPlan(source) { reason = "${source.name}: $it" } ?: continue
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
                    fetch(plan.video, videoPart) { onProgress(percent(it, total)) }
                    val result = if (plan.audio == null) {
                        videoPart
                    } else {
                        val audioPart = File(work, "audio.part")
                        fetch(plan.audio, audioPart) { onProgress(percent(plan.video.size + it, total)) }
                        File(work, "joined.mp4").also { MediaJoiner.join(videoPart, audioPart, it) }
                    }
                    val name = fileName(video)
                    publish(context, result, name)
                    val metadata = JSONObject().put("title", video.title).put("channel", video.channel)
                        .put("duration", video.duration).put("short", video.isShort)
                    context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
                        .edit().putString(video.id, metadata.toString()).apply()
                    revision.update { it + 1 }
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
            throw last ?: IllegalStateException(
                reason?.let { "YouTube không cung cấp bản MP4 tải trực tiếp cho video này ($it)" }
                    ?: "YouTube không cung cấp bản MP4 tải trực tiếp cho video này"
            )
        }

    /** Hỏi thử 2 byte đầu của luồng video và âm thanh: link bị từ chối thì bỏ plan này ngay, khỏi tải dở rồi mới biết. */
    private fun probePlan(plan: Plan) {
        probe(plan.video.url)
        plan.audio?.let { probe(it.url) }
    }

    private fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)

    private fun fileName(video: VideoItem): String {
        val clean = video.title.replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "_").trim().take(55).ifBlank { "Video" }
        return "$clean-${video.id}.mp4"
    }

    /** Đọc /player của [source] thành kế hoạch tải; không dùng được thì trả null kèm lý do qua [why] (hiện trong thông báo lỗi). */
    private suspend fun readPlan(source: Source, why: (String) -> Unit): Plan? {
        val json = try {
            source.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            why(e.message ?: "không kết nối được")
            return null
        }
        if (json.dig("videoDetails", "isLive") == true || json.dig("videoDetails", "isUpcoming") == true) {
            error("Không tải được video đang hoặc sắp phát trực tiếp")
        }
        val status = json.dig("playabilityStatus", "status") as? String
        if (status != "OK") {
            val detail = json.dig("playabilityStatus", "reason") as? String
            why(listOfNotNull(status, detail).joinToString(" - ").ifBlank { "không có dữ liệu phát" })
            return null
        }
        val data = json.optJSONObject("streamingData")
        if (data == null) {
            why("không có streamingData")
            return null
        }
        val adaptive = streams(data.optJSONArray("adaptiveFormats"))
        val video = adaptive.filter { it.mime.startsWith("video/mp4") && "avc1" in it.mime && it.height <= MAX_HEIGHT }
            .maxByOrNull { it.height }
        val audio = adaptive.filter { it.mime.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
        if (video != null && audio != null) return Plan(video, audio)
        val muxed = streams(data.optJSONArray("formats"))
            .filter { it.mime.startsWith("video/mp4") }
            .maxByOrNull { it.height }
        if (muxed == null) why("không có link MP4 trực tiếp (chỉ có luồng mã hóa/SABR)")
        return muxed?.let { Plan(it, null) }
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

    private fun fetch(stream: Stream, target: File, onBytes: (Long) -> Unit) {
        target.outputStream().use { out ->
            var offset = 0L
            while (offset < stream.size) {
                val end = minOf(offset + CHUNK_BYTES, stream.size) - 1
                val bytes = readRange(stream.url, offset, end)
                if (bytes.isEmpty()) throw IOException("Máy chủ video trả dữ liệu rỗng")
                out.write(bytes)
                offset += bytes.size
                onBytes(offset)
            }
        }
    }

    /** Lỗi mạng thoáng qua thì thử lại đúng đoạn đó (có chờ); link bị từ chối thì dừng để đổi client. */
    private fun readRange(url: String, start: Long, end: Long): ByteArray {
        var failure: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return requestRange(url, start, end)
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

    /** Dùng đúng tiêu đề Range và UA media như StreamFetcher của meetube (không dùng tham số range= trên URL). */
    private fun requestRange(url: String, start: Long, end: Long): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", InnerTube.MEDIA_UA)
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

    private fun probe(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", InnerTube.MEDIA_UA)
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
            var published = false
            try {
                val out = resolver.openOutputStream(uri) ?: error("Không ghi được tệp trong thư mục Tải xuống")
                out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
                val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                check(updated == 1) { "Không hoàn tất được tệp trong thư mục Tải xuống" }
                published = true
            } finally {
                if (!published) runCatching { resolver.delete(uri, null, null) }
            }
        } else {
            val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("Máy không có bộ nhớ để lưu video")
            val partial = File.createTempFile("nantube-", ".part", folder)
            try {
                source.copyTo(partial, overwrite = true)
                check(partial.renameTo(File(folder, name))) { "Không hoàn tất được tệp trong thư mục Tải xuống" }
            } finally {
                partial.delete()
            }
        }
    }
}
