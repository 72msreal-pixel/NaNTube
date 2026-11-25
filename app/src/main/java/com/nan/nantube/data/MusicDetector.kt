package com.nan.nantube.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nhận diện NHẠC trong các danh sách video (Video đã thích, Xem sau, Video của bạn, Đã xem...).
 *
 * Thẻ danh sách của YouTube chỉ gắn icon ♪ cho một số video, còn phần đoán theo tiêu đề / tên kênh ([VideoItem.isMusic]) bỏ
 * sót rất nhiều bài (vd "KẺ SAY TÌNH - SKYNote 2025" của kênh "Quốc Thiên Official"), nên bài hát bị tính là video thường. Ở
 * đây hỏi thẳng YouTube Music ([MusicRepository.videoType]): MV chính thức (OMV) và bản âm thanh (ATV) là nhạc. Kết quả nhớ
 * vào máy nên mỗi video chỉ hỏi một lần; hỏi lỗi thì "chưa biết", thử lại sau [RETRY_MS].
 *
 * Chỉ hỏi video đã đủ dài để là một bài hát (30 giây tới 20 phút), chưa là Short, chưa chắc là nhạc.
 */
object MusicDetector {
    private const val PARALLEL = 4
    private const val RETRY_MS = 60_000L
    private const val PER_ID_BUDGET_MS = 10_000L
    private const val PREFS = "music_detector"
    private const val PREFS_KEY = "v1"
    private const val MAX_SAVED = 6_000
    private const val MIN_SEC = 30
    private const val MAX_SEC = 20 * 60

    private val known = ConcurrentHashMap<String, Boolean>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(PREFS_KEY, null)?.split(',')?.forEach { e ->
            if (e.length == 13 && e[11] == ':') known.putIfAbsent(e.substring(0, 11), e[12] == '1')
        }
    }

    private fun save() {
        val p = prefs ?: return
        val all = known.entries.toList().takeLast(MAX_SAVED)
        p.edit().putString(PREFS_KEY, all.joinToString(",") { it.key + ":" + if (it.value) "1" else "0" }).apply()
    }

    /** Áp kết quả đã biết lên một video (không gọi mạng). */
    fun applyCached(v: VideoItem): VideoItem = if (!v.music && known[v.id] == true) v.copy(music = true) else v

    fun needsCheck(v: VideoItem): Boolean {
        if (v.isShort || v.live || v.music || v.id.length != 11 || known.containsKey(v.id)) return false
        failedAt[v.id]?.let { if (System.currentTimeMillis() - it < RETRY_MS) return false }
        val sec = ShortDetector.seconds(v.duration) ?: return false
        return sec in MIN_SEC..MAX_SEC
    }

    /** Kiểm tra [ids] (song song có giới hạn): id -> có phải nhạc. Hỏi không được thì không có trong kết quả. */
    suspend fun check(ids: List<String>): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        for (chunk in ids.distinct().chunked(PARALLEL)) {
            val results = coroutineScope {
                chunk.map { id -> async(Dispatchers.IO) { id to withTimeoutOrNull(PER_ID_BUDGET_MS) { probe(id) } } }.awaitAll()
            }
            var changed = false
            for ((id, isMusic) in results) {
                if (isMusic != null) {
                    known[id] = isMusic
                    failedAt.remove(id)
                    out[id] = isMusic
                    changed = true
                } else failedAt[id] = System.currentTimeMillis()
            }
            if (changed) save()
        }
        return out
    }

    private suspend fun probe(id: String): Boolean? = try {
        val type = MusicRepository.videoType(id, ShortDetector.account)
        type == "MUSIC_VIDEO_TYPE_OMV" || type == "MUSIC_VIDEO_TYPE_ATV"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
