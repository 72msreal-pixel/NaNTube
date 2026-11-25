package com.nan.nantube.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

data class MusicItem(
    val title: String,
    val subtitle: String?,
    val art: String?,
    val videoId: String?,
    val playlistId: String?,
    val browseId: String?,
    val browseParams: String?,
    val listRow: Boolean
) {
    val isSong: Boolean get() = videoId != null

    val watchUrl: String?
        get() = videoId?.let { "https://music.youtube.com/watch?v=$it" + (playlistId?.let { list -> "&list=$list" } ?: "") }

    fun asVideo(): VideoItem? = videoId?.let { VideoItem(it, title, subtitle, null, null, false) }
}

data class MusicShelf(val title: String?, val items: List<MusicItem>) {
    val isList: Boolean get() = items.all { it.listRow }
}

object MusicRepository {
    private const val ORIGIN = "https://music.youtube.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private const val VIDEOS_FILTER = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
    private val SHELF_KEYS = listOf(
        "musicCarouselShelfRenderer", "musicImmersiveCarouselShelfRenderer",
        "musicShelfRenderer", "musicPlaylistShelfRenderer"
    )

    suspend fun home(account: Account?): List<MusicShelf> =
        shelves(call("browse", JSONObject().put("browseId", "FEmusic_home"), account))

