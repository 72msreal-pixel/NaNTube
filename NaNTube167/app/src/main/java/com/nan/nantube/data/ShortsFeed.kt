package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Feed Short lấy theo cách meetube (BrowseService2.getShortsWeb): reel_item_watch (SEEDLESS) cho video đầu + token,
 * reel_watch_sequence cho các trang kế. Mỗi lần gọi YouTube trả bộ video khác nhau; video đã xem ([ShortsSeen]) và quảng cáo bị bỏ.
 */
object ShortsFeed {
    private const val PREFIX = "reel:"
    private const val MAX_EMPTY_PAGES = 5

    fun owns(token: String?) = token?.startsWith(PREFIX) == true

    /** Trang đầu; null khi nguồn này không dùng được (để rơi về tìm kiếm). */
    suspend fun first(account: Account?): Paged<VideoItem>? {
        val root = try {
            call("reel/reel_item_watch", account) {
                put("disablePlayerResponse", true)
                put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
                put("params", "CA8%3D")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        val head = root.optJSONObject("replacementEndpoint")?.optJSONObject("reelWatchEndpoint")
        val firstId = head?.optString("videoId")?.takeIf { it.length == 11 && head?.optJSONObject("adClientParams")?.optBoolean("isAd") != true }
        val token = root.optString("sequenceContinuation").ifBlank { null }
            ?: root.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }

        val all = ArrayList<VideoItem>()
        firstId?.let { all.add(item(it, head?.optString("params"), head?.optString("playerParams"))) }
        var next = token
        var pages = 0
        // Đủ video mới thì dừng; không thì lấy tiếp vài trang (cùng cách ShortsFeedTail của meetube).
        while (next != null && fresh(all).size < 6 && pages < MAX_EMPTY_PAGES) {
            val page = try {
                sequence(account, next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
            all.addAll(page.items)
            next = page.token?.removePrefix(PREFIX)
            pages++
        }
        val picked = fresh(all).ifEmpty { all }.distinctBy { it.id }
        if (picked.isEmpty()) return null
        return Paged(picked, next?.let { PREFIX + it })
    }

    suspend fun more(account: Account?, token: String): Paged<VideoItem> {
        var next: String? = token.removePrefix(PREFIX)
        val out = ArrayList<VideoItem>()
        var pages = 0
        while (next != null && out.isEmpty() && pages < MAX_EMPTY_PAGES) {
            val page = sequence(account, next)
            out.addAll(fresh(page.items))
            next = page.token?.removePrefix(PREFIX)
            pages++
        }
        return Paged(out, next?.let { PREFIX + it })
    }

    private fun fresh(list: List<VideoItem>) = list.filter { !ShortsSeen.contains(it.id) }

    private suspend fun sequence(account: Account?, token: String): Paged<VideoItem> {
        val root = call("reel/reel_watch_sequence", account) { put("sequenceParams", token) }
        val entries = root.optJSONArray("entries")
        val items = ArrayList<VideoItem>()
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val endpoint = entries.optJSONObject(i)?.optJSONObject("command")?.optJSONObject("reelWatchEndpoint") ?: continue
                if (endpoint.optJSONObject("adClientParams")?.optBoolean("isAd") == true) continue
                val id = endpoint.optString("videoId").takeIf { it.length == 11 } ?: continue
                items.add(item(id, endpoint.optString("params"), endpoint.optString("playerParams")))
            }
        }
        val next = root.optString("continuation").ifBlank { null }
            ?: root.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }
        return Paged(items, next?.let { PREFIX + it })
    }

    /** Reel chỉ cho mã video; tiêu đề + kênh được điền khi mở video (xem ShortsPlayback.info). */
    private fun item(id: String, params: String? = null, playerParams: String? = null) = VideoItem(
        id = id, title = "", channel = null, duration = null, views = null, isShort = true,
        reelParams = params?.ifBlank { null }, reelPlayerParams = playerParams?.ifBlank { null }
    )

    /** Thử bằng tài khoản (ngữ cảnh WEB), không được thì gọi ẩn danh như meetube. */
    private suspend fun call(endpoint: String, account: Account?, body: JSONObject.() -> Unit): JSONObject {
        if (account != null) {
            try {
                return InnerTube.call(endpoint, JSONObject().apply(body), account, asWeb = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return InnerTube.call(endpoint, JSONObject().apply(body), null)
    }
}
