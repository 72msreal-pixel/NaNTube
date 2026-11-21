package com.nan.nantube.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

    fun asVideo(): VideoItem? = videoId?.let { VideoItem(it, title, subtitle, null, null, false, musicTrack = true, artwork = art) }
}

data class MusicShelf(val title: String?, val items: List<MusicItem>) {
    val isList: Boolean get() = items.all { it.listRow }
}

object MusicRepository {
    private const val ORIGIN = "https://music.youtube.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private val SHELF_KEYS = listOf(
        "musicCarouselShelfRenderer", "musicImmersiveCarouselShelfRenderer",
        "musicShelfRenderer", "musicPlaylistShelfRenderer"
    )

    suspend fun home(account: Account?): List<MusicShelf> =
        shelves(call("browse", JSONObject().put("browseId", "FEmusic_home"), account))

    suspend fun search(query: String, account: Account?): List<MusicShelf> =
        shelves(call("search", JSONObject().put("query", query).put("params", SONGS_FILTER), account))

    suspend fun open(item: MusicItem, account: Account?): List<MusicShelf> {
        val body = JSONObject().put("browseId", item.browseId)
        item.browseParams?.let { body.put("params", it) }
        return shelves(call("browse", body, account))
    }

    private suspend fun call(endpoint: String, body: JSONObject, account: Account?): JSONObject {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val version = "1.${day.format(Date())}.01.00"
        val client = JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", version)
            .put("hl", "vi").put("gl", "VN")
        val headers = hashMapOf(
            "Origin" to ORIGIN, "Referer" to "$ORIGIN/", "User-Agent" to USER_AGENT,
            "X-Youtube-Client-Name" to "67", "X-Youtube-Client-Version" to version
        )
        if (account?.kind == LoginKind.WEB) headers.putAll(InnerTube.webAuthHeaders(account.authUser, ORIGIN))
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
                for (key in node.keys()) {
                    if (key != "header" && !key.endsWith("HeaderRenderer")) collect(node.opt(key), out)
                }
            }
        }
    }

    private fun readShelf(shelf: JSONObject): MusicShelf? {
        val title = ytText(shelf.opt("title")) ?: ytText(findKey(shelf.opt("header"), "title"))
        val contents = shelf.optJSONArray("contents") ?: return null
        val items = (0 until contents.length()).mapNotNull { readItem(contents.optJSONObject(it)) }
        return if (items.isEmpty()) null else MusicShelf(title, items)
    }

    private fun readItem(wrapper: JSONObject?): MusicItem? {
        val card = wrapper?.optJSONObject("musicTwoRowItemRenderer")
        val row = wrapper?.optJSONObject("musicResponsiveListItemRenderer")
        return when {
            card != null -> build(
                ytText(card.opt("title")), ytText(card.opt("subtitle")), card, card.optJSONObject("navigationEndpoint"), false
            )
            row != null -> readRow(row)
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
        return build(ytText(texts.firstOrNull()), subtitle, row, endpoint, true)
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
