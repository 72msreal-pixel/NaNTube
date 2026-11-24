package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Không quan tâm" và "Không đề xuất kênh này" của menu Short, làm đúng như meetube/MediaServiceCore:
 * - Token lấy từ `reel/reel_item_watch` (ngữ cảnh WEB) với body `{disablePlayerResponse, params = params của reelWatchEndpoint,
 *   playerRequest:{videoId}}` (BrowseApiHelper.getReelDetailsQuery): playerRequest CHỈ có videoId, không có params riêng.
 *   meetube gọi bước này KHÔNG kèm tài khoản (getShortsWeb(auth=false)); bản cũ chỉ gọi kèm tài khoản nên menu không có mục phản hồi.
 * - Token nằm ở `overlay.reelPlayerOverlayRenderer.menu.menuRenderer.items`: mục thứ nhất là Không quan tâm, mục thứ hai là
 *   Không đề xuất kênh (ReelResult.getFeedbackTokens).
 * - `feedback` gửi bằng ngữ cảnh TV của tài khoản (FeedbackApiHelper.getNotInterestedQuery → createQueryTV).
 * Ngoài ra kiểm tra `isProcessed` để không báo thành công giả.
 */
object ShortsFeedback {
    enum class Kind { NOT_INTERESTED, DONT_RECOMMEND_CHANNEL }

    suspend fun send(account: Account?, video: VideoItem, kind: Kind) {
        checkNotNull(account) { "Cần đăng nhập" }
        val notes = ArrayList<String>()
        val token = findToken(account, video, kind, notes)
            ?: error("YouTube không cung cấp thao tác này cho video này (${notes.joinToString(" | ").ifEmpty { "không có dữ liệu" }})")
        val body = JSONObject().put("feedbackTokens", JSONArray().put(token))
        // Như meetube: ngữ cảnh TV của tài khoản trước; tài khoản mã thử thêm WEB + Bearer nếu TV không được.
        val order = if (account.kind == LoginKind.CODE) listOf(false, true) else listOf(false)
        var rejected = false
        var last: Exception? = null
        for (web in order) {
            try {
                val res = InnerTube.call("feedback", body, account, asWeb = web)
                val first = res.optJSONArray("feedbackResponses")?.optJSONObject(0)
                // Không có feedbackResponses (dạng trả khác) thì tin mã HTTP; có mà isProcessed=false là YouTube từ chối.
                if (first == null || first.optBoolean("isProcessed", true)) return
                rejected = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        if (rejected) error("YouTube chưa xác nhận thao tác này")
        throw last ?: IllegalStateException("YouTube không nhận thao tác này")
    }

    private suspend fun findToken(account: Account, video: VideoItem, kind: Kind, notes: MutableList<String>): String? {
        // Ẩn danh (như meetube) trước, rồi tới ngữ cảnh WEB của tài khoản, cuối cùng ngữ cảnh mặc định của tài khoản.
        // Cách đầu: mẫu WEB của meetube (phiên bản mới + visitorData) KÈM tài khoản. Menu ẩn danh có 16 mục nhưng không có mục
        // Không quan tâm (mục này chỉ có khi đã đăng nhập), nên phải gửi token/cookie của tài khoản.
        val modes = if (account.kind == LoginKind.CODE) listOf(false, true) else listOf(false)
        for (tv in modes) {
            for ((i, request) in requests(video).withIndex()) {
                val root = try {
                    InnerTube.commentRequest("reel/reel_item_watch", request, account, tv)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notes += "C${if (tv) "tv" else "w"}$i lỗi ${e.message?.take(25)}"
                    continue
                }
                tokenIn(root, kind)?.let { return it }
                notes += "C${if (tv) "tv" else "w"}$i ${summary(root)}"
            }
        }
        // Dự phòng: mẫu WEB của meetube, ẩn danh.
        for ((i, request) in requests(video).withIndex()) {
            val root = try {
                InnerTube.callMtWebAnonymous("reel/reel_item_watch", request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notes += "A$i lỗi ${e.message?.take(30)}"
                continue
            }
            tokenIn(root, kind)?.let { return it }
            notes += "A$i ${summary(root)}"
        }
        val accounts = ArrayList<Pair<Account?, Boolean>>()
        accounts += null to true
        accounts += account to true
        if (account.kind == LoginKind.CODE) accounts += account to false
        for ((acc, web) in accounts) {
            for ((i, request) in requests(video).withIndex()) {
                val tag = "${if (acc == null) "N" else "T"}${if (web) "w" else "t"}$i"
                val root = try {
                    InnerTube.call("reel/reel_item_watch", request, acc, asWeb = web)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notes += "$tag lỗi ${e.message?.take(30)}"
                    continue
                }
                tokenIn(root, kind)?.let { return it }
                notes += "$tag ${summary(root)}"
            }
        }
        return null
    }

    /** Body y hệt meetube (có `params` của reel, playerRequest chỉ có videoId); dự phòng: không `params`, rồi kiểu cũ "8AEB". */
    private fun requests(video: VideoItem): List<JSONObject> {
        fun base() = JSONObject().put("disablePlayerResponse", true)
        fun player(params: String? = null) = JSONObject().put("videoId", video.id).apply { params?.let { put("params", it) } }
        val list = ArrayList<JSONObject>()
        if (!video.reelParams.isNullOrBlank()) {
            list += base().put("params", video.reelParams).put("playerRequest", player())
        }
        list += base().put("playerRequest", player())
        list += base().put("playerRequest", player("8AEB"))
        return list
    }

    /** Tóm tắt ngắn để báo lỗi: số mục menu và có overlay hay không (không chứa token). */
    private fun summary(root: JSONObject): String {
        val menu = findAllKeys(root, "menuServiceItemRenderer").size + findAllKeys(root, "listItemViewModel").size
        val tokens = ArrayList<String>().also { collectTokens(root, it) }.size
        return "menu=$menu tk=$tokens${if (root.has("overlay")) "" else " noOverlay"}"
    }

    private fun collectTokens(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == "feedbackToken" && v is String && v.isNotBlank()) out.add(v) else collectTokens(v, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectTokens(node.opt(i), out)
        }
    }

    private class MenuItem(val icon: String, val token: String)

    private fun tokenIn(root: JSONObject, kind: Kind): String? {
        val entries = findAllKeys(root, "menuServiceItemRenderer") + findAllKeys(root, "listItemViewModel") +
            findAllKeys(root, "menuNavigationItemRenderer")
        val items = entries.mapNotNull { entry ->
            val token = findKey(entry, "feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MenuItem(findKey(entry, "icon")?.optString("iconType").orEmpty(), token)
        }
        val byIcon = items.firstOrNull { it.icon == if (kind == Kind.NOT_INTERESTED) "NOT_INTERESTED" else "REMOVE" }
        // Như meetube: mục có token thứ nhất = Không quan tâm, thứ hai = Không đề xuất kênh.
        val byOrder = items.getOrNull(kind.ordinal)
        // Không phụ thuộc ngôn ngữ: icon (như MenuWrapper.getVideoToken/getChannelToken của meetube) → thứ tự (như ReelResult.getFeedbackTokens). Không so chữ.
        val picked = (byIcon ?: byOrder)?.token
        if (picked != null) return picked
        // Cuối cùng: đúng hai feedbackToken trong cả phản hồi thì thứ tự là Không quan tâm, Không đề xuất kênh.
        val all = ArrayList<String>()
        collectTokens(root, all)
        return all.distinct().takeIf { it.size == 2 }?.get(kind.ordinal)
    }
}
