package com.nan.nantube.data

import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import org.json.JSONObject

class DeviceCode(
    val deviceCode: String,
    /** Mã hiển thị cho người dùng, dạng "ABC-DEF-GHI". */
    val userCode: String,
    /** Trang nhập mã thủ công. */
    val url: String,
    /** Link có sẵn mã (dùng cho nút "Mở trang xác nhận" và QR). */
    val fullUrl: String,
    val expiresInSec: Int,
    val intervalSec: Int
)

class OAuthTokens(val accessToken: String, val refreshToken: String?, val expiresAt: Long)

class AuthException(message: String) : Exception(message)

/**
 * Đăng nhập bằng mã: OAuth "device flow" của YouTube TV, đúng mô hình trong meetube
 * (YTSignInPresenter: lấy mã -> người dùng duyệt ở yt.be/activate -> poll token mỗi ~3 giây).
 * Phần backend của meetube nằm ở submodule MediaServiceCore (không có trong zip) nên được dựng lại ở đây.
 */
object DeviceCodeAuth {
    // Client công khai của app YouTube TV (SmartTube/meetube dùng cùng cặp này).
    const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    private const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    private const val CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
    private const val TOKEN_URL = "https://www.youtube.com/o/oauth2/token"

    // Giống YTSignInPresenter của meetube.
    const val SIGN_IN_URL = "https://yt.be/activate"
    private const val QR_SIGN_IN_URL = "https://youtube.com/qr/activate/"

    private const val MIN_POLL_SEC = 3

    suspend fun requestCode(): DeviceCode {
        val res = Http.postForm(
            CODE_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE,
                "device_id" to UUID.randomUUID().toString(),
                "model_name" to "ytlr::"
            )
        )
        val json = parse(res)
        val userCode = json.optString("user_code")
        val deviceCode = json.optString("device_code")
        if (res.code !in 200..299 || userCode.isBlank() || deviceCode.isBlank()) {
            throw AuthException(describe(json, res))
        }
        return DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            url = SIGN_IN_URL,
            fullUrl = QR_SIGN_IN_URL + userCode.replace(" ", "-"),
            expiresInSec = json.optInt("expires_in", 1800),
            intervalSec = maxOf(json.optInt("interval", MIN_POLL_SEC), MIN_POLL_SEC)
        )
    }

    /** Poll cho tới khi người dùng duyệt xong. Huỷ coroutine là dừng poll. */
    suspend fun awaitTokens(code: DeviceCode): OAuthTokens {
        var interval = code.intervalSec
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (coroutineContext.isActive) {
            delay(interval * 1000L)
            if (System.currentTimeMillis() > deadline) throw AuthException("Mã đã hết hạn, hãy lấy mã mới.")
            val res = try {
                Http.postForm(
                    TOKEN_URL,
                    mapOf(
                        "client_id" to CLIENT_ID,
                        "client_secret" to CLIENT_SECRET,
                        "code" to code.deviceCode,
                        "grant_type" to "http://oauth.net/grant_type/device/1.0"
                    )
                )
            } catch (e: java.io.IOException) {
                continue // rớt mạng thoáng qua, thử lại ở vòng sau
            }
            val json = parse(res)
            val access = json.optString("access_token")
            if (access.isNotBlank()) return toTokens(json, access)
            when (json.optString("error")) {
                "authorization_pending", "" -> Unit
                "slow_down" -> interval += 2
                "access_denied" -> throw AuthException("Bạn đã từ chối đăng nhập.")
                "expired_token" -> throw AuthException("Mã đã hết hạn, hãy lấy mã mới.")
                else -> throw AuthException(describe(json, res))
            }
        }
        throw kotlinx.coroutines.CancellationException()
    }

    suspend fun refresh(refreshToken: String): OAuthTokens {
        val res = Http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
        )
        val json = parse(res)
        val access = json.optString("access_token")
        if (access.isBlank()) throw AuthException(describe(json, res))
        return toTokens(json, access, fallbackRefresh = refreshToken)
    }

    private fun toTokens(json: JSONObject, access: String, fallbackRefresh: String? = null) = OAuthTokens(
        accessToken = access,
        refreshToken = json.optString("refresh_token").ifBlank { fallbackRefresh },
        // trừ 60 giây cho chắc
        expiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000L
    )

    private fun parse(res: HttpResult): JSONObject = try {
        JSONObject(res.body)
    } catch (_: Exception) {
        JSONObject()
    }

    private fun describe(json: JSONObject, res: HttpResult): String {
        val desc = json.optString("error_description").ifBlank { json.optString("error") }
        return if (desc.isNotBlank()) "$desc (HTTP ${res.code})" else "Lỗi mạng hoặc máy chủ (HTTP ${res.code})"
    }
}
