package com.nan.nantube.data

import android.os.SystemClock
import android.text.Html
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/** Một bản phụ đề mà /player liệt kê (captionTracks). [auto] = YouTube tự tạo (kind "asr"). */
class CaptionTrack(
    val languageCode: String,
    val name: String,
    val baseUrl: String,
    val auto: Boolean,
    /** Nguồn /player (VISIONOS, ANDROID_VR...) đã trả link này; chỉ để báo lỗi cho rõ. */
    val source: String? = null
) {
    /** Khóa để nhớ lựa chọn: cùng ngôn ngữ nhưng bản tự tạo và bản thủ công là hai mục khác nhau. */
    val key: String get() = if (auto) "$languageCode|asr" else languageCode

    /** Chữ hiện trong bảng chọn phụ đề. */
    val label: String get() = if (auto && !name.contains("tự", ignoreCase = true) && !name.contains("auto", ignoreCase = true)) "$name (tự động tạo)" else name
}

/** Một dòng phụ đề hiện từ [startMs] đến [endMs]. */
class CaptionCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val marks: List<CaptionMark> = emptyList()
)

/** Chỉ có khi nguồn json3 cấp thời điểm cho từng đoạn chữ. */
class CaptionMark(val offsetMs: Long, val charIndex: Int)

/**
 * Tải phụ đề của Short từ baseUrl trong /player rồi tự vẽ lên màn (ExoPlayer đang phát DASH tự dựng nên không có sẵn kênh phụ đề).
 * Thử `fmt=json3` trước, không được thì đọc bản XML mặc định của timedtext.
 */
object ShortsCaptions {
    private val cache = ConcurrentHashMap<String, List<CaptionCue>>()
    private val FMT = Regex("&fmt=[^&]*")
    private val XML_TEXT = Regex("<text start=\"([\\d.]+)\"(?: dur=\"([\\d.]+)\")?[^>]*>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)

    /** Chọn bản phụ đề khớp [key] (ưu tiên đúng khóa, rồi cùng ngôn ngữ). Null khi video không có ngôn ngữ đó. */
    fun pick(tracks: List<CaptionTrack>, key: String?): CaptionTrack? {
        if (key == null) return null
        val lang = key.substringBefore('|')
        return tracks.firstOrNull { it.key == key }
            ?: tracks.firstOrNull { it.languageCode == lang && !it.auto }
            ?: tracks.firstOrNull { it.languageCode == lang }
    }

    /** Bản mặc định khi chưa chọn gì: ưu tiên bản người đăng tải lên, không có thì lấy bản tự tạo. */
    fun defaultTrack(tracks: List<CaptionTrack>): CaptionTrack? = tracks.firstOrNull { !it.auto } ?: tracks.firstOrNull()

    /** Phụ đề của [track]; kết quả nhớ lại theo link để không tải lần hai. */
    suspend fun load(track: CaptionTrack): List<CaptionCue> = fetch(track)

    // ---- Đường truyền: OkHttp như meetube (phụ đề của meetube đi qua OkHttp/Cronet của ExoPlayer, không phải HttpURLConnection) ----

    /** UA của meetube cho mọi yêu cầu media/phụ đề (Media3SourceFactory.USER_AGENT); không cookie, không header riêng của InnerTube. */
    private const val CAPTION_UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    /** Địa chỉ IPv4 xếp trước IPv6 (không bỏ IPv6): IPv6 của nhà mạng hay bị YouTube giới hạn gắt và hay treo bắt tay TLS. */
    private val ipv4First = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4First)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private class Reply(val code: Int, val body: String, val retryAfter: String?, val protocol: String)

