package com.nan.nantube.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * "Không quan tâm" và "Không đề xuất kênh này" của menu Short, thay hoàn toàn bằng logic MeTube/MediaServiceCore:
 * 1. Token: chi tiết reel mẫu WEB ẩn danh (`InnerTube.mtReelDetails`, như BrowseService2.continueShortsWeb) rồi lấy
 *    `overlay.reelPlayerOverlayRenderer.menu.menuRenderer.items` (ReelResult.getFeedbackTokens). Mục có token thứ nhất
 *    là Không quan tâm, thứ hai là Không đề xuất kênh (ShortsMediaItem.feedbackTokenItem / feedbackTokenItem2).
 * 2. Gửi: `feedback` bằng ngữ cảnh TV của tài khoản (`InnerTube.mtFeedback`, như FeedbackApiHelper). HTTP 2xx là thành công
 *    (VideoMenuPresenter.sendFeedbackPhone).
 */
object ShortsFeedback {
    enum class Kind { NOT_INTERESTED, DONT_RECOMMEND_CHANNEL }

    suspend fun send(account: Account?, video: VideoItem, kind: Kind) {
        checkNotNull(account) { "Cần đăng nhập" }
        val root = InnerTube.mtReelDetails(video.id, video.reelParams)
        val tokens = menuTokens(root)
        val token = tokens.getOrNull(kind.ordinal)
            ?: error("YouTube không cung cấp thao tác này cho video này")
        val code = InnerTube.mtFeedback(account, token)
        if (code !in 200..299) error("YouTube từ chối thao tác này (HTTP $code)")
    }

    /** MenuWrapper.getFeedbackTokens: mỗi mục menu có token thì lấy token đó, giữ nguyên thứ tự. */
    private fun menuTokens(root: JSONObject): List<String> {
        val items = root.optJSONObject("overlay")?.optJSONObject("reelPlayerOverlayRenderer")
            ?.optJSONObject("menu")?.optJSONObject("menuRenderer")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until items.length()) {
            tokenOf(items.optJSONObject(i)?.optJSONObject("menuServiceItemRenderer"))?.let { out.add(it) }
        }
        return out
    }

    /** MenuItem.getFeedbackToken: serviceEndpoint.feedbackEndpoint, không có thì command (kể cả popup/commandExecutor). */
    private fun tokenOf(item: JSONObject?): String? {
        item ?: return null
        item.optJSONObject("serviceEndpoint")?.optJSONObject("feedbackEndpoint")?.optString("feedbackToken")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return first(item.optJSONObject("command"))
    }

    private fun first(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotBlank() }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) first(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) first(node.opt(i))?.let { return it }
        }
        return null
    }
}
