package com.nan.nantube.data

import org.json.JSONArray
import org.json.JSONObject

data class CommentItem(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String?,
    val likes: String?,
    val replies: String?,
    /** Token mở danh sách phản hồi (null = không có phản hồi). */
    val repliesToken: String?,
    val isReply: Boolean = false
)

data class CommentPage(val items: List<CommentItem>, val token: String?, val total: String?)

/**
 * Bình luận của video qua endpoint `next` của client WEB (không cần đăng nhập). Hỗ trợ cả định dạng mới
 * (commentViewModel + commentEntityPayload) lẫn cũ (commentRenderer).
 */
object CommentsRepository {
    suspend fun first(videoId: String): CommentPage {
        val root = InnerTube.call("next", JSONObject().put("videoId", videoId), null)
        val token = commentsToken(root) ?: throw IllegalStateException("Video này tắt bình luận hoặc chưa có bình luận.")
        val page = more(token)
        val total = ytText(findKey(root, "commentsEntryPointHeaderRenderer")?.opt("commentCount"))
            ?: ytText(findKey(root, "engagementPanelTitleHeaderRenderer")?.opt("contextualInfo"))
        return if (page.total == null && total != null) page.copy(total = total) else page
    }

    suspend fun more(token: String): CommentPage =
        parse(InnerTube.call("next", JSONObject().put("continuation", token), null), replies = false)

    suspend fun replies(token: String): CommentPage =
        parse(InnerTube.call("next", JSONObject().put("continuation", token), null), replies = true)

    private fun commentsToken(root: JSONObject): String? {
        val sections = findAllKeys(root, "itemSectionRenderer").filter { it.optString("sectionIdentifier") == "comment-item-section" } +
            findAllKeys(root, "engagementPanelSectionListRenderer").filter { it.optString("targetId").contains("comments") }
        for (section in sections) {
            findKey(section, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parse(root: JSONObject, replies: Boolean): CommentPage {
        // Định dạng mới: nội dung nằm ở entityBatchUpdate, thẻ chỉ giữ khóa tham chiếu.
        val entities = HashMap<String, JSONObject>()
        val order = ArrayList<JSONObject>()
        val mutations = root.dig("frameworkUpdates", "entityBatchUpdate", "mutations") as? JSONArray
        if (mutations != null) {
            for (i in 0 until mutations.length()) {
                val m = mutations.optJSONObject(i) ?: continue
                val payload = m.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
                payload.optString("key").ifBlank { m.optString("entityKey") }.takeIf { it.isNotBlank() }?.let { entities[it] = payload }
                order.add(payload)
            }
        }

        val items = ArrayList<CommentItem>()
        val threads = findAllKeys(root, "commentThreadRenderer")
        if (!replies && threads.isNotEmpty()) {
            for (t in threads) {
                val key = t.dig("commentViewModel", "commentViewModel", "commentKey") as? String
                val payload = key?.let { entities[it] }
                val repliesToken = findKey(t.optJSONObject("replies"), "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
                val item = if (payload != null) fromPayload(payload, repliesToken, false)
                else (t.dig("comment", "commentRenderer") as? JSONObject)?.let { fromRenderer(it, repliesToken, false) }
                if (item != null) items.add(item)
            }
        } else if (order.isNotEmpty()) {
            order.filter { replies == ((it.optJSONObject("properties")?.optInt("replyLevel") ?: 0) > 0) }
                .mapNotNullTo(items) { fromPayload(it, null, replies) }
        } else {
            findAllKeys(root, "commentRenderer").mapNotNullTo(items) { fromRenderer(it, null, replies) }
        }

        val total = ytText(findKey(root, "commentsHeaderRenderer")?.opt("countText"))
        return CommentPage(items.distinctBy { it.id }, nextToken(root), total)
    }

    /** Token trang kế = continuationItemRenderer nằm trực tiếp trong mảng continuationItems (không phải của phản hồi). */
    private fun nextToken(root: Any?): String? {
        when (root) {
            is JSONObject -> {
                val it = root.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = root.opt(k)
                    if (k == "continuationItems" && v is JSONArray) {
                        for (i in v.length() - 1 downTo 0) {
                            val token = v.optJSONObject(i)?.dig("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token") as? String
                            if (!token.isNullOrBlank()) return token
                        }
                    } else {
                        nextToken(v)?.let { r -> return r }
                    }
                }
            }
            is JSONArray -> for (i in 0 until root.length()) nextToken(root.opt(i))?.let { return it }
        }
        return null
    }

    private fun fromPayload(p: JSONObject, repliesToken: String?, isReply: Boolean): CommentItem? {
        val props = p.optJSONObject("properties") ?: return null
        val text = props.optJSONObject("content")?.optString("content").orEmpty()
        val id = props.optString("commentId").ifBlank { return null }
        val author = p.optJSONObject("author")
        val toolbar = p.optJSONObject("toolbar")
        val replyCount = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" }
        return CommentItem(
            id = id,
            author = author?.optString("displayName").orEmpty().ifBlank { "Người dùng" },
            avatar = author?.optString("avatarThumbnailUrl")?.ifBlank { null },
            text = text,
            published = props.optString("publishedTime").ifBlank { null },
            likes = toolbar?.optString("likeCountNotliked")?.takeIf { it.isNotBlank() && it != "0" },
            replies = if (repliesToken != null) replyCount else null,
            repliesToken = repliesToken,
            isReply = isReply
        )
    }

    private fun fromRenderer(r: JSONObject, repliesToken: String?, isReply: Boolean): CommentItem? {
        val id = r.optString("commentId").ifBlank { return null }
        return CommentItem(
            id = id,
            author = ytText(r.opt("authorText")) ?: "Người dùng",
            avatar = (r.dig("authorThumbnail", "thumbnails", 0, "url") as? String),
            text = ytText(r.opt("contentText")).orEmpty(),
            published = ytText(r.opt("publishedTimeText")),
            likes = ytText(r.opt("voteCount"))?.takeIf { it != "0" },
            replies = if (repliesToken != null) (r.optString("replyCount").ifBlank { null }) else null,
            repliesToken = repliesToken,
            isReply = isReply
        )
    }
}
