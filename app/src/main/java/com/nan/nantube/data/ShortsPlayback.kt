package com.nan.nantube.data

import android.content.Context
import android.net.Uri
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
data class ShortMeta(
    val author: String?,
    val channelId: String?,
    val views: String?,
    val title: String? = null,
    /** Mô tả đầy đủ, số lượt xem thật và ngày đăng (yyyy-MM-dd) cho bảng mô tả khi bấm tiêu đề Short. */
    val description: String? = null,
    val viewCount: Long? = null,
    val publishDate: String? = null
)

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
    val comments: String? = null,
    /** Tài khoản đã thích video chưa, theo `next` (null = `next` không nói). */
    val liked: Boolean? = null
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

/**
 * Phần mô tả của Short đọc từ `next` (client WEB, không cần đăng nhập) theo cách MediaServiceCore (WatchNextHelper):
 * `videoDescriptionHeaderRenderer` (lượt thích / lượt xem / ngày đăng dạng "factoid", ngày đăng dạng chữ), thân mô tả,
 * rồi `microformat` (ISO) và `dateText` của videoPrimaryInfoRenderer làm phương án dự phòng.
 */
class ShortDescription(
    val description: String?,
    /** Các ô số liệu của YouTube (giá trị, nhãn), vd. ("6,5 N","Lượt thích"), ("209 N","Lượt xem"), ("2025","28 thg 8"). */
    val factoids: List<Pair<String, String>>,
    val viewsText: String?,
    val publishText: String?,
    val isoDate: String?
) {
    /** Số thích theo ô số liệu của YouTube (nhãn "Lượt thích"; không có nhãn thì ô đầu khi đủ 3 ô). */
    val likesText: String?
        get() = (factoids.firstOrNull { it.second.contains("thích", ignoreCase = true) || it.second.contains("like", ignoreCase = true) }
            ?: factoids.takeIf { it.size >= 3 }?.first())?.first
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
 * VISIONOS → TV_TIZEN (kèm tài khoản mã) → WEB_EMBED → ANDROID_VR (xem [PlaybackSource]), dựng bản kê DASH từ các luồng, cho
 * ExoPlayer phát từng đoạn (có bộ nhớ đệm trên đĩa), tải trước video kế tiếp. Link bị từ chối (403) thì đổi client
 * (client vừa lỗi xếp cuối) và phát tiếp từ chỗ đang xem.
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
    private val infoStale = ConcurrentHashMap.newKeySet<String>()
    private val infoAt = ConcurrentHashMap<String, Long>()
    private const val INFO_TTL_MS = 60_000L
    private val descStale = ConcurrentHashMap.newKeySet<String>()
    private val commentJobs = HashMap<String, Deferred<String?>>()
    private val descJobs = HashMap<String, Deferred<ShortDescription?>>()
    /** Mô tả đã tải xong (đọc ngay được, không phải đợi) để bảng mô tả mở ra là có đủ số liệu, không nhấp nháy. */
    private val descDone = ConcurrentHashMap<String, ShortDescription>()

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
        if (bytes) {
            info(app, account, video)
            description(video.id)
            commentCount(video.id)
        }
    }

    // ---- Lấy link theo thứ tự của meetube (xem PlaybackSource) -------------------------------------

    private suspend fun resolve(context: Context, account: Account?, video: VideoItem, exclude: Set<String>): PlayPlan {
        val refusals = PlaybackRefusals()
        return PlaybackSource.walk(context, account, video.id, exclude, refusals) { answer ->
            val details = answer.json.optJSONObject("videoDetails")
            val plan = buildPlan(video.id, answer.source.name, answer.streamingData, details)
            if (plan == null) refusals.add(answer.source, "không có luồng phát trực tiếp") else rememberMeta(video.id, answer.json, details)
            plan
        } ?: throw refusals.failure("phát")
    }

    private fun rememberMeta(videoId: String, json: JSONObject, details: JSONObject?) {
        if (details == null) return
        val views = details.optString("viewCount").toLongOrNull()
        metas[videoId] = ShortMeta(
            author = details.optString("author").ifBlank { null },
            channelId = details.optString("channelId").ifBlank { null },
            views = views?.let { "%,d lượt xem".format(it) },
            title = details.optString("title").ifBlank { null },
            description = details.optString("shortDescription").ifBlank { null },
            viewCount = views,
            publishDate = json.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
                ?.let { it.optString("publishDate").ifBlank { it.optString("uploadDate") } }?.ifBlank { null }
        )
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
            if (infoStale.remove(key)) infoJobs.remove(key)
            // Kết quả `next` cũ quá thì hỏi lại để trạng thái tim / đăng ký không đứng yên so với YouTube.
            if (System.currentTimeMillis() - (infoAt[key] ?: 0L) > INFO_TTL_MS) infoJobs.remove(key)
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
                // `next` hỏng (mạng yếu): đừng nhớ kết quả tạm này, lần hỏi sau phải được hỏi lại thật.
                if (fromNext?.channelId == null) infoStale.add(key)
                result
            }
            infoJobs[key] = job
            infoAt[key] = System.currentTimeMillis()
            if (infoJobs.size > 80) infoJobs.keys.firstOrNull { it != key }?.let { infoJobs.remove(it) }
            return job
        }
    }

    /**
     * Số bình luận của video (đã bỏ chữ "bình luận"), hỏi ở scope riêng và nhớ lại để tải sẵn cho Short kế tiếp:
     * vuốt tới là có số ngay. Không có (tắt bình luận / lỗi mạng) thì trả null; lỗi không bị nhớ nên lần sau hỏi lại.
     */
    fun commentCount(videoId: String): Deferred<String?> {
        synchronized(commentJobs) {
            commentJobs[videoId]?.let { if (!it.isCancelled) return it }
            val job = scope.async {
                val clean = try {
                    CommentsRepository.first(videoId).total
                        ?.replace(Regex("\\s*(bình luận|comments?)", RegexOption.IGNORE_CASE), "")?.trim()
                        ?.takeIf { c -> c.any(Char::isDigit) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (clean == null) synchronized(commentJobs) { commentJobs.remove(videoId) }
                clean
            }
            commentJobs[videoId] = job
            if (commentJobs.size > 80) commentJobs.keys.firstOrNull { it != videoId }?.let { commentJobs.remove(it) }
            return job
        }
    }

    /** Quên số liệu đã nhớ của video (thông tin kênh / số thích / mô tả) để lần hỏi sau lấy số mới, vd. sau khi thích / bỏ thích. */
    fun invalidate(videoId: String) {
        synchronized(infoJobs) { infoJobs.keys.removeAll { it.endsWith("|$videoId") } }
        synchronized(descJobs) { descJobs.remove(videoId) }
        descDone.remove(videoId)
    }

    /** Mô tả của video nếu đã tải xong từ trước, ngược lại null (không đợi). */
    fun cachedDescription(videoId: String): ShortDescription? = descDone[videoId]

    /** Mô tả + ngày đăng + số liệu của video (hỏi một lần, nhớ lại; lỗi thì không nhớ để lần sau hỏi lại). */
    fun description(videoId: String): Deferred<ShortDescription?> {
        synchronized(descJobs) {
            if (descStale.remove(videoId)) descJobs.remove(videoId)
            descJobs[videoId]?.let { if (!it.isCancelled) return it }
            val job = scope.async {
                val parsed = try {
                    parseDescription(InnerTube.call("next", JSONObject().put("videoId", videoId), null))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                // Hỏng hoặc chưa có ô số liệu nào: đừng nhớ, lần hỏi sau phải hỏi lại thật.
                if (parsed == null || parsed.factoids.isEmpty()) descStale.add(videoId) else descDone[videoId] = parsed
                parsed
            }
            descJobs[videoId] = job
            if (descJobs.size > 60) descJobs.keys.firstOrNull { it != videoId }?.let { descJobs.remove(it); descDone.remove(it) }
            return job
        }
    }

    private fun parseDescription(root: JSONObject): ShortDescription {
        val header = findKey(root, "videoDescriptionHeaderRenderer")
        val facts = header?.let { findAllKeys(it, "factoidRenderer") }.orEmpty().mapNotNull { f ->
            val value = ytText(f.opt("value"))?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            value to (ytText(f.opt("label")) ?: "")
        }
        val body = ytText(findKey(root, "expandableVideoDescriptionBodyRenderer")?.opt("descriptionBodyText"))?.ifBlank { null }
            ?: findKey(root, "attributedDescription")?.optString("content")?.ifBlank { null }
        val publish = header?.let { ytText(it.opt("publishDate")) }?.ifBlank { null }
            ?: ytText(findKey(root, "videoPrimaryInfoRenderer")?.opt("dateText"))?.ifBlank { null }
        val iso = findKey(root, "microformatDataRenderer")?.let { m ->
            m.optString("uploadDate").ifBlank { m.optString("publishDate") }
        }?.ifBlank { null }
        return ShortDescription(
            description = body,
            factoids = facts,
            viewsText = header?.let { ytText(it.opt("views")) }?.ifBlank { null },
            publishText = publish,
            isoDate = iso
        )
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
            liked = likeStatus(root),
            comments = ytText(findKey(root, "commentsEntryPointHeaderRenderer")?.opt("commentCount"))?.takeIf { c -> c.any(Char::isDigit) }
        )
    }

    /**
     * Trạng thái thích của tài khoản trong `next`, đủ các chỗ MediaServiceCore (MediaItemMetadataImpl.likeStatusItem) đọc:
     * videoMetadataRenderer / musicWatchMetadataRenderer.likeStatus, likeButton.likeButtonRenderer.likeStatus, các nút của
     * transportControlsRenderer (likeButton.toggleButtonRenderer.isToggled hoặc nút LIKE_BUTTON trong `buttons`), rồi các dạng
     * của client WEB mới (likeStatusEntity, view model đang bật). null = `next` không nói.
     */
    private fun likeStatus(root: JSONObject): Boolean? {
        fun status(s: String?): Boolean? = s?.takeIf { it.isNotBlank() }?.let { it == "LIKE" }
        for (name in listOf("videoMetadataRenderer", "musicWatchMetadataRenderer")) {
            val meta = findKey(root, name) ?: continue
            status(meta.optString("likeStatus"))?.let { return it }
            status(meta.dig("likeButton", "likeButtonRenderer", "likeStatus") as? String)?.let { return it }
        }
        val controls = findKey(root, "transportControlsRenderer")
        if (controls != null) {
            val direct = controls.dig("likeButton", "toggleButtonRenderer")
            if (direct is JSONObject && direct.has("isToggled")) return direct.optBoolean("isToggled")
            val buttons = controls.optJSONArray("buttons")
            for (i in 0 until (buttons?.length() ?: 0)) {
                val b = buttons!!.optJSONObject(i) ?: continue
                if (b.optString("type") != "TRANSPORT_CONTROLS_BUTTON_TYPE_LIKE_BUTTON") continue
                status(b.dig("button", "likeButtonRenderer", "likeStatus") as? String)?.let { return it }
                val t = b.dig("button", "toggleButtonRenderer")
                if (t is JSONObject && t.has("isToggled")) return t.optBoolean("isToggled")
            }
        }
        status(findKey(root, "likeButtonRenderer")?.optString("likeStatus"))?.let { return it }
        status(findKey(root, "likeStatusEntity")?.optString("likeStatus"))?.let { return it }
        val vm = findKey(root, "likeButtonViewModel")
        val toggle = vm?.let { findKey(it, "toggleButtonViewModel") }
        if (toggle != null && toggle.has("isToggled")) return toggle.optBoolean("isToggled")
        if (vm != null && vm.has("likeStatus")) return status(vm.optString("likeStatus"))
        return null
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

    /**
     * Trạng thái thích THẬT của tài khoản với video, hỏi YouTube mỗi lần (không nhớ kết quả cũ). null = không đọc được
     * (mạng lỗi hoặc `next` không nói), khi đó giữ nguyên trạng thái đang hiện.
     */
    suspend fun likeStatus(account: Account, videoId: String): Boolean? =
        try {
            likeStatus(InnerTube.callAny("next", JSONObject().put("videoId", videoId), account))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /** Thích / bỏ thích video bằng tài khoản đang chọn. */
    suspend fun like(account: Account, videoId: String, like: Boolean) {
        val body = JSONObject().put("target", JSONObject().put("videoId", videoId))
        InnerTube.callAny(if (like) "like/like" else "like/removelike", body, account)
    }
}
