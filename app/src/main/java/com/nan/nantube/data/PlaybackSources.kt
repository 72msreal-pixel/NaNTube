package com.nan.nantube.data

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

private const val SAFARI_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
    "(KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"
private const val TIZEN_UA = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 " +
    "(KHTML, like Gecko) Version/2.4.0 TV Safari/538.1"
private const val VISION_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
    "(KHTML, like Gecko) Version/26.0 Safari/605.1.15"
private const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
    "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

/** Trang nhúng bất kỳ không phải YouTube cũng được; meetube dùng reddit. */
private const val EMBED_REFERER = "https://www.reddit.com/"
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val CONFIG_START = Regex("ytcfg\\.set\\(\\s*(?=\\{)")

private class EmbedIdentity(val visitor: String, val flags: String, val playerId: String?)

/** Câu trả lời /player phát được của một nguồn (status OK). */
internal class PlaybackAnswer(val source: PlaybackSource, val json: JSONObject) {
    /** Luôn có khi hỏi với `probeOnly = false`: [PlaybackSource.walk] đã bỏ qua các câu trả lời thiếu nó. */
    val streamingData: JSONObject get() = json.getJSONObject("streamingData")
}

/** Lý do từng nguồn không dùng được, để thông báo lỗi nói rõ YouTube từ chối ở đâu. */
internal class PlaybackRefusals {
    private val reasons = mutableListOf<String>()

    fun add(source: PlaybackSource, why: String) {
        reasons += "${source.name}: $why"
    }

    override fun toString() = reasons.joinToString("; ").ifBlank { "không có dữ liệu phát" }

    fun failure(action: String) = IOException("YouTube không cho $action video này ($this)")
}

/**
 * Các nguồn /player và thứ tự hỏi lấy từ meetube (PlayerSourceCatalog + PhoneSourcePlanner, docs/player-sources/LANES.md):
 * VISIONOS → TV_TIZEN → WEB_EMBED → ANDROID_VR. Dùng chung cho Short, nút Tải xuống và nhận diện Short.
 *
 * - Chỉ tài khoản đăng nhập bằng mã mới đi cùng một nguồn: TV_TIZEN kèm Bearer, luôn là nguồn thứ hai. Client TV 7.x và 5.x
 *   kiểu cũ meetube đã bỏ vì link của chúng chết (UNPLAYABLE / 403 ngay byte đầu). Các client web từ chối token OAuth.
 * - Chưa đăng nhập (kể cả tài khoản web): TV_TIZEN chạy ẩn danh và chỉ được hỏi sau khi một nguồn không-web từ chối video vì
 *   nội dung (UNPLAYABLE), đúng `admitsAccountRouteAfter` của meetube. Phiên cookie không dùng để phát, meetube cũng vậy.
 * - Nguồn vừa lỗi (link 403 giữa chừng) được xếp cuối lượt hỏi, như phần phục hồi của meetube.
 *
 * Mỗi nguồn là một dòng khai báo; request dựng ở [load] theo QueryBuilder của meetube.
 */
