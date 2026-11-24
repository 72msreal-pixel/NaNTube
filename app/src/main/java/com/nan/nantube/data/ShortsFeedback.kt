package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Không quan tâm" và "Không đề xuất kênh này" của menu Short. Thao tác là feedbackToken nằm trong menu của reel
 * (`reel/reel_item_watch` → overlay → menu), gửi bằng endpoint `feedback` giống nút xóa khỏi lịch sử.
 */
object ShortsFeedback {
    enum class Kind { NOT_INTERESTED, DONT_RECOMMEND_CHANNEL }

    suspend fun send(account: Account?, videoId: String, kind: Kind) {
        val token = findToken(account, videoId, kind)
            ?: error("YouTube không cung cấp thao tác này cho video này")
        val body = JSONObject().put("feedbackTokens", JSONArray().put(token))
        val result = InnerTube.callAny("feedback", body, account)
        check(result.dig("feedbackResponses", 0, "isProcessed") != false) {
            "YouTube chưa xác nhận thao tác"
        }
    }

    private suspend fun findToken(account: Account?, videoId: String, kind: Kind): String? {
        val request = {
            JSONObject()
                .put("disablePlayerResponse", true)
                .put("playerRequest", JSONObject().put("videoId", videoId).put("params", "8AEB"))
        }
        // Thử bằng tài khoản (ngữ cảnh WEB) trước vì menu có token của riêng tài khoản; không được thì gọi mặc định.
        val roots = ArrayList<JSONObject>()
        if (account != null) {
            try {
                roots += InnerTube.call("reel/reel_item_watch", request(), account, asWeb = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (roots.isEmpty() || roots.all { tokenIn(it, kind) == null }) {
            try {
                roots += InnerTube.call("reel/reel_item_watch", request(), account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (roots.isEmpty()) throw e
            }
        }
        return roots.firstNotNullOfOrNull { tokenIn(it, kind) }
    }

    private fun tokenIn(root: JSONObject, kind: Kind): String? {
        val entries = findAllKeys(root, "menuServiceItemRenderer") + findAllKeys(root, "listItemViewModel")
        for (entry in entries) {
            val title = (ytText(entry.opt("text")) ?: ytText(entry.opt("title"))).orEmpty().lowercase()
            val icon = findKey(entry, "icon")?.optString("iconType").orEmpty()
            val match = when (kind) {
                Kind.NOT_INTERESTED ->
                    title.contains("không quan tâm") || title.contains("not interested") || icon == "NOT_INTERESTED"
                Kind.DONT_RECOMMEND_CHANNEL ->
                    title.contains("không đề xuất kênh") || title.contains("don't recommend channel") ||
                        title.contains("don’t recommend channel")
            }
            if (!match) continue
            val token = findKey(entry, "feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }
}
