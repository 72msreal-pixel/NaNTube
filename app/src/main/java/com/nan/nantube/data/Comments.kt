package com.nan.nantube.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class CommentItem(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String?,
    /** Số thích khi chưa thích / khi đã thích (YouTube trả sẵn cả hai nên đổi trạng thái là đổi chữ ngay). */
    val likes: String?,
    val likesLiked: String?,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    /** Ảnh kênh chủ video đã thả tim (null = chưa thả). */
    val heartedBy: String? = null,
    /** Số phản hồi ("5") và token mở danh sách phản hồi (null = không có phản hồi). */
    val replies: String?,
    val repliesToken: String?,
    /** 0 = bình luận gốc, 1 = phản hồi, 2 = phản hồi của phản hồi... */
    val level: Int = 0,
    /** Chuỗi hành động của YouTube cho nút thích / bỏ thích / không thích / bỏ không thích (null = không có). */
    val likeAction: String? = null,
    val unlikeAction: String? = null,
    val dislikeAction: String? = null,
    val undislikeAction: String? = null
)

data class CommentSort(val label: String, val token: String, val selected: Boolean)

data class CommentPage(
    val items: List<CommentItem>,
    val token: String?,
    val total: String?,
    val sorts: List<CommentSort> = emptyList()
)

/**
 * Bình luận của video qua endpoint `next`. Đọc không cần đăng nhập; có tài khoản thì gọi kèm tài khoản để YouTube trả
 * trạng thái thích / không thích và các hành động thích. Ghi (đăng, trả lời, thích) theo MediaServiceCore của meetube:
 * create_comment / create_comment_reply / perform_comment_action. Hỗ trợ cả định dạng mới (commentViewModel + commentEntityPayload)
 * lẫn cũ (commentRenderer).
 */
object CommentsRepository {
    private const val SUCCEEDED = "STATUS_SUCCEEDED"

    suspend fun first(videoId: String, account: Account? = null): CommentPage {
        val root = next(JSONObject().put("videoId", videoId), account)
        val token = commentsToken(root) ?: throw IllegalStateException("Video này tắt bình luận hoặc chưa có bình luận.")
        val page = more(token, account)
        val total = ytText(findKey(root, "commentsEntryPointHeaderRenderer")?.opt("commentCount"))
            ?: ytText(findKey(root, "engagementPanelTitleHeaderRenderer")?.opt("contextualInfo"))
        return if (page.total == null && total != null) page.copy(total = total) else page
    }

    /** Trang kế của danh sách bình luận, hoặc danh sách phản hồi (token lấy từ [CommentItem.repliesToken] / "Phản hồi khác"). */
    suspend fun more(token: String, account: Account? = null): CommentPage =
        parse(next(JSONObject().put("continuation", token), account))

