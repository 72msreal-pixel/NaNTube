package com.nan.nantube.data

import org.json.JSONArray
import org.json.JSONObject

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
    val progress: Float = 0f
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
        get() = channel?.endsWith("- Topic") == true || MUSIC_TITLE.containsMatchIn(title)

    private companion object {
        val MUSIC_TITLE = Regex(
            "official (music )?(video|audio|mv)|lyrics?|\\bm/?v\\b|\\(audio\\)|\\bfeat\\.|\\bft\\.|remix|karaoke|nhạc|full album|\\bost\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

data class HistoryChip(val label: String, val token: String)

/** Một trang kết quả: video + token để lấy trang kế (null = hết) + các chip lọc server trả về. */
data class VideoPage(
    val items: List<VideoItem>,
    val continuation: String?,
    val chips: List<HistoryChip> = emptyList(),
    /** Số video của playlist, vd "507 video" (chỉ có ở trang đầu của playlist, nếu đọc được). */
    val total: String? = null
)

/**
 * Nguồn video tạm thời: chỉ lấy danh sách (tên, kênh, thời lượng) để hiển thị, chưa phát trong app.
 * Một bộ đọc đệ quy xử lý các loại "renderer" của cả client WEB lẫn TV nên đổi kiểu đăng nhập vẫn đọc được.
 */
object VideoRepository {
    private const val SEARCH_ANY = "EgIQAQ%3D%3D" // chỉ video
    private const val SEARCH_SHORT = "EgIYAQ%3D%3D" // dưới 4 phút

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
        val body = JSONObject().put("browseId", "FEhistory")
        if (!query.isNullOrBlank()) body.put("query", query)
        return parsePage(InnerTube.call("browse", body, account), withChips = query.isNullOrBlank())
    }

    /** Trang kế của mọi danh sách phân trang (lịch sử, playlist, chip lọc) theo token continuation. */
    suspend fun continuePage(account: Account?, token: String): VideoPage =
        parsePage(InnerTube.call("browse", JSONObject().put("continuation", token), account), withChips = false)

    /** Trang đầu của một playlist (WL, LL, UU…); trang kế lấy bằng [continuePage]. */
    suspend fun playlistPage(account: Account?, playlistId: String): VideoPage {
        if (account == null) return VideoPage(emptyList(), null)
        val json = InnerTube.call("browse", JSONObject().put("browseId", "VL$playlistId"), account)
        return parsePage(json, withChips = false, withCount = true)
    }

    /** Toàn bộ video đã tải lên của kênh tài khoản (mục "Video của bạn"). */
    suspend fun uploadsPage(account: Account?): VideoPage {
        val id = uploadsPlaylistId(account) ?: return VideoPage(emptyList(), null)
        return playlistPage(account, id)
    }

    /** Playlist uploads của kênh = "UU" + phần sau "UC" của mã kênh. */
    private suspend fun uploadsPlaylistId(account: Account?): String? {
        val handle = account?.handle ?: return null
        val channel = InnerTube.channelId(handle, account) ?: return null
        return if (channel.startsWith("UC")) "UU" + channel.substring(2) else null
    }

    suspend fun search(query: String, account: Account?): List<VideoItem> =
        parse(InnerTube.call("search", JSONObject().put("query", query).put("params", SEARCH_ANY), account))

    // ---- Parser -------------------------------------------------------------------------

    fun parse(root: JSONObject): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        walk(root, out, null)
        return out.values.toList()
    }

    fun parsePage(root: JSONObject, withChips: Boolean, withCount: Boolean = false): VideoPage =
        VideoPage(
            parse(root), findContinuation(root),
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
     * Token trang kế: lấy cái cuối cùng trong thứ tự tài liệu (token của cả danh sách nằm sau token của
     * các nhóm con). Client WEB dùng continuationItemRenderer, client TV dùng nextContinuationData.
     */
    private fun findContinuation(root: Any?): String? {
        var last: String? = null
        fun go(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = n.opt(k)
                        when {
                            // Chip lọc cũng mang token nhưng không phải trang kế: bỏ qua cả nhánh.
                            k == "chipCloudChipRenderer" || k == "feedFilterChipBarRenderer" || k == "chipBarViewModel" -> Unit
                            k == "continuationCommand" && v is JSONObject ->
                                v.optString("token").takeIf { it.isNotBlank() }?.let { last = it }
                            (k == "nextContinuationData" || k == "reloadContinuationData") && v is JSONObject ->
                                v.optString("continuation").takeIf { it.isNotBlank() }?.let { last = it }
                            else -> go(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) go(n.opt(i))
            }
        }
        go(root)
        return last
    }

    /** Chip lọc (Video, Shorts, Âm nhạc…) do server trả; chip đang chọn không có token nên bị bỏ qua. */
    private fun findChips(root: Any?): List<HistoryChip> =
        findAllKeys(root, "chipCloudChipRenderer").mapNotNull { c ->
            val label = ytText(c.opt("text")) ?: return@mapNotNull null
            val token = c.dig("navigationEndpoint", "continuationCommand", "token") as? String
            if (token.isNullOrBlank()) null else HistoryChip(label, token)
        }.distinctBy { it.label }

    private fun add(out: MutableMap<String, VideoItem>, item: VideoItem?, section: String?) {
        if (item != null && item.id.length == 11 && item.title.isNotBlank() && !out.containsKey(item.id)) {
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

    private fun walk(node: Any?, out: MutableMap<String, VideoItem>, section: String?) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        when {
                            k in VIDEO_KEYS -> add(out, classic(v), section)
                            k == "reelItemRenderer" -> add(out, reel(v), section)
                            k == "shortsLockupViewModel" -> add(out, shortsLockup(v), section)
                            k == "lockupViewModel" -> add(out, lockup(v), section)
                            k == "tileRenderer" -> add(out, tile(v), section)
                            k == "itemSectionRenderer" || k == "shelfRenderer" ->
                                walk(v, out, sectionTitle(v) ?: section)
                            else -> walk(v, out, section)
                        }
                    } else {
                        walk(v, out, section)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), out, section)
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
        return VideoItem(id, title, channel, duration.takeIf { it != "SHORTS" }, views, short, progress = resumeProgress(v.opt("thumbnailOverlays")))
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
            return ytText(r.opt("text"))
        }
        return null
    }

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
        var duration: String? = null
        var progress = 0f
        val overlays = v.dig("contentImage", "thumbnailViewModel", "overlays") as? JSONArray
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val pct = overlays.dig(i, "thumbnailBottomOverlayViewModel", "progressBar", "thumbnailOverlayProgressBarViewModel", "startPercent")
                if (pct is Number) progress = (pct.toFloat() / 100f).coerceIn(0f, 1f)
            }
            for (i in 0 until overlays.length()) {
                val badges = overlays.dig(i, "thumbnailBottomOverlayViewModel", "badges") as? JSONArray ?: continue
                duration = ytText(badges.dig(0, "thumbnailBadgeViewModel", "text"))
                if (duration != null) break
            }
        }
        return VideoItem(id, title, part(0), duration.takeIf { !short }, part(1), short, progress = progress)
    }

    private fun tile(v: JSONObject): VideoItem? {
        val reelId = v.dig("onSelectCommand", "reelWatchEndpoint", "videoId") as? String
        val id = (v.dig("onSelectCommand", "watchEndpoint", "videoId") as? String) ?: reelId ?: return null
        val short = reelId != null || v.optString("contentType").contains("SHORT")
        val meta = v.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val lines = meta.optJSONArray("lines")
        fun line(i: Int): String? {
            val items = lines.dig(i, "lineRenderer", "items") as? JSONArray ?: return null
            return buildString {
                for (j in 0 until items.length()) {
                    val t = ytText(items.dig(j, "lineItemRenderer", "text"))
                    if (t != null) {
                        if (isNotEmpty()) append(" · ")
                        append(t)
                    }
                }
            }.ifBlank { null }
        }
        val tileOverlays = v.dig("header", "tileHeaderRenderer", "thumbnailOverlays")
        val duration = overlayTime(tileOverlays)
        return VideoItem(id, title, line(0), duration.takeIf { !short }, line(1), short, progress = resumeProgress(tileOverlays))
    }
}