    /**
     * Tìm trên YouTube Music như app Music: bài hát (kể cả đĩa đơn / bài lẻ không nằm trong album hay danh sách phát nào của
     * nghệ sĩ) + video + kết quả tổng hợp (nghệ sĩ, album, danh sách phát). Trước đây chỉ hỏi bộ lọc "Bài hát" nên bài lẻ,
     * video, album... không bao giờ hiện, và một lần lỗi là mất cả trang.
     */
    suspend fun search(query: String, account: Account?): List<MusicShelf> = coroutineScope {
        suspend fun run(params: String?): List<MusicShelf> = try {
            val body = JSONObject().put("query", query)
            params?.let { body.put("params", it) }
            shelves(call("search", body, account))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val songs = async { run(SONGS_FILTER) }
        val videos = async { run(VIDEOS_FILTER) }
        val all = async { run(null) }
        val songShelves = songs.await()
        val videoShelves = videos.await()
        val allShelves = all.await()
        val seen = HashSet<String>()
        fun List<MusicShelf>.fresh(title: String?, forced: Boolean): List<MusicShelf> {
            val items = flatMap { it.items }.filter { seen.add(it.videoId ?: it.browseId ?: it.title) }
            return if (items.isEmpty()) emptyList() else listOf(MusicShelf(if (forced) title else null, items))
        }
        val out = ArrayList<MusicShelf>()
        // Kết quả hàng đầu của YouTube (nghệ sĩ / bài khớp nhất) đứng đầu như app Music.
        val top = allShelves.firstOrNull { it.title?.let { t -> t.contains("hàng đầu", true) || t.contains("top result", true) } == true }
        top?.let { shelf ->
            val items = shelf.items.filter { seen.add(it.videoId ?: it.browseId ?: it.title) }
            if (items.isNotEmpty()) out.add(MusicShelf(shelf.title, items))
        }
        out += songShelves.fresh("Bài hát", true)
        out += videoShelves.fresh("Video", true)
        // Phần tổng hợp: giữ tiêu đề từng nhóm của YouTube (Nghệ sĩ, Album, Danh sách phát...), bỏ mục đã hiện ở trên.
        for (shelf in allShelves) {
            val items = shelf.items.filter { seen.add(it.videoId ?: it.browseId ?: it.title) }
            if (items.isNotEmpty()) out.add(MusicShelf(shelf.title, items))
        }
        out
    }

    suspend fun open(item: MusicItem, account: Account?): List<MusicShelf> {
        val body = JSONObject().put("browseId", item.browseId)
        item.browseParams?.let { body.put("params", it) }
        return shelves(call("browse", body, account))
    }

    private val VIDEO_TYPE = Regex("\"musicVideoType\"\\s*:\\s*\"(MUSIC_VIDEO_TYPE_[A-Z_]+)\"")

    /**
     * Loại video theo chính YouTube Music (`musicVideoType`): OMV = MV chính thức, ATV = bản âm thanh của bài hát,
     * UGC = video người dùng, PODCAST_EPISODE... Trả "" nếu YouTube Music không coi video này là nhạc, ném lỗi nếu hỏi không được.
     */
    suspend fun videoType(videoId: String, account: Account?): String {
        val json = call("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true), account)
        return VIDEO_TYPE.find(json.toString())?.groupValues?.get(1).orEmpty()
    }

    private suspend fun call(endpoint: String, body: JSONObject, account: Account?): JSONObject {
        val web = account?.takeIf { it.kind == LoginKind.WEB }
        if (web == null) return post(endpoint, body, null)
        // Tài khoản web: trang của tài khoản. Phiên bị từ chối (HTTP 4xx) thì vẫn hiện nhạc như khách thay vì trống trơn.
        return try {
            post(endpoint, body, web)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            post(endpoint, body, null)
        }
    }

    private suspend fun post(endpoint: String, body: JSONObject, web: Account?): JSONObject {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val version = "1.${day.format(Date())}.01.00"
        val client = JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", version)
            .put("hl", "vi").put("gl", "VN")
        val headers = hashMapOf(
            "Origin" to ORIGIN, "X-Origin" to ORIGIN, "Referer" to "$ORIGIN/", "User-Agent" to USER_AGENT,
            "X-Youtube-Client-Name" to "67", "X-Youtube-Client-Version" to version
        )
        if (web != null) headers.putAll(InnerTube.webAuthHeaders(web.authUser, ORIGIN))
        val payload = JSONObject(body.toString()).put("context", JSONObject().put("client", client))
        return Http.postJson("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false", payload, headers).requireJson()
    }

    private fun shelves(response: JSONObject): List<MusicShelf> =
        mutableListOf<MusicShelf>().also { collect(response.opt("contents"), it) }

    private fun collect(node: Any?, out: MutableList<MusicShelf>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
            is JSONObject -> {
                val shelf = SHELF_KEYS.firstNotNullOfOrNull { node.optJSONObject(it) }
                if (shelf != null) {
                    readShelf(shelf)?.let { out.add(it) }
                    return
                }
                // Kết quả tìm kiếm: mỗi kết quả nằm trong một itemSectionRenderer riêng không tiêu đề; một dãy liền nhau là một danh sách.
                val section = node.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                if (section != null && readItem(section.optJSONObject(0)) != null) {
                    val items = (0 until section.length()).mapNotNull { readItem(section.optJSONObject(it)) }
                    val last = out.lastOrNull()
                    if (last != null && last.title == null) out[out.size - 1] = MusicShelf(null, last.items + items)
                    else out.add(MusicShelf(null, items))
                    return
                }
                node.optJSONObject("gridRenderer")?.let { grid ->
                    val items = grid.optJSONArray("items")
                    val list = (0 until (items?.length() ?: 0)).mapNotNull { readItem(items?.optJSONObject(it)) }
                    if (list.isNotEmpty()) out.add(MusicShelf(ytText(findKey(grid.opt("header"), "title")), list))
                    return
                }
                // Kết quả hàng đầu của tìm kiếm (nghệ sĩ / bài hát nổi bật) + các dòng đi kèm.
                node.optJSONObject("musicCardShelfRenderer")?.let { card ->
                    val items = ArrayList<MusicItem>()
                    val endpoint = card.optJSONObject("onTap")
                        ?: ytRunEndpoint(card.opt("title"))
                    build(ytText(card.opt("title")), ytText(card.opt("subtitle")), card, endpoint, true)?.let { items.add(it) }
                    val contents = card.optJSONArray("contents")
                    for (i in 0 until (contents?.length() ?: 0)) readItem(contents?.optJSONObject(i))?.let { items.add(it) }
                    if (items.isNotEmpty()) out.add(MusicShelf(ytText(findKey(card.opt("header"), "title")) ?: "Kết quả hàng đầu", items))
                    return
                }
                for (key in node.keys()) {
                    if (key != "header" && !key.endsWith("HeaderRenderer")) collect(node.opt(key), out)
                }
            }
        }
    }

    private fun ytRunEndpoint(text: Any?): JSONObject? =
        (text as? JSONObject)?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")

    private fun readShelf(shelf: JSONObject): MusicShelf? {
        val title = ytText(shelf.opt("title")) ?: ytText(findKey(shelf.opt("header"), "title"))
        val contents = shelf.optJSONArray("contents") ?: return null
        val items = (0 until contents.length()).mapNotNull { readItem(contents.optJSONObject(it)) }
        return if (items.isEmpty()) null else MusicShelf(title, items)
    }

    private fun readItem(wrapper: JSONObject?): MusicItem? {
        val card = wrapper?.optJSONObject("musicTwoRowItemRenderer")
        val row = wrapper?.optJSONObject("musicResponsiveListItemRenderer")
        val multi = wrapper?.optJSONObject("musicMultiRowListItemRenderer")
        return when {
            card != null -> build(
                ytText(card.opt("title")), ytText(card.opt("subtitle")), card, card.optJSONObject("navigationEndpoint"), false
            )
            row != null -> readRow(row)
            multi != null -> build(
                ytText(multi.opt("title")), ytText(multi.opt("subtitle")), multi, multi.optJSONObject("onTap"), true
            )
            else -> null
        }
    }

    private fun readRow(row: JSONObject): MusicItem? {
        val columns = row.optJSONArray("flexColumns") ?: JSONArray()
        val texts = (0 until columns.length()).map {
            columns.optJSONObject(it).dig("musicResponsiveListItemFlexColumnRenderer", "text") as? JSONObject
        }
        val subtitle = texts.drop(1).mapNotNull { ytText(it) }.joinToString(" • ")
        val playEndpoint = findKey(row, "musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
        val titleEndpoint = texts.firstOrNull()?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
        val endpoint = listOf(playEndpoint, titleEndpoint, row.optJSONObject("navigationEndpoint"))
            .firstOrNull { it?.has("watchEndpoint") == true || it?.has("browseEndpoint") == true }
        val item = build(ytText(texts.firstOrNull()), subtitle, row, endpoint, true)
        if (item != null) return item
        // Dòng không nêu điểm đến nhưng có mã video riêng (playlistItemData): vẫn là bài hát phát được.
        val videoId = row.optJSONObject("playlistItemData")?.optString("videoId")?.ifBlank { null } ?: return null
        val title = ytText(texts.firstOrNull()) ?: return null
        return MusicItem(title, subtitle.ifBlank { null }, art(row), videoId, null, null, null, true)
    }

    private fun build(title: String?, subtitle: String?, renderer: JSONObject, endpoint: JSONObject?, listRow: Boolean): MusicItem? {
        val watch = endpoint?.optJSONObject("watchEndpoint")
        val browse = endpoint?.optJSONObject("browseEndpoint")
        val videoId = watch?.optString("videoId")?.ifBlank { null }
        val browseId = if (videoId == null) browse?.optString("browseId")?.ifBlank { null } else null
        if (title == null || (videoId == null && browseId == null)) return null
        return MusicItem(
            title = title,
            subtitle = subtitle?.ifBlank { null },
            art = art(renderer),
            videoId = videoId,
            playlistId = watch?.optString("playlistId")?.ifBlank { null },
            browseId = browseId,
            browseParams = browse?.optString("params")?.ifBlank { null },
            listRow = listRow
        )
    }

    private fun art(renderer: JSONObject): String? {
        val thumbs = findKey(renderer, "musicThumbnailRenderer").dig("thumbnail", "thumbnails") as? JSONArray ?: return null
        val url = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.ifBlank { null } ?: return null
        return url.replaceFirst(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }
}
