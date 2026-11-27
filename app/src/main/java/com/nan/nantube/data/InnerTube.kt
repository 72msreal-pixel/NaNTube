package com.nan.nantube.data

import android.webkit.CookieManager
import java.security.MessageDigest
import org.json.JSONObject

class AuthRejected : Exception("Phiên đăng nhập không còn hiệu lực, hãy đăng nhập lại.")

/**
 * Gọi API nội bộ của YouTube (InnerTube) cho từng kiểu đăng nhập:
 * - Chưa đăng nhập hoặc WEB: client WEB (đăng nhập thì kèm cookie + SAPISIDHASH như trình duyệt).
 * - CODE: client TV kèm Bearer token (token TV chỉ dùng được với client TV, đúng ghi chú trong meetube).
 */
object InnerTube {
    internal const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val WEB_VERSION = "2.20250120.01.00"
    private const val TV_VERSION = "7.20250219.14.00"
    private const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    /**
     * [asWeb]: với tài khoản đăng nhập bằng mã, gửi ngữ cảnh WEB kèm Bearer thay vì ngữ cảnh TV.
     * Meetube tạo danh sách phát (playlist/create) đúng kiểu này: mẫu WEB + token của tài khoản.
     */
    suspend fun call(endpoint: String, extra: JSONObject, account: Account?, asWeb: Boolean = false): JSONObject {
        if (account?.kind == LoginKind.WEB) return webAccountCall(endpoint, extra, account)
        val tv = account?.kind == LoginKind.CODE && !asWeb
        val body = JSONObject(extra.toString())
        body.put("context", context(tv))

        val headers = HashMap<String, String>()
        var url = BASE + endpoint + "?prettyPrint=false"
        if (tv) {
            url += "&key=$WEB_KEY"
            headers["User-Agent"] = TV_UA
            headers["X-YouTube-Client-Name"] = "7"
            headers["X-YouTube-Client-Version"] = TV_VERSION
            headers["Authorization"] = "Bearer " + freshToken(account!!)
        } else {
            url += "&key=$WEB_KEY"
            headers["Origin"] = ORIGIN
            headers["X-YouTube-Client-Name"] = "1"
            headers["X-YouTube-Client-Version"] = WEB_VERSION
            if (account?.kind == LoginKind.WEB) headers.putAll(webAuthHeaders(account.authUser))
            else if (account?.kind == LoginKind.CODE) headers["Authorization"] = "Bearer " + freshToken(account)
        }

        val res = Http.postJson(url, body, headers)
        if (res.code == 401 || (tv && res.code == 403)) throw AuthRejected()
        return res.requireJson()
    }

    /**
     * Gọi InnerTube bằng phiên đăng nhập web (cookie), đúng như `WebSession.post` của meetube: mẫu WEB hiện tại + visitorData,
     * UA desktop, và chữ ký đủ ba băm (SAPISIDHASH + SAPISID1PHASH + SAPISID3PHASH). Trước đây app chỉ gửi SAPISIDHASH với
     * client WEB cũ nên YouTube coi phiên là ẩn danh / từ chối: nhật ký tìm kiếm, Video đã xem... của tài khoản web trống.
     */
    private suspend fun webAccountCall(endpoint: String, extra: JSONObject, account: Account): JSONObject {
        val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
        val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
        val visitor = visitorData()
        val body = JSONObject(extra.toString())
        body.put("context", JSONObject().put("client", mtWebClient(visitor).put("hl", "vi").put("gl", "VN")).put("user", safetyUser()))
        val headers = mutableMapOf(
            "Cookie" to cookies,
            "Authorization" to auth,
            "Origin" to ORIGIN,
            "X-Origin" to ORIGIN,
            "Referer" to "$ORIGIN/",
            "X-Goog-AuthUser" to account.authUser.toString(),
            "X-Youtube-Client-Name" to "1",
            "X-Youtube-Client-Version" to MT_WEB_VERSION,
            "User-Agent" to MT_WEB_UA
        )
        if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
        val res = Http.postJson("$BASE$endpoint?prettyPrint=false", body, headers)
        if (res.code == 401) throw AuthRejected()
        return res.requireJson()
    }

    /** Header đăng nhập của tài khoản (Bearer hoặc cookie+SAPISIDHASH) cho các URL ngoài InnerTube, vd gợi ý tìm kiếm. Rỗng nếu chưa đăng nhập. */
    internal suspend fun authHeaders(account: Account?): Map<String, String> = when (account?.kind) {
        LoginKind.CODE -> mapOf("Authorization" to "Bearer " + freshToken(account))
        LoginKind.WEB -> webAuthHeaders(account.authUser)
        null -> emptyMap()
    }

