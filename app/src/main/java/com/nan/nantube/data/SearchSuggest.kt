package com.nan.nantube.data

import android.content.Context
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.json.JSONArray

/**
 * Gợi ý từ khóa khi gõ vào ô tìm kiếm. Dùng đúng máy chủ gợi ý mà meetube/MediaServiceCore dùng
 * (clients1.google.com/complete/search, client youtube), có thêm endpoint dự phòng.
 */
object SearchSuggest {
    private val HTML_TAG = Regex("<[^>]*>")

    suspend fun suggestions(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val enc = URLEncoder.encode(q, "UTF-8")
        val urls = listOf(
            "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=vi&oe=utf-8&q=$enc",
            "https://clients1.google.com/complete/search?client=youtube&ds=yt&hl=vi&gl=VN&xhr=t&oe=utf-8&xssi=t&q=$enc"
        )
        for (url in urls) {
            try {
                val res = Http.get(url)
                if (res.code in 200..299) {
                    val list = parse(res.body)
                    if (list.isNotEmpty()) return list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Thử endpoint kế tiếp.
            }
        }
        return emptyList()
    }

    /**
     * Hai dạng phản hồi: `["từ khóa",["gợi ý 1","gợi ý 2"]]` (client firefox) và
     * `)]}'\n["từ khóa",[["gợi ý 1<b>…</b>",0,[…]],…],{…}]` (client youtube, có tiền tố chống XSSI).
     */
    internal fun parse(body: String): List<String> {
        val start = body.indexOf('[')
        if (start < 0) return emptyList()
        val root = try {
            JSONArray(body.substring(start))
        } catch (_: Exception) {
            return emptyList()
        }
        val list = root.optJSONArray(1) ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until list.length()) {
            val raw = when (val item = list.opt(i)) {
                is String -> item
                is JSONArray -> item.optString(0)
                else -> ""
            }
            val text = raw.replace(HTML_TAG, "").trim()
            if (text.isNotEmpty()) out.add(text)
            if (out.size >= 10) break
        }
        return out.toList()
    }
}

/** Lịch sử từ khóa đã tìm, lưu cục bộ (tối đa 30), mới nhất đứng đầu. */
object SearchHistory {
    private const val PREFS = "nantube_search"
    private const val KEY = "history"
    private const val MAX = 30

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<String> = try {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.take(MAX).forEach { arr.put(it) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        save(context, listOf(q) + load(context).filter { !it.equals(q, ignoreCase = true) })
    }

    fun remove(context: Context, query: String) =
        save(context, load(context).filter { it != query })

    fun clear(context: Context) = save(context, emptyList())
}

/**
 * Từ khóa hot để đề xuất khi ô tìm kiếm còn trống: top tìm kiếm trên Google ở Việt Nam
 * (RSS Google Trends, không cần khóa), nhớ 3 giờ trong máy. Thiếu / lỗi mạng thì bù bằng [FALLBACK]
 * (tên và chủ đề hay được tìm trên YouTube) để luôn đủ 10 từ.
 */
object HotSearches {
    private const val PREFS = "nantube_search"
    private const val KEY = "hot"
    private const val KEY_TIME = "hot_time"
    private const val TTL_MS = 3 * 60 * 60 * 1000L
    const val COUNT = 10

    val FALLBACK = listOf(
        "mrbeast", "sơn tùng m-tp", "nhạc trẻ hot", "tiktok trend", "highlight bóng đá",
        "roblox", "minecraft", "free fire", "liên quân mobile", "podcast"
    )

    private val ITEM = Regex("<item>.*?<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)

    /** Đọc cache ngay (có thể rỗng) để hiện tức thì. */
    fun cached(context: Context): List<String> = try {
        val arr = JSONArray(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        pad(List(arr.length()) { arr.optString(it) })
    } catch (_: Exception) {
        FALLBACK.take(COUNT)
    }

    /** Lấy danh sách mới nếu cache cũ hơn 3 giờ; luôn trả đủ [COUNT] từ. */
    suspend fun load(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0L) < TTL_MS
        if (fresh) return cached(context)
        val trends = try {
            val res = Http.get("https://trends.google.com/trending/rss?geo=VN")
            if (res.code in 200..299) parse(res.body) else emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (trends.isNotEmpty()) {
            val arr = JSONArray()
            trends.take(COUNT).forEach { arr.put(it) }
            prefs.edit().putString(KEY, arr.toString()).putLong(KEY_TIME, System.currentTimeMillis()).apply()
        }
        return pad(trends)
    }

    internal fun parse(xml: String): List<String> =
        ITEM.findAll(xml).map { unescape(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }.distinct().toList()

    private fun pad(list: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        list.filter { it.isNotBlank() }.forEach { out.add(it) }
        for (f in FALLBACK) {
            if (out.size >= COUNT) break
            if (out.none { it.equals(f, ignoreCase = true) }) out.add(f)
        }
        return out.take(COUNT)
    }

    private fun unescape(s: String): String =
        s.removePrefix("<![CDATA[").removeSuffix("]]>")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
}
