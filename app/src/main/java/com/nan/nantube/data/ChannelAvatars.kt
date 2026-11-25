package com.nan.nantube.data

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/**
 * Avatar của kênh đăng video (không phải avatar tài khoản đang dùng), dùng chung cho mọi nơi hiện thẻ video.
 *
 * Feed của client TV (tài khoản đăng nhập bằng mã) không kèm avatar kênh nên phải tra riêng, theo cách
 * `ChannelAvatars` của meetube / `getChannelAvatarUrl` của MediaServiceCore làm:
 *  1. Có mã kênh (UC…) → đọc đầu trang YouTube Music của kênh (vài KB) lấy ảnh đại diện.
 *  2. Chưa có mã kênh → hỏi menu ngữ cảnh của thẻ (get_panel) để biết kênh, rồi như bước 1.
 *  3. Không được thì tìm kênh bằng mã hoặc đúng tên (chỉ lọc "Kênh"), lấy ảnh của kênh trùng khớp.
 * Kết quả nhớ trong máy theo mã kênh và theo tên kênh; kênh không tra được thì không hỏi lại trong lần chạy này.
 */
object ChannelAvatars {
    private const val PREFS = "nantube_channel_avatars"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CHANNELS_ONLY = "EgIQAg=="

    private var appContext: Context? = null
    private val known = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val inflight = ConcurrentHashMap<String, Deferred<String?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)

    /** Nạp avatar đã nhớ từ lần chạy trước. Gọi một lần ở MainActivity. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        try {
            appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if (v is String && v.startsWith("http")) known[k] = v
            }
        } catch (_: Exception) {
        }
    }

    private fun idKey(id: String?) = id?.takeIf { it.startsWith("UC") }?.let { "id:$it" }
    private fun nameKey(name: String?) = name?.trim()?.takeIf { it.isNotEmpty() }?.let { "name:" + it.lowercase(Locale.ROOT) }

    /** Avatar đã biết (ngay lập tức, không gọi mạng) theo mã kênh rồi tên kênh. */
    fun cached(channelId: String?, name: String?): String? =
        idKey(channelId)?.let { known[it] } ?: nameKey(name)?.let { known[it] }

    private fun remember(channelId: String?, name: String?, url: String) {
        val keys = listOfNotNull(idKey(channelId), nameKey(name))
        val edit = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
        for (k in keys) {
            known[k] = url
            edit?.putString(k, url)
        }
        edit?.apply()
    }

    /** Một thẻ video cần avatar: mã kênh nếu YouTube đưa sẵn, tên kênh, và menu ngữ cảnh (khi không có mã). */
    class Request(
        val channelId: String?,
        val name: String?,
        val panelId: String? = null,
        val panelParams: String? = null,
        /** Video chứa thẻ này: chỗ cuối để biết kênh đăng (chủ video) khi thẻ là kênh nhạc / cộng tác nhiều nghệ sĩ. */
        val videoId: String? = null
    )

    /**
     * Tra avatar (chạy được từ coroutine của giao diện; hủy coroutine không hủy lượt tra đang dùng chung).
     * Trả null nếu kênh không có ảnh / không tra được.
     */
    suspend fun resolve(req: Request): String? {
        cached(req.channelId, req.name)?.let { return it }
        val key = idKey(req.channelId) ?: nameKey(req.name) ?: req.panelId?.let { "panel:$it:${req.panelParams}" }
            ?: req.videoId?.let { "video:$it" } ?: return null
        if (key in missing) return null
        val job = inflight.computeIfAbsent(key) {
            scope.async {
                try {
                    gate.withPermit { lookup(req) }
                } finally {
                    inflight.remove(key)
                }
            }
        }
        return try {
            val url = job.await()
            if (url == null) missing.add(key)
            url
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Lỗi mạng: không đánh dấu "không có", lần cuộn sau sẽ thử lại.
        }
    }

    /**
     * Chờ avatar sẵn sàng trước khi danh sách hiện ra: tra URL còn thiếu (song song, dùng chung hàng đợi của [resolve]) rồi nạp
     * ảnh vào bộ nhớ đệm của Coil, tối đa [timeoutMs] cho cả hai bước. Hết giờ thì thôi: phần tra dở vẫn chạy nền và được nhớ cho
     * lần sau, avatar nào chưa kịp sẽ hiện nền xám rồi hiện dần ảnh (xem `Avatar`). Không bao giờ ném lỗi mạng.
     */
    suspend fun prefetch(requests: List<Request>, directUrls: List<String?> = emptyList(), timeoutMs: Long = 3_000L) {
        val ctx = appContext ?: return
        try {
            withTimeoutOrNull(timeoutMs) {
                val urls = LinkedHashSet<String>()
                directUrls.forEach { u -> u?.takeIf { it.isNotBlank() }?.let { urls.add(normalize(it)) } }
                val todo = requests.filter { cached(it.channelId, it.name) == null }
                    .distinctBy { idKey(it.channelId) ?: nameKey(it.name) ?: "panel:${it.panelId}:${it.panelParams}:${it.videoId}" }
                coroutineScope {
                    todo.map { r -> async { resolve(r) } }.awaitAll()
                }
                requests.forEach { r -> cached(r.channelId, r.name)?.let { urls.add(it) } }
                coroutineScope {
                    urls.map { u ->
                        async {
                            try {
                                ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(u).build())
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            }
                        }
                    }.awaitAll()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** [prefetch] cho avatar kênh của một danh sách video. */
    suspend fun prefetchFor(videos: List<VideoItem>, timeoutMs: Long = 3_000L) =
        prefetch(
            videos.filter { !it.isShort || it.channel != null }
                .map { Request(it.channelId, it.channel, it.panelId, it.panelParams, it.id) },
            videos.map { it.avatar },
            timeoutMs
        )

    private fun normalize(url: String) = if (url.startsWith("//")) "https:$url" else url

    private suspend fun lookup(req: Request): String? {
        var id = req.channelId?.takeIf { it.startsWith("UC") }
        if (id == null && req.panelId != null && req.panelParams != null) {
            id = channelIdFromMenu(req.panelId, req.panelParams)
        }
        var url: String? = null
        if (id != null) {
            url = try {
                fromMusicHeader(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // Trang Music lỗi: vẫn còn cách tìm kênh bên dưới.
            }
        }
        // Kênh nhạc (nghệ sĩ / "- Topic") hay kênh cộng tác thường không có ảnh ở trang Music: đọc thẳng đầu trang kênh trên web.
        if (url == null && id != null) {
            url = try {
                fromChannelPage(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        if (url == null && id != null) url = searchChannel(id, req.name)
        // Kênh nhạc / cộng tác nhiều nghệ sĩ ("A, B và nhiều nghệ sĩ khác") không ứng với một kênh để tìm theo tên:
        // lấy ảnh của kênh đăng video (chủ video) trong trang xem của chính video đó.
        if (url == null && req.videoId != null) {
            url = try {
                fromVideoOwner(req.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        // Vẫn chưa có: tìm theo tên đã làm sạch ("A - Topic" -> "A", "A và 1 kênh khác" / "A, B" -> "A").
        if (url == null) {
            for (candidate in nameCandidates(req.name)) {
                url = try {
                    searchChannel(null, candidate)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (url != null) break
            }
        }
        if (url != null) remember(id ?: req.channelId, req.name, url)
        return url
    }

    /** Ảnh đại diện từ đầu trang kênh trên web (client WEB): dùng cho kênh nhạc / cộng tác mà trang Music không trả ảnh vuông. */
    private suspend fun fromChannelPage(channelId: String): String? {
        val client = JSONObject().put("clientName", "WEB").put("clientVersion", "2.20250120.01.00")
            .put("hl", "vi").put("gl", "VN")
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("browseId", channelId)
        val res = Http.postJson(
            "${BASE}browse?prettyPrint=false&key=$WEB_KEY", body,
            mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
        )
        if (res.code !in 200..299) return null
        val header = JSONObject(res.body).optJSONObject("header") ?: return null
        val thumbs = findKey(header, "avatarViewModel")?.dig("image", "sources")
            ?: header.dig("c4TabbedHeaderRenderer", "avatar", "thumbnails")
        return lastUrl(thumbs)
    }

    /** Ảnh của chủ video (videoOwnerRenderer) từ `next` của client WEB. */
    private suspend fun fromVideoOwner(videoId: String): String? {
        val client = JSONObject().put("clientName", "WEB").put("clientVersion", "2.20250120.01.00")
            .put("hl", "vi").put("gl", "VN")
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("videoId", videoId)
        val res = Http.postJson(
            "${BASE}next?prettyPrint=false&key=$WEB_KEY", body,
            mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
        )
        if (res.code !in 200..299) return null
        val owner = findKey(JSONObject(res.body), "videoOwnerRenderer") ?: return null
        return lastUrl(owner.dig("thumbnail", "thumbnails"))
            ?: lastUrl(findKey(owner, "avatarViewModel")?.dig("image", "sources"))
    }

    private val TOPIC_SUFFIX = Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE)
    private val MORE_CHANNELS = Regex("\\s+(và|and|&)\\s+(\\d+|nhiều|những|more|many|other)\\b.*$", RegexOption.IGNORE_CASE)
    private val PLUS_COUNT = Regex("\\s*\\+\\s*\\d+$")
    private val NAME_SEPARATOR = Regex("\\s+(và|and|&)\\s+|\\s*,\\s*")

    /** Tên để tìm kênh: nguyên văn, rồi bản bỏ "- Topic" / "và N kênh khác" / "+N", rồi tên đầu tiên của danh sách cộng tác. */
    private fun nameCandidates(name: String?): List<String> {
        val raw = name?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val cleaned = raw.replace(TOPIC_SUFFIX, "").replace(MORE_CHANNELS, "").replace(PLUS_COUNT, "").trim()
        val first = cleaned.split(NAME_SEPARATOR).firstOrNull()?.trim().orEmpty()
        return listOf(raw, cleaned, first).filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }.take(3)
    }

    /** Menu ngữ cảnh của thẻ TV có mục "Đi tới kênh": lấy mã kênh từ đó. */
    private suspend fun channelIdFromMenu(panelId: String, params: String): String? {
        val json = InnerTube.callTvAnonymous("get_panel", JSONObject().put("panelId", panelId).put("params", params))
        return findAllKeys(json, "browseEndpoint")
            .firstNotNullOfOrNull { it.optString("browseId").takeIf { id -> id.startsWith("UC") } }
    }

    /** Đầu trang YouTube Music của kênh: vài KB (trang kênh thường ~180 KB), ảnh nền trước chính là avatar. */
    private suspend fun fromMusicHeader(channelId: String): String? {
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", musicVersion())
            .put("hl", "vi").put("gl", "VN")
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("browseId", channelId)
        val res = Http.postJson(
            "${BASE}browse?prettyPrint=false&key=$WEB_KEY", body,
            mapOf("Origin" to MUSIC_ORIGIN, "Referer" to "$MUSIC_ORIGIN/")
        )
        if (res.code !in 200..299) throw IllegalStateException("channel avatar HTTP ${res.code}")
        val json = JSONObject(res.body)
        // Kênh thường: ảnh nền trước là avatar. Kênh nghệ sĩ chỉ có ảnh biểu ngữ rộng → bỏ, để bước tìm kiếm lo.
        val thumbs = json.dig("header", "musicVisualHeaderRenderer", "foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
        return lastUrl(thumbs)
    }

    private fun musicVersion(): String {
        val day = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return "1.${day.format(java.util.Date())}.01.00"
    }

    /** Tìm kênh bằng mã hoặc tên (như meetube), lấy ảnh của kênh trùng mã / trùng tên. */
    private suspend fun searchChannel(channelId: String?, name: String?): String? {
        val query = channelId ?: name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val client = JSONObject().put("clientName", "MWEB").put("clientVersion", "2.20251015.01.00").put("hl", "vi")
        val body = JSONObject().put("context", JSONObject().put("client", client))
            .put("query", query).put("params", CHANNELS_ONLY)
        val res = Http.postJson("${BASE}search?prettyPrint=false&key=$WEB_KEY", body)
        if (res.code !in 200..299) throw IllegalStateException("channel search HTTP ${res.code}")
        val json = JSONObject(res.body)
        for (ch in findAllKeys(json, "channelRenderer")) {
            val match = if (channelId != null) {
                ch.optString("channelId") == channelId
            } else {
                ytText(ch.opt("title"))?.trim().equals(name?.trim(), ignoreCase = true)
            }
            if (!match) continue
            lastUrl(ch.dig("thumbnail", "thumbnails"))?.let { return it }
        }
        return null
    }

    private fun lastUrl(thumbs: Any?): String? {
        val arr = thumbs as? org.json.JSONArray ?: return null
        if (arr.length() == 0) return null
        val url = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http") -> url
            else -> null
        }
    }
}
