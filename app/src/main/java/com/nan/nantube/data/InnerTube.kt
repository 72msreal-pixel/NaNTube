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
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val WEB_VERSION = "2.20250120.01.00"
    private const val TV_VERSION = "7.20250219.14.00"
    private const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    /**
     * [asWeb]: với tài khoản đăng nhập bằng mã, gửi ngữ cảnh WEB kèm Bearer thay vì ngữ cảnh TV.
     * Meetube tạo danh sách phát (playlist/create) đúng kiểu này: mẫu WEB + token của tài khoản.
     */
    suspend fun call(endpoint: String, extra: JSONObject, account: Account?, asWeb: Boolean = false): JSONObject {
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

    /** User-Agent mà [call] dùng cho tài khoản này (đường dẫn /player cũ), để tải đúng UA với link nhận được. */
    internal fun callUserAgent(account: Account?): String =
        if (account?.kind == LoginKind.CODE) TV_UA else Http.DEFAULT_UA

    internal const val VISION_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
    internal const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

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

    private fun randomId(length: Int): String {
        val random = java.security.SecureRandom()
        return buildString { repeat(length) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }
    }

    private fun utcOffsetMinutes(): Int = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

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

    private fun safetyUser() = JSONObject().put("enableSafetyMode", false).put("lockedSafetyMode", false)

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

    /**
     * Tạo danh sách phát đúng như meetube (PlaylistApiHelper.getCreatePlaylistQuery + PlaylistService.createPlaylist):
     * POST youtubei/v1/playlist/create, thân là mẫu WEB chỉ thêm `title` (và `videoIds` khi tạo kèm video), KHÔNG có
     * chế độ hiển thị. Tài khoản mã đi kèm Bearer, đúng bộ header của interceptor meetube (UA app, referer TV, không
     * `key`, không Origin / X-Youtube-Client-*); tài khoản web đi theo `WebSession.post` của meetube (cookie + SAPISIDHASH).
     */
    suspend fun createPlaylist(account: Account, title: String, videoId: String?): JSONObject {
        val visitor = visitorData()
        val data = JSONObject().put("title", title)
        if (videoId != null) data.put("videoIds", org.json.JSONArray().put(videoId))

        val headers = HashMap<String, String>()
        val body: JSONObject
        val url: String
        if (account.kind == LoginKind.CODE) {
            body = JSONObject()
                .put("context", JSONObject().put("client", mtWebClient(visitor)).put("user", safetyUser()))
                .put("racyCheckOk", true).put("contentCheckOk", true)
            url = BASE + "playlist/create?prettyPrint=false&t=" + randomId(12)
            headers["User-Agent"] = MT_APP_UA
            headers["Referer"] = MT_APP_REFERER
            headers["Authorization"] = "Bearer " + freshToken(account)
        } else {
            val client = JSONObject().put("clientName", "WEB").put("clientVersion", MT_WEB_VERSION)
                .put("hl", "vi").put("gl", "VN")
            if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
            body = JSONObject().put("context", JSONObject().put("client", client))
            url = BASE + "playlist/create"
            val cookies = rawCookies(ORIGIN) ?: throw AuthRejected()
            val auth = mtAuthorization(cookies, System.currentTimeMillis() / 1000, ORIGIN) ?: throw AuthRejected()
            headers["Cookie"] = cookies
            headers["Authorization"] = auth
            headers["Origin"] = ORIGIN
            headers["X-Origin"] = ORIGIN
            headers["Referer"] = "$ORIGIN/"
            headers["X-Goog-AuthUser"] = account.authUser.toString()
            headers["X-Youtube-Client-Name"] = "1"
            headers["X-Youtube-Client-Version"] = MT_WEB_VERSION
            headers["User-Agent"] = MT_WEB_UA
            if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
        }
        val keys = data.keys()
        while (keys.hasNext()) keys.next().let { body.put(it, data.get(it)) }

        val res = Http.postJson(url, body, headers)
        if (res.code == 401 || res.code == 403) throw AuthRejected()
        return res.requireJson()
    }

    /**
     * /player cho client phát link trực tiếp (VISIONOS, ANDROID_VR), dựng đúng như QueryBuilder + VideoInfoApi của meetube:
     * context có clientScreen / userAgent / browser (Safari cho VISIONOS) / vùng / visitorData do máy chủ cấp, user,
     * racyCheckOk + contentCheckOk + cpn; header Origin + X-Goog-Visitor-Id + UA + X-Youtube-Client-*; URL không có `key`
     * (meetube gọi /player không key, như yt-dlp).
     */
    internal suspend fun directPlayer(videoId: String, vision: Boolean): JSONObject {
        val visitor = visitorData()
        val version = if (vision) "1.02" else "1.65.10"
        val ua = if (vision) VISION_UA else VR_UA
        val client = JSONObject()
            .put("clientName", if (vision) "VISIONOS" else "ANDROID_VR").put("clientVersion", version)
            .put("clientScreen", "WATCH").put("userAgent", ua)
        if (vision) {
            client.put("browserName", "Safari").put("browserVersion", "605.1.15")
                .put("deviceMake", "Apple").put("deviceModel", "RealityDevice17,1")
                .put("osName", "visionOS").put("osVersion", "26.5.23O471")
        } else {
            client.put("androidSdkVersion", "32").put("osName", "Android").put("osVersion", "12L")
                .put("deviceModel", "Quest 3").put("deviceMake", "Oculus")
        }
        client.put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", utcOffsetMinutes().toString())
        if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
        val body = JSONObject()
            .put("context", JSONObject().put("client", client).put("user", safetyUser()))
            .put("racyCheckOk", true).put("contentCheckOk", true)
            .put("videoId", videoId).put("cpn", randomId(16))
        val headers = HashMap<String, String>()
        headers["Origin"] = ORIGIN
        headers["User-Agent"] = ua
        headers["X-Youtube-Client-Name"] = if (vision) "101" else "28"
        headers["X-Youtube-Client-Version"] = version
        if (!visitor.isNullOrEmpty()) headers["X-Goog-Visitor-Id"] = visitor
        val res = Http.postJson(BASE + "player?prettyPrint=false", body, headers)
        check(res.code in 200..299) { "Không đọc được dữ liệu video (HTTP ${res.code})" }
        return JSONObject(res.body)
    }

    private fun context(tv: Boolean): JSONObject {
        val client = JSONObject()
            .put("hl", "vi").put("gl", "VN")
            .put("clientName", if (tv) "TVHTML5" else "WEB")
            .put("clientVersion", if (tv) TV_VERSION else WEB_VERSION)
        return JSONObject().put("client", client)
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

    private const val ORIGIN = "https://www.youtube.com"

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
        val cookies = parseCookies(raw)
        val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: return mapOf("Cookie" to raw)
        val ts = System.currentTimeMillis() / 1000
        val hash = sha1("$ts $sapisid $origin")
        return mapOf(
            "Cookie" to raw,
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
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