    /** Thích / bỏ thích / không thích / bỏ không thích: [action] là chuỗi hành động có sẵn trong [CommentItem]. */
    suspend fun vote(account: Account, action: String) {
        InnerTube.callAny("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)), account)
    }

    suspend fun post(account: Account, videoId: String, text: String): CommentItem {
        val data = JSONObject().put("createCommentParams", createCommentParams(videoId)).put("commentText", text)
        return written(InnerTube.callAny("comment/create_comment", data, account), account, text, 0)
    }

    /** Trả lời luôn gắn vào bình luận gốc của luồng ([parentId]); trả lời một phản hồi thì tự mở đầu bằng @tên người đó. */
    suspend fun reply(account: Account, videoId: String, parentId: String, text: String, level: Int): CommentItem {
        val data = JSONObject().put("createReplyParams", createReplyParams(videoId, parentId)).put("commentText", text)
        return written(InnerTube.callAny("comment/create_comment_reply", data, account), account, text, level)
    }

    /** Có tài khoản thì thử gọi kèm tài khoản (ngữ cảnh WEB); không được thì đọc ẩn danh, chỉ mất phần thích / không thích. */
    private suspend fun next(body: JSONObject, account: Account?): JSONObject {
        if (account != null) {
            try {
                return InnerTube.call("next", body, account, asWeb = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return InnerTube.call("next", body, null)
    }

    private fun commentsToken(root: JSONObject): String? {
        val sections = findAllKeys(root, "itemSectionRenderer").filter { it.optString("sectionIdentifier") == "comment-item-section" } +
            findAllKeys(root, "engagementPanelSectionListRenderer").filter { it.optString("targetId").contains("comments") }
        for (section in sections) {
            findKey(section, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parse(root: JSONObject): CommentPage {
        val entities = entities(root)
        val items = ArrayList<CommentItem>()
        var token: String? = null
        for (el in continuationItems(root)) {
            val more = el.optJSONObject("continuationItemRenderer")
            if (more != null) {
                token = continuationToken(more) ?: token
            } else {
                item(el, entities)?.let { items.add(it) }
            }
        }
        // Định dạng không có danh sách thẻ: lấy theo thứ tự các thực thể (replyLevel cho biết độ sâu).
        if (items.isEmpty()) {
            entities.values.mapNotNullTo(items) { if (it.has("properties")) fromPayload(it, null, null, null, entities) else null }
            findAllKeys(root, "commentRenderer").mapNotNullTo(items) { fromRenderer(it, null) }
        }
        val total = ytText(findKey(root, "commentsHeaderRenderer")?.opt("countText"))
        return CommentPage(items.distinctBy { it.id }, token, total, sorts(root))
    }

    /** Mọi phần tử của các mảng `continuationItems` (trang đầu của bình luận có thêm tiêu đề, trang sau chỉ có thẻ + token). */
    private fun continuationItems(node: Any?, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == "continuationItems" && v is JSONArray) {
                        for (i in 0 until v.length()) v.optJSONObject(i)?.let { out.add(it) }
                    } else {
                        continuationItems(v, out)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) continuationItems(node.opt(i), out)
        }
        return out
    }

    private fun continuationToken(node: JSONObject?): String? =
        findKey(node, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }

    /** Định dạng mới: nội dung nằm ở entityBatchUpdate, thẻ chỉ giữ khóa tham chiếu (khóa → thực thể). */
    private fun entities(root: JSONObject): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        val mutations = root.dig("frameworkUpdates", "entityBatchUpdate", "mutations") as? JSONArray ?: return out
        for (i in 0 until mutations.length()) {
            val m = mutations.optJSONObject(i) ?: continue
            val payload = m.optJSONObject("payload") ?: continue
            val body = payload.keys().asSequence().firstNotNullOfOrNull { payload.optJSONObject(it) } ?: continue
            val key = body.optString("key").ifBlank { m.optString("entityKey") }
            if (key.isNotBlank()) out[key] = body
        }
        return out
    }

    private fun item(el: JSONObject, entities: Map<String, JSONObject>): CommentItem? {
        val thread = el.optJSONObject("commentThreadRenderer")
        val base = thread ?: el
        val repliesToken = continuationToken(thread?.optJSONObject("replies"))
        val outer = base.optJSONObject("commentViewModel")
        val vm = outer?.optJSONObject("commentViewModel") ?: outer
        if (vm != null) {
            val payload = entities[vm.optString("commentKey")]
            if (payload != null) return fromPayload(payload, vm, repliesToken, vm, entities)
        }
        val renderer = base.dig("comment", "commentRenderer") as? JSONObject ?: base.optJSONObject("commentRenderer")
        return renderer?.let { fromRenderer(it, repliesToken) }
    }

    private fun fromPayload(p: JSONObject, vm: JSONObject?, repliesToken: String?, refs: JSONObject?, entities: Map<String, JSONObject>): CommentItem? {
        val props = p.optJSONObject("properties") ?: return null
        val id = props.optString("commentId").ifBlank { return null }
        val author = p.optJSONObject("author")
        val toolbar = p.optJSONObject("toolbar")
        val state = refs?.optString("toolbarStateKey")?.let { entities[it] }
        val likeState = state?.optString("likeState").orEmpty()
        val disliked = likeState.contains("DISLIKED")
        val hearted = state?.optString("heartState").orEmpty().let { it.contains("HEARTED") && !it.contains("UNHEARTED") }
        // Các lệnh thích nằm ở thực thể "toolbar surface" (hoặc ngay trong thẻ), mỗi lệnh có performCommentActionEndpoint.action.
        val surfaces = listOfNotNull("toolbarSurfaceKey", "commentSurfaceKey").mapNotNull { refs?.optString(it)?.let { k -> entities[k] } } + listOfNotNull(vm)
        fun action(command: String) = surfaces.firstNotNullOfOrNull { s ->
            findKey(findKey(s, command), "performCommentActionEndpoint")?.optString("action")?.takeIf { it.isNotBlank() }
        }
        val replyCount = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" }
        return CommentItem(
            id = id,
            author = author?.optString("displayName").orEmpty().ifBlank { "Người dùng" },
            avatar = author?.optString("avatarThumbnailUrl")?.ifBlank { null },
            text = props.optJSONObject("content")?.optString("content").orEmpty(),
            published = props.optString("publishedTime").ifBlank { null },
            likes = toolbar?.optString("likeCountNotliked")?.takeIf { it.isNotBlank() && it != "0" },
            likesLiked = toolbar?.optString("likeCountLiked")?.takeIf { it.isNotBlank() },
            liked = !disliked && likeState.endsWith("LIKED"),
            disliked = disliked,
            heartedBy = if (hearted) toolbar?.optString("creatorThumbnailUrl")?.ifBlank { null } else null,
            replies = if (repliesToken != null) replyCount else null,
            repliesToken = repliesToken,
            level = props.optInt("replyLevel"),
            likeAction = action("likeCommand"),
            unlikeAction = action("unlikeCommand"),
            dislikeAction = action("dislikeCommand"),
            undislikeAction = action("undislikeCommand")
        )
    }

    private fun fromRenderer(r: JSONObject, repliesToken: String?): CommentItem? {
        val id = r.optString("commentId").ifBlank { return null }
        val buttons = r.dig("actionButtons", "commentActionButtonsRenderer") as? JSONObject
        fun toggle(button: String, key: String) =
            (buttons?.dig(button, "toggleButtonRenderer", key, "performCommentActionEndpoint", "action") as? String)?.ifBlank { null }
        val likes = ytText(r.opt("voteCount"))?.takeIf { it != "0" }
        return CommentItem(
            id = id,
            author = ytText(r.opt("authorText")) ?: "Người dùng",
            avatar = (r.dig("authorThumbnail", "thumbnails", 0, "url") as? String),
            text = ytText(r.opt("contentText")).orEmpty(),
            published = ytText(r.opt("publishedTimeText")),
            likes = likes,
            likesLiked = null,
            liked = r.optString("voteStatus") == "LIKE",
            replies = if (repliesToken != null) (r.optString("replyCount").ifBlank { null }) else null,
            repliesToken = repliesToken,
            likeAction = toggle("likeButton", "defaultServiceEndpoint"),
            unlikeAction = toggle("likeButton", "toggledServiceEndpoint"),
            dislikeAction = toggle("dislikeButton", "defaultServiceEndpoint"),
            undislikeAction = toggle("dislikeButton", "toggledServiceEndpoint")
        )
    }

    private fun sorts(root: JSONObject): List<CommentSort> {
        val menu = findKey(root, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems") ?: return emptyList()
        return (0 until menu.length()).mapNotNull { i ->
            val m = menu.optJSONObject(i) ?: return@mapNotNull null
            val token = continuationToken(m.optJSONObject("serviceEndpoint")) ?: return@mapNotNull null
            CommentSort(m.optString("title").ifBlank { return@mapNotNull null }, token, m.optBoolean("selected"))
        }
    }

    // ---- Ghi: đúng các tham số mà MediaServiceCore (CommentsApiParams) dựng --------------------------------

    /** YouTube từ chối thì ném lỗi kèm lời của YouTube; thành công thì trả bình luận vừa đăng (dựng từ chữ + tài khoản, lấy id từ câu trả lời nếu có). */
    private fun written(answer: JSONObject, account: Account, text: String, level: Int): CommentItem {
        val result = findKey(answer, "actionResult")
        val status = result?.optString("status").orEmpty()
        if (status.isNotEmpty() && status != SUCCEEDED) {
            throw IllegalStateException(ytText(result?.opt("feedbackText")) ?: "YouTube không cho đăng bình luận này")
        }
        val id = findAllKeys(answer, "commentRenderer").firstNotNullOfOrNull { it.optString("commentId").ifBlank { null } }
            ?: findAllKeys(answer, "properties").firstNotNullOfOrNull { it.optString("commentId").ifBlank { null } }
            ?: "local-" + System.nanoTime()
        return CommentItem(
            id = id,
            author = account.handle ?: account.name,
            avatar = account.photo,
            text = text,
            published = "vừa xong",
            likes = null,
            likesLiked = "1",
            replies = null,
            repliesToken = null,
            level = level
        )
    }

    /** {2: videoId, 5: {}, 10: 7} */
    private fun createCommentParams(videoId: String): String =
        encode(Proto().string(2, videoId).message(5, Proto()).varint(10, 7))

    /** {2: videoId, 4: id bình luận gốc, 5: {1: 0}, 10: 7} */
    private fun createReplyParams(videoId: String, parentId: String): String =
        encode(Proto().string(2, videoId).string(4, parentId).message(5, Proto().varint(1, 0)).varint(10, 7))

    /** Base64 chuẩn có đệm, rồi mã hoá URL, như client web của YouTube gửi. */
    private fun encode(proto: Proto): String =
        URLEncoder.encode(Base64.encodeToString(proto.bytes.toByteArray(), Base64.NO_WRAP), "UTF-8")

    /** Protobuf vừa đủ: số nguyên, chuỗi và message lồng, theo thứ tự ghi. */
    private class Proto {
        val bytes = ByteArrayOutputStream()

        fun varint(field: Int, value: Int): Proto = apply { tag(field, 0); writeVarint(value.toLong()) }

        fun string(field: Int, value: String): Proto = apply {
            val utf8 = value.toByteArray(Charsets.UTF_8)
            tag(field, 2)
            writeVarint(utf8.size.toLong())
            bytes.write(utf8)
        }

        fun message(field: Int, value: Proto): Proto = apply {
            tag(field, 2)
            writeVarint(value.bytes.size().toLong())
            bytes.write(value.bytes.toByteArray())
        }

        private fun tag(field: Int, wireType: Int) = writeVarint(((field shl 3) or wireType).toLong())

        private fun writeVarint(value: Long) {
            var v = value
            while (v and 0x7F.inv().toLong() != 0L) {
                bytes.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            bytes.write(v.toInt())
        }
    }
}
