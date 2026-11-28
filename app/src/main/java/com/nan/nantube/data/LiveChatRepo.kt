package com.nan.nantube.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Một biểu tượng cảm xúc của YouTube hoặc của chủ kênh (ảnh + mã gõ như ":ten:"). */
data class ChatEmoji(val id: String, val shortcut: String, val url: String, val custom: Boolean)

/** Một đoạn của tin nhắn: chữ hoặc biểu tượng cảm xúc. */
sealed class ChatRun {
    data class Text(val text: String) : ChatRun()
    data class Emoji(val emoji: ChatEmoji) : ChatRun()
}

enum class ChatRole { NORMAL, MEMBER, MODERATOR, OWNER }

class ChatMessage(
    val id: String,
    val author: String,
    val photo: String?,
    val runs: List<ChatRun>,
    val role: ChatRole,
    val verified: Boolean,
    /** Ảnh huy hiệu thành viên (nếu có). */
    val badgeUrl: String?,
    /** Siêu trò chuyện: số tiền đã gửi. */
    val amount: String? = null,
    /** Mã kênh người viết (UC...), để tra dấu tích xác minh như bình luận. */
    val channelId: String? = null
) {
    /** Nội dung chữ thuần (emoji thành mã gõ), để so tin tự dựng với bản thật YouTube gửi về. */
    val text: String get() = runs.joinToString("") { if (it is ChatRun.Text) it.text else (it as ChatRun.Emoji).emoji.shortcut }
}

/** Một lựa chọn của bảng chọn; [percent] (vd "50%") và [ratio] chỉ có khi YouTube cho xem kết quả (ratio < 0 = chưa hiện). */
data class ChatPollChoice(val text: String, val percent: String?, val ratio: Float, val selected: Boolean, val voteParams: String? = null)

/** Bảng chọn (poll) của chủ kênh: câu hỏi, dòng "@kênh • 2 phút trước • 252 lượt bình chọn" và các lựa chọn. */
data class ChatPoll(
    val id: String,
    /** Mã khung hành động YouTube gắn cho bảng chọn (để biết lệnh đóng khung nào). */
    val panelId: String?,
    val question: String,
    val meta: String?,
    val avatar: String?,
    val choices: List<ChatPollChoice>
) {
    /** Đã bình chọn rồi (YouTube chỉ cho xem kết quả sau khi người xem chọn). */
    val voted get() = choices.any { it.selected }

    /**
     * Bản cập nhật kết quả (updateLiveChatPollAction) gửi chung cho mọi người xem: không biết mình đã chọn gì và có thể thiếu mã bình chọn,
     * nên giữ lại lựa chọn + mã từ bản trước của cùng bảng chọn.
     */
    fun after(old: ChatPoll?): ChatPoll {
        if (old == null || old.id != id) return this
        return copy(
            panelId = panelId ?: old.panelId,
            avatar = avatar ?: old.avatar,
            choices = choices.mapIndexed { i, c ->
                val prev = old.choices.getOrNull(i)?.takeIf { it.text == c.text } ?: return@mapIndexed c
                c.copy(selected = c.selected || prev.selected, voteParams = c.voteParams ?: prev.voteParams)
            }
        )
    }
}

/** Thay đổi của khung hành động (phía trên ô nhập), theo đúng thứ tự YouTube gửi. */
sealed class ChatPanelEvent {
    class ShowPoll(val poll: ChatPoll) : ChatPanelEvent()
    class ClosePanel(val panelId: String?) : ChatPanelEvent()
    /** Người xem không được chat (vd "Chế độ chỉ dành cho người đăng ký"): thay ô nhập bằng dòng [text]. */
    class Restricted(val text: String) : ChatPanelEvent()
    /** Ô nhập chat bình thường; [sendParams] có khi YouTube cho phép tài khoản này gửi. */
    class Input(val sendParams: String?) : ChatPanelEvent()
}

