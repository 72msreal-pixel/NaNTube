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
    /** Nhãn ghim của YouTube ("Được ghim bởi @kênh" / "@kênh đã ghim"); null = không ghim. Chỉ bình luận của chủ video mới ghim được. */
    val pinned: String? = null,
    /** YouTube báo thẳng người viết là chủ kênh của video (định dạng mới có isCreator). */
    val creator: Boolean = false,
    /** Mã kênh (UC…) của người viết, để so với mã kênh chủ video (xem [VideoOwner]). */
    val authorChannelId: String? = null,
    /** Người viết có dấu tích xác minh (kênh xác minh / nghệ sĩ chính thức) theo YouTube. */
    val verified: Boolean = false,
    /** Số phản hồi ("5") và token mở danh sách phản hồi (null = không có phản hồi). */
    val replies: String?,
    val repliesToken: String?,
    /** 0 = bình luận gốc, 1 = phản hồi, 2 = phản hồi của phản hồi... */
    val level: Int = 0,
    /** Chuỗi hành động của YouTube cho nút thích / bỏ thích / không thích / bỏ không thích (null = không có). */
    val likeAction: String? = null,
    val unlikeAction: String? = null,
    val dislikeAction: String? = null,
    val undislikeAction: String? = null,
    val detailToken: String? = null,
    val editCommand: CommentEditCommand? = null,
    val deleteAction: String? = null,
    val own: Boolean = false,
    val fromTv: Boolean = false
) {
    fun isOwnedBy(account: Account?): Boolean {
        if (account == null || id.startsWith("local-")) return false
        val handle = account.handle?.trim()?.trimStart('@').orEmpty()
        return own || (handle.isNotEmpty() && author.startsWith('@') && author.drop(1).equals(handle, ignoreCase = true))
    }
}

/** Chủ video: mã kênh (UC…) và các handle (@…) đã biết; người viết là chủ video nếu trùng mã kênh hoặc trùng handle ĐÚNG TỪNG KÝ TỰ. */
class OwnerRef(val id: String?, val handles: Set<String>) {
    fun matches(c: CommentItem): Boolean =
        (id != null && c.authorChannelId == id) || (c.author.startsWith("@") && c.author in handles)
}

/** Chủ video đọc từ videoOwnerRenderer trong `next` của chính video (mã kênh + handle từ canonicalBaseUrl); nhớ lại theo video. */
object VideoOwner {
    class Found(val id: String?, val handle: String?)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Found>()

