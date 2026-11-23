package com.nan.nantube.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Nhận diện Short trong mọi danh sách (một chỗ chung: [resolve]).
 *
 * Thẻ danh sách của YouTube không đáng tin để phân biệt Short: huy hiệu SHORTS chỉ có ở Short ngắn, Short dài tới 3 phút là
 * thẻ video thường, còn các cách đoán (thời lượng, "#" trong tiêu đề, ảnh bìa, chuyển hướng /shorts/) đều nhận nhầm video
 * ngang và nhạc thành Short (meetube cũng dính lỗi này). Nên ở đây KHÔNG đoán và KHÔNG hỏi "có phải Short không" qua web.
 *
 * Cách làm: hỏi chính YouTube về video bằng `/player` (đúng các nguồn và thứ tự của meetube mà trình phát Short và nút
 * Tải xuống của NaNTube đang dùng, qua [PlaybackSource.walk]) rồi đọc KÍCH THƯỚC THẬT của luồng video. Định nghĩa
 * của YouTube cho Short là video ≤ 3 phút có khung dọc hoặc vuông, nên:
 *   Short  <=> thời lượng ≤ 180 giây VÀ khung dọc (rộng/cao ≤ 0,8; khung vuông của nhạc/tuyển tập không tính).
 * Video ngang (16:9) và nhạc không bao giờ khớp. Không đọc được (không phát được, lỗi mạng) thì "chưa biết": không nhớ,
 * thử lại sau [RETRY_MS]. Kết quả nhớ vào máy nên mỗi video chỉ hỏi một lần. Quyết định từng video ghi ở logcat, tag
 * "ShortDetector".
 *
 * Chỉ hỏi video đã nêu thời lượng ≤ 3 phút và chưa được YouTube đánh dấu Short, nên hầu hết danh sách không tốn thêm gì.
 */
object ShortDetector {
    private const val TAG = "ShortDetector"
    private const val MAX_SHORT_SEC = 180
    private const val PARALLEL = 4
    private const val RETRY_MS = 60_000L
    private const val PER_ID_BUDGET_MS = 12_000L
    private const val PREFS = "short_detector"
    // Khóa mới: bỏ kết quả cũ (bản trước nhận nhầm nhạc / tuyển tập khung vuông là Short).
    private const val PREFS_KEY = "v2"
    // Tiêu đề kiểu tuyển tập / nhạc dài: không bao giờ đoán là Short.
    private val NOT_SHORT_TITLE = Regex(
        "tuyển tập|tổng hợp|compilation|best of|full (album|episode|movie)|non-?stop|playlist|\\d+\\s*(hours?|giờ)",
        RegexOption.IGNORE_CASE
    )
    private const val MAX_SAVED = 6_000

    private val known = ConcurrentHashMap<String, Boolean>()
    private val failedAt = ConcurrentHashMap<String, Long>() // chưa biết: thử lại sau RETRY_MS, không dồn dập
    @Volatile private var prefs: android.content.SharedPreferences? = null
    @Volatile private var appContext: Context? = null

    /**
     * Tài khoản đang dùng (do các hàm tải danh sách đặt). Video riêng tư / không phát được khi chưa đăng nhập thì hỏi lại
     * /player kèm tài khoản này: chủ video xem được nên vẫn đọc được kích thước.
     */
    @Volatile var account: Account? = null

