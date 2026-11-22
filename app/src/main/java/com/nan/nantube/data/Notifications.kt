package com.nan.nantube.data

import org.json.JSONObject

/** Một thông báo của YouTube (video mới của kênh đã đăng ký, trả lời bình luận…). */
data class NotificationItem(
    val key: String,
    /** Nội dung đã gộp ("Kênh: tiêu đề video"). */
    val message: String,
    val time: String?,
    /** Avatar kênh gửi thông báo. */
    val avatar: String?,
    /** Ảnh bìa video (có thể null với thông báo không phải video). */
    val thumbnail: String?,
    val videoId: String?,
    val url: String?,
    val unread: Boolean
)

/**
 * Thông báo: `notification/get_notification_menu` như `NotificationsServiceInt` của MediaServiceCore
 * (client TV với tài khoản mã, client WEB với tài khoản web) và `get_unseen_count` cho số đỏ trên chuông.
 */
object NotificationRepository {
    suspend fun load(account: Account?): List<NotificationItem> {
        account ?: return emptyList()
        val json = InnerTube.call(
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
            out.putIfAbsent(
                key,
                NotificationItem(
                    key = key,
                    message = message,
                    time = ytText(r.opt("sentTimeText")),
                    avatar = firstUrl(r.dig("thumbnail", "thumbnails")),
                    thumbnail = videoId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
                        ?: firstUrl(r.dig("videoThumbnail", "thumbnails")),
                    videoId = videoId,
                    url = url,
                    unread = !r.optBoolean("read", false)
                )
            )
        }
        return out.values.toList()
    }

    /** Số thông báo chưa xem; 0 nếu không đọc được (không làm hỏng giao diện). */
    suspend fun unseenCount(account: Account?): Int {
        account ?: return 0
        return try {
            val json = InnerTube.call("notification/get_unseen_count", JSONObject(), account)
            findAllKeys(json, "updateNotificationsUnseenCountAction").firstOrNull()?.optInt("unseenCount", 0) ?: 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
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