    /**
     * Gọi bằng ngữ cảnh TV nhưng không kèm token: cho các lượt hỏi nhỏ không nên gắn với tài khoản
     * (đọc menu ngữ cảnh của thẻ video để biết kênh, như `PanelService.getChannelId` của MediaServiceCore).
     */
    suspend fun callTvAnonymous(endpoint: String, extra: JSONObject): JSONObject {
        val body = JSONObject(extra.toString())
        body.put("context", context(true))
        val headers = mapOf(
            "User-Agent" to TV_UA,
            "X-YouTube-Client-Name" to "7",
            "X-YouTube-Client-Version" to TV_VERSION
        )
        return Http.postJson("$BASE$endpoint?prettyPrint=false&key=$WEB_KEY", body, headers).requireJson()
    }

    /**
     * Gọi thao tác ghi (danh sách phát) theo thứ tự meetube dùng: tài khoản mã thử ngữ cảnh TV rồi WEB+Bearer
     * ([webFirst] đảo thứ tự, dùng cho playlist/create). Tài khoản khác chỉ có một cách gọi.
     * Lỗi ở cách đầu không làm hỏng thao tác nếu cách sau chạy được.
     */
    suspend fun callAny(endpoint: String, extra: JSONObject, account: Account?, webFirst: Boolean = false): JSONObject {
        if (account?.kind != LoginKind.CODE) return call(endpoint, extra, account)
        return try {
            call(endpoint, extra, account, asWeb = webFirst)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (first: Exception) {
            try {
                call(endpoint, extra, account, asWeb = !webFirst)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                throw first
            }
        }
    }

    /** UA mà mọi request tới googlevideo của meetube dùng (Media3SourceFactory.MEDIA_USER_AGENT), trình tải cũng phải dùng đúng UA này. */
    internal const val MEDIA_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    // ---- Dựng request đúng như MediaServiceCore của meetube -----------------------------------

    /** WEB hiện tại của meetube (CLIENTS.WEB) và UA desktop đi kèm (DefaultHeaders.USER_AGENT_WEB). */
    private const val MT_WEB_VERSION = "2.20260708.00.00"
    private const val MT_WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

    /** UA mà meetube gắn cho mọi request API có Bearer (DefaultHeaders.APP_USER_AGENT) và referer mặc định của nó. */
    private const val MT_APP_UA =
        "Mozilla/5.0 (Linux armeabi-v7a; Android 7.1.2; Fire OS 6.0) Cobalt/22.lts.3.306369-gold (unlike Gecko) v8/8.8.278.8-jit gles Starboard/13, Amazon_ATV_mediatek8695_2019/NS6294 (Amazon, AFTMM, Wireless) com.amazon.firetv.youtube/22.3.r2.v66.0"
    private const val MT_APP_REFERER = "https://www.youtube.com/tv"

    private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    internal fun randomId(length: Int): String {
        val random = java.security.SecureRandom()
        return buildString { repeat(length) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }
    }

    internal fun utcOffsetMinutes(): Int = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    @Volatile
    private var cachedVisitor: String? = null

    /**
     * visitorData do máy chủ cấp, như `VisitorService` của meetube (youtubei/v1/visitor_id, mẫu WEB + cookie SOCS).
     * Meetube ghi lại: /player không có visitorData bị YouTube đòi xác minh "không phải bot", có visitorData hợp lệ thì
     * trả link bình thường. Lấy một lần rồi nhớ trong phiên chạy; lỗi mạng thì trả null (request vẫn đi, như meetube).
     */
    suspend fun visitorData(): String? {
        cachedVisitor?.let { return it }
        return try {
            val body = JSONObject().put("context", JSONObject().put("client", mtWebClient(null)).put("user", safetyUser()))
                .put("racyCheckOk", true).put("contentCheckOk", true)
            val res = Http.postJson(BASE + "visitor_id", body, mapOf(
                "Accept-Language" to "en-US, en;q=0.9",
                "Cookie" to "SOCS=CAE=",
                "Origin" to ORIGIN,
                "Referer" to ORIGIN,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
                "X-Youtube-Client-Name" to "1",
                "X-Youtube-Client-Version" to "2.20250213.05.00"
            ))
            if (res.code !in 200..299) return null
            JSONObject(res.body).dig("responseContext", "visitorData")
                ?.let { it as? String }?.takeIf { it.isNotBlank() }
                ?.also { cachedVisitor = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    internal fun safetyUser() = JSONObject().put("enableSafetyMode", false).put("lockedSafetyMode", false)

    /** Phần "client" của mẫu WEB trong meetube (AppClient.WEB.browseTemplate): có clientScreen, userAgent, browser, vùng, visitorData. */
    private fun mtWebClient(visitor: String?): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB").put("clientVersion", MT_WEB_VERSION)
            .put("clientScreen", "WATCH").put("userAgent", MT_WEB_UA)
            .put("browserName", "Chrome").put("browserVersion", "147.0.0.0")
            .put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", utcOffsetMinutes().toString())
        if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
        return client
    }

    /**
     * Chữ ký phiên web đúng như `WebSession.authorization` của meetube: SAPISIDHASH, thêm SAPISID1PHASH / SAPISID3PHASH
     * khi cookie có __Secure-1PAPISID / __Secure-3PAPISID. Null nếu chưa có cookie SAPISID.
     */
    private fun mtAuthorization(cookieHeader: String?, nowSeconds: Long, origin: String): String? {
        val cookies = parseCookies(cookieHeader.orEmpty())
        val sapisid = cookies["SAPISID"]?.takeIf { it.isNotEmpty() }
            ?: cookies["__Secure-3PAPISID"]?.takeIf { it.isNotEmpty() } ?: return null
        fun token(name: String, secret: String) = "$name ${nowSeconds}_${sha1("$nowSeconds $secret $origin")}"
        val tokens = mutableListOf(token("SAPISIDHASH", sapisid))
        cookies["__Secure-1PAPISID"]?.takeIf { it.isNotEmpty() }?.let { tokens.add(token("SAPISID1PHASH", it)) }
        cookies["__Secure-3PAPISID"]?.takeIf { it.isNotEmpty() }?.let { tokens.add(token("SAPISID3PHASH", it)) }
        return tokens.joinToString(" ")
    }

    /** Mẫu WEB/TV của MediaServiceCore, riêng cho luồng bình luận. Không gửi thao tác ghi ẩn danh. */
    internal suspend fun commentRequest(endpoint: String, data: JSONObject, account: Account, tv: Boolean): JSONObject {
        require(!tv || account.kind == LoginKind.CODE)
        val visitor = visitorData()
        val tvVersion = "7.20260707.07.00"
        val cobalt = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
        val client = if (tv) JSONObject()
            .put("clientName", "TVHTML5").put("clientVersion", tvVersion)
            .put("clientScreen", "WATCH").put("userAgent", cobalt)
            .put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", utcOffsetMinutes().toString())
            .put("tvAppInfo", JSONObject().put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION").put("zylonLeftNav", true))
            .put("webpSupport", false).put("animatedWebpSupport", true)
        else mtWebClient(visitor).put("hl", "vi").put("gl", "VN")
        if (tv && visitor != null) client.put("visitorData", visitor)
        val body = JSONObject(data.toString())
            .put("context", JSONObject().put("client", client).put("user", safetyUser()))
            .put("racyCheckOk", true).put("contentCheckOk", true)
        val headers = mutableMapOf(
            "X-YouTube-Client-Name" to if (tv) "7" else "1",
            "X-YouTube-Client-Version" to if (tv) tvVersion else MT_WEB_VERSION,
            "User-Agent" to if (account.kind == LoginKind.CODE) MT_APP_UA else MT_WEB_UA,
            "Referer" to if (account.kind == LoginKind.CODE) MT_APP_REFERER else "$ORIGIN/"
        )
        if (account.kind == LoginKind.CODE) {
            headers["Authorization"] = "Bearer " + freshToken(account)
        } else {
            val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
            val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
            headers.putAll(mapOf("Authorization" to auth, "Cookie" to cookies, "Origin" to ORIGIN,
                "X-Origin" to ORIGIN, "X-Goog-AuthUser" to account.authUser.toString()))
        }
        if (visitor != null) headers["X-Goog-Visitor-Id"] = visitor
        val result = Http.postJson(BASE + endpoint + "?prettyPrint=false", body, headers)
        if (result.code == 401) throw AuthRejected()
        if (result.code in 200..299 && result.body.isBlank() && endpoint == "comment/perform_comment_action") return JSONObject()
        return result.requireJson()
    }

    /**
     * Tạo danh sách phát bằng đúng phiên đăng nhập đang chọn, theo thứ tự thử giống meetube:
     * - Tài khoản mã: mẫu WEB + Bearer + UA/Referer của app meetube + tham số `t` (PlaylistApi.createPlaylist dùng
     *   createQueryWeb, chỉ gửi title [+ videoIds], không gửi privacyStatus); bị từ chối thì thử ngữ cảnh TV, rồi mới
     *   thử kèm privacyStatus.
     * - Tài khoản web: cookie + SAPISIDHASH như trình duyệt; bị từ chối thì thử lại không kèm privacyStatus.
     * Chỉ sang cách kế khi YouTube từ chối rõ ràng (HTTP 400/403/404, nghĩa là chưa ghi gì); lỗi khác (5xx, mạng) dừng
     * ngay để không tạo trùng khi chưa biết kết quả ghi.
     */
    suspend fun createPlaylist(account: Account, title: String, videoId: String?): JSONObject {
        val endpoint = BASE + "playlist/create?prettyPrint=false"
        fun data(withPrivacy: Boolean): JSONObject {
            val d = JSONObject().put("title", title)
            if (withPrivacy) d.put("privacyStatus", "PRIVATE")
            if (videoId != null) d.put("videoIds", org.json.JSONArray().put(videoId))
            return d
        }

        val attempts = ArrayList<suspend () -> HttpResult>()
        if (account.kind == LoginKind.CODE) {
            val authorization = "Bearer " + freshToken(account)
            val visitor = visitorData()
            fun webBearer(withPrivacy: Boolean): suspend () -> HttpResult = {
                val body = data(withPrivacy)
                    .put("context", JSONObject().put("client", mtWebClient(visitor)).put("user", safetyUser()))
                    .put("racyCheckOk", true).put("contentCheckOk", true)
                Http.postJson("$endpoint&t=" + randomId(12), body, mapOf(
                    "User-Agent" to MT_APP_UA,
                    "Referer" to MT_APP_REFERER,
                    "Authorization" to authorization
                ))
            }
            attempts.add(webBearer(false))
            attempts.add {
                val body = data(false).put("context", context(tv = true))
                Http.postJson(endpoint, body, mapOf(
                    "User-Agent" to TV_UA,
                    "Referer" to MT_APP_REFERER,
                    "X-YouTube-Client-Name" to "7",
                    "X-YouTube-Client-Version" to TV_VERSION,
                    "Authorization" to authorization
                ))
            }
            attempts.add(webBearer(true))
        } else {
            val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
            val visitor = visitorData()
            fun webCookie(withPrivacy: Boolean): suspend () -> HttpResult = {
                // SAPISIDHASH ký theo giờ hiện tại nên dựng lại ở mỗi lần gửi.
                val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
                val client = JSONObject().put("clientName", "WEB").put("clientVersion", MT_WEB_VERSION)
                    .put("hl", "vi").put("gl", "VN")
                if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
                val headers = mutableMapOf(
                    "Cookie" to cookies,
                    "Authorization" to auth,
                    "Origin" to ORIGIN,
                    "X-Origin" to ORIGIN,
                    "Referer" to "$ORIGIN/",
                    "X-Goog-AuthUser" to account.authUser.toString(),
                    "X-Youtube-Client-Name" to "1",
                    "X-Youtube-Client-Version" to MT_WEB_VERSION,
                    "User-Agent" to MT_WEB_UA
                )
                if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
                Http.postJson(endpoint, data(withPrivacy).put("context", JSONObject().put("client", client)), headers)
            }
            attempts.add(webCookie(true))
            attempts.add(webCookie(false))
        }

        var firstRejected: HttpResult? = null
        for (attempt in attempts) {
            val result = attempt()
            if (result.code == 401) throw AuthRejected()
            if (result.code in 200..299) return result.requireJson()
            if (firstRejected == null) firstRejected = result
            if (result.code != 400 && result.code != 403 && result.code != 404) break
        }
        return (firstRejected ?: throw Exception("Không gửi được yêu cầu tạo danh sách phát")).requireJson()
    }

    /** Kết quả /player dành cho việc ghi lịch sử: phản hồi + visitorData đã gửi kèm (các lệnh stats phải đi cùng visitor này). */
    internal class TrackingPlayer(val json: JSONObject, val visitor: String?)

    /** Phiên bản + UA của client TV (CLIENTS.TV trong meetube) dùng cho /player của tài khoản đăng nhập. */
    private const val MT_TV_VERSION = "7.20260707.07.00"
    private const val MT_TV_UA =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    /** /player có xác thực TV để lấy dữ liệu theo dõi của đúng tài khoản mã. */
    internal suspend fun tvTrackingPlayer(account: Account, videoId: String, cpn: String): TrackingPlayer {
        require(account.kind == LoginKind.CODE)
        val visitor = visitorData()
        val headers = HashMap<String, String>()
        val client = JSONObject()
            .put("clientName", "TVHTML5").put("clientVersion", MT_TV_VERSION)
            .put("clientScreen", "WATCH").put("userAgent", MT_TV_UA)
            .put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", utcOffsetMinutes().toString())
        if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
        // signatureTimestamp: QueryBuilder đổi 5 chữ số thành dạng TV (+001); không lấy được thì bỏ playbackContext.
        val sts = try {
            PlayerJs.get().sts.toString().let { if (it.length == 5) it + "001" else it }.toLong()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val playbackContext = sts?.let {
            JSONObject()
                .put("contentPlaybackContext", JSONObject()
                    .put("html5Preference", "HTML5_PREF_WANTS").put("lactMilliseconds", 60000)
                    .put("isInlinePlaybackNoAd", true).put("signatureTimestamp", it))
                .put("devicePlaybackCapabilities", JSONObject().put("supportsVp9Encoding", true).put("supportXhr", false))
        }
        headers["Origin"] = ORIGIN
        headers["User-Agent"] = MT_TV_UA
        headers["X-Youtube-Client-Name"] = "7"
        headers["X-Youtube-Client-Version"] = MT_TV_VERSION
        headers["Authorization"] = "Bearer " + freshToken(account)
        if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor

        val body = JSONObject().put("context", JSONObject().put("client", client).put("user", safetyUser()))
        playbackContext?.let { body.put("playbackContext", it) }
        body.put("racyCheckOk", true).put("contentCheckOk", true)
            .put("videoId", videoId).put("cpn", cpn)
        val res = Http.postJson(BASE + "player?prettyPrint=false", body, headers)
        if (res.code == 401 || res.code == 403) throw AuthRejected()
        return TrackingPlayer(res.requireJson(), visitor)
    }

    /**
     * /player của phiên web (cookie + SAPISIDHASH đủ ba băm, mẫu WEB của meetube) để lấy ei / vm / of gắn với đúng tài khoản.
     * Cookie phải đi cùng thì YouTube mới ghi ping `api/stats` vào lịch sử của tài khoản (không có thì chỉ ghi ẩn danh).
     */
    internal suspend fun webTrackingPlayer(account: Account, videoId: String, cpn: String): TrackingPlayer {
        require(account.kind == LoginKind.WEB)
        val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
        val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
        val visitor = visitorData()
        val sts = try {
            PlayerJs.get().sts.toLong()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val body = JSONObject()
            .put("context", JSONObject().put("client", mtWebClient(visitor).put("hl", "vi").put("gl", "VN")).put("user", safetyUser()))
        sts?.let {
            body.put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject()
                .put("html5Preference", "HTML5_PREF_WANTS").put("lactMilliseconds", 60000)
                .put("isInlinePlaybackNoAd", true).put("signatureTimestamp", it)))
        }
        body.put("racyCheckOk", true).put("contentCheckOk", true).put("videoId", videoId).put("cpn", cpn)
        val headers = mutableMapOf(
            "Cookie" to cookies,
            "Authorization" to auth,
            "Origin" to ORIGIN,
            "X-Origin" to ORIGIN,
            "Referer" to "$ORIGIN/",
            "X-Goog-AuthUser" to account.authUser.toString(),
            "X-Youtube-Client-Name" to "1",
            "X-Youtube-Client-Version" to MT_WEB_VERSION,
            "User-Agent" to MT_WEB_UA
        )
        if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
        val res = Http.postJson(BASE + "player?prettyPrint=false", body, headers)
        if (res.code == 401 || res.code == 403) throw AuthRejected()
        return TrackingPlayer(res.requireJson(), visitor)
    }

    /** Header cho ping `api/stats` của tài khoản web: cookie + chữ ký như trình duyệt (SAPISIDHASH ký theo giờ nên dựng lại mỗi lần). */
    internal fun webStatsHeaders(account: Account, visitor: String?): Map<String, String> {
        require(account.kind == LoginKind.WEB)
        val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
        val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
        return mutableMapOf(
            "Cookie" to cookies,
            "Authorization" to auth,
            "Origin" to ORIGIN,
            "X-Origin" to ORIGIN,
            "Referer" to "$ORIGIN/",
            "X-Goog-AuthUser" to account.authUser.toString(),
            "User-Agent" to MT_WEB_UA
        ).apply { if (!visitor.isNullOrEmpty()) put("X-Goog-Visitor-Id", visitor) }
    }

    internal suspend fun tvStatsHeaders(account: Account, visitor: String?): Map<String, String> {
        require(account.kind == LoginKind.CODE)
        return mutableMapOf(
            "Authorization" to ("Bearer " + freshToken(account)),
            "User-Agent" to MT_APP_UA,
            "Referer" to MT_APP_REFERER
        ).apply { if (!visitor.isNullOrEmpty()) put("X-Goog-Visitor-Id", visitor) }
    }

    private fun context(tv: Boolean): JSONObject {
        val client = JSONObject()
            .put("hl", "vi").put("gl", "VN")
            .put("clientName", if (tv) "TVHTML5" else "WEB")
            .put("clientVersion", if (tv) TV_VERSION else WEB_VERSION)
        return JSONObject().put("client", client)
    }

    /**
     * Chi tiết reel đúng như MeTube (BrowseService2 + BrowseApiHelper.getReelDetailsQuery): mẫu WEB, KHÔNG kèm tài khoản.
     * Interceptor của MeTube cho request ẩn danh: `key` + `prettyPrint=false` + `t`, UA/Referer của app, X-Goog-Visitor-Id.
     */
    internal suspend fun mtReelDetails(videoId: String, params: String?): JSONObject {
        val visitor = visitorData()
        val details = JSONObject().put("disablePlayerResponse", true)
        if (!params.isNullOrBlank()) details.put("params", params)
        details.put("playerRequest", JSONObject().put("videoId", videoId))
        val body = details
            .put("context", JSONObject().put("client", mtWebClient(visitor)).put("user", safetyUser()))
            .put("racyCheckOk", true).put("contentCheckOk", true)
        val headers = mutableMapOf("User-Agent" to MT_APP_UA, "Referer" to MT_APP_REFERER)
        if (visitor != null) headers["X-Goog-Visitor-Id"] = visitor
        val url = BASE + "reel/reel_item_watch?key=$WEB_KEY&prettyPrint=false&t=" + randomId(12)
        return Http.postJson(url, body, headers).requireJson()
    }

    /**
     * `feedback` đúng như MeTube (FeedbackApiHelper.getNotInterestedQuery = createQueryTV, gửi kèm tài khoản):
     * body mẫu TV hiện tại + visitorData, URL chỉ có prettyPrint (không `key`, không `t`), UA/Referer của app, Bearer.
     * Tài khoản web (cookie) dùng mẫu WEB + SAPISIDHASH như WebSession. Trả mã HTTP, MeTube coi 2xx là thành công.
     */
    /** Body client TV đầy đủ như meetube (AppClient.TV.browseTemplate): clientScreen, userAgent, vùng, visitorData, tvAppInfo. */
    private fun mtTvClient(visitor: String?): JSONObject = JSONObject()
        .put("clientName", "TVHTML5").put("clientVersion", MT_TV_VERSION)
        .put("clientScreen", "WATCH").put("userAgent", MT_TV_UA)
        .put("acceptLanguage", "vi").put("acceptRegion", "VN")
        .put("utcOffsetMinutes", utcOffsetMinutes().toString())
        .put("visitorData", visitor ?: "")
        .put("tvAppInfo", JSONObject().put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION").put("zylonLeftNav", true))
        .put("webpSupport", false).put("animatedWebpSupport", true)

    /**
     * `browse` của tài khoản mã đúng như meetube (BrowseApiHelper + AppClient.TV.browseTemplate): body TV đầy đủ + user + racyCheckOk /
     * contentCheckOk, URL chỉ có prettyPrint, UA/Referer của app, Bearer. Body TV trần (như [call]) khiến YouTube trả thẻ nhạc thiếu
     * thời lượng ở trang chủ. [extra] là phần riêng của lệnh (browseId hoặc continuation).
     */
    internal suspend fun mtTvBrowse(account: Account, extra: JSONObject): JSONObject {
        val visitor = visitorData()
        val headers = mutableMapOf(
            "Authorization" to "Bearer " + freshToken(account),
            "User-Agent" to MT_APP_UA,
            "Referer" to MT_APP_REFERER
        )
        val body = JSONObject(extra.toString())
            .put("context", JSONObject().put("client", mtTvClient(visitor)).put("user", safetyUser()))
            .put("racyCheckOk", true).put("contentCheckOk", true)
        val res = Http.postJson(BASE + "browse?prettyPrint=false", body, headers)
        if (res.code == 401 || res.code == 403) throw AuthRejected()
        return res.requireJson()
    }

    internal suspend fun mtFeedback(account: Account, token: String): Int {
        val visitor = visitorData()
        val headers = mutableMapOf<String, String>()
        val client: JSONObject
        if (account.kind == LoginKind.CODE) {
            client = mtTvClient(visitor)
            headers["Authorization"] = "Bearer " + freshToken(account)
            headers["User-Agent"] = MT_APP_UA
            headers["Referer"] = MT_APP_REFERER
        } else {
            client = mtWebClient(visitor)
            val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
            val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
            headers.putAll(mapOf("Authorization" to auth, "Cookie" to cookies, "Origin" to ORIGIN, "X-Origin" to ORIGIN,
                "Referer" to "$ORIGIN/", "X-Goog-AuthUser" to account.authUser.toString(),
                "X-Youtube-Client-Name" to "1", "X-Youtube-Client-Version" to MT_WEB_VERSION, "User-Agent" to MT_WEB_UA))
            if (visitor != null) headers["X-Goog-Visitor-Id"] = visitor
        }
        val body = JSONObject()
            .put("context", JSONObject().put("client", client).put("user", safetyUser()))
            .put("racyCheckOk", true).put("contentCheckOk", true)
            .put("feedbackTokens", org.json.JSONArray().put(token))
        val res = Http.postJson(BASE + "feedback?prettyPrint=false", body, headers)
        if (res.code == 401) throw AuthRejected()
        return res.code
    }

    private suspend fun freshToken(account: Account): String {
        val access = account.accessToken
        if (access != null && System.currentTimeMillis() < account.expiresAt) return access
        val refresh = account.refreshToken ?: throw AuthRejected()
        val tokens = try {
            DeviceCodeAuth.refresh(refresh)
        } catch (e: AuthException) {
            throw AuthRejected()
        }
        AccountStore.updateTokens(account.id, tokens.accessToken, tokens.expiresAt)
        return tokens.accessToken
    }

    // ---- WEB: cookie + SAPISIDHASH --------------------------------------------------------

    internal const val ORIGIN = "https://www.youtube.com"

    private fun rawCookies(origin: String): String? = try {
        CookieManager.getInstance().getCookie(origin)
    } catch (_: Exception) {
        null
    }

    private fun parseCookies(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        raw.split(";").forEach { p ->
            val i = p.indexOf('=')
            if (i > 0) out[p.substring(0, i).trim()] = p.substring(i + 1).trim()
        }
        return out
    }

    fun webCookies(): Map<String, String> = rawCookies(ORIGIN)?.let(::parseCookies) ?: emptyMap()

    fun isWebSignedIn(): Boolean {
        val c = webCookies()
        return c.containsKey("SAPISID") || c.containsKey("__Secure-3PAPISID")
    }

    internal fun webAuthHeaders(authUser: Int, origin: String = ORIGIN): Map<String, String> {
        val raw = rawCookies(origin) ?: return emptyMap()
        // Đủ ba băm như WebSession.authorization của meetube (YouTube hiện đòi SAPISID1PHASH / SAPISID3PHASH).
        val auth = mtAuthorization(raw, System.currentTimeMillis() / 1000, origin) ?: return mapOf("Cookie" to raw)
        return mapOf(
            "Cookie" to raw,
            "Authorization" to auth,
            "X-Goog-AuthUser" to authUser.toString(),
            "X-Origin" to origin
        )
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- Lấy thông tin tài khoản sau khi đăng nhập ------------------------------------------

    /** Thông tin hiển thị lấy từ accounts_list (tên Google, email, kênh đang chọn, số người đăng ký). */
    class Profile(
        val googleName: String?,
        val email: String?,
        val channelName: String?,
        val handle: String?,
        val subs: String?,
        val photo: String?
    )

    suspend fun profile(account: Account): Profile? {
        val body = JSONObject().put(
            "accountReadMask",
            JSONObject().put("returnOwner", true).put("returnBrandAccounts", true).put("returnPersonaAccounts", false)
        )
        val json = call("account/accounts_list", body, account)
        val header = findKey(json, "googleAccountHeaderRenderer")
        val items = findAllKeys(json, "accountItem")
        val item = items.firstOrNull { it.optBoolean("isSelected") } ?: items.firstOrNull()
        var email = header?.let { ytText(it.opt("email")) }
        var handle = item?.let { ytText(it.opt("channelHandle")) }
        var subs: String? = null
        // accountByline có thể là email, @handle hoặc số người đăng ký tuỳ loại tài khoản.
        val byline = item?.let { ytText(it.opt("accountByline")) }
        if (byline != null) {
            when {
                byline.startsWith("@") -> if (handle == null) handle = byline
                byline.contains("@") -> if (email == null) email = byline
                else -> subs = byline
            }
        }
        if (header == null && item == null) return null
        return Profile(
            googleName = header?.let { ytText(it.opt("name")) },
            email = email,
            channelName = item?.let { ytText(it.opt("accountName")) },
            handle = handle,
            subs = subs,
            photo = item?.dig("accountPhoto", "thumbnails", 0, "url") as? String
        )
    }

    private val SUBS_REGEX = Regex(
        "(Không có người đăng ký|No subscribers|\\d[\\d.,\\u00a0\\u202f ]*\\s?(?:N|Tr|K|M|B|nghìn|triệu|tỷ)?\\s?(?:người đăng ký|subscribers?))",
        RegexOption.IGNORE_CASE
    )

    private fun findSubs(node: Any?): String? {
        when (node) {
            is String -> SUBS_REGEX.find(node)?.let { return it.value.trim() }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) findSubs(node.opt(keys.next()))?.let { return it }
            }
            is org.json.JSONArray -> for (i in 0 until node.length()) findSubs(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Mã kênh (UC…) từ @handle, null nếu không đổi được. */
    suspend fun channelId(handle: String, account: Account): String? {
        val h = if (handle.startsWith("@")) handle else "@$handle"
        val resolved = call("navigation/resolve_url", JSONObject().put("url", "https://www.youtube.com/$h"), account)
        return findKey(resolved, "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
    }

    /** Số người đăng ký của kênh: đổi @handle thành mã kênh rồi đọc phần đầu trang kênh. */
    suspend fun channelSubs(handle: String, account: Account): String? {
        val h = if (handle.startsWith("@")) handle else "@$handle"
        val resolved = call("navigation/resolve_url", JSONObject().put("url", "https://www.youtube.com/$h"), account)
        val id = findKey(resolved, "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() } ?: return null
        val page = call("browse", JSONObject().put("browseId", id), account)
        return findSubs(page)
    }

    private fun Account.withProfile(p: Profile?) = if (p == null) this else copy(
        name = p.channelName ?: name,
        googleName = p.googleName ?: googleName,
        email = p.email ?: email,
        handle = p.handle ?: handle,
        subs = p.subs ?: subs,
        photo = p.photo ?: photo
    )

    /** Làm mới thông tin hiển thị của mọi tài khoản đã đăng nhập (lỗi thì giữ dữ liệu cũ). */
    suspend fun refreshProfiles() {
        for (acc in AccountStore.accounts.toList()) {
            try {
                val p = profile(acc)
                var updated = if (p != null) acc.withProfile(p) else acc
                if (updated.subs == null) {
                    updated.handle?.let { h ->
                        val subs = try {
                            channelSubs(h, updated)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        if (subs != null) updated = updated.copy(subs = subs)
                    }
                }
                if (updated != acc) AccountStore.replace(updated)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Tài khoản đăng nhập bằng mã: hỏi accounts_list bằng token vừa nhận. */
    suspend fun accountForTokens(tokens: OAuthTokens): Account {
        val temp = Account(
            id = "code-" + tokens.accessToken.take(12), kind = LoginKind.CODE, name = "Tài khoản YouTube",
            accessToken = tokens.accessToken, refreshToken = tokens.refreshToken, expiresAt = tokens.expiresAt
        )
        val full = try {
            temp.withProfile(profile(temp))
        } catch (_: Exception) {
            temp // Không lấy được tên thì vẫn đăng nhập được với tên mặc định.
        }
        val stable = (full.email ?: full.handle ?: full.name.takeIf { it != temp.name })
            ?.let { "code-" + it.lowercase() } ?: temp.id
        return full.copy(id = stable)
    }

    /** Tài khoản web ở ô [slot]; null nếu ô đó không có tài khoản. */
    suspend fun webAccount(slot: Int): Account? {
        val temp = Account(id = "web-$slot", kind = LoginKind.WEB, name = "", authUser = slot)
        val json = call("account/account_menu", JSONObject(), temp)
        val header = findKey(json, "activeAccountHeaderRenderer") ?: return null
        val name = ytText(header.opt("accountName")) ?: return null
        val email = ytText(header.opt("email"))
        val handle = ytText(header.opt("channelHandle"))
        val photo = header.dig("accountPhoto", "thumbnails", 0, "url") as? String
        val base = Account(
            id = "web-", kind = LoginKind.WEB, name = name, handle = handle, email = email,
            photo = photo, authUser = slot
        )
        val full = try {
            base.withProfile(profile(base.copy(id = "web-$slot")))
        } catch (_: Exception) {
            base
        }
        val key = (full.email ?: full.handle ?: full.name).lowercase()
        return full.copy(id = "web-$key")
    }
}
