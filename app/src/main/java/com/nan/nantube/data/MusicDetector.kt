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
    /** Thời lượng ("3:45") của bài nhạc mà feed không ghi (thẻ Main chỉ có ♪), chỉ giữ trong lúc app chạy, không lưu xuống máy. */
    private val durations = ConcurrentHashMap<String, String>()
    private val durationFailedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(PREFS_KEY, null)?.split(',')?.forEach { e ->
            if (e.length == 13 && e[11] == ':') known.putIfAbsent(e.substring(0, 11), e[12] == '1')
        }
    }

    /** Thứ tự: tìm kiếm theo id (đường Tìm kiếm đã hiện đủ giờ cho tài khoản mã), rồi /player (videoDetails.lengthSeconds). */
    private suspend fun resolveDuration(id: String): String? {
        val viaSearch = try {
            VideoRepository.durationViaSearch(ShortDetector.account, id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (viaSearch != null) return viaSearch
        return ShortDetector.lengthSeconds(id)?.let(::clock)
    }

    private fun clock(sec: Int): String {
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun save() {
        val p = prefs ?: return
        val all = known.entries.toList().takeLast(MAX_SAVED)
        p.edit().putString(PREFS_KEY, all.joinToString(",") { it.key + ":" + if (it.value) "1" else "0" }).apply()
    }

    /** Áp kết quả đã biết lên một video (không gọi mạng). */
    fun applyCached(v: VideoItem): VideoItem {
        var out = v
        if (!out.music && known[out.id] == true) out = out.copy(music = true)
        if (out.duration == null && !out.live && !out.isShort) durations[out.id]?.let { out = out.copy(duration = it) }
        return out
    }

    /** Bài nhạc chưa có thời lượng (feed chỉ gắn ♪): cần hỏi /player để thẻ hiện "♪ 3:45". */
    fun needsDuration(v: VideoItem): Boolean {
        if (v.isShort || v.live || v.duration != null || !v.isMusic || v.id.length != 11 || durations.containsKey(v.id)) return false
        durationFailedAt[v.id]?.let { if (System.currentTimeMillis() - it < RETRY_MS) return false }
        return true
    }

    /** Lấy thời lượng cho các bài nhạc đang hiện mà thẻ không ghi, từng đợt nhỏ; mỗi đợt gọi [mark] với id -> thời lượng. */
    suspend fun fillDurations(current: () -> List<VideoItem>, mark: (Map<String, String>) -> Unit) {
        val tried = HashSet<String>()
        while (true) {
            val todo = current().filter { it.id !in tried && needsDuration(it) }.take(8)
            if (todo.isEmpty()) break
            tried.addAll(todo.map { it.id })
            val found = HashMap<String, String>()
            for (chunk in todo.map { it.id }.chunked(PARALLEL)) {
                val results = coroutineScope {
                    chunk.map { id ->
                        async(Dispatchers.IO) {
                            id to try {
                                withTimeoutOrNull(PER_ID_BUDGET_MS) { resolveDuration(id) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                }
                for ((id, d) in results) {
                    if (d != null) {
                        durations[id] = d
                        durationFailedAt.remove(id)
                        found[id] = d
                    } else durationFailedAt[id] = System.currentTimeMillis()
                }
            }
            if (found.isNotEmpty()) mark(found)
        }
    }

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

    /**
     * Đánh dấu nhạc cho một danh sách đang hiện (Main, Tìm kiếm), từng đợt nhỏ: sau mỗi đợt có bài nhạc thì gọi [mark] với các id đó.
     * [current] đọc lại danh sách mỗi vòng nên trang mới tải thêm cũng được xét.
     */
    suspend fun classify(current: () -> List<VideoItem>, mark: (Set<String>) -> Unit) {
        val tried = HashSet<String>()
        while (true) {
            val todo = current().filter { it.id !in tried && needsCheck(it) }.take(8)
            if (todo.isEmpty()) break
            tried.addAll(todo.map { it.id })
            val found = check(todo.map { it.id }).filterValues { it }.keys
            if (found.isNotEmpty()) mark(found)
        }
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
