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
    /** Âm thanh của đoạn cắt lấy sớm hơn mốc bắt đầu chừng này: video cắt ở khung khóa trước mốc. */
    private const val AUDIO_LEAD_MS = 6_000L
    private const val METADATA_PREFS = "download_metadata"
    // Android có thể thêm " (1)" vào tên nếu tải lại một video đã tồn tại.
    private val savedName = Regex("^(.+)-([A-Za-z0-9_-]{11})(?: [(][0-9]+[)])?[.](mp4|m4a|mp3)$")
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

    /**
     * Danh sách trên thiết bị. Đọc cả tệp do phiên bản cũ của app ghi.
     * Video đã tải mà Android không còn trả về cho app (vd sau khi gỡ app cài lại) được khôi phục từ sổ ghi
     * trong thư mục NaNTube của [account] (null = khách), với điều kiện tệp vẫn còn trong thư mục Tải xuống.
     */
    suspend fun page(context: Context, account: Account? = null): VideoPage = withContext(Dispatchers.IO) {
        NanStorage.init(context)
        val names = if (Build.VERSION.SDK_INT >= 29) {
            val columns = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val where = "${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ? AND " +
                "${MediaStore.Downloads.MIME_TYPE} IN (?, ?, ?) AND ${MediaStore.Downloads.IS_PENDING} = 0 AND " +
                "${MediaStore.Downloads.SIZE} > 0"
            val args = arrayOf(context.packageName, "video/mp4", "audio/mp4", "audio/mpeg")
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
        val records = readRecords(account)
        val prefs = context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
        fun itemOf(name: String): VideoItem? {
            val match = savedName.matchEntire(name) ?: return null
            val (fileTitle, id, ext) = match.destructured
            val audio = ext != "mp4"
            val metadata = prefs.getString(if (audio) "a$id" else id, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: if (audio) null else records.optJSONObject(id)
            fun field(key: String): String? = (metadata?.opt(key) as? String)?.takeIf { it.isNotBlank() }
            return VideoItem(id, field("title") ?: fileTitle, field("channel"), field("duration"), null,
                metadata?.optBoolean("short") == true && !audio, music = audio)
        }
        val listed = names.mapNotNull { itemOf(it) }
        val seen = listed.map { it.id }.toHashSet()
        val restored = restoredNames(records).mapNotNull { name ->
            val item = itemOf(name) ?: return@mapNotNull null
            if (seen.add(item.id)) item else null
        }
        val items = (listed + restored).distinctBy { it.id to it.music }
        VideoPage(items, null, total = "${items.size} mục")
    }

    // ---------- Sổ video đã tải (lưu ở thư mục NaNTube, theo từng tài khoản) ----------

    private const val RECORDS = "downloads"

    private fun readRecords(account: Account?): JSONObject =
        (NanStorage.readJson(RECORDS, account) as? JSONObject) ?: JSONObject()

    /** Ghi nhận một video vừa tải xong vào sổ của [account]: id → tiêu đề, kênh, thời lượng, Short, tên tệp, lúc tải. */
    private fun recordDownload(account: Account?, video: VideoItem, fileName: String) {
        val records = readRecords(account)
        records.put(
            video.id,
            JSONObject().put("title", video.title).put("channel", video.channel).put("duration", video.duration)
                .put("short", video.isShort).put("file", fileName).put("at", System.currentTimeMillis())
        )
        NanStorage.writeJson(RECORDS, account, records)
    }

    /** Tên tệp trong sổ mà tệp vẫn còn trong thư mục Tải xuống chung (cần quyền truy cập NaNTube/tệp), mới nhất trước. */
    private fun restoredNames(records: JSONObject): List<String> {
        if (!NanStorage.hasAccess()) return emptyList()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return records.keys().asSequence().mapNotNull { id ->
            val entry = records.optJSONObject(id) ?: return@mapNotNull null
            val name = entry.optString("file").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val file = File(downloads, name)
            if (file.isFile && file.length() > 0) name to entry.optLong("at") else null
        }.sortedByDescending { it.second }.map { it.first }.toList()
    }

    private class Stream(
        val url: String, val size: Long, val mime: String, val height: Int, val bitrate: Int,
        /** "0-740" / "741-1500": phần đầu và chỉ mục sidx của luồng MP4, để tải đoạn video chỉ lấy các mảnh cần. */
        val init: String? = null, val index: String? = null
    )

    /** [video] null = chỉ tải âm thanh; [audio] null = [video] là luồng liền (đã có cả tiếng). */
    private class Plan(val video: Stream?, val audio: Stream?, val details: JSONObject? = null)

    /** googlevideo từ chối link (403 / 404 / 410): link hết hạn hoặc client lấy link này không được phục vụ. */
    private class Refused(val code: Int) : IOException("Máy chủ video từ chối tải (HTTP $code)")

    /**
     * Các mức đồ họa, dung lượng âm thanh và độ dài của video, để dựng bảng Tải xuống. Dùng cùng danh sách nguồn /player
     * với lúc tải thật ([PlaybackSource.walk]).
     */
    suspend fun info(context: Context, account: Account?, videoId: String): DownloadInfo = withContext(Dispatchers.IO) {
        val refusals = PlaybackRefusals()
        PlaybackSource.walk(context.applicationContext, account, videoId, refusals = refusals) { answer ->
            val adaptive = streams(answer.streamingData.optJSONArray("adaptiveFormats"))
            val audio = adaptive.filter { it.mime.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
            val avc = adaptive.filter { it.mime.startsWith("video/mp4") && "avc1" in it.mime && it.height <= MAX_HEIGHT }
            val muxed = streams(answer.streamingData.optJSONArray("formats")).filter { it.mime.startsWith("video/mp4") }
            val qualities = if (audio != null && avc.isNotEmpty()) {
                avc.groupBy { it.height }.map { (height, list) -> QualityOption(height, list.first().size + audio.size) }
            } else {
                muxed.groupBy { it.height }.map { (height, list) -> QualityOption(height, list.first().size) }
            }.sortedByDescending { it.height }
            val audioBytes = audio?.size ?: muxed.minByOrNull { it.height }?.size ?: -1L
            if (qualities.isEmpty() && audio == null) {
                refusals.add(answer.source, "không có link MP4 trực tiếp (chỉ có luồng mã hóa/SABR)")
                return@walk null
            }
            val length = answer.json.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toLongOrNull()?.times(1000) ?: -1L
            DownloadInfo(qualities, audioBytes, length)
        } ?: throw refusals.failure("tải")
    }

    /**
     * Lấy link, tải (có chia đoạn) rồi ghép / cắt / đổi định dạng theo [request]: MP4 (video + âm thanh), M4A hoặc MP3, nằm trong [work].
     * Dùng cho bảng Tải xuống (trình phát Shorts đã chuyển sang phát trực tiếp, xem [ShortsPlayback]). [onMeta] nhận thông tin kênh của video.
     * Như DownloadJob của meetube: link bị từ chối thì đổi nguồn, lấy link mới, tải lại; không thử lại đúng link cũ.
     */
    private suspend fun produce(
        context: Context, account: Account?, video: VideoItem, request: DownloadRequest, work: File,
        onMeta: (JSONObject) -> Unit, onProgress: (Int) -> Unit
    ): File {
        var failure: Exception? = null
        val refusals = PlaybackRefusals()
        val file = PlaybackSource.walk(context, account, video.id, refusals = refusals) { answer ->
            val plan = readPlan(answer, request, refusals) ?: return@walk null
            plan.details?.let(onMeta)
            try {
                probePlan(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                return@walk null
            }
            try {
                assemble(plan, request, work, onProgress)
            } catch (e: Refused) {
                failure = e
                null
            }
        }
        if (file != null) return file
        val last = failure
        if (last is Refused) {
            throw IOException("YouTube từ chối link của video này (HTTP ${last.code}). Thử lại sau hoặc chọn video khác.")
        }
        throw last ?: refusals.failure("tải")
    }

    /** Tải các luồng của [plan] (chỉ các mảnh cần khi cắt đoạn) rồi tạo tệp kết quả. */
    private fun assemble(plan: Plan, request: DownloadRequest, work: File, onProgress: (Int) -> Unit): File {
        val clip = request.isClip
        val videoPlan = if (clip) plan.video?.let { clipPlan(it, request.clipStartMs, request.clipEndMs) } else null
        // Âm thanh lấy rộng hơn vài giây phía trước: điểm cắt video là khung khóa ở trước mốc bắt đầu.
        val audioPlan = if (clip) plan.audio?.let { clipPlan(it, maxOf(0L, request.clipStartMs - AUDIO_LEAD_MS), request.clipEndMs) } else null
        val videoLen = plan.video?.let { videoPlan?.fileLength() ?: it.size } ?: 0L
        val audioLen = plan.audio?.let { audioPlan?.fileLength() ?: it.size } ?: 0L
        val total = videoLen + audioLen

        var videoPart: File? = null
        var audioPart: File? = null
        if (plan.video != null) {
            videoPart = File(work, "video.part")
            fetch(plan.video, videoPart, videoPlan) { onProgress(percent(it, total)) }
        }
        if (plan.audio != null) {
            audioPart = File(work, "audio.part")
            fetch(plan.audio, audioPart, audioPlan) { onProgress(percent(videoLen + it, total)) }
        }
        onProgress(100)

        val window = if (clip) {
            MediaJoiner.Clip(
                request.clipStartMs * 1000, request.clipEndMs * 1000,
                (videoPlan?.startMs ?: 0L) * 1000, (audioPlan?.startMs ?: 0L) * 1000
            )
        } else null

        if (request.audioOnly) {
            val source = audioPart ?: error("Không có luồng âm thanh")
            if (request.audioFormat == AudioFormat.MP3) {
                return File(work, "audio.mp3").also {
                    Mp3Encoder.encode(source, it, window?.startUs ?: 0L, window?.endUs ?: 0L, window?.audioPartStartUs ?: 0L)
                }
            }
            return File(work, "audio.m4a").also { MediaJoiner.join(null, source, it, window) }
        }
        val videoFile = videoPart ?: error("Không có luồng video")
        // Luồng liền (đã có cả tiếng) và tải nguyên thì dùng luôn; cắt đoạn thì đọc cả hai track từ cùng tệp.
        if (audioPart == null && !clip) return videoFile
        return File(work, "joined.mp4").also { MediaJoiner.join(videoFile, audioPart ?: videoFile, it, window) }
    }

    /** Các mảnh MP4 phủ [startMs, endMs] của luồng, hoặc null để tải nguyên (WebM, luồng liền, chỉ mục không đọc được). */
    private fun clipPlan(stream: Stream, startMs: Long, endMs: Long): ClipRanges.Plan? {
        val init = ClipRanges.range(stream.init)
        val index = ClipRanges.range(stream.index)
        if (init == null || index == null || init[0] != 0L || index[1] > 1024 * 1024) return null
        if (!stream.mime.startsWith("video/mp4") && !stream.mime.startsWith("audio/mp4")) return null
        return try {
            val head = readRange(stream.url, 0, index[1])
            ClipRanges.plan(head, init[1].toInt(), index[0].toInt(), startMs, endMs)
        } catch (e: Refused) {
            throw e
        } catch (_: IOException) {
            null
        }
    }

    /** Tải theo [request] (video ở mức đồ họa đã chọn, âm thanh M4A / MP3, hoặc một đoạn) rồi lưu vào thư mục Tải xuống. */
    suspend fun download(
        context: Context, account: Account?, video: VideoItem, request: DownloadRequest = DownloadRequest(),
        onProgress: (Int) -> Unit
    ): String =
        withContext(Dispatchers.IO) {
            val work = File(context.cacheDir, "download-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val result = produce(context.applicationContext, account, video, request, work, {}, onProgress)
                val name = fileName(video, request)
                publish(context, result, name, if (request.audioOnly) request.audioFormat.mime else "video/mp4")
                // Âm thanh: nhớ tiêu đề/kênh để hiện ở chip Âm nhạc của "Nội dung đã tải".
                if (request.audioOnly && !request.isClip) {
                    val metadata = JSONObject().put("title", video.title).put("channel", video.channel)
                        .put("duration", video.duration).put("short", false)
                    context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
                        .edit().putString("a${video.id}", metadata.toString()).apply()
                }
                // Chỉ video đầy đủ vào sổ "Đã tải xuống": đoạn cắt không phải bản của video này.
                if (!request.audioOnly && !request.isClip) {
                    val metadata = JSONObject().put("title", video.title).put("channel", video.channel)
                        .put("duration", video.duration).put("short", video.isShort)
                    context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
                        .edit().putString(video.id, metadata.toString()).apply()
                    NanStorage.init(context)
                    runCatching { recordDownload(account, video, name) }
                }
                revision.update { it + 1 }
                name
            } finally {
                work.deleteRecursively()
            }
        }

    /** Hỏi thử 2 byte đầu của luồng video và âm thanh: link bị từ chối thì bỏ plan này ngay, khỏi tải dở rồi mới biết. */
    private fun probePlan(plan: Plan) {
        plan.video?.let { probe(it.url) }
        plan.audio?.let { probe(it.url) }
    }

    private fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)

    private fun fileName(video: VideoItem, request: DownloadRequest): String {
        val clean = video.title.replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "_").trim().take(55).ifBlank { "Video" }
        val part = if (request.isClip) " (${DownloadText.clipLabel(request.clipStartMs, request.clipEndMs)})" else ""
        val ext = if (request.audioOnly) request.audioFormat.ext else "mp4"
        return "$clean$part-${video.id}.$ext"
    }

    /** Đọc câu trả lời /player thành kế hoạch tải; không dùng được thì trả null kèm lý do ghi vào [refusals]. */
    private fun readPlan(answer: PlaybackAnswer, request: DownloadRequest, refusals: PlaybackRefusals): Plan? {
        val adaptive = streams(answer.streamingData.optJSONArray("adaptiveFormats"))
        val audio = adaptive.filter { it.mime.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
        val details = answer.json.optJSONObject("videoDetails")
        val muxed = streams(answer.streamingData.optJSONArray("formats")).filter { it.mime.startsWith("video/mp4") }
        if (request.audioOnly) {
            if (audio != null) return Plan(null, audio, details)
            // Không có luồng âm thanh riêng: lấy track âm thanh từ luồng liền nhỏ nhất.
            val smallest = muxed.minByOrNull { it.size }
            if (smallest == null) refusals.add(answer.source, "không có link MP4 trực tiếp (chỉ có luồng mã hóa/SABR)")
            return smallest?.let { Plan(null, it, details) }
        }
        val video = pickHeight(adaptive.filter { it.mime.startsWith("video/mp4") && "avc1" in it.mime && it.height <= MAX_HEIGHT }, request.height)
        if (video != null && audio != null) return Plan(video, audio, details)
        val progressive = pickHeight(muxed, request.height)
        if (progressive == null) refusals.add(answer.source, "không có link MP4 trực tiếp (chỉ có luồng mã hóa/SABR)")
        return progressive?.let { Plan(it, null, details) }
    }

    /** Đúng mức [wanted]; không có thì mức cao nhất không vượt quá; [wanted] null = cao nhất. */
    private fun pickHeight(list: List<Stream>, wanted: Int?): Stream? {
        if (wanted == null) return list.maxByOrNull { it.height }
        return list.filter { it.height == wanted }.maxByOrNull { it.bitrate }
            ?: list.filter { it.height <= wanted }.maxByOrNull { it.height }
            ?: list.minByOrNull { it.height }
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
            Stream(
                url, size, format.optString("mimeType"), format.optInt("height"), format.optInt("bitrate"),
                rangeOf(format.optJSONObject("initRange")), rangeOf(format.optJSONObject("indexRange"))
            )
        }
    }

    private fun rangeOf(node: JSONObject?): String? {
        val start = node?.optString("start")?.takeIf { it.isNotBlank() } ?: return null
        val end = node.optString("end").takeIf { it.isNotBlank() } ?: return null
        return "$start-$end"
    }

    /**
     * Tải [stream] vào [target]. Có [plan] (cắt đoạn) thì ghi phần đầu rồi chỉ các mảnh cần; [onBytes] nhận số byte đã có trong tệp.
     */
    private fun fetch(stream: Stream, target: File, plan: ClipRanges.Plan?, onBytes: (Long) -> Unit) {
        target.outputStream().use { out ->
            var offset = plan?.from ?: 0L
            val end = (plan?.to ?: (stream.size - 1)) + 1
            var done = 0L
            if (plan != null) {
                out.write(plan.header)
                done = plan.header.size.toLong()
                onBytes(done)
            }
            while (offset < end) {
                val last = minOf(offset + CHUNK_BYTES, end) - 1
                val bytes = readRange(stream.url, offset, last)
                if (bytes.isEmpty()) throw IOException("Máy chủ video trả dữ liệu rỗng")
                out.write(bytes)
                offset += bytes.size
                done += bytes.size
                onBytes(done)
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

    private fun publish(context: Context, source: File, name: String, mime: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
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
