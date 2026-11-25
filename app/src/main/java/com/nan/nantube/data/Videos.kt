package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Icon nhạc (♪) mà YouTube gắn trong lớp phủ ảnh bìa của một số video: imageName/iconType bắt đầu bằng MUSIC. */
private val MUSIC_ICON = Regex("\"(imageName|iconType)\":\"MUSIC")

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String?,
    val duration: String?,
    val views: String?,
    val isShort: Boolean,
    /** Tiêu đề nhóm (vd "Hôm nay", "29 thg 10") mà YouTube xếp video này vào, dùng cho trang Video đã xem. */
    val section: String? = null,
    /** Đã xem bao nhiêu phần (0..1) để vẽ thanh đỏ dưới ảnh bìa. */
    val progress: Float = 0f,
    val historyFeedbackToken: String? = null,
    val historyPanel: HistoryChip? = null,
    /** Ảnh đại diện kênh nếu YouTube trả kèm (thẻ video kiểu trang chủ YouTube). */
    val avatar: String? = null,
    /** Chắc chắn là nhạc (YouTube gắn icon ♪, hoặc lấy từ chip Âm nhạc), ngoài phần đoán theo tiêu đề. */
    val music: Boolean = false,
    /** Mã kênh (UC…) nếu thẻ có nêu, để tra avatar kênh khi feed không kèm ảnh (xem [ChannelAvatars]). */
    val channelId: String? = null,
    /** Menu ngữ cảnh của thẻ TV (get_panel): chỗ cuối cùng để biết kênh khi thẻ không nêu mã kênh. */
    val panelId: String? = null,
    val panelParams: String? = null,
    /** Thời điểm đăng dạng YouTube ghi ("3 giờ trước"), có khi thẻ nêu; dùng cho màn Thông báo. */
    val published: String? = null,
    /** Dòng của video sắp công chiếu / phát trực tiếp ("Công chiếu vào 24 thg 11, 20:00"), theo upcomingEventData. */
    val upcoming: String? = null,
    /** Đang phát trực tiếp (thẻ ghi "đang xem" hoặc có nhãn LIVE). */
    val live: Boolean = false,
    /** `params` / `playerParams` của reelWatchEndpoint do feed Short trả, để hỏi lại menu của đúng reel này (Không quan tâm...). */
    val reelParams: String? = null,
    val reelPlayerParams: String? = null,
    /** Ảnh bìa vuông (đĩa nhạc / bài hát) khi YouTube trả ảnh 1:1; có thì thẻ vẽ vuông như YouTube. */
    val artUrl: String? = null,
    /** Thẻ này là một danh sách phát / tuyển tập / đĩa nhạc (trang chủ trộn lẫn với video): thẻ vẽ kiểu danh sách thay vì kiểu video. */
    val playlist: SearchResult.Playlist? = null
) {
    /** Ảnh bìa lấy thẳng từ CDN theo id: luôn dùng được, khỏi phụ thuộc cấu trúc JSON từng loại. */
    val thumbnail: String
        get() = if (isShort) "https://i.ytimg.com/vi/$id/oar2.jpg" else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    /** Ảnh bìa 16:9 cho ô thư viện: Short cũng dùng ảnh ngang (có viền đen hai bên như YouTube). */
    val coverThumbnail: String
        get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    val watchUrl: String
        get() = if (isShort) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id"

    /** Đoán là nhạc: kênh "- Topic" của YouTube Music hoặc tiêu đề kiểu MV / lyrics / remix. */
    val isMusic: Boolean
        get() = music || channel?.endsWith("- Topic") == true ||
            channel?.contains("vevo", ignoreCase = true) == true || MUSIC_TITLE.containsMatchIn(title)

    /** Ảnh bìa 16:9 độ phân giải cao cho thẻ lớn ở trang chủ (có thể 404 với video cũ: khi đó rơi về [thumbnail]). */
    val hqThumbnail: String
        get() = "https://i.ytimg.com/vi/$id/hq720.jpg"

    private companion object {
        val MUSIC_TITLE = Regex(
            "official (music )?(video|audio|mv|lyric)|lyrics?|\\bm/?v\\b|\\(audio\\)|\\bfeat\\.|\\bft\\.|remix|karaoke|nhạc|full album|\\bost\\b|bài hát|ca khúc|lofi|lo-fi|acoustic|\\bcover\\b|\\bmp3\\b|\\bsingle\\b|\\bsong\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

private val PUBLISHED_RE = Regex(
    "\\d+\\s*(giây|phút|giờ|ngày|tuần|tháng|năm|seconds?|minutes?|hours?|days?|weeks?|months?|years?)\\s*(trước|ago)",
    RegexOption.IGNORE_CASE
)
private val WATCHING_RE = Regex("đang xem|watching", RegexOption.IGNORE_CASE)

/** "3 giờ trước" nằm trong một đoạn chữ như "1,2 N lượt xem · 3 giờ trước"; null nếu không có. */
internal fun publishedIn(text: String?): String? = text?.let { PUBLISHED_RE.find(it)?.value }

internal fun isWatchingText(text: String?): Boolean = text != null && WATCHING_RE.containsMatchIn(text)

data class HistoryChip(val label: String, val token: String)

/** Một trang kết quả: video + token để lấy trang kế (null = hết) + các chip lọc server trả về. */
data class VideoPage(
    val items: List<VideoItem>,
    val continuation: String?,
    val chips: List<HistoryChip> = emptyList(),
    /** Số video của playlist, vd "507 video" (chỉ có ở trang đầu của playlist, nếu đọc được). */
    val total: String? = null
)

/** Một trang bất kỳ + token trang kế (null = hết). */
data class Paged<T>(val items: List<T>, val token: String?)

/** Một mức thông báo kênh: [params] là `modifyChannelNotificationPreferenceEndpoint.params` dùng cho notification/modify_channel_preference. */
data class NotifyState(val stateId: Int, val title: String, val params: String)

/** Một dòng kết quả tìm kiếm: video, kênh hoặc danh sách phát (theo đúng thứ tự YouTube trả về). */
sealed class SearchResult {
    abstract val key: String

    data class Video(val video: VideoItem) : SearchResult() {
        override val key get() = "v:" + video.id
    }

    data class Channel(
        val id: String,
        val title: String,
        val handle: String?,
        val info: String?,
        val avatar: String?,
        /** Đã đăng ký hay chưa theo YouTube; null = kết quả không kèm nút đăng ký. */
        val subscribed: Boolean? = null,
        /** `params` của lệnh đăng ký (YouTube dùng cho tùy chọn thông báo trên di động); có thể trống. */
        val subscribeParams: String? = null,
        /** Các mức thông báo (chuông) YouTube trả kèm nút đăng ký: Tất cả / Dành riêng cho bạn / Không nhận. Rỗng nếu không có. */
        val notifyStates: List<NotifyState> = emptyList(),
        /** `stateId` của mức đang chọn theo YouTube (null = không rõ). */
        val notifyCurrent: Int? = null
    ) : SearchResult() {
        override val key get() = "c:$id"
        val url: String get() = "https://www.youtube.com/channel/$id"
    }

    data class Playlist(
        val id: String,
        val title: String,
        val channel: String?,
        val count: String?,
        val thumbnail: String?,
        /** "Đĩa nhạc", "Danh sách phát"... nếu YouTube ghi. */
        val kind: String? = null
    ) : SearchResult() {
        override val key get() = "p:$id"
        val url: String get() = "https://www.youtube.com/playlist?list=$id"
    }
}

/**
 * Bộ lọc kết quả tìm kiếm: [params] là tham số `params` của InnerTube (mã theo SearchFilterHelper của MediaServiceCore),
 * [keep] lọc thêm phía máy cho mục YouTube không có tham số riêng (Chưa xem).
 */
class SearchFilter(
    val label: String,
    val params: String?,
    /** Hiện kết quả thành lưới Short 2 cột thay vì thẻ video lớn. */
    val grid: Boolean = false,
    val keep: (SearchResult) -> Boolean = { true }
)

val SEARCH_FILTERS = listOf(
    SearchFilter("Tất cả", null),
    SearchFilter("Shorts", "EgIQCQ%3D%3D", grid = true),
    SearchFilter("Chưa xem", null) { it !is SearchResult.Video || it.video.progress <= 0f },
    SearchFilter("Video", "EgIQAQ%3D%3D"),
    SearchFilter("Tải lên gần đây", "EgIIAw%3D%3D"), // tuần này
    SearchFilter("Trực tiếp", "EgJAAQ%3D%3D"),
    SearchFilter("Kênh", "EgIQAg%3D%3D"),
    SearchFilter("Danh sách phát", "EgIQAw%3D%3D")
)

/** Chip ở trang chủ: "Tất cả" là trang chủ YouTube, các chip còn lại là tìm kiếm theo chủ đề. */
class HomeChip(val label: String, val query: String?, val params: String? = null, val music: Boolean = false)

val HOME_CHIPS = listOf(
    HomeChip("Tất cả", null),
    HomeChip("Âm nhạc", "nhạc mới nhất", music = true),
    HomeChip("Trò chơi", "game hay nhất"),
    HomeChip("Tin tức", "tin tức mới nhất hôm nay"),
    HomeChip("Thể thao", "thể thao bóng đá highlights"),
    HomeChip("Live", "trực tiếp", params = "EgJAAQ%3D%3D")
)

/**
 * Nguồn video tạm thời: chỉ lấy danh sách (tên, kênh, thời lượng) để hiển thị, chưa phát trong app.
 * Một bộ đọc đệ quy xử lý các loại "renderer" của cả client WEB lẫn TV nên đổi kiểu đăng nhập vẫn đọc được.
 */
object VideoRepository {
    private const val SEARCH_ANY = "EgIQAQ%3D%3D" // chỉ video
    private const val SEARCH_SHORT = "EgIYAQ%3D%3D" // dưới 4 phút
    private const val SEARCH_SHORTS_ONLY = "EgIQCQ%3D%3D" // chỉ Shorts

    suspend fun home(account: Account?): List<VideoItem> {
        val json = InnerTube.call("browse", JSONObject().put("browseId", "FEwhat_to_watch"), account)
        val list = parse(json)
        // Chưa đăng nhập, trang chủ thường trống: lấy tạm kết quả tìm kiếm thịnh hành.
        return if (list.isNotEmpty()) list else search("thịnh hành", account)
    }

    suspend fun shorts(account: Account?): List<VideoItem> {
        val query = JSONObject().put("query", "#shorts")
        val filtered = try {
            parse(InnerTube.call("search", JSONObject(query.toString()).put("params", SEARCH_SHORT), account))
        } catch (e: AuthRejected) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val list = if (filtered.isNotEmpty()) filtered else parse(InnerTube.call("search", query, account))
        val onlyShort = list.filter { it.isShort }
        return if (onlyShort.isNotEmpty()) onlyShort else list
    }

    /** Feed Shorts có trang kế (chip lọc "Shorts" của tìm kiếm); lỗi hoặc trống thì rơi về [shorts]. */
    suspend fun shortsPage(account: Account?): Paged<VideoItem> {
        val reel = try {
            ShortsFeed.first(account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return reel ?: shortsSearchPage(account)
    }

    private suspend fun shortsSearchPage(account: Account?): Paged<VideoItem> {
        val page = try {
            searchPage("#shorts", SEARCH_SHORTS_ONLY, account).let { Paged(it.items.shortVideos(), it.token) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (_: Exception) {
            null
        }
        return if (page != null && page.items.isNotEmpty()) page else Paged(shorts(account), null)
    }

    suspend fun shortsMore(account: Account?, token: String): Paged<VideoItem> {
        if (ShortsFeed.owns(token)) return ShortsFeed.more(account, token)
        val page = searchMore(token, account)
        return Paged(page.items.shortVideos(), page.token)
    }

    private fun List<SearchResult>.shortVideos(): List<VideoItem> =
        filterIsInstance<SearchResult.Video>().map { it.video }.filter { it.isShort }

    /** Video đã xem của tài khoản (cần đăng nhập). */
    suspend fun history(account: Account?): List<VideoItem> {
        if (account == null) return emptyList()
        return parse(InnerTube.call("browse", JSONObject().put("browseId", "FEhistory"), account))
    }

    /**
     * Trang đầu của lịch sử xem; [query] để tìm trong lịch sử (như ô "Tìm kiếm trong danh sách video đã xem").
     * Trang kế lấy bằng [continuePage] với token trả về.
     */
    suspend fun historyPage(account: Account?, query: String? = null): VideoPage {
        if (account == null) return VideoPage(emptyList(), null)
        ShortDetector.account = account
        val body = JSONObject().put("browseId", "FEhistory")
        if (!query.isNullOrBlank()) body.put("query", query)
        return parsePage(InnerTube.call("browse", body, account), withChips = query.isNullOrBlank(), history = true).resolved()
    }

    /** Trang kế của mọi danh sách phân trang (lịch sử, playlist, chip lọc) theo token continuation. */
    suspend fun continuePage(account: Account?, token: String, history: Boolean = false): VideoPage {
        if (account != null) ShortDetector.account = account
        val page = parsePage(InnerTube.call("browse", JSONObject().put("continuation", token), account), withChips = false, history = history)
        return (if (token in uploadsTokens) tagUploads(page, uploadsTags) else page).resolved()
    }

    /** Trang đầu của một playlist (WL, LL, UU…); trang kế lấy bằng [continuePage]. */
    suspend fun playlistPage(account: Account?, playlistId: String, guestOk: Boolean = false): VideoPage {
        if (account == null && !guestOk) return VideoPage(emptyList(), null)
        if (account != null) ShortDetector.account = account
        val page = try {
            val json = InnerTube.call("browse", JSONObject().put("browseId", "VL$playlistId"), account)
            parsePage(json, withChips = false, withCount = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (e: Exception) {
            // Mix / tuyển tập tự tạo (RD…) không có trang danh sách: đọc qua hàng đợi phát bên dưới.
            if (guestOk) null else throw e
        }
        if (page != null && page.items.isNotEmpty()) return page.resolved()
        if (guestOk) mixPage(playlistId)?.let { return it.resolved() }
        return (page ?: VideoPage(emptyList(), null)).resolved()
    }

    /**
     * Danh sách kết hợp / tuyển tập / mix (mã RD…): YouTube chỉ cho đọc qua `next` (hàng đợi phát, playlistPanelRenderer).
     * Mã dạng RDAMVM<id> / RD<id> mang sẵn video gốc thì gửi kèm videoId. Không có trang kế (hàng đợi khoảng vài chục bài).
     */
    private suspend fun mixPage(playlistId: String): VideoPage? {
        val body = JSONObject().put("playlistId", playlistId)
        val seed = when {
            playlistId.startsWith("RDAMVM") -> playlistId.removePrefix("RDAMVM")
            playlistId.startsWith("RDEM") || playlistId.startsWith("RDCLAK") || playlistId.startsWith("RDGM") -> null
            playlistId.startsWith("RD") && playlistId.length == 13 -> playlistId.substring(2)
            else -> null
        }?.takeIf { it.length == 11 }
        seed?.let { body.put("videoId", it) }
        val json = try {
            InnerTube.call("next", body, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        val out = LinkedHashMap<String, VideoItem>()
        fun take(r: JSONObject) {
            val id = r.optString("videoId")
            val title = ytText(r.opt("title")) ?: return
            if (id.length != 11 || out.containsKey(id)) return
            val channel = ytText(r.opt("shortBylineText")) ?: ytText(r.opt("longBylineText"))
            out[id] = VideoItem(id, title, channel, ytText(r.opt("lengthText")), null, false)
        }
        fun walkPanel(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val wrapper = n.optJSONObject("playlistPanelVideoWrapperRenderer")
                    val direct = n.optJSONObject("playlistPanelVideoRenderer")
                    when {
                        wrapper != null -> wrapper.optJSONObject("primaryRenderer")?.optJSONObject("playlistPanelVideoRenderer")?.let(::take)
                        direct != null -> take(direct)
                        else -> { val k = n.keys(); while (k.hasNext()) walkPanel(n.opt(k.next())) }
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) walkPanel(n.opt(i))
            }
        }
        walkPanel(json.opt("contents"))
        if (out.isEmpty()) return null
        return VideoPage(out.values.toList(), null, emptyList(), "${out.size} video")
    }

    private val previews = java.util.concurrent.ConcurrentHashMap<String, List<VideoItem>>()

    /** Vài video đầu của một danh sách phát cho thẻ kết quả tìm kiếm (khách cũng xem được). Lỗi thì trả rỗng. */
    suspend fun playlistPreview(account: Account?, playlistId: String): List<VideoItem> {
        previews[playlistId]?.let { return it }
        return try {
            val json = InnerTube.call("browse", JSONObject().put("browseId", "VL$playlistId"), account)
            parse(json).filter { it.id.length == 11 }.take(12).map(ShortDetector::applyCached)
                .also { if (it.isNotEmpty()) previews[playlistId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Chỗ chung nhận ra Short không được YouTube đánh dấu (Short dài...) cho mọi danh sách: xem [ShortDetector.resolve]. */
    private suspend fun VideoPage.resolved(): VideoPage {
        return copy(items = ShortDetector.resolve(items))
    }

    private suspend fun List<SearchResult>.resolvedResults(): List<SearchResult> {
        val videos = filterIsInstance<SearchResult.Video>().map { it.video }
        if (videos.none { ShortDetector.needsCheck(it) }) return map { r ->
            if (r is SearchResult.Video) SearchResult.Video(ShortDetector.applyCached(r.video)) else r
        }
        val fixed = ShortDetector.resolve(videos).associateBy { it.id }
        return map { r -> if (r is SearchResult.Video) SearchResult.Video(fixed[r.video.id] ?: r.video) else r }
    }

    /** Các Short của kênh theo tab Shorts (nhãn của chính YouTube); [complete] = đã đọc hết tab. */
    private class ShortTags(val ids: Set<String>, val complete: Boolean)

    private const val CHANNEL_SHORTS_TAB = "EgZzaG9ydHPyBgUKA5oBAA=="
    private const val MAX_SHORTS_TAB_PAGES = 12
    private val uploadsTokens: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile private var uploadsTags: ShortTags? = null
    @Volatile private var tagsCache: Triple<String, Long, ShortTags?>? = null

    /** Đọc tab Shorts của kênh: mọi Short (kể cả Short dài tới 3 phút) đều nằm ở đây, nên đây là nguồn tag chắc chắn. */
    private suspend fun channelShortTags(account: Account?, channel: String): ShortTags? {
        tagsCache?.let { (c, at, tags) -> if (c == channel && System.currentTimeMillis() - at < 120_000L) return tags }
        val ids = LinkedHashSet<String>()
        var body = JSONObject().put("browseId", channel).put("params", CHANNEL_SHORTS_TAB)
        var pages = 0
        var complete = false
        try {
            while (true) {
                val json = InnerTube.call("browse", body, account)
                parse(json).filter { it.isShort }.forEach { ids.add(it.id) }
                pages++
                val next = findContinuation(json)
                if (next == null) { complete = true; break }
                if (pages >= MAX_SHORTS_TAB_PAGES) break
                body = JSONObject().put("continuation", next)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (_: Exception) {
            complete = false
        }
        // Tab trả rỗng có thể là kênh không có Short, cũng có thể là client không đọc được tab: không dám kết luận "không phải Short".
        val tags = if (ids.isEmpty() && !complete) null else ShortTags(ids, complete && ids.isNotEmpty())
        tagsCache = Triple(channel, System.currentTimeMillis(), tags)
        return tags
    }

    /** Áp tag của tab Shorts lên một trang "Video của bạn": có trong tab = Short; video không có trong tab (kể cả không công khai) để ShortDetector xét. */
    private fun tagUploads(page: VideoPage, tags: ShortTags?): VideoPage {
        if (tags == null) return page
        val shorts = page.items.filter { it.id in tags.ids }.map { it.id }
        // Không kết luận "không phải Short" từ việc vắng mặt ở tab Shorts: video không công khai / riêng tư không có ở tab đó.
        ShortDetector.declare(shorts, emptyList())
        page.continuation?.let { uploadsTokens.add(it) }
        return page
    }

    /** Toàn bộ video đã tải lên của kênh tài khoản (mục "Video của bạn"). */
    suspend fun uploadsPage(account: Account?): VideoPage {
        val handle = account?.handle ?: return VideoPage(emptyList(), null)
        val channel = InnerTube.channelId(handle, account)?.takeIf { it.startsWith("UC") } ?: return VideoPage(emptyList(), null)
        ShortDetector.account = account
        val tags = channelShortTags(account, channel)
        uploadsTags = tags
        val json = InnerTube.call("browse", JSONObject().put("browseId", "VLUU" + channel.substring(2)), account)
        return tagUploads(parsePage(json, withChips = false, withCount = true), tags).resolved()
    }

    // ---- Trang chủ kiểu YouTube + tìm kiếm có phân trang ---------------------------------

    /**
     * Trang chủ có phân trang. Token trang kế có tiền tố cho biết gọi endpoint nào:
     * "b:" = browse (trang chủ), "s:" = search, "sm:" = search của chip Âm nhạc (đánh dấu ♪ cho cả danh sách).
     */
    suspend fun homePage(account: Account?): Paged<VideoItem> {
        val json = try {
            InnerTube.call("browse", JSONObject().put("browseId", "FEwhat_to_watch"), account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (_: Exception) {
            null
        }
        val items = json?.let { parse(it).filter { v -> !v.isShort } }.orEmpty()
        if (items.isNotEmpty()) {
            return Paged(items, json?.let { findContinuation(it) }?.let { "b:$it" })
        }
        // Chưa đăng nhập, trang chủ thường trống: lấy tạm kết quả tìm kiếm thịnh hành.
        return chipPage(account, "thịnh hành", null, false)
    }

    /** Một chip chủ đề ở trang chủ = một lượt tìm kiếm, chỉ lấy video thường (Short đã có tab riêng). */
    suspend fun chipPage(account: Account?, query: String, params: String?, music: Boolean): Paged<VideoItem> {
        val page = searchPage(query, params, account)
        return Paged(page.items.toVideos(music), page.token?.let { (if (music) "sm:" else "s:") + it })
    }

    /** Trang kế của trang chủ / chip, theo tiền tố token ở [homePage]. */
    suspend fun homeMore(account: Account?, token: String): Paged<VideoItem> {
        val raw = token.substringAfter(':')
        if (token.startsWith("b:")) {
            val json = InnerTube.call("browse", JSONObject().put("continuation", raw), account)
            return Paged(parse(json).filter { !it.isShort }, findContinuation(json)?.let { "b:$it" })
        }
        val music = token.startsWith("sm:")
        val page = searchMore(raw, account)
        return Paged(page.items.toVideos(music), page.token?.let { (if (music) "sm:" else "s:") + it })
    }

    private fun List<SearchResult>.toVideos(music: Boolean): List<VideoItem> =
        filterIsInstance<SearchResult.Video>()
            .map { it.video }
            .filter { !it.isShort }
            .map { if (music) it.copy(music = true) else it }

    /**
     * Tìm kiếm đầy đủ như YouTube: không ép lọc "chỉ video" nên ra cả kênh, danh sách phát, trực tiếp…
     * [params] chỉ dùng khi người dùng chọn chip lọc (xem [SEARCH_FILTERS]).
     */
    suspend fun searchPage(query: String, params: String?, account: Account?): Paged<SearchResult> {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        val json = searchCall(body, account)
        var results = parseResults(json)
        // "Tất cả" của một nghệ sĩ / bài hát hay chỉ ra kênh + danh sách phát + vài video; các bài lẻ (đĩa đơn, video không
        // thuộc danh sách phát nào của tác giả) nằm ở bộ lọc "Video". Khi trang đầu có ít video thì bổ sung thẳng từ đó.
        if (params == null && results.count { it is SearchResult.Video } < MIN_VIDEOS_FIRST_PAGE) {
            val extra = try {
                parseResults(searchCall(JSONObject().put("query", query).put("params", SEARCH_ANY), account))
                    .filterIsInstance<SearchResult.Video>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthRejected) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (extra.isNotEmpty()) {
                val have = results.map { it.key }.toHashSet()
                results = results + extra.filter { have.add(it.key) }
            }
        }
        return Paged(results.resolvedResults(), findContinuation(json))
    }

    suspend fun searchMore(token: String, account: Account?): Paged<SearchResult> {
        val json = searchCall(JSONObject().put("continuation", token), account)
        return Paged(parseResults(json).resolvedResults(), findContinuation(json))
    }

    private const val MIN_VIDEOS_FIRST_PAGE = 8

    /**
     * Gọi search bằng tài khoản; phiên bị YouTube từ chối / trả lỗi (HTTP 4xx, phản hồi hỏng) thì thử lại như khách để vẫn ra
     * kết quả thay vì trang trống. Chỉ báo "đăng nhập lại" ([AuthRejected]) khi chính khách cũng không tìm được.
     */
    private suspend fun searchCall(body: JSONObject, account: Account?): JSONObject {
        if (account == null) return InnerTube.call("search", body, null)
        // Tài khoản mã (client TV): kết quả của TV chỉ có danh sách phát / vài thẻ, thiếu video lẻ và nhạc. Hỏi bằng mẫu WEB
        // + Bearer của tài khoản (như meetube làm với playlist/create) để có đủ video; không được thì như khách.
        if (account.kind == LoginKind.CODE) {
            val web = try {
                InnerTube.call("search", body, account, asWeb = true).takeIf { hasVideos(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (web != null) return web
            val guest = try {
                InnerTube.call("search", body, null).takeIf { hasVideos(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            return guest ?: InnerTube.call("search", body, account)
        }
        return try {
            InnerTube.call("search", body, account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            InnerTube.call("search", body, null)
        }
    }

    /** Phản hồi có ít nhất một video (thẻ video thật) hoặc là trang kế hợp lệ. */
    private fun hasVideos(json: JSONObject): Boolean = parseResults(json).any { it is SearchResult.Video }

    suspend fun search(query: String, account: Account?): List<VideoItem> =
        parse(InnerTube.call("search", JSONObject().put("query", query).put("params", SEARCH_ANY), account))

    suspend fun removeFromHistory(account: Account?, video: VideoItem) {
        requireNotNull(account) { "Hãy đăng nhập để xóa video đã xem" }
        val token = video.historyFeedbackToken ?: video.historyPanel?.let { panel ->
            val body = JSONObject().put("panelId", panel.label).put("params", panel.token)
            historyToken(InnerTube.call("get_panel", body, account))
        } ?: error("YouTube không trả thao tác xóa cho video này")
        val body = JSONObject().put("feedbackTokens", JSONArray().put(token))
        val result = InnerTube.call("feedback", body, account)
        check(result.dig("feedbackResponses", 0, "isProcessed") == true) {
            "YouTube chưa xác nhận xóa video khỏi lịch sử"
        }
    }

    private fun historyAction(item: VideoItem?, renderer: JSONObject, history: Boolean): VideoItem? {
        if (item == null || !history) return item
        val panel = findKey(renderer, "getPanelEndpoint")
        val id = panel?.optString("panelId").orEmpty()
        val params = panel?.optString("params").orEmpty()
        return item.copy(
            historyFeedbackToken = historyToken(renderer),
            historyPanel = if (id.isNotBlank() && params.isNotBlank()) HistoryChip(id, params) else null
        )
    }

    private fun historyToken(renderer: JSONObject): String? {
        val entries = findAllKeys(renderer, "menuServiceItemRenderer") + findAllKeys(renderer, "listItemViewModel")
        val remove = entries.firstNotNullOfOrNull { entry ->
            val title = (ytText(entry.opt("text")) ?: ytText(entry.opt("title"))).orEmpty().lowercase()
            val icon = findKey(entry, "icon")?.optString("iconType").orEmpty()
            if ((title.contains("lịch sử") || title.contains("video đã xem")) &&
                (title.contains("xóa") || title.contains("xoá")) ||
                title.contains("remove from watch history") || title.contains("remove from history") ||
                icon == "REMOVE_FROM_WATCH_HISTORY") {
                findKey(entry, "feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotBlank() }
            } else null
        }
        if (remove != null) return remove
        // Trong trang lịch sử, menu TV cũ chỉ có một feedback endpoint ẩn thẻ.
        val endpoints = findAllKeys(renderer, "feedbackEndpoint")
        return endpoints.singleOrNull()?.takeIf { it.dig("uiActions", "hideEnclosingContainer") == true }
            ?.optString("feedbackToken")?.takeIf { it.isNotBlank() }
    }

    // ---- Parser -------------------------------------------------------------------------

    fun parse(root: JSONObject, history: Boolean = false): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        walk(root, out, null, history)
        return out.values.toList()
    }

    fun parsePage(root: JSONObject, withChips: Boolean, withCount: Boolean = false, history: Boolean = false): VideoPage =
        VideoPage(
            parse(root, history), findContinuation(root),
            if (withChips) findChips(root) else emptyList(),
            if (withCount) findVideoCount(root) else null
        )

    private val COUNT_RE = Regex("^\\d[\\d.,\\u00a0 ]*video[s]?$", RegexOption.IGNORE_CASE)

    /** "507 video" từ phần đầu playlist: numVideosText (WEB) hoặc một dòng metadata dạng view model. */
    private fun findVideoCount(root: Any?): String? {
        var found: String? = null
        fun go(n: Any?) {
            if (found != null) return
            when (n) {
                is JSONObject -> {
                    n.optString("content").trim().takeIf { COUNT_RE.matches(it) }?.let { found = it; return }
                    ytText(n.opt("numVideosText"))?.trim()?.takeIf { COUNT_RE.matches(it) }?.let { found = it; return }
                    val keys = n.keys()
                    while (keys.hasNext() && found == null) go(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) go(n.opt(i))
            }
        }
        go(root)
        return found
    }

    /**
     * Token trang kế: ưu tiên token của cả danh sách theo đường dẫn ([listLevelContinuation], như MediaServiceCore),
     * rồi mới tới token cuối cùng theo thứ tự tài liệu ([lastContinuation]). Client WEB dùng continuationItemRenderer,
     * client TV dùng nextContinuationData.
     */
    private fun findContinuation(root: Any?): String? = listLevelContinuation(root) ?: lastContinuation(root)

    /** Nhánh chip lọc cũng mang token nhưng không phải trang kế: bỏ qua cả nhánh. */
    private val CHIP_BRANCHES = setOf("chipCloudChipRenderer", "feedFilterChipBarRenderer", "chipBarViewModel")

    /**
     * Nơi giữ token trang kế CỦA CẢ DANH SÁCH (phần `continuations` của chúng): trang đầu của client TV/WEB và trang kế
     * (`sectionListContinuation`, `gridContinuation`...). Giống thứ tự ưu tiên của `BrowseResultTV.getContinuationToken` /
     * `WatchNextResultContinuation.getContinuationToken` trong MediaServiceCore (meetube).
     */
    private val LIST_HOLDERS = setOf(
        "sectionListRenderer", "gridRenderer", "playlistVideoListRenderer",
        "sectionListContinuation", "gridContinuation", "playlistVideoListContinuation", "horizontalListContinuation"
    )

    /** Token đầu tiên dùng được trong mảng `continuations` (ưu tiên token "trang kế" hơn token "tải lại"). */
    private fun tokenOf(continuations: JSONArray?): String? {
        if (continuations == null) return null
        var reload: String? = null
        for (i in 0 until continuations.length()) {
            val c = continuations.optJSONObject(i) ?: continue
            (c.optJSONObject("nextContinuationData") ?: c.optJSONObject("nextRadioContinuationData"))
                ?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
            if (reload == null) reload = c.optJSONObject("reloadContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
        }
        return reload
    }

    /**
     * Token của cả danh sách theo ĐƯỜNG DẪN, không theo thứ tự tài liệu: lấy cái ở tầng nông nhất (token của một nhóm con/
     * một kệ nằm sâu hơn token của danh sách). Trang lịch sử của client TV chia theo ngày thành nhiều kệ; token nằm sâu
     * trong kệ hay token "reload" ở cuối `tvSurfaceContentRenderer` không được phép thế chỗ token của danh sách,
     * nếu không trang kế trả về lại đúng vài video đầu và lịch sử đứng lại.
     */
    private fun listLevelContinuation(root: Any?): String? {
        var bestDepth = Int.MAX_VALUE
        var best: String? = null
        fun go(n: Any?, depth: Int) {
            when (n) {
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k in CHIP_BRANCHES) continue
                        val v = n.opt(k)
                        if (v is JSONObject && k in LIST_HOLDERS) {
                            tokenOf(v.optJSONArray("continuations"))?.let {
                                if (depth <= bestDepth) { bestDepth = depth; best = it }
                            }
                        }
                        go(v, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) go(n.opt(i), depth + 1)
            }
        }
        go(root, 0)
        return best
    }

    /**
     * Cách cũ cho client WEB (`continuationItemRenderer` ở cuối danh sách) và các dạng lạ: token cuối cùng theo thứ tự
     * tài liệu. Token "reload" (tải lại cùng nội dung, không phải trang kế) chỉ dùng khi không còn token nào khác.
     */
    private fun lastContinuation(root: Any?): String? {
        var last: String? = null
        var reload: String? = null
        fun go(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = n.opt(k)
                        when {
                            k in CHIP_BRANCHES -> Unit
                            k == "continuationCommand" && v is JSONObject ->
                                v.optString("token").takeIf { it.isNotBlank() }?.let { last = it }
                            k == "nextContinuationData" && v is JSONObject ->
                                v.optString("continuation").takeIf { it.isNotBlank() }?.let { last = it }
                            k == "reloadContinuationData" && v is JSONObject ->
                                v.optString("continuation").takeIf { it.isNotBlank() }?.let { reload = it }
                            else -> go(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) go(n.opt(i))
            }
        }
        go(root)
        return last ?: reload
    }

    /** Chip lọc (Video, Shorts, Âm nhạc…) do server trả; chip đang chọn không có token nên bị bỏ qua. */
    private fun findChips(root: Any?): List<HistoryChip> =
        findAllKeys(root, "chipCloudChipRenderer").mapNotNull { c ->
            val label = ytText(c.opt("text")) ?: return@mapNotNull null
            val token = c.dig("navigationEndpoint", "continuationCommand", "token") as? String
            if (token.isNullOrBlank()) null else HistoryChip(label, token)
        }.distinctBy { it.label }

    private fun add(out: MutableMap<String, VideoItem>, item: VideoItem?, section: String?) {
        if (item != null && (item.id.length == 11 || item.playlist != null) && item.title.isNotBlank() && !out.containsKey(item.id)) {
            out[item.id] = if (item.section == null && section != null) item.copy(section = section) else item
        }
    }

    /** Tiêu đề nhóm của một itemSectionRenderer / shelfRenderer (vd "Hôm nay"), null nếu không có. */
    private fun sectionTitle(v: JSONObject): String? =
        ytText(v.dig("header", "itemSectionHeaderRenderer", "title"))
            ?: ytText(v.opt("headline"))

    private val VIDEO_KEYS = listOf(
        "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer"
    )

    private fun walk(node: Any?, out: MutableMap<String, VideoItem>, section: String?, history: Boolean) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        when {
                            k in VIDEO_KEYS -> add(out, historyAction(classic(v), v, history), section)
                            k == "reelItemRenderer" -> add(out, historyAction(reel(v), v, history), section)
                            k == "shortsLockupViewModel" -> add(out, historyAction(shortsLockup(v), v, history), section)
                            k == "lockupViewModel" -> add(out, historyAction(
                                if (PLAYLIST_LOCKUPS.any { v.optString("contentType").contains(it) }) playlistItem(playlistLockup(v)) else lockup(v),
                                v, history
                            ), section)
                            k == "tileRenderer" -> add(out, historyAction(
                                if (isPlaylistTile(v)) playlistItem(tilePlaylist(v)) else tile(v),
                                v, history
                            ), section)
                            k == "itemSectionRenderer" || k == "shelfRenderer" ->
                                walk(v, out, sectionTitle(v) ?: section, history)
                            else -> walk(v, out, section, history)
                        }
                    } else {
                        walk(v, out, section, history)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), out, section, history)
        }
    }

    private fun classic(v: JSONObject): VideoItem? {
        val id = v.optString("videoId")
        val title = ytText(v.opt("title")) ?: return null
        val channel = ytText(v.opt("ownerText")) ?: ytText(v.opt("longBylineText")) ?: ytText(v.opt("shortBylineText"))
        val duration = ytText(v.opt("lengthText")) ?: overlayTime(v.opt("thumbnailOverlays"))
        val views = ytText(v.opt("shortViewCountText")) ?: ytText(v.opt("viewCountText")) ?: ytText(v.opt("videoInfo"))
        // Short trong playlist/lịch sử chỉ lộ ra qua reelWatchEndpoint; vẫn giữ thời lượng nếu có.
        val short = duration == "SHORTS" || overlayStyle(v.opt("thumbnailOverlays")) == "SHORTS" ||
            v.dig("navigationEndpoint", "reelWatchEndpoint") != null
        return VideoItem(
            id, title, channel, duration.takeIf { it != "SHORTS" }, views, short,
            progress = resumeProgress(v.opt("thumbnailOverlays")),
            avatar = avatarOf(v),
            music = MUSIC_ICON.containsMatchIn(v.opt("thumbnailOverlays")?.toString().orEmpty()),
            artUrl = squareArt(v.dig("thumbnail", "thumbnails") as? JSONArray),
            channelId = channelIdOf(v),
            published = ytText(v.opt("publishedTimeText")) ?: publishedIn(views),
            upcoming = upcomingText(v.optJSONObject("upcomingEventData")),
            live = overlayStyle(v.opt("thumbnailOverlays")) == "LIVE" ||
                isWatchingText(ytText(v.opt("viewCountText")) ?: ytText(v.opt("shortViewCountText")))
        )
    }

    /**
     * Như VideoItem.getUpcomingEventText của MediaServiceCore: upcomingEventText có chỗ trống DATE_PLACEHOLDER,
     * thay bằng ngày giờ từ startTime (giây) theo múi giờ máy.
     */
    private fun upcomingText(event: JSONObject?): String? {
        val text = ytText(event?.opt("upcomingEventText")) ?: return null
        val startMs = event?.optString("startTime")?.toLongOrNull()?.times(1000) ?: return text
        val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(startMs))
        return text.replace("DATE_PLACEHOLDER", date)
    }

    /**
     * Ảnh đại diện kênh có sẵn trong thẻ: WEB cũ (channelThumbnailWithLinkRenderer), view model mới
     * (avatarViewModel / decoratedAvatarViewModel). Lấy ảnh lớn nhất để không bị mờ ở avatar 36dp trở lên.
     * Thẻ TV không có ảnh này: avatar sẽ do [ChannelAvatars] tra theo mã kênh / tên kênh.
     */
    private fun avatarOf(v: JSONObject): String? {
        val thumbs = (findKey(v, "channelThumbnailWithLinkRenderer")?.dig("thumbnail", "thumbnails") as? JSONArray)
            ?: (findKey(v, "avatarViewModel")?.dig("image", "sources") as? JSONArray)
            ?: return null
        if (thumbs.length() == 0) return null
        return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    /** Mã kênh (UC…) mà thẻ nêu: ở dòng tên kênh, ảnh đại diện hay menu "Đi tới kênh". Null nếu không có. */
    private fun channelIdOf(v: JSONObject): String? =
        findAllKeys(v, "browseEndpoint").firstNotNullOfOrNull { e ->
            e.optString("browseId").takeIf { it.startsWith("UC") && it.length == 24 }
        }

    /** Menu ngữ cảnh của thẻ TV (nhấn giữ): panelId + params để hỏi get_panel, null nếu thẻ không có. */
    private fun menuPanel(v: JSONObject): Pair<String, String>? {
        val e = v.dig("onLongPressCommand", "showEngagementPanelEndpoint") as? JSONObject ?: return null
        val tag = e.dig("identifier", "tag") as? String
        val params = e.dig("globalConfiguration", "params") as? String
        return if (!tag.isNullOrBlank() && !params.isNullOrBlank()) tag to params else null
    }

    /** Phần đã xem (0..1) từ thumbnailOverlayResumePlaybackRenderer; 0 nếu không có. */
    private fun resumeProgress(overlays: Any?): Float {
        if (overlays !is JSONArray) return 0f
        for (i in 0 until overlays.length()) {
            val r = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayResumePlaybackRenderer") ?: continue
            return (r.optInt("percentDurationWatched", 0) / 100f).coerceIn(0f, 1f)
        }
        return 0f
    }

    private fun overlayTime(overlays: Any?): String? {
        if (overlays !is JSONArray) return null
        for (i in 0 until overlays.length()) {
            val r = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
            ytText(r.opt("text"))?.let { return it }
        }
        return null
    }

    private val DURATION_IN_JSON = Regex("\"(?:simpleText|text|label)\":\"(\\d{1,2}(?::\\d{2}){1,2})\"")

    private fun overlayStyle(overlays: Any?): String? {
        if (overlays !is JSONArray) return null
        for (i in 0 until overlays.length()) {
            val r = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
            return r.optString("style").ifBlank { null }
        }
        return null
    }

    private fun reel(v: JSONObject): VideoItem? {
        val id = v.optString("videoId")
        val title = ytText(v.opt("headline")) ?: return null
        return VideoItem(id, title, null, null, ytText(v.opt("viewCountText")), true)
    }

    private fun shortsLockup(v: JSONObject): VideoItem? {
        val id = (v.dig("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId") as? String)
            ?: v.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.length == 11 }
            ?: return null
        val title = ytText(v.dig("overlayMetadata", "primaryText")) ?: return null
        return VideoItem(id, title, null, null, ytText(v.dig("overlayMetadata", "secondaryText")), true)
    }

    private fun lockup(v: JSONObject): VideoItem? {
        // TVHTML5 trả Short dưới dạng lockupViewModel, chỉ phân biệt bằng reelWatchEndpoint hoặc LOCKUP_CONTENT_TYPE_SHORT.
        val type = v.optString("contentType")
        val reelId = findKey(v, "reelWatchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
        val short = type.contains("SHORT") || reelId != null
        if (type != "LOCKUP_CONTENT_TYPE_VIDEO" && !short) return null
        val id = reelId ?: v.optString("contentId")
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val rows = meta.dig("metadata", "contentMetadataViewModel", "metadataRows") as? JSONArray
        fun part(row: Int): String? = ytText(rows.dig(row, "metadataParts", 0, "text"))
        var progress = 0f
        // Như MediaServiceCore (LockupItem.getThumbnailView): ảnh bìa nằm ở thumbnailViewModel, hoặc ảnh chính của collectionThumbnailViewModel.
        val overlays = (v.dig("contentImage", "thumbnailViewModel", "overlays")
            ?: v.dig("contentImage", "collectionThumbnailViewModel", "primaryThumbnail", "thumbnailViewModel", "overlays")) as? JSONArray
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val pct = overlays.dig(i, "thumbnailBottomOverlayViewModel", "progressBar", "thumbnailOverlayProgressBarViewModel", "startPercent")
                if (pct is Number) progress = (pct.toFloat() / 100f).coerceIn(0f, 1f)
            }
        }
        val duration = lockupBadge(overlays)
        val allParts = buildList {
            for (r in 0 until (rows?.length() ?: 0)) {
                val parts = rows.dig(r, "metadataParts") as? JSONArray ?: continue
                for (j in 0 until parts.length()) ytText(parts.dig(j, "text"))?.let { add(it) }
            }
        }
        return VideoItem(
            id, title, part(0), duration.takeIf { !short }, part(1), short,
            progress = progress,
            avatar = avatarOf(meta),
            music = MUSIC_ICON.containsMatchIn(overlays?.toString().orEmpty()),
            artUrl = squareArt(v.dig("contentImage", "thumbnailViewModel", "image", "sources") as? JSONArray),
            channelId = channelIdOf(meta) ?: channelIdOf(v),
            published = allParts.firstNotNullOfOrNull { publishedIn(it) },
            live = allParts.any { isWatchingText(it) } || duration.equals("LIVE", ignoreCase = true) ||
                duration.equals("TRỰC TIẾP", ignoreCase = true)
        )
    }

    /**
     * Chữ huy hiệu trên ảnh bìa của lockupViewModel (thời lượng / LIVE). Như MediaServiceCore (LockupItem.getBadge): client WEB để ở
     * thumbnailOverlayBadgeViewModel.thumbnailBadges, client TV ở thumbnailBottomOverlayViewModel.badges.
     */
    /** Ảnh cuối (lớn nhất) của một mảng ảnh nếu nó vuông (1:1), ngược lại null. */
    private fun squareArt(thumbs: JSONArray?): String? {
        val t = thumbs?.optJSONObject(thumbs.length() - 1) ?: return null
        val w = t.optInt("width")
        val h = t.optInt("height")
        if (w <= 0 || h <= 0 || kotlin.math.abs(w.toFloat() / h - 1f) > 0.05f) return null
        return t.optString("url").takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun lastUrl(arr: JSONArray?): String? =
        arr?.optJSONObject(arr.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }

    private fun lockupBadge(overlays: JSONArray?): String? {
        if (overlays == null) return null
        for (i in 0 until overlays.length()) {
            val badges = (overlays.dig(i, "thumbnailOverlayBadgeViewModel", "thumbnailBadges")
                ?: overlays.dig(i, "thumbnailBottomOverlayViewModel", "badges")) as? JSONArray ?: continue
            for (j in 0 until badges.length()) {
                ytText(badges.dig(j, "thumbnailBadgeViewModel", "text"))?.let { return it }
            }
        }
        return null
    }

    private fun tile(v: JSONObject): VideoItem? {
        val reelId = v.dig("onSelectCommand", "reelWatchEndpoint", "videoId") as? String
        val id = (v.dig("onSelectCommand", "watchEndpoint", "videoId") as? String) ?: reelId ?: return null
        val meta = v.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val lines = meta.optJSONArray("lines")
        fun line(i: Int) = tileLine(lines, i)
        val tileOverlays = v.dig("header", "tileHeaderRenderer", "thumbnailOverlays")
        val duration = overlayTime(tileOverlays) ?: DURATION_IN_JSON.find(tileOverlays?.toString().orEmpty())?.groupValues?.get(1)
        // Đủ các dấu hiệu của TileItem.isShorts (MediaServiceCore): reelWatchEndpoint, kiểu thẻ TILE_STYLE_YTLR_SHORTS, huy hiệu
        // SHORTS trên ảnh bìa (+ contentType). Short dài không có dấu hiệu nào: do ShortDetector.resolve xử lý ở repository.
        val short = reelId != null || v.optString("contentType").contains("SHORT") ||
            v.optString("style") == "TILE_STYLE_YTLR_SHORTS" ||
            overlayStyle(tileOverlays) == "SHORTS" || duration == "SHORTS"
        val channelId = channelIdOf(v)
        val panel = if (channelId == null && !short) menuPanel(v) else null
        val meta1 = line(1)
        val meta2 = line(2)
        return VideoItem(
            id, title, line(0), duration.takeIf { !short }, meta1, short,
            progress = resumeProgress(tileOverlays),
            music = MUSIC_ICON.containsMatchIn(tileOverlays?.toString().orEmpty()),
            channelId = channelId, panelId = panel?.first, panelParams = panel?.second,
            published = publishedIn(meta1) ?: publishedIn(meta2),
            live = isWatchingText(meta1) || isWatchingText(meta2) || overlayStyle(tileOverlays) == "LIVE"
        )
    }

    /** Một dòng chữ của thẻ TV: các mục trong dòng nối bằng " · " (bỏ các mục chỉ là dấu phân cách "•" mà YouTube chèn giữa các phần). */
    private fun tileLine(lines: JSONArray?, i: Int): String? {
        val items = lines.dig(i, "lineRenderer", "items") as? JSONArray ?: return null
        return buildString {
            for (j in 0 until items.length()) {
                val t = ytText(items.dig(j, "lineItemRenderer", "text"))
                if (t != null && t.any { it.isLetterOrDigit() }) {
                    if (isNotEmpty()) append(" · ")
                    append(t)
                }
            }
        }.ifBlank { null }
    }

    // ---- Kết quả tìm kiếm hỗn hợp (video + kênh + danh sách phát) --------------------------

    fun parseResults(root: JSONObject): List<SearchResult> {
        val out = LinkedHashMap<String, SearchResult>()
        walkResults(root, out)
        return out.values.toList()
    }

    private fun putResult(out: MutableMap<String, SearchResult>, r: SearchResult?) {
        if (r != null && !out.containsKey(r.key)) out[r.key] = r
    }

    private fun putVideo(out: MutableMap<String, SearchResult>, item: VideoItem?) {
        if (item != null && item.id.length == 11 && item.title.isNotBlank()) putResult(out, SearchResult.Video(item))
    }

    /** Kênh trong kết quả: bản WEB (channelRenderer) và các biến thể của client TV. */
    private val CHANNEL_KEYS = setOf("channelRenderer", "compactChannelRenderer", "gridChannelRenderer", "pivotChannelRenderer")
    private const val TILE_CHANNEL = "TILE_CONTENT_TYPE_CHANNEL"

    private val AD_KEYS = setOf("adSlotRenderer", "searchPyvRenderer", "promotedSparklesWebRenderer", "promotedVideoRenderer")

    private fun walkResults(node: Any?, out: MutableMap<String, SearchResult>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        when {
                            k in AD_KEYS -> Unit
                            k in VIDEO_KEYS -> putVideo(out, classic(v))
                            k == "reelItemRenderer" -> putVideo(out, reel(v))
                            k == "shortsLockupViewModel" -> putVideo(out, shortsLockup(v))
                            k == "lockupViewModel" -> lockupResult(v)?.let { putResult(out, it) }
                            k == "tileRenderer" -> when {
                                isChannelTile(v) -> putResult(out, tileChannel(v))
                                isPlaylistTile(v) -> putResult(out, tilePlaylist(v))
                                else -> putVideo(out, tile(v))
                            }
                            k in CHANNEL_KEYS -> putResult(out, channelResult(v))
                            k == "playlistRenderer" -> putResult(out, playlistResult(v))
                            else -> walkResults(v, out)
                        }
                    } else {
                        walkResults(v, out)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walkResults(node.opt(i), out)
        }
    }

    private fun lockupResult(v: JSONObject): SearchResult? {
        val type = v.optString("contentType")
        if (PLAYLIST_LOCKUPS.any { type.contains(it) }) return playlistLockup(v)
        if (type.contains("CHANNEL")) return channelLockup(v)
        return lockup(v)?.takeIf { it.id.length == 11 && it.title.isNotBlank() }?.let { SearchResult.Video(it) }
    }

    private val PLAYLIST_LOCKUPS = listOf("PLAYLIST", "ALBUM", "PODCAST")
    private val KIND_WORDS = listOf("đĩa nhạc", "album", "đĩa đơn", "single", "ep", "danh sách phát", "playlist", "podcast")
    private val COUNT_IN_TEXT = Regex("\\d[\\d.,\\u00a0]*\\s*(video|videos|bài hát|bài|tracks?|songs?)", RegexOption.IGNORE_CASE)
    private val DOT_SPLIT = Regex("\\s*[•·]\\s*")

    /** Chữ của các dòng metadata, tách theo dấu • (YouTube hay gộp "Đĩa nhạc • HRXSTAL, HARDX" thành một đoạn). */
    private fun lockupTexts(meta: JSONObject): List<String> {
        val rows = meta.dig("metadata", "contentMetadataViewModel", "metadataRows") as? JSONArray
        return (0 until (rows?.length() ?: 0)).flatMap { r ->
            val parts = rows.dig(r, "metadataParts") as? JSONArray
            (0 until (parts?.length() ?: 0)).mapNotNull { ytText(parts.dig(it, "text")) }
        }.flatMap { it.split(DOT_SPLIT) }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun playlistLockup(v: JSONObject): SearchResult? {
        val id = v.optString("contentId").ifBlank { return null }
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val texts = lockupTexts(meta)
        val count = findKey(v, "thumbnailBadgeViewModel")?.let { ytText(it.opt("text")) }
            ?: texts.firstOrNull { COUNT_IN_TEXT.containsMatchIn(it) }
        val kind = texts.firstOrNull { t -> KIND_WORDS.any { t.equals(it, ignoreCase = true) } }
        val channel = texts.firstOrNull { it != count && it != kind && !COUNT_IN_TEXT.containsMatchIn(it) }
        val thumb = lastUrl(findKey(v, "thumbnailViewModel")?.dig("image", "sources") as? JSONArray)
        return SearchResult.Playlist(id, title, channel, count, thumb, kind)
    }

    /** Danh sách phát ở trang chủ thành một VideoItem mang [VideoItem.playlist] (khóa = mã danh sách phát). */
    private fun playlistItem(r: SearchResult?): VideoItem? {
        val pl = r as? SearchResult.Playlist ?: return null
        return VideoItem(pl.id, pl.title, pl.channel, null, null, false, playlist = pl)
    }

    private fun isPlaylistTile(v: JSONObject): Boolean =
        v.optString("contentType").let { it == "TILE_CONTENT_TYPE_PLAYLIST" || it == "TILE_CONTENT_TYPE_RADIO" } ||
            tileBrowseId(v)?.startsWith("VL") == true

    /** Danh sách phát của client TV (tài khoản đăng nhập bằng mã): trước đây bị đọc thành một video nên chỉ hiện mỗi ảnh. */
    private fun tilePlaylist(v: JSONObject): SearchResult? {
        val id = (v.dig("onSelectCommand", "watchEndpoint", "playlistId") as? String)?.takeIf { it.isNotBlank() }
            ?: tileBrowseId(v)?.removePrefix("VL")?.takeIf { it.isNotBlank() }
            ?: return null
        val meta = v.dig("metadata", "tileMetadataRenderer") as? JSONObject ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val lines = meta.optJSONArray("lines")
        val texts = (0 until (lines?.length() ?: 0)).mapNotNull { tileLine(lines, it) }
            .flatMap { it.split(DOT_SPLIT) }.map { it.trim() }.filter { it.isNotEmpty() }
        val overlays = v.dig("header", "tileHeaderRenderer", "thumbnailOverlays")
        val overlayText = overlays?.toString().orEmpty()
        val count = COUNT_IN_TEXT.find(overlayText)?.value
            ?: overlayTime(overlays)?.takeIf { t -> t.isNotBlank() && t.all { it.isDigit() || it in ".,\u00a0 " } }?.let { "$it video" }
            ?: texts.firstOrNull { COUNT_IN_TEXT.containsMatchIn(it) }
        val kind = texts.firstOrNull { t -> KIND_WORDS.any { t.equals(it, ignoreCase = true) } }
        val channel = texts.firstOrNull { it != count && it != kind && !COUNT_IN_TEXT.containsMatchIn(it) }
        val thumb = lastUrl(v.dig("header", "tileHeaderRenderer", "thumbnail", "thumbnails") as? JSONArray)
        return SearchResult.Playlist(id, title, channel, count, thumb, kind)
    }

    private fun playlistResult(v: JSONObject): SearchResult? {
        val id = v.optString("playlistId").ifBlank { return null }
        val title = ytText(v.opt("title")) ?: return null
        val channel = ytText(v.opt("longBylineText")) ?: ytText(v.opt("shortBylineText"))
        val count = ytText(v.opt("videoCountText")) ?: v.optString("videoCount").ifBlank { null }?.let { "$it video" }
        val thumb = lastUrl(v.dig("thumbnails", 0, "thumbnails") as? JSONArray)
            ?: lastUrl(v.dig("thumbnail", "thumbnails") as? JSONArray)
        return SearchResult.Playlist(id, title, channel, count, thumb)
    }

    /**
     * Kênh: YouTube hiện trả @handle trong subscriberCountText và số người đăng ký trong videoCountText
     * (bản cũ thì ngược lại), nên gom hết rồi tách theo chữ "@".
     */
    private fun channelResult(v: JSONObject): SearchResult? {
        val id = v.optString("channelId").ifBlank { return null }
        val title = ytText(v.opt("title")) ?: return null
        val avatar = v.dig("thumbnail", "thumbnails", 0, "url") as? String
        val texts = listOfNotNull(ytText(v.opt("subscriberCountText")), ytText(v.opt("videoCountText")))
        val fromUrl = (v.dig("navigationEndpoint", "browseEndpoint", "canonicalBaseUrl") as? String)
            ?.takeIf { it.startsWith("/@") }?.substring(1)
        val handle = texts.firstOrNull { it.startsWith("@") } ?: fromUrl
        val info = texts.filter { it != handle }.joinToString(" • ").ifBlank { null }
        val button = v.dig("subscribeButton", "subscribeButtonRenderer") as? JSONObject
        return SearchResult.Channel(id, title, handle, info, avatar, button?.optBoolean("subscribed"), subscribeParams(button), notifyStates(button), notifyCurrent(button))
    }

    private fun tileBrowseId(v: JSONObject) = v.dig("onSelectCommand", "browseEndpoint", "browseId") as? String

    /** Thẻ TV là kênh khi YouTube ghi loại kênh, hoặc khi thẻ mở thẳng một mã kênh (UC…) chứ không phải video. */
    private fun isChannelTile(v: JSONObject) = v.optString("contentType") == TILE_CHANNEL || tileBrowseId(v)?.startsWith("UC") == true

    /** Kênh kiểu lockupViewModel (client WEB mới): mã kênh ở contentId, chữ ở các dòng metadata. */
    private fun channelLockup(v: JSONObject): SearchResult? {
        val id = v.optString("contentId").ifBlank { return null }
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val rows = meta.dig("metadata", "contentMetadataViewModel", "metadataRows") as? JSONArray
        val texts = (0 until (rows?.length() ?: 0)).flatMap { r ->
            val parts = rows.dig(r, "metadataParts") as? JSONArray
            (0 until (parts?.length() ?: 0)).mapNotNull { ytText(parts.dig(it, "text")) }
        }
        val handle = texts.firstOrNull { it.startsWith("@") }
        val info = texts.filter { it != handle }.joinToString(" • ").ifBlank { null }
        val avatar = findKey(v, "avatarViewModel")?.dig("image", "sources", 0, "url") as? String
        return SearchResult.Channel(id, title, handle, info, avatar, false)
    }

    /** Kênh trong kết quả của client TV (tài khoản đăng nhập bằng mã): thẻ tileRenderer loại kênh, dòng chữ là @handle và số người đăng ký. */
    private fun tileChannel(v: JSONObject): SearchResult? {
        val id = tileBrowseId(v) ?: v.optString("contentId").ifBlank { null } ?: return null
        val meta = v.dig("metadata", "tileMetadataRenderer") as? JSONObject ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val lines = meta.optJSONArray("lines")
        val texts = (0 until (lines?.length() ?: 0)).mapNotNull { tileLine(lines, it) }
        val handle = texts.firstOrNull { it.startsWith("@") }
        val info = texts.filter { it != handle }.joinToString(" • ").ifBlank { null }
        val avatar = v.dig("header", "tileHeaderRenderer", "thumbnail", "thumbnails", 0, "url") as? String
        // Thẻ TV thường không kèm nút đăng ký: coi như chưa đăng ký, vẫn cho bấm Đăng ký.
        val button = findKey(v, "subscribeButtonRenderer")
        return SearchResult.Channel(id, title, handle, info, avatar, button?.optBoolean("subscribed") ?: false, subscribeParams(button), notifyStates(button), notifyCurrent(button))
    }

    /**
     * Các mức chuông thông báo trong nút đăng ký (subscriptionNotificationToggleButtonRenderer.states), đúng cách
     * MediaServiceCore đọc: tên ở inlineMenuButton.buttonRenderer.text, params ở modifyChannelNotificationPreferenceEndpoint.
     */
    internal fun notifyStates(button: JSONObject?): List<NotifyState> {
        val states = findKey(button, "subscriptionNotificationToggleButtonRenderer")?.optJSONArray("states") ?: return emptyList()
        return (0 until states.length()).mapNotNull { i ->
            val st = states.optJSONObject(i) ?: return@mapNotNull null
            val params = st.dig("inlineMenuButton", "buttonRenderer", "serviceEndpoint", "modifyChannelNotificationPreferenceEndpoint", "params") as? String
            val title = ytText(st.dig("inlineMenuButton", "buttonRenderer", "text"))
            if (params.isNullOrBlank() || title.isNullOrBlank()) null else NotifyState(st.optInt("stateId", i), title, params)
        }
    }

    internal fun notifyCurrent(button: JSONObject?): Int? =
        findKey(button, "subscriptionNotificationToggleButtonRenderer")?.let { if (it.has("currentStateId")) it.optInt("currentStateId") else null }

    private fun subscribeParams(button: JSONObject?): String? =
        findKey(button, "subscribeEndpoint")?.optString("params")?.ifBlank { null }
}