/** Kết quả một lượt đọc chat: hành động mới + mã để đọc lượt kế + thời gian chờ. */
class ChatBatch(
    val messages: List<ChatMessage>,
    val removedIds: Set<String>,
    val pinned: ChatMessage?,
    val continuation: String?,
    val waitMs: Long,
    val panelEvents: List<ChatPanelEvent> = emptyList()
)

/** Dữ liệu khởi tạo của khung chat: lượt đầu (kèm khung hành động) + danh sách emoji. */
class ChatStart(
    val batch: ChatBatch,
    /** Emoji theo nhóm (tên nhóm → emoji); rỗng nếu trang không trả. */
    val emojiGroups: List<Pair<String, List<ChatEmoji>>>,
    /** Đã đọc bằng phiên đăng nhập của tài khoản (nên ô nhập / chế độ hạn chế / bảng chọn đúng theo tài khoản). */
    val authed: Boolean = false
)

/**
 * Chat trực tiếp của YouTube (chưa gửi tin nhắn được): trang `live_chat?is_popout=1` có sẵn ytInitialData với lượt tin nhắn đầu,
 * mã đọc tiếp và danh sách emoji; các lượt sau hỏi `live_chat/get_live_chat` bằng mã đó. Đọc ẩn danh, không cần đăng nhập.
 */
object LiveChatRepo {
    private const val WEB_VERSION = "2.20260708.00.00"
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    private val DATA_START = Regex("(?:window\\[\"ytInitialData\"]|var ytInitialData)\\s*=\\s*(?=\\{)")

