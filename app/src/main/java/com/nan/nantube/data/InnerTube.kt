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

    suspend fun call(endpoint: String, extra: JSONObject, account: Account?): JSONObject {
        val tv = account?.kind == LoginKind.CODE
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
        }

        val res = Http.postJson(url, body, headers)
        if (res.code == 401 || (tv && res.code == 403)) throw AuthRejected()
        if (res.code !in 200..299) throw Exception("YouTube trả lỗi HTTP ${res.code}")
        return try {
            JSONObject(res.body)
        } catch (_: Exception) {
            throw Exception("Phản hồi không đọc được")
        }
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

    fun webCookies(): Map<String, String> {
        val raw = try {
            CookieManager.getInstance().getCookie(ORIGIN)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split(";").forEach { p ->
            val i = p.indexOf('=')
            if (i > 0) out[p.substring(0, i).trim()] = p.substring(i + 1).trim()
        }
        return out
    }

    fun isWebSignedIn(): Boolean {
        val c = webCookies()
        return c.containsKey("SAPISID") || c.containsKey("__Secure-3PAPISID")
    }

    private fun webAuthHeaders(authUser: Int): Map<String, String> {
        val raw = try {
            CookieManager.getInstance().getCookie(ORIGIN)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        val cookies = webCookies()
        val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: return mapOf("Cookie" to raw)
        val ts = System.currentTimeMillis() / 1000
        val hash = sha1("$ts $sapisid $ORIGIN")
        return mapOf(
            "Cookie" to raw,
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
            "X-Goog-AuthUser" to authUser.toString(),
            "X-Origin" to ORIGIN
        )
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- Lấy thông tin tài khoản sau khi đăng nhập ------------------------------------------

    /** Tài khoản đăng nhập bằng mã: hỏi accounts_list bằng token vừa nhận. */
    suspend fun accountForTokens(tokens: OAuthTokens): Account {
        val temp = Account(
            id = "code-" + tokens.accessToken.take(12), kind = LoginKind.CODE, name = "Tài khoản YouTube",
            accessToken = tokens.accessToken, refreshToken = tokens.refreshToken, expiresAt = tokens.expiresAt
        )
        var name: String? = null
        var email: String? = null
        var handle: String? = null
        var photo: String? = null
        try {
            val body = JSONObject().put(
                "accountReadMask",
                JSONObject().put("returnOwner", true).put("returnBrandAccounts", true).put("returnPersonaAccounts", false)
            )
            val json = call("account/accounts_list", body, temp)
            val items = findAllKeys(json, "accountItem")
            val item = items.firstOrNull { it.optBoolean("isSelected") } ?: items.firstOrNull()
            if (item != null) {
                name = ytText(item.opt("accountName"))
                email = ytText(item.opt("accountByline"))?.takeIf { it.contains("@") && !it.startsWith("@") }
                handle = ytText(item.opt("channelHandle"))
                    ?: ytText(item.opt("accountByline"))?.takeIf { it.startsWith("@") }
                photo = item.dig("accountPhoto", "thumbnails", 0, "url") as? String
            }
        } catch (_: Exception) {
            // Không lấy được tên thì vẫn đăng nhập được với tên mặc định.
        }
        val stable = (email ?: handle ?: name)?.let { "code-" + it.lowercase() } ?: temp.id
        return temp.copy(id = stable, name = name ?: temp.name, email = email, handle = handle, photo = photo)
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
        val key = (email ?: handle ?: name).lowercase()
        return Account(
            id = "web-$key", kind = LoginKind.WEB, name = name, handle = handle, email = email,
            photo = photo, authUser = slot
        )
    }
}
