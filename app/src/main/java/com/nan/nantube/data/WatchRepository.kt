package com.nan.nantube.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class WatchSource(val video: Uri, val audio: Uri? = null, val type: String? = null)
internal data class WatchDetails(
    val title: String? = null, val channel: String? = null, val channelId: String? = null,
    val description: String? = null, val views: String? = null, val date: String? = null,
    val rating: String? = null, val subscribed: Boolean? = null, val subscribeParams: String? = null,
    val comments: String? = null, val commentsAsWeb: Boolean = false, val suggested: List<VideoItem> = emptyList()
)
internal data class WatchComment(val id: String, val author: String, val text: String, val date: String?, val replies: String?)
internal data class WatchComments(val items: List<WatchComment>, val next: String?)

// Contracts cross-checked against MeeTube's PhoneSourcePlanner and MediaServiceCore's
// WatchNextHelper, ActionsApiHelper and comments models. No MeeTube UI/modules are linked.
internal object WatchRepository {
    private fun JSONObject?.str(key: String) = (this?.opt(key) as? String)?.takeIf { it.isNotBlank() }
    private fun mediaUrl(raw: String?): Uri? = raw?.let(Uri::parse)?.takeIf {
        val host = it.host.orEmpty().lowercase()
        it.scheme == "https" && (host == "googlevideo.com" || host.endsWith(".googlevideo.com"))
    }
    private fun target(id: String) = JSONObject().put("videoId", id)

    suspend fun sources(video: VideoItem, account: Account?, client: Int): List<WatchSource> = withContext(Dispatchers.IO) {
        video.offlineUri?.let { return@withContext listOf(WatchSource(Uri.parse(it))) }
        require(video.id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "ID video không hợp lệ" }
        val response = when (client) {
            0 -> InnerTube.directPlayer(video.id, vision = true)
            1 -> InnerTube.directPlayer(video.id, vision = false)
            else -> InnerTube.call("player", target(video.id), account)
        }
        val status = response.optJSONObject("playabilityStatus")
        check(status.str("status") == "OK") { status.str("reason") ?: "Video không khả dụng" }
        val data = response.optJSONObject("streamingData") ?: error("Không có dữ liệu phát")
        fun formats(name: String) = data.optJSONArray(name).objects().filter { mediaUrl(it.str("url")) != null }
        val adaptive = formats("adaptiveFormats")
        val audio = adaptive.filter { it.optString("mimeType").startsWith("audio/mp4") && !it.has("audioTrack") }
            .maxByOrNull { it.optInt("bitrate") }
            ?: adaptive.filter { it.optString("mimeType").startsWith("audio/mp4") }
                .maxWithOrNull(compareBy<JSONObject> { it.optJSONObject("audioTrack")?.optBoolean("audioIsDefault") == true }.thenBy { it.optInt("bitrate") })
        val videoFormat = adaptive.filter { it.optString("mimeType").startsWith("video/mp4") && "avc1" in it.optString("mimeType") && it.optInt("height") <= 1080 }
            .maxByOrNull { it.optInt("height") }
        val muxed = formats("formats").filter { it.optString("mimeType").startsWith("video/mp4") }.maxByOrNull { it.optInt("height") }
        val audioUri = mediaUrl(audio.str("url"))
        val videoUri = mediaUrl(videoFormat.str("url"))
        buildList {
            mediaUrl(data.str("hlsManifestUrl"))?.let { add(WatchSource(it, type = "application/x-mpegURL")) }
            if (video.musicTrack && audioUri != null) add(WatchSource(audioUri))
            if (!video.musicTrack && videoUri != null && audioUri != null) add(WatchSource(videoUri, audioUri))
            mediaUrl(muxed.str("url"))?.let { add(WatchSource(it)) }
            mediaUrl(data.str("dashManifestUrl"))?.let { add(WatchSource(it, type = "application/dash+xml")) }
        }.also { check(it.isNotEmpty()) { "Không có luồng trực tiếp tương thích" } }
    }

