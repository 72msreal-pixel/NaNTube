package com.nan.nantube.data

import android.content.Context
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gợi ý từ khóa khi gõ vào ô tìm kiếm. Dùng đúng máy chủ gợi ý mà meetube/MediaServiceCore dùng
 * (clients1.google.com/complete/search, client youtube), có thêm endpoint dự phòng.
 */
object SearchSuggest {
    /** Số gợi ý tối đa hiện khi gõ. */
    const val MAX_SUGGESTIONS = 20

    private val HTML_TAG = Regex("<[^>]*>")

    /** Gợi ý khi gõ; đã đăng nhập thì gửi kèm tài khoản nên YouTube trộn cả từ khóa đã tìm của tài khoản đó vào gợi ý. */
    suspend fun suggestions(query: String, account: Account? = null): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return fetch(q, account)
    }

    /**
     * Nhật ký tìm kiếm của tài khoản trên YouTube: gợi ý với ô trống + token tài khoản (cách meetube lấy
     * danh sách tìm kiếm gần đây). Chưa đăng nhập hoặc lỗi thì trả rỗng.
     */
    suspend fun accountHistory(account: Account?): List<String> =
        if (account == null) emptyList() else fetch("", account)

    /** Xóa toàn bộ nhật ký tìm kiếm của tài khoản trên YouTube (history/clear_search_history như meetube). */
    suspend fun clearAccountHistory(account: Account) {
        InnerTube.callAny("history/clear_search_history", JSONObject().put("nop", "false"), account)
    }

    private suspend fun fetch(q: String, account: Account?): List<String> {
        val enc = URLEncoder.encode(q, "UTF-8")
        val urls = ArrayList<String>()
        // Tài khoản web: YouTube web hỏi máy chủ gợi ý của chính youtube.com kèm cookie (nhật ký tìm kiếm nằm ở đây);
        // chữ ký SAPISIDHASH tính theo origin youtube.com nên không dùng được với clients1.google.com.
        val webUrl = if (account?.kind == LoginKind.WEB)
            "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&hl=vi&gl=VN&gs_ri=youtube&ds=yt&cp=1&xhr=t&oe=utf-8&xssi=t&authuser=${account.authUser}&q=$enc"
        else null
        webUrl?.let { urls.add(it) }
        // Giống MediaServiceCore (meetube): client youtube-lr, đúng danh sách gợi ý của YouTube.
        urls.add("https://clients1.google.com/complete/search?client=youtube-lr&ds=yt&xhr=t&oe=utf-8&xssi=t&hl=vi&gl=VN&q=$enc")
        urls.add("https://clients1.google.com/complete/search?client=youtube&ds=yt&hl=vi&gl=VN&xhr=t&oe=utf-8&xssi=t&q=$enc")
        urls.add("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=vi&oe=utf-8&q=$enc")
        val auth = try {
            InnerTube.authHeaders(account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
        val webHeaders = if (account?.kind == LoginKind.WEB)
            auth + mapOf("Origin" to InnerTube.ORIGIN, "Referer" to InnerTube.ORIGIN + "/")
        else auth
        for (url in urls) {
            try {
                // Chữ ký web chỉ hợp lệ với youtube.com; các máy chủ Google khác chỉ nhận Bearer của tài khoản mã.
                val headers = when {
                    url === webUrl -> webHeaders
                    account?.kind == LoginKind.WEB -> emptyMap()
                    else -> auth
                }
                val res = Http.get(url, headers)
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
            if (out.size >= MAX_SUGGESTIONS) break
        }
        return out.toList()
    }
}

/**
 * Lịch sử từ khóa đã tìm, lưu cục bộ (tối đa 30), mới nhất đứng đầu. Lưu cho MỌI người dùng (khách và tài khoản): trước đây
 * tài khoản chỉ trông chờ YouTube trả nhật ký nên khi máy chủ không trả (hay phiên web không hợp lệ) thì mục Nhật ký trống trơn.
 * Mỗi tài khoản một danh sách riêng ([owner] = id tài khoản, null = khách).
 */
object SearchHistory {
    private const val PREFS = "nantube_search"
    private const val KEY = "history"
    private const val MAX = 30

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(owner: String?) = if (owner == null) KEY else "$KEY:$owner"

    fun load(context: Context, owner: String? = null): List<String> = try {
        val arr = JSONArray(prefs(context).getString(key(owner), "[]"))
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(context: Context, owner: String?, list: List<String>) {
        val arr = JSONArray()
        list.take(MAX).forEach { arr.put(it) }
        prefs(context).edit().putString(key(owner), arr.toString()).apply()
    }

    fun add(context: Context, query: String, owner: String? = null) {
        val q = query.trim()
        if (q.isEmpty()) return
        save(context, owner, listOf(q) + load(context, owner).filter { !it.equals(q, ignoreCase = true) })
    }

    fun remove(context: Context, query: String, owner: String? = null) =
        save(context, owner, load(context, owner).filter { !it.equals(query, ignoreCase = true) })

    fun clear(context: Context, owner: String? = null) = save(context, owner, emptyList())

    // YouTube không cho xóa riêng từng mục nhật ký tài khoản, nên mục bị xóa được ẩn đi trong máy (không đụng tới các mục khác).
    private fun hiddenKey(owner: String?) = "hidden:" + (owner ?: "guest")

    fun hidden(context: Context, owner: String? = null): Set<String> = try {
        val arr = JSONArray(prefs(context).getString(hiddenKey(owner), "[]"))
        List(arr.length()) { arr.optString(it).lowercase() }.filter { it.isNotBlank() }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    fun hide(context: Context, query: String, owner: String? = null) {
        val arr = JSONArray()
        (hidden(context, owner) + query.trim().lowercase()).take(200).forEach { arr.put(it) }
        prefs(context).edit().putString(hiddenKey(owner), arr.toString()).apply()
    }

    /** Tìm lại một từ thì cho hiện lại trong nhật ký. */
    fun unhide(context: Context, query: String, owner: String? = null) {
        val set = hidden(context, owner) - query.trim().lowercase()
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs(context).edit().putString(hiddenKey(owner), arr.toString()).apply()
    }
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