    /** GET bằng OkHttp (HTTP/2 qua ALPN); hủy coroutine thì hủy luôn cuộc gọi đang chạy. */
    private suspend fun httpGet(url: String, timeoutMs: Int): Reply = suspendCancellableCoroutine { cont ->
        val call = try {
            client.newBuilder().readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS).build()
                .newCall(Request.Builder().url(url).header("User-Agent", CAPTION_UA).build())
        } catch (e: IllegalArgumentException) {
            cont.resumeWithException(IOException("Link phụ đề không hợp lệ"))
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { Reply(it.code, it.body?.string().orEmpty(), it.header("Retry-After"), it.protocol.toString()) }
                    if (cont.isActive) cont.resume(result)
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    /** Chỉ một yêu cầu timedtext chạy một lúc (như meetube chỉ tải bản đang chọn): nhiều nơi cùng hỏi một link thì chờ nhau rồi lấy từ bộ nhớ đệm. */
    private val gate = Mutex()

    /** Thời điểm (uptime ms) YouTube cho hỏi lại sau khi trả 429; trong lúc đó không gửi thêm yêu cầu nào để khỏi bị giới hạn lâu hơn. */
    @Volatile
    private var blockedUntil = 0L
    private const val COOLDOWN_MS = 20_000L
    private const val RETRY_DELAY_MS = 2_000L

    /**
     * Tải một bản phụ đề. Thử `json3`, rồi `vtt` (định dạng meetube dùng cho phụ đề dịch), rồi XML mặc định, nhưng chỉ chuyển sang định dạng
     * khác khi YouTube trả 200 mà rỗng/đọc không ra. Gặp 429 (bị giới hạn) thì dừng ngay, không thử định dạng khác, chờ một nhịp hỏi lại một lần,
     * vẫn 429 thì nghỉ [COOLDOWN_MS].
     */
    private suspend fun fetch(track: CaptionTrack): List<CaptionCue> {
        // Link tương đối (client mweb) thì thêm tên miền, như CaptionTrack.getBaseUrlQuery của meetube.
        val url = if (track.baseUrl.startsWith("/")) "https://www.youtube.com${track.baseUrl}" else track.baseUrl
        cache[url]?.let { return it }
        return gate.withLock {
            cache[url]?.let { return@withLock it }
            val base = url.replace(FMT, "")
            val timeout = 10_000
            // Thông tin kèm lỗi để biết hỏng ở đâu: nguồn cấp link, giao thức, Retry-After.
            val tag = track.source?.let { "nguồn $it" } ?: "nguồn không rõ"
            var why = "không có dữ liệu"
            for (fmt in listOf("json3", "vtt", null)) {
                var res: Reply? = null
                for (attempt in 0..1) {
                    val wait = blockedUntil - SystemClock.elapsedRealtime()
                    if (wait > 0) throw IOException("Không tải được phụ đề (HTTP 429 · $tag · đang nghỉ, thử lại sau ${wait / 1000 + 1}s)")
                    res = try {
                        httpGet(if (fmt == null) base else "$base&fmt=$fmt", timeout)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        why = e.javaClass.simpleName
                        null
                    }
                    if (res?.code != 429) break
                    if (attempt == 0) {
                        delay(RETRY_DELAY_MS)
                    } else {
                        blockedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
                        val extra = listOfNotNull(res.protocol, res.retryAfter?.let { "Retry-After $it" }).joinToString(" · ")
                        throw IOException("Không tải được phụ đề (HTTP 429 · $tag · $extra)")
                    }
                }
                if (res == null) continue
                if (res.code !in 200..299) {
                    why = "HTTP ${res.code}"
                    continue
                }
                if (res.body.isBlank()) {
                    why = "YouTube trả rỗng"
                    continue
                }
                val cues = when (fmt) {
                    "json3" -> parseJson3(res.body)
                    "vtt" -> parseVtt(res.body)
                    else -> parseXml(res.body)
                }
                if (cues.isNotEmpty()) {
                    cache[url] = cues
                    return@withLock cues
                }
                why = "không đọc được nội dung"
            }
            throw IOException("Không tải được phụ đề ($why · $tag)")
        }
    }

    /** Dòng đang hiện ở thời điểm [positionMs] (nhiều dòng chồng nhau thì lấy dòng bắt đầu muộn nhất). */
    fun textAt(cues: List<CaptionCue>, positionMs: Long): String? {
        var found: CaptionCue? = null
        for (cue in cues) {
            if (cue.startMs > positionMs) break
            if (positionMs < cue.endMs) found = cue
        }
        return found?.text
    }

    private fun parseJson3(body: String): List<CaptionCue> {
        val events = try {
            JSONObject(body).optJSONArray("events")
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<CaptionCue>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val segs = event.optJSONArray("segs") ?: continue
            val marks = ArrayList<CaptionMark>()
            val raw = buildString {
                for (j in 0 until segs.length()) {
                    val seg = segs.optJSONObject(j) ?: continue
                    val offset = seg.optLong("tOffsetMs", -1L)
                    val words = seg.optString("utf8")
                    if (offset > 0 && words.isNotBlank()) marks.add(CaptionMark(offset, length))
                    append(words)
                }
            }.replace('\n', ' ')
            val text = raw.trim()
            if (text.isEmpty()) continue
            val trimmed = raw.indexOfFirst { !it.isWhitespace() }
            val start = event.optLong("tStartMs")
            val duration = event.optLong("dDurationMs")
            out.add(CaptionCue(start, start + duration.coerceAtLeast(1L), text,
                marks.map { CaptionMark(it.offsetMs, (it.charIndex - trimmed).coerceIn(0, text.length)) }))
        }
        return out.sortedBy { it.startMs }
    }

    private val VTT_TIME = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})[.,](\\d{3})")
    private val VTT_TAG = Regex("<[^>]*>")

    private fun parseVtt(body: String): List<CaptionCue> {
        val out = ArrayList<CaptionCue>()
        for (block in body.replace("\r\n", "\n").split("\n\n")) {
            val lines = block.lines()
            val at = lines.indexOfFirst { it.contains("-->") }
            if (at < 0) continue
            val times = VTT_TIME.findAll(lines[at]).toList()
            if (times.size < 2) continue
            fun ms(m: MatchResult) = ((m.groupValues[1].toLongOrNull() ?: 0L) * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 1000 +
                m.groupValues[4].toLong()
            var text = lines.drop(at + 1).joinToString(" ") { VTT_TAG.replace(it, "") }
            repeat(2) { text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString() }
            text = text.replace('\n', ' ').trim()
            if (text.isNotEmpty()) out.add(CaptionCue(ms(times[0]), ms(times[1]).coerceAtLeast(ms(times[0]) + 1L), text))
        }
        return out.sortedBy { it.startMs }
    }

    private fun parseXml(body: String): List<CaptionCue> {
        val out = ArrayList<CaptionCue>()
        for (m in XML_TEXT.findAll(body)) {
            val start = ((m.groupValues[1].toDoubleOrNull() ?: continue) * 1000).toLong()
            val duration = ((m.groupValues[2].toDoubleOrNull() ?: 2.0) * 1000).toLong()
            // timedtext hay mã hóa HTML hai lần ("&amp;#39;"), giải hai lần cho ra chữ thật.
            var text = m.groupValues[3]
            repeat(2) { text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString() }
            text = text.replace('\n', ' ').trim()
            if (text.isNotEmpty()) out.add(CaptionCue(start, start + duration.coerceAtLeast(1L), text))
        }
        return out.sortedBy { it.startMs }
    }
}
