package com.nan.nantube.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject

/** Thông tin kênh của một Short lấy từ videoDetails của /player. */
data class ShortMeta(val author: String?, val channelId: String?, val views: String?, val title: String? = null)

/**
 * Kênh của Short kèm trạng thái đăng ký thật của tài khoản (đọc từ nút đăng ký trong `next`), để nút Đăng ký / Đã đăng ký
 * đúng ngay từ đầu và có đủ mức thông báo cho bảng chuông.
 */
class ShortInfo(
    val channelId: String?,
    val author: String?,
    val avatar: String?,
    val subscribed: Boolean?,
    val subscribeParams: String?,
    val notifyStates: List<NotifyState>,
    val notifyCurrent: Int?,
    /** Tiêu đề + số thích + số bình luận (nếu `next` có) để hiện như YouTube; feed reel chỉ có mã video. */
    val title: String? = null,
    val likes: String? = null,
    val comments: String? = null
) {
    fun toChannel(): SearchResult.Channel? {
        val id = channelId ?: return null
        return SearchResult.Channel(
            id = id, title = author ?: id, handle = null, info = null, avatar = avatar,
            subscribed = subscribed, subscribeParams = subscribeParams,
            notifyStates = notifyStates, notifyCurrent = notifyCurrent
        )
    }
}

/** Một luồng googlevideo trong /player (adaptive hoặc muxed). */
class MediaStream(
    val url: String,
    val mime: String,
    val codecs: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val size: Long,
    val initRange: String?,
    val indexRange: String?,
    val itag: Int,
    val sampleRate: String?
) {
    val isVideo get() = mime.startsWith("video/")
    val shortSide get() = minOf(width, height)

    /** Đủ thông tin để dựng DASH SegmentBase (init + sidx). */
    val dashable get() = initRange != null && indexRange != null && indexRange != "0-0"
}

/**
 * Kế hoạch phát của một video, đọc từ /player: các luồng DASH (video + âm thanh) để ExoPlayer phát trực tiếp theo đoạn,
 * không còn tải cả tệp về như bản cũ. [source] là client đã trả kế hoạch này (để bỏ nó khi link bị từ chối).
 */
