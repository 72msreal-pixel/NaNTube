package com.nan.nantube.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class LoginKind { CODE, WEB }

/**
 * Một tài khoản đã đăng nhập.
 * CODE: đăng nhập bằng mã (OAuth thiết bị kiểu YouTube TV), giữ access/refresh token.
 * WEB : đăng nhập tự nhập qua trang Google, phiên nằm trong cookie của WebView, [authUser] là ô tài khoản.
 */
data class Account(
    val id: String,
    val kind: LoginKind,
    val name: String,
    val handle: String? = null,
    val email: String? = null,
    val photo: String? = null,
    val authUser: Int = 0,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L
) {
    /** Dòng phụ dưới tên: ưu tiên email, không có thì handle. */
    val secondary: String? get() = email ?: handle
}

/** Kho tài khoản (SharedPreferences riêng của app) + trạng thái Compose để UI tự cập nhật. */
object AccountStore {
    private const val PREFS = "nantube_accounts"
    private var appContext: Context? = null

    val accounts = mutableStateListOf<Account>()
    private var selectedId by mutableStateOf<String?>(null)

    val selected: Account? get() = accounts.firstOrNull { it.id == selectedId } ?: accounts.firstOrNull()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(prefs.getString("accounts", "[]"))
            for (i in 0 until arr.length()) accounts.add(fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
            // Dữ liệu hỏng thì coi như chưa đăng nhập.
        }
        selectedId = prefs.getString("selected", null)
    }

    /** Thêm mới hoặc cập nhật (theo id) rồi chọn luôn tài khoản đó. */
    fun upsert(account: Account) {
        val i = accounts.indexOfFirst { it.id == account.id }
        if (i >= 0) accounts[i] = account else accounts.add(account)
        selectedId = account.id
        persist()
    }

    /** Cập nhật token (khi refresh) mà không đổi tài khoản đang chọn. */
    fun updateTokens(id: String, accessToken: String, expiresAt: Long) {
        val i = accounts.indexOfFirst { it.id == id }
        if (i < 0) return
        accounts[i] = accounts[i].copy(accessToken = accessToken, expiresAt = expiresAt)
        persist()
    }

    fun select(id: String) {
        selectedId = id
        persist()
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        accounts.forEach { arr.put(toJson(it)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("accounts", arr.toString())
            .putString("selected", selectedId)
            .apply()
    }

    private fun toJson(a: Account) = JSONObject()
        .put("id", a.id).put("kind", a.kind.name).put("name", a.name)
        .put("handle", a.handle ?: JSONObject.NULL).put("email", a.email ?: JSONObject.NULL)
        .put("photo", a.photo ?: JSONObject.NULL).put("authUser", a.authUser)
        .put("accessToken", a.accessToken ?: JSONObject.NULL)
        .put("refreshToken", a.refreshToken ?: JSONObject.NULL)
        .put("expiresAt", a.expiresAt)

    private fun fromJson(o: JSONObject) = Account(
        id = o.getString("id"),
        kind = LoginKind.valueOf(o.getString("kind")),
        name = o.getString("name"),
        handle = o.optString("handle").takeIf { it.isNotBlank() && it != "null" },
        email = o.optString("email").takeIf { it.isNotBlank() && it != "null" },
        photo = o.optString("photo").takeIf { it.isNotBlank() && it != "null" },
        authUser = o.optInt("authUser", 0),
        accessToken = o.optString("accessToken").takeIf { it.isNotBlank() && it != "null" },
        refreshToken = o.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
        expiresAt = o.optLong("expiresAt", 0L)
    )
}
