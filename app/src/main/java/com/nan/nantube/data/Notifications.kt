package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Loại thông báo, dùng để chia tab (Bình luận / Lượt đề cập / Kênh của bạn) và chọn cách hiện chữ. */
enum class NotificationKind { UPLOAD, LIVE, COMMENT, MENTION, OWN, OTHER }

/** Một thông báo của YouTube (video mới của kênh đã đăng ký, trả lời bình luận…). */
data class NotificationItem(
    val key: String,
    /** Dòng đậm trên cùng: tên kênh / người gửi, hoặc "Đang phát trực tiếp". Có thể trống. */
    val title: String,
    /** Dòng nội dung bên dưới ("Video tải lên: …", "Đã bình luận: …"). */
    val body: String,
    val time: String?,
    /** Tuổi của thông báo theo giờ (đọc từ [time]); null nếu không đọc được. */
    val ageHours: Double?,
    /** Avatar kênh gửi thông báo nếu YouTube trả kèm. */
    val avatar: String?,
    /** Ảnh bìa video (có thể null với thông báo không phải video). */
    val thumbnail: String?,
    val isShort: Boolean,
    val videoId: String?,
    val url: String?,
    val unread: Boolean,
    val kind: NotificationKind,
    /** Tên kênh / người gửi (để tra avatar, để tắt thông báo của kênh). */
    val channel: String?,
    val channelId: String?,
    /** Mã để báo YouTube ẩn thông báo (chỉ có khi lấy từ hộp thư thật). */
    val hideToken: String?
)

/** [fromInbox] = true khi đọc được hộp thư thông báo thật của YouTube; false khi phải dựng từ video mới của kênh đã đăng ký. */
class NotificationResult(val items: List<NotificationItem>, val fromInbox: Boolean)

/**
 * Thông báo.
 *
 * Hộp thư thật (`notification/get_notification_menu`) bị YouTube từ chối với phiên đăng nhập bằng mã (client TV):
 * HTTP 400 "Precondition check failed" (MediaServiceCore / meetube cũng gặp đúng lỗi này). Vì vậy:
 * 1. Thử hộp thư thật (client WEB + cookie cho tài khoản web; TV rồi WEB + Bearer cho tài khoản mã).
 * 2. Nếu bị từ chối: dựng thông báo từ trang Kênh đăng ký (`FEsubscriptions`, client TV chạy được với token mã),
 *    mỗi video mới / buổi phát trực tiếp của kênh đã đăng ký là một thông báo, giống mục "Video tải lên" của YouTube.
 * Trạng thái đã xem / ẩn / tắt kênh ở chế độ 2 do app tự nhớ trên máy.
 */
object NotificationRepository {
    private class Cached(val at: Long, val result: NotificationResult)

    private val cache = HashMap<String, Cached>()
    private val inboxRefused = HashSet<String>()
    private const val TTL_MS = 120_000L
    private const val MAX_FEED_ITEMS = 40
    private const val FEED_MAX_AGE_HOURS = 24.0 * 30
    private const val FRESH_HOURS = 48.0

    /** [force] = bỏ bộ nhớ đệm và thử lại cả hộp thư thật. */
    suspend fun load(account: Account?, force: Boolean = false): NotificationResult {
        account ?: return NotificationResult(emptyList(), false)
        val now = System.currentTimeMillis()
        if (!force) {
            val hit = synchronized(cache) { cache[account.id] }
            if (hit != null && now - hit.at < TTL_MS) return applyLocal(account, hit.result)
        }
        val raw = fetch(account, force)
        // Chờ avatar kênh sẵn sàng rồi mới đưa danh sách ra, để dòng thông báo không hiện chữ cái / nền trống rồi mới đổi sang ảnh.
        ChannelAvatars.prefetch(
            raw.items.map { ChannelAvatars.Request(it.channelId, it.channel, videoId = it.videoId) },
            raw.items.map { it.avatar }
        )
        synchronized(cache) { cache[account.id] = Cached(now, raw) }
        return applyLocal(account, raw)
    }