    /** Nạp kết quả đã nhớ từ lần chạy trước. Gọi một lần ở MainActivity. */
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(PREFS_KEY, null)?.split(',')?.forEach { e ->
            val i = e.indexOf(':')
            if (i == 11 && e.length == 13) known.putIfAbsent(e.substring(0, 11), e[12] == '1')
        }
    }

    private fun save() {
        val p = prefs ?: return
        val all = known.entries.toList().takeLast(MAX_SAVED)
        p.edit().remove("v").putString(PREFS_KEY, all.joinToString(",") { it.key + ":" + if (it.value) "1" else "0" }).apply()
    }

    /** Áp kết quả đã biết lên một video (không gọi mạng). */
    fun applyCached(v: VideoItem): VideoItem = if (!v.isShort && known[v.id] == true) v.copy(isShort = true) else v

    /** Có đáng hỏi không: chưa là Short, chưa từng kiểm tra, và đủ ngắn để có thể là Short. */
    fun needsCheck(v: VideoItem): Boolean {
        if (v.isShort || v.live || v.id.length != 11 || known.containsKey(v.id)) return false
        if (v.isMusic || NOT_SHORT_TITLE.containsMatchIn(v.title)) return false
        failedAt[v.id]?.let { if (System.currentTimeMillis() - it < RETRY_MS) return false }
        val sec = seconds(v.duration) ?: return false
        return sec in 1..MAX_SHORT_SEC
    }

    /**
     * Kết quả chắc chắn từ nguồn có nhãn của YouTube (vd tab Shorts của kênh): [shorts] là Short, [others] thì không.
     * Ghi đè mọi kết quả đoán trước đó.
     */
    fun declare(shorts: Collection<String>, others: Collection<String>) {
        if (shorts.isEmpty() && others.isEmpty()) return
        for (id in others) if (id.length == 11) { known[id] = false; failedAt.remove(id) }
        for (id in shorts) if (id.length == 11) { known[id] = true; failedAt.remove(id) }
        save()
    }

    /** "2:55" -> 175, "1:02:03" -> 3723; không đọc được thì null. */
    internal fun seconds(duration: String?): Int? {
        val parts = duration?.trim()?.split(':') ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        var total = 0
        for (p in parts) total = total * 60 + (p.trim().toIntOrNull() ?: return null)
        return total
    }

    /**
     * Chỗ chung cho mọi danh sách: áp kết quả đã nhớ, rồi kiểm tra các video còn nghi ngờ, tối đa [budgetMs] cho cả lượt.
     * Hết hạn thì video chưa kiểm tra giữ nguyên (các màn danh sách dài vẫn quét nền tiếp bằng [check]).
     */
    suspend fun resolve(items: List<VideoItem>, budgetMs: Long = 4_000L): List<VideoItem> {
        val todo = items.map(::applyCached).filter { needsCheck(it) }.map { it.id }
        if (todo.isNotEmpty()) withTimeoutOrNull(budgetMs) { check(todo) }
        // check() ghi vào bộ nhớ ngay từng đợt nên dù hết hạn vẫn có kết quả của các đợt đã xong.
        return items.map(::applyCached)
    }

    /** Kiểm tra [ids] (song song có giới hạn); trả về id -> có phải Short. Id không xác định được thì không có trong kết quả. */
    suspend fun check(ids: List<String>): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        for (chunk in ids.distinct().chunked(PARALLEL)) {
            val results = coroutineScope {
                chunk.map { id -> async(Dispatchers.IO) { id to withTimeoutOrNull(PER_ID_BUDGET_MS) { probe(id) } } }.awaitAll()
            }
            var changed = false
            for ((id, isShort) in results) {
                if (isShort != null) {
                    known[id] = isShort
                    failedAt.remove(id)
                    out[id] = isShort
                    changed = true
                } else {
                    failedAt[id] = System.currentTimeMillis()
                }
            }
            if (changed) save()
        }
        return out
    }

    /**
     * Hỏi /player theo thứ tự của meetube: nguồn đầu cho kết luận thì dùng, không thì thử nguồn sau. Tài khoản mã đi cùng
     * TV_TIZEN nên video riêng tư của chủ kênh vẫn đọc được.
     */
    private suspend fun probe(id: String): Boolean? {
        val context = appContext ?: return null
        val refusals = PlaybackRefusals()
        val isShort = PlaybackSource.walk(context, account, id, refusals = refusals, probeOnly = true) { answer ->
            verdict(answer.json).also { Log.d(TAG, "$id: ${answer.source.name} -> ${describe(answer.json)} => $it") }
        }
        if (isShort == null) Log.d(TAG, "$id: không đọc được ($refusals)")
        return isShort
    }

    /** Kết luận từ một phản hồi /player: true = Short, false = không, null = không đủ dữ liệu. */
    internal fun verdict(json: JSONObject): Boolean? {
        if ((json.dig("playabilityStatus", "status") as? String) != "OK") return null
        if (json.dig("videoDetails", "isLive") == true || json.dig("videoDetails", "isUpcoming") == true) return false
        val sec = json.dig("videoDetails", "lengthSeconds")?.toString()?.toIntOrNull()
        if (sec != null && sec > MAX_SHORT_SEC) return false
        // YouTube nói rõ video không đủ điều kiện làm Short thì tin theo.
        if (json.dig("microformat", "playerMicroformatRenderer", "isShortsEligible") == false) return false
        val (w, h) = dimensions(json) ?: return null
        // Chỉ khung DỌC thật (9:16, 3:4, 4:5: rộng/cao <= 0,8). Khung vuông (1:1) là ảnh bìa của nhạc / tuyển tập, không phải Short.
        return w * 10 <= h * 8
    }

    /** Rộng x cao của luồng video: ưu tiên luồng thật, rồi khung nhúng của microformat. */
    internal fun dimensions(json: JSONObject): Pair<Int, Int>? {
        val data = json.optJSONObject("streamingData")
        for (key in listOf("adaptiveFormats", "formats")) {
            val arr = data?.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                if (!f.optString("mimeType").startsWith("video/")) continue
                val w = f.optInt("width")
                val h = f.optInt("height")
                if (w > 0 && h > 0) return w to h
            }
        }
        val embed = json.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")?.optJSONObject("embed")
        val w = embed?.opt("width")?.toString()?.toIntOrNull() ?: 0
        val h = embed?.opt("height")?.toString()?.toIntOrNull() ?: 0
        return if (w > 0 && h > 0) w to h else null
    }

    private fun describe(json: JSONObject): String =
        "status=${json.dig("playabilityStatus", "status")} len=${json.dig("videoDetails", "lengthSeconds")} dim=${dimensions(json)}"
}