class PlayPlan(
    val videoId: String,
    val source: String,
    val videos: List<MediaStream>,
    val audio: MediaStream?,
    val muxed: MediaStream?,
    val durationSec: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Phát Shorts theo cách của meetube (PhoneSourcePlanner + Media3SourceFactory): lấy link trực tiếp theo thứ tự
 * VISIONOS → ANDROID_VR → TV_TIZEN → WEB_EMBED → client của tài khoản, dựng bản kê DASH từ các luồng, cho ExoPlayer phát từng đoạn (có bộ nhớ đệm
 * trên đĩa), tải trước video kế tiếp. Link bị từ chối (403) thì đổi client (client vừa lỗi xếp cuối) và phát tiếp từ chỗ đang xem.
 *
 * Việc lấy link chạy ở scope riêng nên vuốt qua video khác không làm hủy lượt đang chạy; cùng một video được hỏi nhiều lần
 * (đang xem + tải trước) chỉ hỏi một lần.
 */
object ShortsPlayback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = HashMap<String, Deferred<PlayPlan>>()
    private val resolved = ConcurrentHashMap<String, PlayPlan>()
    private val metas = ConcurrentHashMap<String, ShortMeta>()
    private val infoJobs = HashMap<String, Deferred<ShortInfo>>()

    /** Link googlevideo sống 6 giờ; giữ kế hoạch tối đa 3 giờ cho chắc. */
    private const val PLAN_TTL_MS = 3L * 60 * 60 * 1000
    private const val MAX_SHORT_SIDE = 720
    private const val MAX_VIDEO_REPS = 4

    fun meta(videoId: String): ShortMeta? = metas[videoId]

    /** Kế hoạch phát của [video]; [exclude] là các client vừa bị từ chối (xếp cuối). */
    fun plan(context: Context, account: Account?, video: VideoItem, exclude: Set<String> = emptySet()): Deferred<PlayPlan> {
        val app = context.applicationContext
        synchronized(inflight) {
            val have = resolved[video.id]
            if (have != null && System.currentTimeMillis() - have.createdAt < PLAN_TTL_MS && have.source !in exclude) {
                return scope.async { have }
            }
            if (have != null) resolved.remove(video.id)
            inflight[video.id]?.let { return it }
            val job = scope.async {
                try {
                    resolve(app, account, video, exclude).also { resolved[video.id] = it }
                } finally {
                    synchronized(inflight) { inflight.remove(video.id) }
                }
            }
            inflight[video.id] = job
            return job
        }
    }

    /** Bỏ kế hoạch đã nhớ của video (link bị từ chối): lần hỏi sau lấy link mới. */
    fun forget(videoId: String) {
        synchronized(inflight) { resolved.remove(videoId) }
    }

    /** Tải trước: lấy link, kênh và vài giây đầu của luồng (vào bộ nhớ đệm đĩa) để vuốt tới là phát ngay. */
    fun warm(context: Context, account: Account?, video: VideoItem, bytes: Boolean) {
        val app = context.applicationContext
        scope.async {
            try {
                val plan = plan(app, account, video).await()
                if (bytes) ShortsMedia.prefetch(app, plan)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Tải trước hỏng thì thôi; lúc xem thật sẽ hỏi lại và báo lỗi.
            }
        }
        if (bytes) info(app, account, video)
    }

    // ---- Lấy link theo thứ tự của meetube ----------------------------------------------------------

    private suspend fun resolve(context: Context, account: Account?, video: VideoItem, exclude: Set<String>): PlayPlan {
        var reason: String? = null
        // Nguồn vừa lỗi xếp cuối; thứ tự các nguồn còn lại không đổi.
        for (source in PlaybackSource.available().sortedBy { it.name in exclude }) {
            val json = try {
                source.load(context, account, video.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reason = "${source.name}: ${e.message ?: "không kết nối được"}"
                continue
            }
            if (json.dig("videoDetails", "isLive") == true || json.dig("videoDetails", "isUpcoming") == true) {
                error("Video đang hoặc sắp phát trực tiếp")
            }
            val status = json.dig("playabilityStatus", "status") as? String
            if (status != "OK") {
                val detail = json.dig("playabilityStatus", "reason") as? String
                reason = "${source.name}: " + listOfNotNull(status, detail).joinToString(" - ").ifBlank { "không có dữ liệu phát" }
                continue
            }
            val data = json.optJSONObject("streamingData")
            if (data == null) {
                reason = "${source.name}: không có streamingData"
                continue
            }
            val details = json.optJSONObject("videoDetails")
            val plan = buildPlan(video.id, source.name, data, details)
            if (plan == null) {
                reason = "${source.name}: không có luồng phát trực tiếp"
                continue
            }
            if (details != null) {
                val views = details.optString("viewCount").toLongOrNull()
                metas[video.id] = ShortMeta(
                    author = details.optString("author").ifBlank { null },
                    channelId = details.optString("channelId").ifBlank { null },
                    views = views?.let { "%,d lượt xem".format(it) },
                    title = details.optString("title").ifBlank { null }
                )
            }
            return plan
        }
        throw IOException(reason?.let { "YouTube không cho phát video này ($it)" } ?: "YouTube không cho phát video này")
    }

    private fun buildPlan(videoId: String, source: String, data: JSONObject, details: JSONObject?): PlayPlan? {
        val adaptive = streams(data.optJSONArray("adaptiveFormats"))
        // Một họ codec cho cả bộ video (ExoPlayer chỉ đổi mức chất lượng trong cùng một AdaptationSet): H.264 trước vì máy nào cũng giải được.
        val videoFamily = listOf("avc1", "vp9", "av01").firstNotNullOfOrNull { codec ->
            adaptive.filter { it.isVideo && it.dashable && it.codecs.startsWith(codec) && it.shortSide <= MAX_SHORT_SIDE }
                .takeIf { it.isNotEmpty() }
        }.orEmpty()
        val videos = videoFamily
            .groupBy { it.shortSide }
            .map { (_, sameSize) -> sameSize.maxByOrNull { it.bitrate }!! }
            .sortedBy { it.shortSide }
            .takeLast(MAX_VIDEO_REPS)
        val audioPool = adaptive.filter { !it.isVideo && it.dashable }
        val audio = (audioPool.filter { it.mime == "audio/mp4" }.ifEmpty { audioPool }).maxByOrNull { it.bitrate }

        val muxed = streams(data.optJSONArray("formats"))
            .filter { it.mime == "video/mp4" }
            .maxByOrNull { it.height }

        val dash = videos.isNotEmpty() && audio != null
        if (!dash && muxed == null) return null
        val duration = details?.optString("lengthSeconds")?.toIntOrNull()
            ?: (adaptive.firstOrNull()?.let { approxSeconds(data, it.itag) })
            ?: 60
        return PlayPlan(videoId, source, if (dash) videos else emptyList(), if (dash) audio else null, muxed, duration)
    }

    private fun approxSeconds(data: JSONObject, itag: Int): Int? {
        val formats = data.optJSONArray("adaptiveFormats") ?: return null
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            if (f.optInt("itag") == itag) return f.optString("approxDurationMs").toLongOrNull()?.let { (it / 1000).toInt() + 1 }
        }
        return null
    }

    private fun streams(formats: JSONArray?): List<MediaStream> {
        if (formats == null) return emptyList()
        return (0 until formats.length()).mapNotNull { index ->
            val f = formats.optJSONObject(index) ?: return@mapNotNull null
            val url = f.optString("url").ifBlank { null } ?: return@mapNotNull null
            val uri = Uri.parse(url)
            val host = uri.host ?: return@mapNotNull null
            if (uri.scheme != "https" || !(host == "googlevideo.com" || host.endsWith(".googlevideo.com"))) return@mapNotNull null
            val type = f.optString("mimeType")
            val mime = type.substringBefore(';').trim()
            val codecs = type.substringAfter("codecs=\"", "").substringBefore('"')
            val size = f.optString("contentLength").toLongOrNull() ?: uri.getQueryParameter("clen")?.toLongOrNull() ?: 0L
            MediaStream(
                url = url, mime = mime, codecs = codecs,
                bitrate = f.optInt("bitrate"), width = f.optInt("width"), height = f.optInt("height"), fps = f.optInt("fps", 30),
                size = size,
                initRange = range(f.optJSONObject("initRange")), indexRange = range(f.optJSONObject("indexRange")),
                itag = f.optInt("itag"), sampleRate = f.optString("audioSampleRate").ifBlank { null }
            )
        }
    }

    private fun range(o: JSONObject?): String? {
        if (o == null) return null
        val start = o.optString("start").ifBlank { return null }
        val end = o.optString("end").ifBlank { return null }
        return "$start-$end"
    }

    // ---- Kênh + trạng thái đăng ký ----------------------------------------------------------------

    /**
     * Kênh của video kèm trạng thái đăng ký thật. Hỏi song song với /player (không chờ tải video như bản cũ nên nút Đăng ký
     * luôn có), có nhớ lại để lướt qua video đã xem không phải hỏi lại. Không đọc được `next` thì dùng kênh trong /player.
     */
    fun info(context: Context, account: Account?, video: VideoItem): Deferred<ShortInfo> {
        val app = context.applicationContext
        val key = "${account?.id}|${video.id}"
        synchronized(infoJobs) {
            infoJobs[key]?.let { if (!it.isCancelled) return it }
            val job = scope.async {
                val fromNext = try {
                    parseInfo(InnerTube.callAny("next", JSONObject().put("videoId", video.id), account))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val result = if (fromNext?.channelId != null) {
                    fromNext
                } else {
                    // `next` không cho kênh: lấy từ /player (nó vẫn đang chạy vì cần để phát).
                    val meta = metas[video.id] ?: try {
                        plan(app, account, video).await()
                        metas[video.id]
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    ShortInfo(
                        channelId = meta?.channelId ?: video.channelId, author = meta?.author ?: video.channel, avatar = video.avatar,
                        subscribed = null, subscribeParams = null, notifyStates = emptyList(), notifyCurrent = null
                    )
                }
                result
            }
            infoJobs[key] = job
            if (infoJobs.size > 80) infoJobs.keys.firstOrNull { it != key }?.let { infoJobs.remove(it) }
            return job
        }
    }

    private fun parseInfo(root: JSONObject): ShortInfo? {
        val owner = findKey(root, "videoOwnerRenderer")
        val ownerId = owner?.dig("navigationEndpoint", "browseEndpoint", "browseId") as? String
        val buttons = findAllKeys(root, "subscribeButtonRenderer")
        val button = buttons.firstOrNull { ownerId != null && it.optString("channelId") == ownerId }
            ?: buttons.firstOrNull { it.optString("channelId").isNotBlank() }
        val channelId = ownerId?.takeIf { it.startsWith("UC") } ?: button?.optString("channelId")?.ifBlank { null } ?: return null
        val subscribed = if (button != null && button.has("subscribed")) button.optBoolean("subscribed") else null
        return ShortInfo(
            channelId = channelId,
            author = owner?.let { ytText(it.opt("title")) },
            avatar = owner?.dig("thumbnail", "thumbnails", 0, "url") as? String,
            subscribed = subscribed,
            subscribeParams = findKey(button, "subscribeEndpoint")?.optString("params")?.ifBlank { null },
            notifyStates = VideoRepository.notifyStates(button),
            notifyCurrent = VideoRepository.notifyCurrent(button),
            title = ytText(findKey(root, "videoPrimaryInfoRenderer")?.opt("title"))?.ifBlank { null },
            likes = likeLabel(root),
            comments = ytText(findKey(root, "commentsEntryPointHeaderRenderer")?.opt("commentCount"))?.takeIf { c -> c.any(Char::isDigit) }
        )
    }

    private fun likeLabel(root: JSONObject): String? {
        val shown = findKey(root, "likeCountEntity")?.dig("likeCountIfIndifferent", "content") as? String
        if (shown != null && shown.any(Char::isDigit)) return shown
        val raw = Regex("\"likeCount\":\"?(\\d+)").find(root.toString())?.groupValues?.get(1)?.toLongOrNull()
        return raw?.let { countLabel(it) }
    }

    /** 1234 → "1,2 N", 3 400 000 → "3,4 Tr" (cách YouTube tiếng Việt rút gọn số). */
    internal fun countLabel(n: Long): String {
        fun fmt(v: Double, unit: String): String {
            val num = if (v < 10) "%.1f".format(java.util.Locale.forLanguageTag("vi"), v).removeSuffix(",0") else v.toLong().toString()
            return "$num $unit"
        }
        return when {
            n < 1_000 -> n.toString()
            n < 1_000_000 -> fmt(n / 1e3, "N")
            n < 1_000_000_000 -> fmt(n / 1e6, "Tr")
            else -> fmt(n / 1e9, "T")
        }
    }

    /** Thích / bỏ thích video bằng tài khoản đang chọn. */
    suspend fun like(account: Account, videoId: String, like: Boolean) {
        val body = JSONObject().put("target", JSONObject().put("videoId", videoId))
        InnerTube.callAny(if (like) "like/like" else "like/removelike", body, account)
    }
}
