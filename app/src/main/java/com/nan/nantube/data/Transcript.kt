package com.nan.nantube.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Bản chép lời đúng như `TranscriptSheet` của MeeTube (get_transcript bị từ chối nên bảng chính là track phụ đề mà /player đã trả):
 * - [pick]: bỏ bản dịch máy (`tlang=`) và bản canh thời gian của lồng tiếng (`variant=timing-optimized`); bản người đăng tải thắng bản tự tạo.
 * - [load]: một yêu cầu `fmt=json3` bằng OkHttp trơn (không UA / cookie / header riêng, như MusicLink.client của MeeTube), hạn 6 giây,
 *   mã khác 2xx thì báo `HTTP <mã>`. Mỗi `event` là một dòng: nối các `segs[].utf8`, gộp khoảng trắng, bỏ dòng rỗng.
 */
object Transcript {
    private val cache = ConcurrentHashMap<String, List<CaptionCue>>()
    private val FMT = Regex("([?&])fmt=[^&]*")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    fun pick(tracks: List<CaptionTrack>): CaptionTrack? {
        var auto: CaptionTrack? = null
        for (track in tracks) {
            val url = track.baseUrl
            if (url.isBlank() || "tlang=" in url || "variant=timing-optimized" in url) continue
            if (!track.auto) return track // bản của người đăng tải
            if (auto == null) auto = track
        }
        return auto
    }

    suspend fun load(track: CaptionTrack): List<CaptionCue> {
        // Link tương đối (client mweb) thì thêm tên miền, như CaptionTrack.getBaseUrlQuery của MediaServiceCore.
        val base = if (track.baseUrl.startsWith("/")) "https://www.youtube.com${track.baseUrl}" else track.baseUrl
        val replaced = FMT.replace(base, "$1fmt=json3")
        val url = if ("fmt=json3" in replaced) replaced else "$replaced&fmt=json3"
        cache[url]?.let { return it }
        val lines = parse(body(url))
        cache[url] = lines
        return lines
    }

    private suspend fun body(url: String): String = suspendCancellableCoroutine { cont ->
        val call = try {
            client.newCall(Request.Builder().url(url).build())
        } catch (e: IllegalArgumentException) {
            cont.resumeWithException(IOException("Link phụ đề không hợp lệ"))
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val text = it.body?.string() ?: throw IOException("HTTP ${it.code}")
                        if (cont.isActive) cont.resume(text)
                    }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    /** json3: {"events":[{"tStartMs":..,"segs":[{"utf8":".."}]}]}; sự kiện xuống dòng không có chữ. */
    private fun parse(body: String): List<CaptionCue> {
        val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
        val out = ArrayList<CaptionCue>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val segs = event.optJSONArray("segs") ?: continue
            val text = buildString {
                for (j in 0 until segs.length()) append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
            }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) continue
            val start = event.optLong("tStartMs")
            out.add(CaptionCue(start, start + event.optLong("dDurationMs").coerceAtLeast(1L), text))
        }
        return out
    }
}
