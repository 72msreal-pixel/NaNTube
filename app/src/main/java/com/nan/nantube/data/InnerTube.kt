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