    /** Số thông báo chưa xem (số đỏ trên chuông); 0 nếu không đọc được để không làm hỏng giao diện. */
    suspend fun unseenCount(account: Account?): Int {
        account ?: return 0
        return try {
            load(account).items.count { it.unread }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun fetch(account: Account, force: Boolean): NotificationResult {
        if (force) synchronized(inboxRefused) { inboxRefused.remove(account.id) }
        val skipInbox = synchronized(inboxRefused) { account.id in inboxRefused }
        if (!skipInbox) {
            try {
                return NotificationResult(loadInbox(account), true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // HTTP 400/403 hoặc phản hồi không có hộp thư: nhớ để lần sau khỏi gọi lại, rồi dựng từ kênh đăng ký.
                synchronized(inboxRefused) { inboxRefused.add(account.id) }
            }
        }
        return NotificationResult(loadFromSubscriptions(account), false)
    }

    // ---- Hộp thư thật ----------------------------------------------------------------------

    private suspend fun loadInbox(account: Account): List<NotificationItem> {
        val json = InnerTube.callAny(
            "notification/get_notification_menu",
            JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"),
            account
        )
        val out = LinkedHashMap<String, NotificationItem>()
        for (r in findAllKeys(json, "notificationRenderer")) {
            val message = ytText(r.opt("shortMessage")) ?: continue
            val videoId = r.dig("navigationEndpoint", "watchEndpoint", "videoId") as? String
            val rawUrl = r.dig("navigationEndpoint", "commandMetadata", "webCommandMetadata", "url") as? String
            val url = when {
                videoId != null -> "https://www.youtube.com/watch?v=$videoId"
                rawUrl == null -> null
                rawUrl.startsWith("/") -> "https://www.youtube.com$rawUrl"
                else -> rawUrl
            }
            val key = r.optString("notificationId").ifBlank { null } ?: (videoId ?: message)
            val time = ytText(r.opt("sentTimeText"))
            val d = describe(message)
            val hideToken = findAllKeys(r.opt("contextualMenu"), "recordNotificationInteractionsEndpoint")
                .firstNotNullOfOrNull { it.optString("serializedInteractionsRequest").ifBlank { null } }
            val channelId = findAllKeys(r, "browseEndpoint").firstNotNullOfOrNull {
                it.optString("browseId").takeIf { id -> id.startsWith("UC") && id.length == 24 }
            }
            out.putIfAbsent(
                key,
                NotificationItem(
                    key = key,
                    title = d.title,
                    body = d.body,
                    time = time,
                    ageHours = ageHoursOf(time),
                    avatar = firstUrl(r.dig("thumbnail", "thumbnails")),
                    thumbnail = videoId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
                        ?: firstUrl(r.dig("videoThumbnail", "thumbnails")),
                    isShort = rawUrl?.startsWith("/shorts/") == true,
                    videoId = videoId,
                    url = url,
                    unread = !r.optBoolean("read", false),
                    kind = d.kind,
                    channel = d.channel,
                    channelId = channelId,
                    hideToken = hideToken
                )
            )
        }
        // Hộp thư trống hợp lệ có thẻ "Thông báo của bạn sẽ hiện ở đây". Không có cả hai = phản hồi lạ: coi như bị từ chối.
        if (out.isEmpty() && findKey(json, "backgroundPromoRenderer") == null) {
            error("Hộp thư thông báo không có dữ liệu")
        }
        return out.values.toList()
    }

    // ---- Dựng từ kênh đăng ký ---------------------------------------------------------------

    /** Video mới từ kênh đăng ký (mới nhất trước), không lọc theo trạng thái trên máy; dùng cho thông báo nền. */
    suspend fun uploadsFeed(account: Account): List<NotificationItem> = loadFromSubscriptions(account)

    private suspend fun loadFromSubscriptions(account: Account): List<NotificationItem> {
        val json = InnerTube.call("browse", JSONObject().put("browseId", "FEsubscriptions"), account)
        return VideoRepository.parse(json)
            .map { v ->
                val age = ageHoursOf(v.published)
                val channel = v.channel?.trim()?.ifBlank { null }
                NotificationItem(
                    key = "v:${v.id}",
                    title = if (v.live) "Đang phát trực tiếp" else channel ?: "YouTube",
                    body = if (v.live) listOfNotNull(channel, v.title).joinToString(": ") else "Video tải lên: ${v.title}",
                    time = v.published,
                    ageHours = age,
                    avatar = v.avatar,
                    thumbnail = v.thumbnail,
                    isShort = v.isShort,
                    videoId = v.id,
                    url = v.watchUrl,
                    unread = true, // [applyLocal] quyết định theo mục đã xem
                    kind = if (v.live) NotificationKind.LIVE else NotificationKind.UPLOAD,
                    channel = channel,
                    channelId = v.channelId,
                    hideToken = null
                )
            }
            .filter { it.ageHours == null || it.ageHours <= FEED_MAX_AGE_HOURS }
            .sortedBy { it.ageHours ?: Double.MAX_VALUE }
            .take(MAX_FEED_ITEMS)
    }

    // ---- Trạng thái trên máy: đã xem / ẩn / tắt kênh --------------------------------------------

    private fun prefs() = AccountStore.libraryPrefs()

    private fun getSet(name: String, account: Account): MutableSet<String> =
        prefs()?.getStringSet("$name:${account.id}", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun putSet(name: String, account: Account, values: Set<String>) {
        prefs()?.edit()?.putStringSet("$name:${account.id}", values)?.apply()
    }

    private fun applyLocal(account: Account, raw: NotificationResult): NotificationResult {
        val hidden = getSet("notif_hidden", account)
        val muted = getSet("notif_muted", account)
        val seen = getSet("notif_seen", account)
        val items = raw.items
            .filter { it.key !in hidden && (it.channel == null || it.channel.lowercase() !in muted) }
            .map { n ->
                val base = if (raw.fromInbox) n.unread else n.ageHours == null || n.ageHours <= FRESH_HOURS
                n.copy(unread = base && n.key !in seen)
            }
        return NotificationResult(items, raw.fromInbox)
    }

    /** Ghi nhớ các thông báo đã xem để chấm xanh biến mất ở lần mở sau (giữ tối đa 300 mục gần nhất). */
    fun markSeen(account: Account?, items: List<NotificationItem>) {
        account ?: return
        if (items.none { it.unread }) return
        val merged = LinkedHashSet<String>()
        items.forEach { merged.add(it.key) }
        merged.addAll(getSet("notif_seen", account))
        putSet("notif_seen", account, merged.take(300).toSet())
    }

    /** Ẩn một thông báo (nhớ trên máy; thông báo của hộp thư thật còn được báo cho YouTube). */
    suspend fun hide(account: Account?, item: NotificationItem) {
        account ?: return
        putSet("notif_hidden", account, getSet("notif_hidden", account).apply { add(item.key) })
        val token = item.hideToken ?: return
        try {
            InnerTube.callAny(
                "notification/record_interactions",
                JSONObject().put("serializedRecordNotificationInteractionsRequest", token),
                account
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Đã ẩn trên máy rồi; YouTube không nhận thì thôi.
        }
    }

    /** Tắt mọi thông báo từ kênh của [item] (nhớ trên máy). */
    fun mute(account: Account?, item: NotificationItem) {
        account ?: return
        val name = item.channel?.lowercase() ?: return
        putSet("notif_muted", account, getSet("notif_muted", account).apply { add(name) })
        ChannelNotify.set(account, item.channelId, item.channel, false)
    }

    fun hasLocalFilters(account: Account?): Boolean =
        account != null && (getSet("notif_hidden", account).isNotEmpty() || getSet("notif_muted", account).isNotEmpty())

    fun restoreHidden(account: Account?) {
        account ?: return
        putSet("notif_hidden", account, emptySet())
        putSet("notif_muted", account, emptySet())
    }

    // ---- Đọc chữ của thông báo ----------------------------------------------------------------

    private class Described(val title: String, val body: String, val kind: NotificationKind, val channel: String?)

    private val UPLOAD_VERB = Regex("\\s+(đã tải lên|đã đăng tải|đã đăng|uploaded)\\s*$", RegexOption.IGNORE_CASE)
    private val LIVE_VERB = Regex(
        "\\s+(đang phát trực tiếp|đang trực tiếp|đã bắt đầu phát trực tiếp|is live now|is live|started a live stream)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val MENTION_HINT = Regex("nhắc đến|đề cập|mention", RegexOption.IGNORE_CASE)
    private val COMMENT_HINT = Regex("bình luận|trả lời|comment|repl", RegexOption.IGNORE_CASE)
    private val OWN_HINT = Regex("của bạn|your (post|channel|video|short)", RegexOption.IGNORE_CASE)
    private val ACTOR_SPLIT = Regex("^(.*?)\\s+(đã|commented|replied|mentioned)\\b", RegexOption.IGNORE_CASE)

    /** "Kênh đã tải lên: Tiêu đề" -> dòng đậm "Kênh" + "Video tải lên: Tiêu đề", như app YouTube. */
    private fun describe(message: String): Described {
        val i = message.indexOf(": ")
        val head = (if (i > 0) message.substring(0, i) else message).trim()
        val rest = if (i > 0) message.substring(i + 2).trim() else ""
        LIVE_VERB.find(head)?.let {
            val name = head.substring(0, it.range.first).trim()
            return Described("Đang phát trực tiếp", listOf(name, rest).filter { s -> s.isNotEmpty() }.joinToString(": "), NotificationKind.LIVE, name)
        }
        UPLOAD_VERB.find(head)?.let {
            val name = head.substring(0, it.range.first).trim()
            return Described(name, if (rest.isEmpty()) "Video mới" else "Video tải lên: $rest", NotificationKind.UPLOAD, name)
        }
        val mention = MENTION_HINT.containsMatchIn(head)
        if (mention || COMMENT_HINT.containsMatchIn(head)) {
            val m = ACTOR_SPLIT.find(head)
            val name = m?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: head
            val verb = head.removePrefix(name).trim().replaceFirstChar { c -> c.uppercase() }
            val body = if (verb.isEmpty()) rest else if (rest.isEmpty()) verb else "$verb: $rest"
            return Described(name, body, if (mention) NotificationKind.MENTION else NotificationKind.COMMENT, name)
        }
        if (OWN_HINT.containsMatchIn(message)) return Described("", message, NotificationKind.OWN, null)
        return Described(head, rest, NotificationKind.OTHER, null)
    }

    private val AGE_RE = Regex(
        "(\\d+)\\s*(giây|phút|giờ|ngày|tuần|tháng|năm|seconds?|minutes?|hours?|days?|weeks?|months?|years?)",
        RegexOption.IGNORE_CASE
    )

    /** "17 giờ trước" -> 17.0, "2 tuần trước" -> 336.0; null nếu không đọc được. "Vừa xong" -> 0. */
    fun ageHoursOf(text: String?): Double? {
        if (text == null) return null
        if (text.contains("vừa xong", ignoreCase = true) || text.contains("just now", ignoreCase = true)) return 0.0
        val m = AGE_RE.find(text) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        val perUnit = when {
            unit.startsWith("giây") || unit.startsWith("second") -> 1.0 / 3600
            unit.startsWith("phút") || unit.startsWith("minute") -> 1.0 / 60
            unit.startsWith("giờ") || unit.startsWith("hour") -> 1.0
            unit.startsWith("ngày") || unit.startsWith("day") -> 24.0
            unit.startsWith("tuần") || unit.startsWith("week") -> 24.0 * 7
            unit.startsWith("tháng") || unit.startsWith("month") -> 24.0 * 30
            else -> 24.0 * 365
        }
        return n * perUnit
    }

    private fun firstUrl(thumbs: Any?): String? {
        val arr = thumbs as? org.json.JSONArray ?: return null
        if (arr.length() == 0) return null
        val url = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http") -> url
            else -> null
        }
    }
}
