package com.nan.nantube.data

import android.accounts.AccountManager
import android.content.Context
import android.webkit.CookieManager
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
    /** Tên tài khoản Google (khác tên kênh), hiện ở đầu popup như app YouTube. */
    val googleName: String? = null,
    /** Chuỗi số người đăng ký của kênh, đã định dạng sẵn ("288 người đăng ký"). */
    val subs: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L
) {
    /** Dòng phụ dưới tên: ưu tiên email, không có thì handle. */
    val secondary: String? get() = email ?: handle

    /** Nhãn hiện cạnh tên để phân biệt hai tài khoản cùng Gmail. */
    val tag: String get() = if (kind == LoginKind.CODE) "code" else "web"
}

/** Kho tài khoản (SharedPreferences riêng của app) + trạng thái Compose để UI tự cập nhật. */
object AccountStore {
    private const val PREFS = "nantube_accounts"
    private var appContext: Context? = null

    val accounts = mutableStateListOf<Account>()

    /** Email Google có trên máy nhưng chưa đăng nhập vào app: hiện ở cuối danh sách. */
    val pending = mutableStateListOf<String>()

    /** Email người dùng đã xoá khỏi danh sách, để lần quét tài khoản trên máy không thêm lại. */
    private val hidden = mutableSetOf<String>()
    private var selectedId by mutableStateOf<String?>(null)

    /** Đang dùng "tài khoản khách" (chưa đăng nhập): [selected] trả về null dù đã có tài khoản trong danh sách. */
    var guest by mutableStateOf(false)
        private set

    val selected: Account?
        get() = if (guest) null else accounts.firstOrNull { it.id == selectedId } ?: accounts.firstOrNull()

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
        guest = prefs.getBoolean("guest", false)
        readStrings(prefs.getString("pending", "[]")).forEach { pending.add(it) }
        hidden.addAll(readStrings(prefs.getString("hidden", "[]")))
    }

    private fun readStrings(json: String?): List<String> = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun norm(email: String) = email.trim().lowercase()

    private fun isSignedIn(email: String) = accounts.any { it.email?.let { e -> norm(e) } == norm(email) }

    /** Thêm mới hoặc cập nhật (theo id). [select] = false thì giữ nguyên tài khoản đang dùng. */
    fun upsert(account: Account, select: Boolean = true) {
        val i = accounts.indexOfFirst { it.id == account.id }
        if (i >= 0) accounts[i] = account else accounts.add(account)
        if (select) {
            selectedId = account.id
            guest = false
        }
        account.email?.let { e -> pending.removeAll { norm(it) == norm(e) } }
        persist()
    }

    /** Đăng xuất = xoá hẳn tài khoản khỏi app. Hết tài khoản web thì dọn luôn cookie của WebView. */
    fun remove(id: String) {
        val acc = accounts.firstOrNull { it.id == id } ?: return
        accounts.remove(acc)
        if (selectedId == id) selectedId = accounts.firstOrNull()?.id
        if (accounts.none { it.kind == LoginKind.WEB }) {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (_: Exception) {
            }
        }
        persist()
    }

    /** Đổi chỗ tài khoản [fromId] sang vị trí của [toId] (dùng khi kéo thả). */
    fun move(fromId: String, toId: String) {
        val from = accounts.indexOfFirst { it.id == fromId }
        val to = accounts.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        accounts.add(to, accounts.removeAt(from))
        persist()
    }

    /** Thêm email trên máy (người dùng tự chọn) vào danh sách chưa đăng nhập. */
    fun addPending(email: String) {
        val e = email.trim()
        if (e.isEmpty()) return
        hidden.remove(norm(e))
        if (!isSignedIn(e) && pending.none { norm(it) == norm(e) }) pending.add(e)
        persist()
    }

    /** Bỏ email khỏi danh sách chưa đăng nhập vì đã đăng nhập xong (không ẩn vĩnh viễn). */
    fun dropPending(email: String) {
        if (pending.removeAll { norm(it) == norm(email) }) persist()
    }

    /** Cập nhật thông tin hiển thị của tài khoản đã có, không đổi tài khoản đang chọn. */
    fun replace(account: Account) {
        val i = accounts.indexOfFirst { it.id == account.id }
        if (i < 0) return
        accounts[i] = account
        account.email?.let { e -> pending.removeAll { norm(it) == norm(e) } }
        persist()
    }

    /** Kho nhỏ cho dữ liệu thư viện (vd mã danh sách Music của từng tài khoản). */
    fun libraryPrefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences("nantube_library", Context.MODE_PRIVATE)

    var permissionAsked: Boolean
        get() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean("permAsked", false) ?: false
        set(v) {
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("permAsked", v)?.apply()
        }

    fun removePending(email: String) {
        pending.removeAll { norm(it) == norm(email) }
        hidden.add(norm(email))
        persist()
    }

    /** Quét các tài khoản Google app nhìn thấy được trên máy (Android 8+ chỉ thấy tài khoản người dùng đã cấp quyền qua bảng chọn). */
    fun refreshDeviceAccounts(context: Context) {
        try {
            val found = AccountManager.get(context).getAccountsByType("com.google").map { it.name }
            var changed = false
            for (e in found) {
                if (norm(e) in hidden || isSignedIn(e) || pending.any { norm(it) == norm(e) }) continue
                pending.add(e)
                changed = true
            }
            if (changed) persist()
        } catch (_: Exception) {
            // Không có quyền xem tài khoản: bỏ qua, người dùng vẫn thêm được qua bảng chọn.
        }
    }

    /** Cập nhật token (khi refresh) mà không đổi tài khoản đang chọn. */
    fun updateTokens(id: String, accessToken: String, expiresAt: Long) {
        val i = accounts.indexOfFirst { it.id == id }
        if (i < 0) return
        accounts[i] = accounts[i].copy(accessToken = accessToken, expiresAt = expiresAt)
        persist()
    }

    fun useGuest() {
        guest = true
        persist()
    }

    fun select(id: String) {
        selectedId = id
        guest = false
        persist()
    }

    private fun accountsJson() = JSONArray().also { arr -> accounts.forEach { arr.put(toJson(it)) } }

    private fun persist() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("accounts", accountsJson().toString())
            .putString("selected", selectedId)
            .putBoolean("guest", guest)
            .putString("pending", JSONArray(pending.toList()).toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .apply()
    }

    private fun JSONObject.optText(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun toJson(a: Account) = JSONObject()
        .put("id", a.id).put("kind", a.kind.name).put("name", a.name)
        .put("handle", a.handle ?: JSONObject.NULL).put("email", a.email ?: JSONObject.NULL)
        .put("photo", a.photo ?: JSONObject.NULL).put("authUser", a.authUser)
        .put("googleName", a.googleName ?: JSONObject.NULL).put("subs", a.subs ?: JSONObject.NULL)
        .put("accessToken", a.accessToken ?: JSONObject.NULL)
        .put("refreshToken", a.refreshToken ?: JSONObject.NULL)
        .put("expiresAt", a.expiresAt)

    private fun fromJson(o: JSONObject) = Account(
        id = o.getString("id"),
        kind = LoginKind.valueOf(o.getString("kind")),
        name = o.getString("name"),
        handle = o.optText("handle"),
        email = o.optText("email"),
        photo = o.optText("photo"),
        authUser = o.optInt("authUser", 0),
        googleName = o.optText("googleName"),
        subs = o.optText("subs"),
        accessToken = o.optText("accessToken"),
        refreshToken = o.optText("refreshToken"),
        expiresAt = o.optLong("expiresAt", 0L)
    )
}
