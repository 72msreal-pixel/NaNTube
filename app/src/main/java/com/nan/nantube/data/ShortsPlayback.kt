package com.nan.nantube.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONObject

/** Thông tin kênh của một Short, lấy từ videoDetails của /player (feed Shorts không kèm kênh). */
data class ShortMeta(val author: String?, val channelId: String?, val views: String?)

/**
 * Tải trước và nhớ tệp phát của Shorts. Việc tải chạy ở scope riêng nên vuốt qua video khác không làm hủy
 * lượt tải đang chạy; cùng một video được hỏi nhiều lần (đang xem + tải trước) chỉ tải một lần.
 */
object ShortsPlayback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = HashMap<String, Deferred<File>>()
    private val metas = ConcurrentHashMap<String, ShortMeta>()

    fun meta(videoId: String): ShortMeta? = metas[videoId]

    fun request(context: Context, account: Account?, video: VideoItem): Deferred<File> {
        val app = context.applicationContext
        synchronized(inflight) {
            inflight[video.id]?.let { return it }
            val job = scope.async {
                try {
                    VideoDownloads.playbackFile(app, account, video) { details ->
                        val views = details.optString("viewCount").toLongOrNull()
                        metas[video.id] = ShortMeta(
                            author = details.optString("author").ifBlank { null },
                            channelId = details.optString("channelId").ifBlank { null },
                            views = views?.let { "%,d lượt xem".format(it) }
                        )
                    }
                } finally {
                    synchronized(inflight) { inflight.remove(video.id) }
                }
            }
            inflight[video.id] = job
            return job
        }
    }

    /** Đăng ký / hủy đăng ký kênh của Short; tên kênh dùng làm tiêu đề thẻ. */
    suspend fun subscribe(account: Account, meta: ShortMeta, subscribe: Boolean) {
        val id = meta.channelId ?: error("Chưa biết kênh của video này")
        ChannelSubscriptions.set(account, SearchResult.Channel(id, meta.author ?: id, null, null, null), subscribe)
    }

    /** Thích / bỏ thích video bằng tài khoản đang chọn. */
    suspend fun like(account: Account, videoId: String, like: Boolean) {
        val body = JSONObject().put("target", JSONObject().put("videoId", videoId))
        InnerTube.callAny(if (like) "like/like" else "like/removelike", body, account)
    }
}