    /**
     * Mở chat. Có [account] thì thử đọc bằng chính tài khoản đó trước (YouTube chỉ báo đúng "chỉ dành cho người đăng ký",
     * ô nhập có mã gửi, bảng chọn bấm được khi biết người xem là ai); không được thì rơi về đọc ẩn danh như cũ.
     * - Tài khoản web: trang `live_chat` kèm cookie của WebView.
     * - Tài khoản mã: lấy khung chat từ `next` (Bearer), vì token TV không dùng được cho trang web.
     */
    suspend fun start(videoId: String, account: Account? = null): ChatStart {
        if (account != null) {
            try {
                return startFrom(authedChat(videoId, account), authed = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // đọc theo tài khoản hỏng: dùng đường ẩn danh bên dưới
            }
        }
        return startFrom(anonymousChat(videoId), authed = false)
    }

    private suspend fun authedChat(videoId: String, account: Account): JSONObject {
        if (account.kind == LoginKind.WEB) {
            val cookies = InnerTube.webCookieHeader() ?: throw AuthRejected()
            val headers = mutableMapOf(
                "User-Agent" to DESKTOP_UA, "Referer" to "https://www.youtube.com/watch?v=$videoId",
                "Cookie" to cookies, "Accept-Language" to "vi-VN,vi;q=0.9"
            )
            val authUser = if (account.authUser > 0) "&authuser=${account.authUser}" else ""
            val res = Http.get("https://www.youtube.com/live_chat?is_popout=1&v=$videoId$authUser", headers, timeoutMs = 12_000)
            if (res.code !in 200..299) throw Exception("Không mở được khung chat (HTTP ${res.code})")
            return chatFromPage(res.body)
        }
        // Tài khoản mã: `next` (TV + Bearer) có liveChatRenderer của buổi live.
        val root = InnerTube.call("next", JSONObject().put("videoId", videoId), account)
        return findKey(root, "liveChatRenderer") ?: throw Exception("Video này không có chat trực tiếp")
    }

    private suspend fun anonymousChat(videoId: String): JSONObject {
        val res = Http.get(
            "https://www.youtube.com/live_chat?is_popout=1&v=$videoId",
            mapOf("User-Agent" to DESKTOP_UA, "Referer" to "https://www.youtube.com/watch?v=$videoId"),
            timeoutMs = 12_000
        )
        if (res.code !in 200..299) throw Exception("Không mở được khung chat (HTTP ${res.code})")
        return chatFromPage(res.body)
    }

    private fun chatFromPage(html: String): JSONObject {
        val match = DATA_START.find(html) ?: throw Exception("Video này không có chat trực tiếp")
        val data = try {
            JSONTokener(html.substring(match.range.last + 1)).nextValue() as? JSONObject
        } catch (_: Exception) {
            null
        } ?: throw Exception("Không đọc được dữ liệu chat")
        return data.dig("contents", "liveChatRenderer") as? JSONObject
            ?: throw Exception("Video này không có chat trực tiếp")
    }

    private fun startFrom(chat: JSONObject, authed: Boolean): ChatStart {
        val emojis = parseEmojis(chat)
        val batch = parseBatch(chat.optJSONArray("actions"), chat.optJSONArray("continuations"), chat.optJSONObject("actionPanel"))
        return ChatStart(batch, emojiGroups(chat, emojis), authed)
    }

    /** Gọi endpoint ghi / đọc của chat bằng phiên tài khoản: web = cookie + SAPISIDHASH; mã = WEB+Bearer rồi TV+Bearer. */
    private suspend fun accountCall(endpoint: String, data: JSONObject, account: Account): JSONObject {
        if (account.kind != LoginKind.CODE) return InnerTube.commentRequest(endpoint, data, account, tv = false)
        return try {
            InnerTube.commentRequest(endpoint, data, account, tv = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (first: Exception) {
            try {
                InnerTube.commentRequest(endpoint, data, account, tv = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                throw first
            }
        }
    }

    /** Lượt đọc kế. [account] != null và [authed] = tiếp tục đọc bằng phiên tài khoản (phải cùng kiểu với lượt [start]). */
    suspend fun poll(continuation: String, account: Account? = null, authed: Boolean = false): ChatBatch {
        val json = if (authed && account != null) {
            accountCall("live_chat/get_live_chat", JSONObject().put("continuation", continuation), account)
        } else {
            val client = JSONObject().put("clientName", "WEB").put("clientVersion", WEB_VERSION)
                .put("acceptLanguage", "vi").put("acceptRegion", "VN")
                .put("utcOffsetMinutes", InnerTube.utcOffsetMinutes().toString())
            val body = JSONObject().put("context", JSONObject().put("client", client).put("user", InnerTube.safetyUser()))
                .put("continuation", continuation)
            Http.postJson(
                InnerTube.BASE + "live_chat/get_live_chat?prettyPrint=false", body,
                mapOf("Origin" to InnerTube.ORIGIN, "User-Agent" to DESKTOP_UA, "X-Youtube-Client-Name" to "1", "X-Youtube-Client-Version" to WEB_VERSION)
            ).requireJson()
        }
        val c = json.dig("continuationContents", "liveChatContinuation") as? JSONObject
            ?: return ChatBatch(emptyList(), emptySet(), null, null, 5_000)
        return parseBatch(c.optJSONArray("actions"), c.optJSONArray("continuations"), c.optJSONObject("actionPanel"))
    }

    /**
     * Gửi tin nhắn (live_chat/send_message). [sendParams] lấy từ ô nhập của khung chat (sendLiveChatMessageEndpoint.params).
     * Mã emoji dạng ":ten:" được đổi thành đoạn emoji thật nếu có trong [emojis] (theo mã gõ), còn lại là chữ.
     * Trả về hành động YouTube echo lại (tin nhắn của mình) để hiện ngay; YouTube từ chối (vd chỉ dành cho người đăng ký) thì ném lỗi kèm lý do.
     */
    suspend fun send(account: Account, sendParams: String, text: String, emojis: Map<String, ChatEmoji>): ChatBatch {
        val segments = JSONArray()
        var last = 0
        for (m in Regex(":[^:\\s]+:").findAll(text)) {
            val e = emojis[m.value] ?: continue
            if (m.range.first > last) segments.put(JSONObject().put("text", text.substring(last, m.range.first)))
            segments.put(JSONObject().put("emojiId", e.id))
            last = m.range.last + 1
        }
        if (last < text.length) segments.put(JSONObject().put("text", text.substring(last)))
        val body = JSONObject().put("params", sendParams)
            .put("richMessage", JSONObject().put("textSegments", segments))
            .put("clientMessageId", InnerTube.randomId(26))
            .put("webClientInfo", JSONObject().put("isDocumentHidden", false))
        val json = accountCall("live_chat/send_message", body, account)
        actionError(json)?.let { throw Exception(it) }
        return parseBatch(json.optJSONArray("actions"), null)
    }

    /**
     * Mã gửi tự dựng từ kênh + video khi khung chat không kèm sẵn (đọc ẩn danh / mã chưa về), đúng như `LiveChatMessageSender.params` của meetube:
     * protobuf {1: {5: {1: kênh, 2: video}}, 2: 1, 3: 4}, base64 + mã hoá URL, rồi base64 lần nữa. YouTube cho phép hay không thì tự báo lỗi khi gửi.
     */
    fun localSendParams(channelId: String, videoId: String): String {
        val ids = CommentsRepository.Proto().string(1, channelId).string(2, videoId)
        val inner = CommentsRepository.encode(CommentsRepository.Proto().message(1, CommentsRepository.Proto().message(5, ids)).varint(2, 1).varint(3, 4))
        return Base64.encodeToString(inner.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Lý do YouTube từ chối một hành động chat (200 OK nhưng kèm liveChatActionErrorRenderer), null nếu không có lỗi. */
    private fun actionError(json: JSONObject): String? =
        (findKey(json, "liveChatActionErrorRenderer") ?: findKey(json, "showLiveChatActionErrorAction"))
            ?.let { ytText(it.opt("errorText")) ?: "YouTube không nhận yêu cầu này" }

    /** Bình chọn (live_chat/send_live_chat_vote) bằng mã [voteParams] của lựa chọn; trả về cập nhật kết quả nếu YouTube echo. */
    suspend fun vote(account: Account, voteParams: String): ChatBatch {
        val json = accountCall("live_chat/send_live_chat_vote", JSONObject().put("params", voteParams), account)
        actionError(json)?.let { throw Exception(it) }
        val actions = json.optJSONArray("actions")
            ?: (json.dig("continuationContents", "liveChatContinuation") as? JSONObject)?.optJSONArray("actions")
        return parseBatch(actions, null)
    }

    /** Khung hành động (ô nhập / chế độ hạn chế / bảng chọn) → sự kiện; null nếu không nhận ra. */
    private fun panelEvent(panel: JSONObject?): ChatPanelEvent? {
        panel ?: return null
        findKey(panel, "pollRenderer")?.let { r ->
            val panelId = (findKey(panel, "liveChatActionPanelRenderer")?.optString("id") ?: panel.optString("id")).ifBlank { null }
            return parsePoll(r, panelId)?.let { ChatPanelEvent.ShowPoll(it) }
        }
        findKey(panel, "liveChatRestrictedParticipationRenderer")?.let { r ->
            return ChatPanelEvent.Restricted(ytText(r.opt("message")) ?: "Bạn không thể trò chuyện lúc này")
        }
        if (findKey(panel, "liveChatMessageInputRenderer") != null) {
            // Có mã gửi (sendLiveChatMessageEndpoint) = tài khoản này chat được; ẩn danh thì không có (lúc gửi sẽ tự dựng mã như meetube).
            return ChatPanelEvent.Input(findKey(panel, "sendLiveChatMessageEndpoint")?.optString("params")?.ifBlank { null })
        }
        return null
    }

    private fun parsePoll(r: JSONObject, panelId: String?): ChatPoll? {
        val header = r.optJSONObject("header")?.optJSONObject("pollHeaderRenderer")
        val question = header?.let { ytText(it.opt("pollQuestion")) }?.ifBlank { null } ?: return null
        val list = r.optJSONArray("choices")
        val choices = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val c = list?.optJSONObject(i) ?: return@mapNotNull null
            val text = ytText(c.opt("text"))?.ifBlank { null } ?: return@mapNotNull null
            ChatPollChoice(
                text = text,
                percent = ytText(c.opt("votePercentage"))?.ifBlank { null },
                ratio = c.optDouble("voteRatio", -1.0).toFloat(),
                selected = c.optBoolean("selected"),
                voteParams = findKey(c, "sendLiveChatVoteEndpoint")?.optString("params")?.ifBlank { null }
            )
        }
        if (choices.isEmpty()) return null
        return ChatPoll(
            id = r.optString("liveChatPollId").ifBlank { question },
            panelId = panelId,
            question = question,
            meta = header?.let { ytText(it.opt("metadataText")) }?.ifBlank { null },
            avatar = lastUrl(header?.dig("thumbnail", "thumbnails") as? JSONArray),
            choices = choices
        )
    }

    private fun parseBatch(actions: JSONArray?, continuations: JSONArray?, actionPanel: JSONObject? = null): ChatBatch {
        val panelEvents = ArrayList<ChatPanelEvent>()
        panelEvent(actionPanel)?.let { panelEvents += it }
        val messages = ArrayList<ChatMessage>()
        val removed = HashSet<String>()
        var pinned: ChatMessage? = null
        for (i in 0 until (actions?.length() ?: 0)) {
            val a = actions?.optJSONObject(i) ?: continue
            a.dig("addChatItemAction", "item")?.let { item -> parseItem(item as? JSONObject)?.let { messages += it } }
            (a.dig("removeChatItemAction", "targetItemId") as? String)?.let { removed += it }
            // Khung hành động: bảng chọn mở / cập nhật kết quả / đóng, và ô nhập đổi chế độ (vd bật "chỉ người đăng ký" giữa chừng).
            (a.dig("showLiveChatActionPanelAction", "panelToShow") as? JSONObject)?.let { p -> panelEvent(p)?.let { panelEvents += it } }
            (a.dig("updateLiveChatPollAction", "pollToUpdate", "pollRenderer") as? JSONObject)?.let { r ->
                parsePoll(r, null)?.let { panelEvents += ChatPanelEvent.ShowPoll(it) }
            }
            a.optJSONObject("closeLiveChatActionPanelAction")?.let { panelEvents += ChatPanelEvent.ClosePanel(it.optString("targetPanelId").ifBlank { null }) }
            a.dig("addBannerToLiveChatCommand", "bannerRenderer", "liveChatBannerRenderer", "contents")
                ?.let { parseItem(it as? JSONObject) }?.let { pinned = it }
        }
        var next: String? = null
        var wait = 5_000L
        for (i in 0 until (continuations?.length() ?: 0)) {
            val c = continuations?.optJSONObject(i) ?: continue
            val d = c.optJSONObject("invalidationContinuationData") ?: c.optJSONObject("timedContinuationData")
                ?: c.optJSONObject("reloadContinuationData") ?: continue
            next = d.optString("continuation").ifBlank { null } ?: continue
            wait = d.optLong("timeoutMs", 5_000L)
            break
        }
        return ChatBatch(messages, removed, pinned, next, wait.coerceIn(1_500L, 10_000L), panelEvents)
    }

    private fun parseItem(item: JSONObject?): ChatMessage? {
        item ?: return null
        val r = item.optJSONObject("liveChatTextMessageRenderer") ?: item.optJSONObject("liveChatPaidMessageRenderer") ?: return null
        val id = r.optString("id").ifBlank { return null }
        val author = ytText(r.opt("authorName")) ?: return null
        var role = ChatRole.NORMAL
        var verified = false
        var badge: String? = null
        val badges = r.optJSONArray("authorBadges")
        for (i in 0 until (badges?.length() ?: 0)) {
            val b = badges?.optJSONObject(i)?.optJSONObject("liveChatAuthorBadgeRenderer") ?: continue
            when (b.dig("icon", "iconType") as? String) {
                "OWNER" -> role = ChatRole.OWNER
                "MODERATOR" -> if (role != ChatRole.OWNER) role = ChatRole.MODERATOR
                "VERIFIED" -> verified = true
                else -> b.dig("customThumbnail", "thumbnails")?.let { t ->
                    badge = lastUrl(t as? JSONArray)
                    if (role == ChatRole.NORMAL) role = ChatRole.MEMBER
                }
            }
        }
        val runs = ArrayList<ChatRun>()
        val parts = r.optJSONObject("message")?.optJSONArray("runs")
        for (i in 0 until (parts?.length() ?: 0)) {
            val p = parts?.optJSONObject(i) ?: continue
            val e = p.optJSONObject("emoji")
            if (e != null) {
                val emoji = toEmoji(e)
                // Emoji không có ảnh thì giữ mã gõ của nó.
                runs.add(if (emoji != null) ChatRun.Emoji(emoji) else ChatRun.Text(e.dig("shortcuts", 0) as? String ?: ""))
            } else {
                runs.add(ChatRun.Text(p.optString("text")))
            }
        }
        return ChatMessage(
            id, author, lastUrl(r.dig("authorPhoto", "thumbnails") as? JSONArray), runs, role, verified, badge,
            amount = ytText(r.opt("purchaseAmountText")),
            channelId = r.optString("authorExternalChannelId").ifBlank { null }
        )
    }

    private fun lastUrl(thumbs: JSONArray?): String? {
        if (thumbs == null || thumbs.length() == 0) return null
        return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.ifBlank { null }?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun toEmoji(e: JSONObject): ChatEmoji? {
        val url = lastUrl(e.dig("image", "thumbnails") as? JSONArray) ?: return null
        val id = e.optString("emojiId").ifBlank { url }
        val shortcut = e.dig("shortcuts", 0) as? String ?: id
        return ChatEmoji(id, shortcut, url, e.optBoolean("isCustomEmoji"))
    }

    private fun parseEmojis(chat: JSONObject): Map<String, ChatEmoji> {
        val out = LinkedHashMap<String, ChatEmoji>()
        val list = chat.optJSONArray("emojis")
        for (i in 0 until (list?.length() ?: 0)) list?.optJSONObject(i)?.let { e -> toEmoji(e)?.let { out[it.id] = it } }
        return out
    }

    /** Nhóm emoji theo bộ chọn của YouTube (nhóm của chủ kênh, nhóm YouTube...); không có bộ chọn thì gom hết một nhóm. */
    private fun emojiGroups(chat: JSONObject, emojis: Map<String, ChatEmoji>): List<Pair<String, List<ChatEmoji>>> {
        if (emojis.isEmpty()) return emptyList()
        val picker = findKey(chat.dig("actionPanel", "liveChatMessageInputRenderer"), "emojiPickerRenderer")
        val cats = picker?.optJSONArray("categories")
        val groups = ArrayList<Pair<String, List<ChatEmoji>>>()
        for (i in 0 until (cats?.length() ?: 0)) {
            val c = cats?.optJSONObject(i)?.optJSONObject("emojiPickerCategoryRenderer") ?: continue
            val ids = c.optJSONArray("emojiIds") ?: continue
            val items = (0 until ids.length()).mapNotNull { emojis[ids.optString(it)] }
            if (items.isNotEmpty()) groups += (ytText(c.opt("title")) ?: "") to items
        }
        return groups.ifEmpty { listOf("" to emojis.values.toList()) }
    }
}