    suspend fun find(videoId: String, account: Account?): Found? {
        cache[videoId]?.let { return it }
        return try {
            val json = InnerTube.callAny("next", JSONObject().put("videoId", videoId), account)
            val owner = findKey(json, "videoOwnerRenderer")
            val endpoint = owner.dig("navigationEndpoint", "browseEndpoint") as? JSONObject
                ?: owner.dig("title", "runs", 0, "navigationEndpoint", "browseEndpoint") as? JSONObject
            val id = endpoint?.optString("browseId")?.takeIf { it.startsWith("UC") }
            val handle = endpoint?.optString("canonicalBaseUrl")?.trimStart('/')?.takeIf { it.startsWith("@") }
            if (id == null && handle == null) null else Found(id, handle).also { cache[videoId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

/** "@handle" trong nhãn ghim, dù nhãn viết kiểu "Pinned by @handle" hay "@handle đã ghim". */
fun pinnedHandle(label: String?): String? = label?.let { Regex("@[\\p{L}\\p{N}._-]+").find(it)?.value }

/** Endpoint và tham số chỉnh sửa do chính YouTube cấp cho bình luận của tài khoản. */
data class CommentEditCommand(val path: String, val payload: String, val textField: String)

data class CommentVoteResult(val liked: Boolean, val disliked: Boolean, val count: String?)

data class CommentSort(val label: String, val token: String, val selected: Boolean)

data class CommentPage(
    val items: List<CommentItem>,
    val token: String?,
    val total: String?,
    val sorts: List<CommentSort> = emptyList(),
    val fromTv: Boolean = false
)

/**
 * Bình luận của video qua endpoint `next`. Đọc không cần đăng nhập; có tài khoản thì gọi kèm tài khoản để YouTube trả
 * trạng thái thích / không thích và các hành động thích. Ghi (đăng, trả lời, thích) theo MediaServiceCore của meetube:
 * create_comment / create_comment_reply / perform_comment_action. Hỗ trợ cả định dạng mới (commentViewModel + commentEntityPayload)
 * lẫn cũ (commentRenderer).
 */
object CommentsRepository {
    private const val SUCCEEDED = "STATUS_SUCCEEDED"

    private data class Read(val root: JSONObject, val tv: Boolean = false, val signed: Boolean = false)
    private data class Manage(val edit: CommentEditCommand?, val delete: String?)
    private fun JSONObject?.field(name: String): String? = (this?.opt(name) as? String)?.takeIf { it.isNotBlank() }

    suspend fun first(videoId: String, account: Account? = null): CommentPage {
        val body = JSONObject().put("videoId", videoId)
        suspend fun start(read: Read): CommentPage? {
            val token = commentsToken(read.root) ?: return null
            val page = more(token, account, read.tv)
            val total = ytText(findKey(read.root, "commentsEntryPointHeaderRenderer")?.opt("commentCount"))
                ?: ytText(findKey(read.root, "engagementPanelTitleHeaderRenderer")?.opt("contextualInfo"))
            // Danh sách Nổi bật / Mới nhất nằm ở tiêu đề bảng bình luận của phản hồi đầu, không có trong trang bình luận.
            // Như MeeTube: token Top / Newest lấy từ bảng bình luận của phản hồi /next, trang bình luận chỉ là phương án dự phòng.
            return page.copy(total = page.total ?: total, sorts = sorts(read.root).ifEmpty { page.sorts })
        }
        if (account?.kind == LoginKind.CODE) {
            // Như MeeTube: lấy continuation và hành động trong cùng ngữ cảnh TV khi đăng nhập bằng mã.
            tryRead(body, account, tv = true)?.takeIf { it.signed }?.let { read ->
                val page = try { start(read) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (page != null && page.items.isNotEmpty()) return page
            }
        }
        return start(next(body, account, tv = false))
            ?: throw IllegalStateException("Video này tắt bình luận hoặc chưa có bình luận.")
    }

    /** Chỉ gọi khi người dùng mở "Xem phản hồi" / "Phản hồi khác". */
    suspend fun more(token: String, account: Account? = null, tv: Boolean = false, debugOf: String? = null): CommentPage {
        val read = next(JSONObject().put("continuation", token), account, tv)
        val page = parse(read)
        if (debugOf != null) saveRepliesDebug(account, debugOf, read.root, page)
        return page
    }

    /**
     * Gỡ lỗi nhánh phản hồi: ghi lại dữ liệu thô YouTube trả (5 lần mở phản hồi gần nhất, [opened] = bình luận được mở) cùng bản tóm tắt đã đọc được
     * (id, cấp, tác giả, có token con không) vào tệp debug_replies.json trong thư mục dữ liệu NaNTube.
     */
    private fun saveRepliesDebug(account: Account?, opened: String, raw: JSONObject, page: CommentPage) {
        runCatching {
            val summary = JSONArray()
            for (it in page.items) summary.put(
                JSONObject().put("id", it.id).put("level", it.level).put("author", it.author)
                    .put("hasRepliesToken", it.repliesToken != null).put("replies", it.replies ?: JSONObject.NULL)
                    .put("text", it.text.take(60))
            )
            val entry = JSONObject().put("time", System.currentTimeMillis()).put("opened", opened).put("summary", summary).put("raw", raw)
            val old = NanStorage.readJson("debug_replies", account) as? JSONArray ?: JSONArray()
            val keep = JSONArray()
            for (i in maxOf(0, old.length() - 4) until old.length()) keep.put(old.get(i))
            keep.put(entry)
            NanStorage.writeJson("debug_replies", account, keep)
        }
    }

    suspend fun vote(account: Account, videoId: String, item: CommentItem, rootId: String, liked: Boolean, disliked: Boolean, currentCount: String?, like: Boolean): CommentVoteResult {
        val turnOn = if (like) !liked else !disliked
        fun action(c: CommentItem) = when {
            like && turnOn -> c.likeAction
            like -> c.unlikeAction
            turnOn -> c.dislikeAction
            else -> c.undislikeAction
        }
        var source = item
        var key = action(item)
        if (key == null) {
            source = resolve(account, videoId, item, rootId) { action(it) != null }
                ?: throw IllegalStateException("YouTube chưa cấp thao tác này cho tài khoản. Thử đăng nhập lại rồi mở lại bình luận.")
            key = action(source)
        }
        val answer = perform(account, requireNotNull(key), source.fromTv)
        checkResult(answer, "YouTube từ chối thao tác đánh giá")
        val newLiked = like && turnOn
        val newDisliked = !like && turnOn
        val sourceLiked = if (source === item) liked else source.liked
        val originalCount = if (source === item) currentCount
            else if (source.liked) source.likesLiked ?: source.likes else source.likes
        val count = when {
            newLiked && source.likesLiked != null -> source.likesLiked.takeUnless { it == "0" }
            !newLiked && source.likesLiked != null -> source.likes
            newLiked == sourceLiked -> originalCount
            originalCount == null || originalCount.toIntOrNull() != null ->
                ((originalCount?.toIntOrNull() ?: 0) + if (newLiked) 1 else -1).takeIf { it > 0 }?.toString()
            else -> originalCount
        }
        return CommentVoteResult(newLiked, newDisliked, count)
    }

    suspend fun edit(account: Account, videoId: String, item: CommentItem, rootId: String, text: String): String {
        require(item.isOwnedBy(account))
        val source = if (item.editCommand != null) item else resolve(account, videoId, item, rootId, webFirst = true) {
            it.editCommand != null
        } ?: throw IllegalStateException("YouTube không cấp lệnh chỉnh sửa cho phiên này. Nếu đăng nhập bằng mã, hãy thử tài khoản đăng nhập Web.")
        val command = requireNotNull(source.editCommand)
        val body = JSONObject(command.payload).put(command.textField, text)
        val answer = InnerTube.commentRequest(command.path, body, account, source.fromTv)
        val status = checkResult(answer, "YouTube từ chối chỉnh sửa bình luận")
        val updated = parse(Read(answer, source.fromTv, true)).items.firstOrNull { sameId(it.id, item.id) }
            ?: findAllKeys(answer, "commentRenderer").mapNotNull { fromRenderer(it, null, Read(answer, source.fromTv, true)) }
                .firstOrNull { sameId(it.id, item.id) }
        if (status != SUCCEEDED && updated == null) {
            val reread = resolve(account, videoId, item, rootId, webFirst = !source.fromTv) { it.text == text }
            if (reread == null) throw IllegalStateException("Chưa xác nhận được thay đổi trên YouTube. Mở lại bình luận để kiểm tra.")
        }
        return updated?.text?.takeIf { it.isNotBlank() } ?: text
    }

    suspend fun delete(account: Account, videoId: String, item: CommentItem, rootId: String) {
        require(item.isOwnedBy(account))
        val tv = if (item.deleteAction != null) item.fromTv else account.kind == LoginKind.CODE
        val commentId = if (item.id == rootId || '.' in item.id) item.id else "$rootId.${item.id}"
        val key = item.deleteAction ?: encode(Proto().varint(1, 6).varint(2, 2).string(3, commentId).string(5, videoId))
        val answer = perform(account, key, tv)
        val remove = findKey(answer, "removeCommentAction")
        val result = remove?.optJSONObject("actionResult") ?: findKey(answer, "actionResult")
        if (checkResult(answer, "YouTube từ chối xóa bình luận", result) != SUCCEEDED) {
            throw IllegalStateException("YouTube chưa xác nhận đã xóa bình luận.")
        }
        val removedId = remove.field("commentId")
        if (removedId != null && !sameId(removedId, commentId)) throw IllegalStateException("YouTube trả về bình luận khác.")
    }

    private suspend fun perform(account: Account, action: String, tv: Boolean): JSONObject =
        InnerTube.commentRequest("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)), account, tv)

    suspend fun post(account: Account, videoId: String, text: String): CommentItem {
        val data = JSONObject().put("createCommentParams", createCommentParams(videoId)).put("commentText", text)
        val tv = account.kind == LoginKind.CODE
        return written(InnerTube.commentRequest("comment/create_comment", data, account, tv), account, text, 0, tv)
    }

    /** Trả lời luôn gắn vào bình luận gốc của luồng ([parentId]). */
    suspend fun reply(account: Account, videoId: String, parentId: String, text: String, level: Int): CommentItem {
        val data = JSONObject().put("createReplyParams", createReplyParams(videoId, parentId)).put("commentText", text)
        val tv = account.kind == LoginKind.CODE
        return written(InnerTube.commentRequest("comment/create_comment_reply", data, account, tv), account, text, level, tv)
    }

    private suspend fun tryRead(body: JSONObject, account: Account, tv: Boolean): Read? = try {
        val root = InnerTube.commentRequest("next", body, account, tv)
        val signed = root.dig("responseContext", "mainAppWebResponseContext", "loggedOut") != true
        Read(root, tv, signed)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun next(body: JSONObject, account: Account?, tv: Boolean): Read {
        if (tv && account != null) {
            val root = InnerTube.commentRequest("next", body, account, tv = true)
            return Read(root, tv = true, signed = root.dig("responseContext", "mainAppWebResponseContext", "loggedOut") != true)
        }
        return account?.let { tryRead(body, it, tv = false) } ?: Read(InnerTube.call("next", body, null))
    }

    private fun sameId(a: String, b: String): Boolean = a == b ||
        (('.' in a) != ('.' in b) && a.substringAfterLast('.') == b.substringAfterLast('.'))

    /** MeeTube đọc lại bình luận đang chọn bằng detailViewEndpoint để lấy token thích/bỏ thích. */
    private suspend fun resolve(account: Account, videoId: String, item: CommentItem, rootId: String, webFirst: Boolean = false, usable: (CommentItem) -> Boolean): CommentItem? {
        if (item.id.startsWith("local-")) return null
        suspend fun lookup(token: String, tv: Boolean): List<CommentItem> {
            val body = JSONObject().put("continuation", token)
            return tryRead(body, account, tv)?.takeIf { it.signed }?.let { parse(it).items }.orEmpty()
        }
        fun match(items: List<CommentItem>) = items.firstOrNull { sameId(it.id, item.id) && usable(it) }
        if (!webFirst && item.detailToken != null) match(lookup(item.detailToken, item.fromTv))?.let { return it }
        val modes = if (account.kind != LoginKind.CODE) listOf(false)
            else if (webFirst) listOf(false, true) else listOf(item.fromTv, !item.fromTv)
        for (tv in modes) {
            val page = lookup(focusedCommentParams(videoId, item.id), tv)
            match(page)?.let { return it }
            if (item.id != rootId && page.none { sameId(it.id, item.id) }) {
                if ('.' !in item.id) match(lookup(focusedCommentParams(videoId, "$rootId.${item.id}"), tv))?.let { return it }
                val parent = page.firstOrNull { it.id == rootId }
                    ?: lookup(focusedCommentParams(videoId, rootId), tv).firstOrNull { it.id == rootId }
                parent?.repliesToken?.let { token -> match(lookup(token, tv))?.let { return it } }
            }
        }
        if (webFirst && item.detailToken != null) return match(lookup(item.detailToken, item.fromTv))
        return null
    }

    private fun commentsToken(root: JSONObject): String? {
        val panels = findAllKeys(root, "engagementPanelSectionListRenderer").filter {
            it.optString("targetId").contains("comments") || it.optString("panelIdentifier") == "comment-item-section"
        }
        for (panel in panels) {
            val firstSort = panel.dig("header", "engagementPanelTitleHeaderRenderer", "menu", "sortFilterSubMenuRenderer", "subMenuItems", 0) as? JSONObject
            continuationToken(firstSort)?.let { return it }
        }
        val sections = findAllKeys(root, "itemSectionRenderer").filter { it.optString("sectionIdentifier") == "comment-item-section" }
        return (sections + panels).firstNotNullOfOrNull(::continuationToken)
    }

    private fun parse(read: Read): CommentPage {
        val root = read.root
        val entities = entities(root)
        val items = ArrayList<CommentItem>()
        val legacy = root.dig("continuationContents", "itemSectionContinuation") as? JSONObject
        var token = continuationToken(legacy?.opt("continuations"))
        val legacyItems = legacy?.optJSONArray("contents")
        val entries = if (legacyItems == null) continuationItems(root) else
            (0 until legacyItems.length()).mapNotNull { legacyItems.optJSONObject(it) }
        for (el in entries) {
            val more = el.optJSONObject("continuationItemRenderer")
            if (more != null) token = continuationToken(more) ?: token
            else item(el, entities, read)?.let { items.add(it) }
        }
        if (items.isEmpty()) {
            for (vm in findAllKeys(root, "commentViewModel")) {
                entities[vm.optString("commentKey")]?.let { p -> fromPayload(p, vm, null, entities, read)?.let(items::add) }
            }
            findAllKeys(root, "commentRenderer").mapNotNullTo(items) { fromRenderer(it, null, read) }
            if (items.isEmpty()) entities.values.mapNotNullTo(items) { fromPayload(it, null, null, entities, read) }
        }
        val total = ytText(findKey(root, "commentsHeaderRenderer")?.opt("countText"))
        return CommentPage(items.distinctBy { it.id }, token, total, sorts(root), read.tv)
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

    private fun continuationToken(node: Any?): String? =
        findKey(node, "continuationCommand").field("token")
            ?: findKey(node, "nextContinuationData").field("continuation")
            ?: findKey(node, "reloadContinuationData").field("continuation")

    /** Định dạng mới: nội dung nằm ở entityBatchUpdate, thẻ chỉ giữ khóa tham chiếu (khóa → thực thể). */
    private fun entities(root: JSONObject): Map<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
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

    private fun item(el: JSONObject, entities: Map<String, JSONObject>, read: Read): CommentItem? {
        val thread = el.optJSONObject("commentThreadRenderer")
        val base = thread ?: el
        val repliesToken = continuationToken(thread?.optJSONObject("replies"))
        val outer = base.optJSONObject("commentViewModel")
        val vm = outer?.optJSONObject("commentViewModel") ?: outer
        if (vm != null) entities[vm.optString("commentKey")]?.let { return fromPayload(it, vm, repliesToken, entities, read) }
        val renderer = base.dig("comment", "commentRenderer") as? JSONObject ?: base.optJSONObject("commentRenderer")
        return renderer?.let { fromRenderer(it, repliesToken, read) }
    }

    private fun manage(surfaces: List<JSONObject>, signed: Boolean): Manage {
        if (!signed) return Manage(null, null)
        var edit: CommentEditCommand? = null
        val updateFields = listOf(
            Triple("updateCommentEndpoint", "comment/update_comment", "commentText"),
            Triple("updateCommentReplyEndpoint", "comment/update_comment_reply", "replyText")
        )
        for (surface in surfaces) for ((name, path, field) in updateFields) {
            val command = findKey(surface, name) ?: continue
            if (command.length() != 0 && edit == null) edit = CommentEditCommand(path, command.toString(), field)
        }
        val delete = surfaces.firstNotNullOfOrNull { surface ->
            listOf("deleteCommentCommand", "deleteCommand", "removeCommentCommand").firstNotNullOfOrNull { name ->
                findKey(findKey(surface, name), "performCommentActionEndpoint").field("action")
            }
        }
        return Manage(edit, delete)
    }

    private fun fromPayload(p: JSONObject, vm: JSONObject?, repliesToken: String?, entities: Map<String, JSONObject>, read: Read): CommentItem? {
        val props = p.optJSONObject("properties") ?: return null
        val id = props.field("commentId") ?: vm.field("commentId") ?: return null
        val author = p.optJSONObject("author")
        val toolbar = p.optJSONObject("toolbar")
        val state = vm.field("toolbarStateKey")?.let { entities[it] }
        val keys = listOf("toolbarSurfaceKey", "commentSurfaceKey", "sharedKey")
        val surfaces = keys.mapNotNull { vm.field(it)?.let(entities::get) } + listOfNotNull(vm, toolbar, p)
        fun action(name: String) = if (!read.signed) null else surfaces.firstNotNullOfOrNull {
            findKey(findKey(it, name), "performCommentActionEndpoint").field("action")
        }
        val controls = manage(surfaces, read.signed)
        return CommentItem(
            id = id,
            author = author.field("displayName") ?: "Người dùng",
            avatar = author.field("avatarThumbnailUrl"),
            text = props.optJSONObject("content").field("content").orEmpty(),
            published = props.field("publishedTime"),
            likes = toolbar.field("likeCountNotliked")?.takeUnless { it == "0" },
            likesLiked = toolbar.field("likeCountLiked"),
            liked = state.field("likeState") == "TOOLBAR_LIKE_STATE_LIKED",
            disliked = state.field("likeState") == "TOOLBAR_LIKE_STATE_DISLIKED",
            heartedBy = toolbar.field("creatorThumbnailUrl").takeIf { state.field("heartState") == "TOOLBAR_HEART_STATE_HEARTED" },
            pinned = ytText(vm?.opt("pinnedText")),
            creator = author?.optBoolean("isCreator") == true,
            verified = author?.optBoolean("isVerified") == true || author?.optBoolean("isArtist") == true,
            authorChannelId = author.field("channelId")?.takeIf { it.startsWith("UC") }
                ?: listOf(author, vm).firstNotNullOfOrNull { node ->
                    findAllKeys(node, "browseEndpoint").firstNotNullOfOrNull { it.optString("browseId").takeIf { id -> id.startsWith("UC") } }
                },
            replies = toolbar.field("replyCount")?.takeIf { repliesToken != null && it != "0" },
            repliesToken = repliesToken,
            level = props.optInt("replyLevel"),
            likeAction = action("likeCommand"),
            unlikeAction = action("unlikeCommand"),
            dislikeAction = action("dislikeCommand"),
            undislikeAction = action("undislikeCommand"),
            editCommand = controls.edit,
            deleteAction = controls.delete,
            own = read.signed && (controls.edit != null || author?.optBoolean("isCurrentUser") == true),
            fromTv = read.tv
        )
    }

    private fun fromRenderer(r: JSONObject, repliesToken: String?, read: Read): CommentItem? {
        val id = r.field("commentId") ?: return null
        val buttons = r.dig("actionButtons", "commentActionButtonsRenderer") as? JSONObject
        fun toggle(button: String, key: String): String? {
            if (!read.signed) return null
            val endpoint = buttons.dig(button, "toggleButtonRenderer", key) as? JSONObject
            val authenticated = endpoint?.dig("authDeterminedCommand", "authenticatedCommand") ?: endpoint
            return findKey(authenticated, "performCommentActionEndpoint").field("action")
        }
        val detailToken = continuationToken(r.optJSONObject("detailViewEndpoint"))
        val controls = manage(listOf(r), read.signed)
        val voteStatus = r.field("voteStatus")
        val disliked = voteStatus in setOf("DISLIKE", "DISLIKED") || r.optBoolean("isDisliked") ||
            buttons.dig("dislikeButton", "toggleButtonRenderer", "isToggled") == true
        val liked = !disliked && (voteStatus in setOf("LIKE", "LIKED") || r.optBoolean("isLiked") ||
            buttons.dig("likeButton", "toggleButtonRenderer", "isToggled") == true)
        return CommentItem(
            id = id,
            author = ytText(r.opt("authorText")) ?: "Người dùng",
            avatar = r.dig("authorThumbnail", "thumbnails", 0, "url") as? String,
            text = ytText(r.opt("contentText")).orEmpty(),
            published = ytText(r.opt("publishedTimeText")),
            likes = ytText(r.opt("voteCount"))?.takeUnless { it == "0" },
            likesLiked = null,
            liked = liked,
            disliked = disliked,
            pinned = ytText(r.dig("pinnedCommentBadge", "pinnedCommentBadgeRenderer", "label")),
            creator = r.optBoolean("authorIsChannelOwner") || r.optBoolean("isCreator"),
            verified = (r.dig("authorCommentBadge", "authorCommentBadgeRenderer", "icon", "iconType") as? String)
                ?.let { it.startsWith("CHECK") || it.contains("ARTIST") } == true,
            authorChannelId = (r.dig("authorEndpoint", "browseEndpoint", "browseId") as? String)?.takeIf { it.startsWith("UC") },
            replies = if (repliesToken != null || detailToken != null) r.field("replyCount")
                ?: ytText(r.opt("repliesCount")) ?: findKey(r.opt("repliesCount"), "accessibilityData").field("label") else null,
            repliesToken = repliesToken ?: detailToken.takeIf { r.has("repliesCount") || r.optInt("replyCount") > 0 },
            likeAction = toggle("likeButton", "defaultServiceEndpoint"),
            unlikeAction = toggle("likeButton", "toggledServiceEndpoint"),
            dislikeAction = toggle("dislikeButton", "defaultServiceEndpoint"),
            undislikeAction = toggle("dislikeButton", "toggledServiceEndpoint"),
            detailToken = detailToken,
            editCommand = controls.edit,
            deleteAction = controls.delete,
            own = read.signed && (controls.edit != null || r.optBoolean("isCurrentUser")),
            fromTv = read.tv
        )
    }

    private fun sorts(root: JSONObject): List<CommentSort> {
        val menu = findKey(root, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems") ?: return emptyList()
        return (0 until menu.length()).mapNotNull { i ->
            val m = menu.optJSONObject(i) ?: return@mapNotNull null
            // MeeTube đọc continuation của chính mục menu (next / radio / reload) trước khi tìm lệnh khác.
            val token = continuationToken(m.optJSONObject("continuation")) ?: continuationToken(m) ?: return@mapNotNull null
            val label = ytText(m.opt("title")) ?: return@mapNotNull null
            CommentSort(label, token, i == 0)
        }
    }

    // ---- Ghi: đúng các tham số mà MediaServiceCore (CommentsApiParams) dựng --------------------------------

    private fun checkResult(answer: JSONObject, failure: String, result: JSONObject? = findKey(answer, "actionResult")): String? {
        val status = result.field("status")
        val error = findKey(answer, "showErrorAction") ?: findKey(answer, "errorMessage") ?: answer.optJSONObject("error")
        if (error != null || (status != null && status != SUCCEEDED)) {
            throw IllegalStateException(ytText(result?.opt("feedbackText"))
                ?: ytText(findKey(error, "messageRenderer")?.opt("text")) ?: ytText(error?.opt("errorText"))
                ?: error.field("message") ?: failure)
        }
        if (answer.dig("responseContext", "mainAppWebResponseContext", "loggedOut") == true) throw AuthRejected()
        return status
    }

    private fun written(answer: JSONObject, account: Account, text: String, level: Int, tv: Boolean): CommentItem {
        val status = checkResult(answer, "YouTube không cho đăng bình luận này")
        val read = Read(answer, tv, true)
        val posted = parse(read).items.firstOrNull { it.text == text }
        if (posted != null) return posted.copy(level = level, own = true)
        val id = findAllKeys(answer, "commentRenderer").firstNotNullOfOrNull { it.field("commentId") }
            ?: findAllKeys(answer, "commentEntityPayload").firstNotNullOfOrNull { it.optJSONObject("properties").field("commentId") }
        if (id == null && status != SUCCEEDED) throw IllegalStateException("YouTube chưa xác nhận đã đăng bình luận.")
        return CommentItem(
            id = id ?: "local-" + System.nanoTime(),
            author = account.handle ?: account.name, avatar = account.photo, text = text,
            published = "vừa xong", likes = null, likesLiked = null,
            replies = null, repliesToken = null, level = level, own = true, fromTv = tv
        )
    }

    private fun focusedCommentParams(videoId: String, commentId: String): String = encode(
        Proto().message(2, Proto().string(2, videoId)).varint(3, 6).message(6,
            Proto().message(4, Proto().string(4, videoId).varint(15, 2).string(16, commentId)).string(8, "comments-section"))
    )

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
