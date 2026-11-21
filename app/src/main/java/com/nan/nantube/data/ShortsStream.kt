package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** Adapter for the seedless reel/sequence flow used by MeeTube's browse service. */
internal class ShortsStream {
    private var cursor: String? = null
    private var revision = 0
    private var loaded = emptySet<String>()
    private var loadingMore = false

    suspend fun start(account: Account?): List<VideoItem> {
        val version = ++revision
        cursor = null
        loaded = emptySet()
        val seed = optional { web("reel_item_watch", JSONObject().put("disablePlayerResponse", true)
            .put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS").put("params", "CA8%3D"), account) }
        val first = seed?.let { read(it, null) }
        val next = seed?.optString("sequenceContinuation")?.takeIf { it.isNotBlank() } ?: continuation(seed)
        val reel = next?.let { key -> optional { page(key, account) } }
        val items = (listOfNotNull(first) + reel?.items.orEmpty()).distinctBy { it.id }
            .ifEmpty { VideoRepository.shorts(account) }
        if (version == revision) {
            cursor = reel?.continuation ?: next?.takeUnless { reel != null }
            loaded = items.mapTo(HashSet()) { it.id }
        }
        return items
    }

    suspend fun more(account: Account?): List<VideoItem> {
        val key = cursor ?: return emptyList()
        if (loadingMore) return emptyList()
        val version = revision
        loadingMore = true
        try {
            val batch = page(key, account)
            if (version != revision) return emptyList()
            cursor = batch.continuation?.takeUnless { it == key }
            return batch.items.filterNot { it.id in loaded }.also { result -> loaded = loaded + result.map { it.id } }
        } finally { loadingMore = false }
    }

    private suspend fun page(key: String, account: Account?): VideoPage = coroutineScope {
        val root = web("reel_watch_sequence", JSONObject().put("sequenceParams", key), account)
        val rows = root.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until rows.length()).mapNotNull { i -> findKey(rows.opt(i), "reelWatchEndpoint") }
            .filter { it.dig("adClientParams", "isAd") != true }
        // Limit concurrent detail lookups; the sequence carries IDs/thumbnails but often not titles.
        val videos = entries.chunked(4).flatMap { group ->
            group.map { item -> async {
                val id = item.optString("videoId").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return@async null
                val params = item.optString("params")
                val details = if (params.isBlank()) null else optional { web("reel_item_watch",
                    JSONObject().put("disablePlayerResponse", true).put("params", params)
                        .put("playerRequest", JSONObject().put("videoId", id)), account) }
                if (details == null) VideoItem(id, "Shorts", null, null, null, true) else read(details, id)
            } }.awaitAll().filterNotNull()
        }
        VideoPage(videos.distinctBy { it.id }, root.optString("continuation").takeIf { it.isNotBlank() } ?: continuation(root))
    }

    private fun read(json: JSONObject, id: String?): VideoItem? {
        val endpoint = json.dig("replacementEndpoint", "reelWatchEndpoint") as? JSONObject
        if (endpoint.dig("adClientParams", "isAd") == true) return null
        val videoId = (id ?: endpoint?.optString("videoId"))?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
        val header = findKey(json, "reelPlayerHeaderRenderer")
        val channel = ytText(header?.opt("channelTitleText"))
        if (channel?.startsWith("Video ad upload channel") == true) return null
        val title = ytText(header?.opt("reelTitleText")) ?: ytText(findKey(json, "videoDescriptionHeaderRenderer")?.opt("title")) ?: "Shorts"
        return VideoItem(videoId, title, channel, null, null, true)
    }

    private fun continuation(json: JSONObject?): String? =
        (json.dig("continuationEndpoint", "continuationCommand", "token") as? String)?.takeIf { it.isNotBlank() }

    private suspend fun web(endpoint: String, body: JSONObject, account: Account?): JSONObject =
        InnerTube.call("reel/$endpoint", body, account.takeUnless { it?.kind == LoginKind.CODE }, asWeb = true)

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
