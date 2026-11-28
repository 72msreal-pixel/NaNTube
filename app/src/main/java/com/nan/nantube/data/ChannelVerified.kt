package com.nan.nantube.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/**
 * Kênh có dấu tích xác minh hay không, tra thẳng từ đầu trang kênh (InnerTube `browse`, client WEB).
 *
 * Dùng khi bình luận không kèm cờ `isVerified` (hay gặp ở bình luận của chủ video như MrBeast: YouTube chỉ báo isCreator
 * nên app chỉ vẽ viên sáng mà thiếu tích). Kết quả nhớ theo mã kênh; lỗi mạng thì không nhớ để lần sau thử lại.
 */
object ChannelVerified {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    // Mọi dấu hiệu YouTube dùng cho tích xác minh / nghệ sĩ chính thức trong phần header của kênh.
    private val MARKERS = listOf(
        "BADGE_STYLE_TYPE_VERIFIED", // gồm cả BADGE_STYLE_TYPE_VERIFIED_ARTIST
        "CHECK_CIRCLE_THICK",
        "CHECK_CIRCLE_FILLED",
        "OFFICIAL_ARTIST_BADGE"
    )

    private val known = ConcurrentHashMap<String, Boolean>()
    private val gate = Semaphore(3)

    /** Đã biết (không gọi mạng); null = chưa tra. */
    fun cached(channelId: String?): Boolean? = channelId?.let { known[it] }

    /** Tra kênh [channelId] (UC…). Trả false nếu không xác minh hoặc không tra được. */
    suspend fun check(channelId: String?): Boolean {
        val id = channelId?.takeIf { it.startsWith("UC") } ?: return false
        known[id]?.let { return it }
        return try {
            gate.withPermit {
                known[id]?.let { return@withPermit it }
                val client = JSONObject().put("clientName", "WEB").put("clientVersion", "2.20250120.01.00")
                    .put("hl", "en").put("gl", "US")
                val body = JSONObject().put("context", JSONObject().put("client", client)).put("browseId", id)
                val res = Http.postJson(
                    "${BASE}browse?prettyPrint=false&key=$WEB_KEY", body,
                    mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
                )
                if (res.code !in 200..299) return@withPermit false
                val header = JSONObject(res.body).optJSONObject("header")?.toString().orEmpty()
                val verified = MARKERS.any { header.contains(it) }
                known[id] = verified
                verified
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