internal enum class PlaybackSource(
    private val clientName: String,
    private val clientId: String,
    private val clientVersion: String,
    private val userAgent: String,
    private val browser: Pair<String, String>?,
    private val device: List<Pair<String, String>>,
    private val supportXhr: Boolean,
    /** Câu trả lời mã hóa (signatureCipher / n) phải giải bằng player JS. */
    private val cipher: Boolean,
    private val budgetMs: Long
) {
    VISIONOS(
        "VISIONOS", "101", "1.02", VISION_UA, "Safari" to "605.1.15",
        listOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
        supportXhr = true, cipher = false, budgetMs = 7_000
    ),
    TV_TIZEN(
        "TVHTML5", "7", "5.20260707", TIZEN_UA, "Safari" to "538.1",
        listOf("deviceMake" to "Samsung", "deviceModel" to "SmartTV", "osName" to "Tizen", "osVersion" to "2.4.0"),
        supportXhr = false, cipher = true, budgetMs = 15_000
    ),
    WEB_EMBED(
        "WEB_EMBEDDED_PLAYER", "56", "2.20260708.00.00", SAFARI_UA, "Safari" to "605.1.15",
        emptyList(), supportXhr = false, cipher = true, budgetMs = 20_000
    ),
    ANDROID_VR(
        "ANDROID_VR", "28", "1.65.10", VR_UA, null,
        listOf("androidSdkVersion" to "32", "osName" to "Android", "osVersion" to "12L", "deviceModel" to "Quest 3", "deviceMake" to "Oculus"),
        supportXhr = true, cipher = false, budgetMs = 7_000
    );

    private val usable get() = !cipher || PlayerJs.supported()

    /**
     * /player của nguồn này. [carrier] là tài khoản mã (hoặc null); chỉ TV_TIZEN mang nó.
     * Trả nguyên phản hồi của YouTube, [PlayerJs.normalize] đã đổi các link mã hóa thành link dùng được.
     */
    private suspend fun load(context: Context, carrier: Account?, videoId: String): JSONObject {
        require(VIDEO_ID.matches(videoId)) { "ID video không hợp lệ" }
        val embed = if (this == WEB_EMBED) embedIdentity(videoId) else null
        val player = if (cipher) PlayerJs.get(embed?.playerId) else null
        val visitor = embed?.visitor ?: InnerTube.visitorData()
        val sts = player?.sts ?: PlayerJs.cachedSts()

        val headers = mutableMapOf(
            "Origin" to InnerTube.ORIGIN, "User-Agent" to userAgent,
            "X-Youtube-Client-Name" to clientId, "X-Youtube-Client-Version" to clientVersion
        )
        if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
        if (this == TV_TIZEN) headers += InnerTube.authHeaders(carrier)

        val json = Http.postJson(InnerTube.BASE + "player?prettyPrint=false", body(videoId, visitor, sts, embed), headers).requireJson()
        if (player != null && json.dig("playabilityStatus", "status") == "OK") {
            json.optJSONObject("streamingData")?.let { PlayerJs.normalize(context, player, it) }
        }
        return json
    }

    private fun body(videoId: String, visitor: String?, sts: Int?, embed: EmbedIdentity?): JSONObject {
        val client = JSONObject()
            .put("clientName", clientName).put("clientVersion", clientVersion)
            .put("clientScreen", "WATCH").put("userAgent", userAgent)
        browser?.let { client.put("browserName", it.first).put("browserVersion", it.second) }
        device.forEach { client.put(it.first, it.second) }
        client.put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", InnerTube.utcOffsetMinutes().toString())
        if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)

        val context = JSONObject().put("client", client).put("user", InnerTube.safetyUser())
        if (this == WEB_EMBED) context.put("thirdParty", JSONObject().put("embedUrl", EMBED_REFERER))

        val body = JSONObject().put("context", context)
        if (sts != null) {
            val content = JSONObject()
                .put("html5Preference", "HTML5_PREF_WANTS").put("lactMilliseconds", 60000)
                .put("isInlinePlaybackNoAd", true).put("signatureTimestamp", sts)
            embed?.let { content.put("encryptedHostFlags", it.flags) }
            body.put("playbackContext", JSONObject().put("contentPlaybackContext", content)
                .put("devicePlaybackCapabilities", JSONObject().put("supportsVp9Encoding", true).put("supportXhr", supportXhr)))
        }
        return body.put("racyCheckOk", true).put("contentCheckOk", true)
            .put("videoId", videoId).put("cpn", InnerTube.randomId(16))
    }

    /** Visitor + cờ chủ nhúng của trang nhúng phải đi đôi với nhau, lấy lệch cặp là bị từ chối. */
    private suspend fun embedIdentity(videoId: String): EmbedIdentity {
        val res = Http.get("${InnerTube.ORIGIN}/embed/$videoId?html5=1", mapOf("User-Agent" to userAgent, "Referer" to EMBED_REFERER))
        if (res.code !in 200..299) throw IOException("Trang nhúng: HTTP ${res.code}")
        val config = JSONObject()
        for (match in CONFIG_START.findAll(res.body)) {
            val value = try {
                JSONTokener(res.body.substring(match.range.last + 1)).nextValue() as? JSONObject
            } catch (_: org.json.JSONException) {
                null
            }
            if (value != null) for (key in value.keys()) config.put(key, value.opt(key))
        }
        val visitor = (config.opt("VISITOR_DATA") as? String)
            ?: (config.dig("INNERTUBE_CONTEXT", "client", "visitorData") as? String)
        val flags = config.dig("WEB_PLAYER_CONTEXT_CONFIGS", "WEB_PLAYER_CONTEXT_CONFIG_ID_EMBEDDED_PLAYER", "encryptedHostFlags") as? String
        if (visitor.isNullOrBlank() || flags.isNullOrBlank()) throw IOException("Trang nhúng thiếu cấu hình phát")
        val playerUrl = config.opt("PLAYER_JS_URL") as? String
        return EmbedIdentity(visitor, flags, PlayerJs.idFrom(playerUrl ?: res.body))
    }

    /** Hỏi /player trong hạn của nguồn; lỗi hoặc quá hạn thì ghi lý do và trả null để lượt hỏi đi tiếp. */
    private suspend fun ask(context: Context, carrier: Account?, videoId: String, refusals: PlaybackRefusals): JSONObject? {
        val json = try {
            withTimeoutOrNull(budgetMs) { load(context, carrier, videoId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            refusals.add(this, e.message ?: "không kết nối được")
            return null
        }
        if (json == null) refusals.add(this, "quá ${budgetMs / 1000} giây không trả lời")
        return json
    }

    companion object {
        /** Thứ tự hỏi: khai báo ở trên, TV_TIZEN chỉ có khi có tài khoản mã, nguồn trong [last] (vừa lỗi) xếp cuối. */
        fun order(signedIn: Boolean, last: Set<String>): List<PlaybackSource> =
            entries.filter { it.usable && (signedIn || it != TV_TIZEN) }.sortedBy { it.name in last }

        /**
         * Hỏi lần lượt từng nguồn, đưa câu trả lời phát được cho [accept]; [accept] trả khác null là dừng và là kết quả.
         * Trả null khi hết nguồn, lý do từng nguồn nằm ở [refusals]. [probeOnly]: chỉ cần đọc thông tin video, nên nhận cả
         * video trực tiếp và câu trả lời không có streamingData.
         */
        suspend fun <T : Any> walk(
            context: Context,
            account: Account?,
            videoId: String,
            last: Set<String> = emptySet(),
            refusals: PlaybackRefusals = PlaybackRefusals(),
            probeOnly: Boolean = false,
            /** Cho phép video đang phát trực tiếp đi qua (trình xem video dài phát bằng HLS); mặc định vẫn từ chối như cũ. */
            allowLive: Boolean = false,
            accept: suspend (PlaybackAnswer) -> T?
        ): T? {
            val carrier = account?.takeIf { it.kind == LoginKind.CODE }
            val queue = ArrayDeque(order(carrier != null, last))
            val asked = HashSet<PlaybackSource>()
            while (true) {
                val source = queue.removeFirstOrNull() ?: return null
                asked += source
                val json = source.ask(context, carrier, videoId, refusals) ?: continue
                if (!probeOnly && json.dig("videoDetails", "isUpcoming") == true) {
                    throw IOException(if (allowLive) "Buổi phát trực tiếp chưa bắt đầu" else "Video đang hoặc sắp phát trực tiếp")
                }
                if (!probeOnly && !allowLive && json.dig("videoDetails", "isLive") == true) {
                    throw IOException("Video đang hoặc sắp phát trực tiếp")
                }
                val status = json.dig("playabilityStatus", "status") as? String
                if (status != "OK") {
                    val detail = json.dig("playabilityStatus", "reason") as? String
                    refusals.add(source, listOfNotNull(status, detail).joinToString(" - ").ifBlank { "không có dữ liệu phát" })
                    // Chưa đăng nhập: nguồn không-web từ chối vì nội dung thì TV_TIZEN (ẩn danh) được hỏi ngay sau đó.
                    if (carrier == null && source != WEB_EMBED && status == "UNPLAYABLE" && TV_TIZEN.usable && TV_TIZEN !in asked) {
                        queue.addFirst(TV_TIZEN)
                    }
                    continue
                }
                if (!probeOnly && json.optJSONObject("streamingData") == null) {
                    refusals.add(source, "không có streamingData")
                    continue
                }
                accept(PlaybackAnswer(source, json))?.let { return it }
            }
        }
    }
}