    private fun token(node: Any?): String? = findKey(node, "continuationCommand").str("token")
        ?: findKey(node, "nextContinuationData").str("continuation")
        ?: findKey(node, "reloadContinuationData").str("continuation")

    suspend fun details(video: VideoItem, account: Account?): WatchDetails = withContext(Dispatchers.IO) {
        val body = target(video.id)
        val json = InnerTube.call("next", body, account)
        val info = parseDetails(json, video.id)
        if (account?.kind != LoginKind.CODE) return@withContext info
        // WEB often exposes modern comment tokens; preserve TV metadata and route this token separately.
        val web = try { parseDetails(InnerTube.call("next", body, null, asWeb = true), video.id) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        info.copy(description = info.description ?: web?.description,
            comments = web?.comments ?: info.comments, commentsAsWeb = web?.comments != null)
    }

    internal fun parseDetails(json: JSONObject, id: String): WatchDetails {
        val primary = findKey(json, "videoPrimaryInfoRenderer") ?: findKey(json, "videoMetadataRenderer")
        val secondary = findKey(json, "videoSecondaryInfoRenderer")
        val owner = findKey(secondary ?: json, "videoOwnerRenderer") ?: findKey(json, "videoOwner")
        val detailsPanel = findKey(json, "videoDescriptionHeaderRenderer")
        val sub = findKey(owner ?: json, "subscribeButtonRenderer")
        val channel = ((owner?.let { findKey(it, "browseEndpoint").str("browseId") })
            ?: sub.str("channelId") ?: findKey(detailsPanel?.opt("channelNavigationEndpoint"), "browseEndpoint").str("browseId"))
            ?.takeIf { it.startsWith("UC") }
        val commentSection = findAllKeys(json, "itemSectionRenderer").firstOrNull { it.str("sectionIdentifier") == "comment-item-section" || it.str("targetId") == "comments-section" }
        val commentPanel = findAllKeys(json, "engagementPanelSectionListRenderer").firstOrNull {
            listOf(it.str("panelIdentifier"), it.str("targetId")).any { key -> key?.contains("comment", ignoreCase = true) == true }
        }
        val rating = (findKey(primary ?: json, "likeButtonRenderer").str("likeStatus") ?: primary.str("likeStatus")
            ?: findKey(json, "likeStatusEntityPayload").str("likeStatus"))?.removePrefix("LIKE_STATUS_")
            ?: when {
                findKey(primary, "segmentedLikeDislikeButtonRenderer").dig("likeButton", "toggleButtonRenderer", "isToggled") == true -> "LIKE"
                findKey(primary, "segmentedLikeDislikeButtonRenderer").dig("dislikeButton", "toggleButtonRenderer", "isToggled") == true -> "DISLIKE"
                else -> null
            }
        val suggested = (findKey(json, "secondaryResults") ?: findKey(json, "watchNextSecondaryResults"))?.let { VideoRepository.parse(it) }
            ?: findAllKeys(json, "itemSectionRenderer").filter { it.str("targetId") == "watch-next-feed" }.flatMap { VideoRepository.parse(it) }
        return WatchDetails(
            title = ytText(primary?.opt("title")) ?: ytText(detailsPanel?.opt("title")),
            channel = ytText(owner?.opt("title")) ?: ytText(detailsPanel?.opt("channel")), channelId = channel,
            description = ytText(findKey(json, "expandableVideoDescriptionBodyRenderer")?.opt("descriptionBodyText"))
                ?: ytText(secondary?.opt("attributedDescription")) ?: ytText(secondary?.opt("description"))
                ?: ytText(primary?.opt("description")),
            views = ytText(findKey(primary, "videoViewCountRenderer")?.opt("viewCount")) ?: ytText(detailsPanel?.opt("views")),
            date = ytText(primary?.opt("dateText")) ?: ytText(detailsPanel?.opt("publishDate")),
            rating = rating,
            subscribed = (sub?.opt("subscribed") ?: owner?.opt("subscribed") ?: findKey(owner, "subscriptionButton")?.opt("subscribed")) as? Boolean,
            subscribeParams = findKey(sub ?: owner, "subscribeEndpoint").str("params"),
            comments = token(commentSection) ?: token(commentPanel),
            suggested = suggested.filter { it.id != id }.distinctBy { it.id }
        )
    }

    suspend fun playerDetails(video: VideoItem, account: Account?): WatchDetails {
        val response = InnerTube.call("player", target(video.id), account)
        val d = response.optJSONObject("videoDetails")
        return WatchDetails(title = d.str("title"), channel = d.str("author"), channelId = d.str("channelId"), description = d.str("shortDescription"))
    }

    suspend fun rate(video: VideoItem, account: Account, rating: String) {
        val endpoint = when (rating) { "LIKE" -> "like"; "DISLIKE" -> "dislike"; else -> "removelike" }
        checkAction(InnerTube.callAny("like/$endpoint", JSONObject().put("target", target(video.id)), account))
    }

    suspend fun subscribe(channel: String, account: Account, enabled: Boolean, params: String?) {
        val body = JSONObject().put("channelIds", JSONArray().put(channel))
        if (enabled && params != null) body.put("params", params)
        val action = if (enabled) "subscribe" else "unsubscribe"
        checkAction(InnerTube.callAny("subscription/$action", body, account))
    }

    private fun checkAction(response: JSONObject) {
        val status = response.str("status")
        check(!response.has("error") && (status == null || status == "STATUS_SUCCEEDED" || status == "SUCCESS" || status == "OK")) {
            "YouTube chưa xác nhận thao tác"
        }
    }

    suspend fun comments(key: String, account: Account?, asWeb: Boolean): WatchComments = withContext(Dispatchers.IO) {
        val body = JSONObject().put("continuation", key)
        parseComments(InnerTube.call("next", body, account.takeUnless { asWeb && it?.kind == LoginKind.CODE }, asWeb = asWeb))
    }

    internal fun parseComments(json: JSONObject): WatchComments {
        val entities = findAllKeys(json, "commentEntityPayload").mapNotNull { e -> e.str("key")?.let { it to e } }.toMap()
        val rows = listOf("onResponseReceivedEndpoints", "onResponseReceivedActions", "onResponseReceivedCommands")
            .flatMap { json.optJSONArray(it).objects() }.flatMap { action ->
                (action.optJSONObject("reloadContinuationItemsCommand") ?: action.optJSONObject("appendContinuationItemsAction"))
                    ?.optJSONArray("continuationItems").objects()
            }.ifEmpty { findKey(json, "itemSectionContinuation")?.optJSONArray("contents").objects() }
        var next: String? = null
        val result = rows.mapNotNull { row ->
            val thread = row.optJSONObject("commentThreadRenderer")
            if (row.has("continuationItemRenderer")) next = token(row) ?: next
            val container = thread?.opt("comment") ?: thread?.opt("commentViewModel") ?: row
            val vm = findKey(container, "commentViewModel")
            val entity = entities[vm.str("commentKey")]
            val props = entity?.optJSONObject("properties")
            val old = findKey(container, "commentRenderer")
            val id = props.str("commentId") ?: old.str("commentId") ?: return@mapNotNull null
            val text = ytText(props?.opt("content")) ?: ytText(old?.opt("contentText")) ?: return@mapNotNull null
            WatchComment(id, entity?.optJSONObject("author").str("displayName") ?: ytText(old?.opt("authorText")) ?: "Người dùng",
                text, props.str("publishedTime") ?: ytText(old?.opt("publishedTimeText")), token(findKey(thread, "commentRepliesRenderer")))
        }
        return WatchComments(result.distinctBy { it.id }, next ?: token(findKey(json, "itemSectionContinuation")?.opt("continuations")))
    }
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
