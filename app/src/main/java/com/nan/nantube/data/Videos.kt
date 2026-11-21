package com.nan.nantube.data

import org.json.JSONArray
import org.json.JSONObject

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String?,
    val duration: String?,
    val views: String?,
    val isShort: Boolean
) {
    /** Ảnh bìa lấy thẳng từ CDN theo id: luôn dùng được, khỏi phụ thuộc cấu trúc JSON từng loại. */
    val thumbnail: String
        get() = if (isShort) "https://i.ytimg.com/vi/$id/oar2.jpg" else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    val watchUrl: String
        get() = if (isShort) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id"
}

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

    suspend fun search(query: String, account: Account?): List<VideoItem> =
        parse(InnerTube.call("search", JSONObject().put("query", query).put("params", SEARCH_ANY), account))

    // ---- Parser -------------------------------------------------------------------------

    fun parse(root: JSONObject): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        walk(root, out)
        return out.values.toList()
    }

    private fun add(out: MutableMap<String, VideoItem>, item: VideoItem?) {
        if (item != null && item.id.length == 11 && item.title.isNotBlank() && !out.containsKey(item.id)) {
            out[item.id] = item
        }
    }

    private val VIDEO_KEYS = listOf(
        "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer"
    )

    private fun walk(node: Any?, out: MutableMap<String, VideoItem>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        when {
                            k in VIDEO_KEYS -> add(out, classic(v))
                            k == "reelItemRenderer" -> add(out, reel(v))
                            k == "shortsLockupViewModel" -> add(out, shortsLockup(v))
                            k == "lockupViewModel" -> add(out, lockup(v))
                            k == "tileRenderer" -> add(out, tile(v))
                            else -> walk(v, out)
                        }
                    } else {
                        walk(v, out)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), out)
        }
    }

    private fun classic(v: JSONObject): VideoItem? {
        val id = v.optString("videoId")
        val title = ytText(v.opt("title")) ?: return null
        val channel = ytText(v.opt("ownerText")) ?: ytText(v.opt("longBylineText")) ?: ytText(v.opt("shortBylineText"))
        val duration = ytText(v.opt("lengthText")) ?: overlayTime(v.opt("thumbnailOverlays"))
        val views = ytText(v.opt("shortViewCountText")) ?: ytText(v.opt("viewCountText"))
        val short = duration == "SHORTS" || overlayStyle(v.opt("thumbnailOverlays")) == "SHORTS"
        return VideoItem(id, title, channel, duration.takeIf { !short }, views, short)
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
        val id = (v.dig("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId") as? String) ?: return null
        val title = ytText(v.dig("overlayMetadata", "primaryText")) ?: return null
        return VideoItem(id, title, null, null, ytText(v.dig("overlayMetadata", "secondaryText")), true)
    }

    private fun lockup(v: JSONObject): VideoItem? {
        if (v.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = v.optString("contentId")
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = ytText(meta.opt("title")) ?: return null
        val rows = meta.dig("metadata", "contentMetadataViewModel", "metadataRows") as? JSONArray
        fun part(row: Int): String? = ytText(rows.dig(row, "metadataParts", 0, "text"))
        var duration: String? = null
        val overlays = v.dig("contentImage", "thumbnailViewModel", "overlays") as? JSONArray
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val badges = overlays.dig(i, "thumbnailBottomOverlayViewModel", "badges") as? JSONArray ?: continue
                duration = ytText(badges.dig(0, "thumbnailBadgeViewModel", "text"))
                if (duration != null) break
            }
        }
        return VideoItem(id, title, part(0), duration, part(1), false)
    }

    private fun tile(v: JSONObject): VideoItem? {
        val id = v.dig("onSelectCommand", "watchEndpoint", "videoId") as? String ?: return null
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
        val duration = overlayTime(v.dig("header", "tileHeaderRenderer", "thumbnailOverlays"))
        return VideoItem(id, title, line(0), duration, line(1), false)
    }
}
