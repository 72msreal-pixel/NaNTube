package com.nan.nantube.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kho dữ liệu chung của NaNTube nằm ngoài vùng riêng của app: `/storage/emulated/0/NaNTube/`.
 * Gỡ app rồi cài lại thì thư mục này vẫn còn, app đọc lại là có đủ dữ liệu.
 *
 * Cấu trúc:
 *   NaNTube/guest/<tính năng>.json            dữ liệu của chế độ khách (chưa đăng nhập)
 *   NaNTube/accounts/<id tài khoản>/<tính năng>.json   dữ liệu của từng tài khoản
 *
 * Cách thêm một tính năng mới (vd "watch_later"): chỉ cần gọi
 *   NanStorage.writeJson("watch_later", account, jsonArray)   // lưu
 *   NanStorage.readJson("watch_later", account)               // đọc (JSONObject hoặc JSONArray, null nếu chưa có)
 * hoặc [read]/[write] với chuỗi thường. [account] = null là khách. Không cần làm gì khác.
 *
 * Chưa cấp quyền "Quản lý tất cả tệp" thì dữ liệu vẫn được giữ trong vùng riêng của app (bản sao dự phòng) và
 * tự chép ra thư mục NaNTube ngay khi quyền được cấp ([onAccessChanged]).
 *
 * Không đưa token đăng nhập vào đây: thư mục này ai cũng đọc được.
 */
object NanStorage {
    const val FOLDER = "NaNTube"
    private const val GUEST = "guest"
    private const val ACCOUNTS = "accounts"
    private const val MIRROR = "nantube-data"

    private var appContext: Context? = null
    private val listeners = ArrayList<() -> Unit>()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Gọi khi người dùng vừa cấp quyền: chép dữ liệu dự phòng ra thư mục chung rồi báo các tính năng nạp lại. */
    @Synchronized
    fun onAccessChanged() {
        if (!hasAccess()) return
        syncMirrorToExternal()
        listeners.toList().forEach { runCatching { it() } }
    }

    /** Tính năng đăng ký để được báo nạp lại dữ liệu sau khi quyền được cấp. */
    @Synchronized
    fun addReloadListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    // ---------- Quyền truy cập ----------

    /** Android 11+: "Quản lý tất cả tệp". Android 10 trở xuống: quyền ghi bộ nhớ thông thường. */
    fun hasAccess(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** Màn cài đặt để cấp quyền trên Android 11+; Android cũ hơn trả null (xin bằng hộp thoại quyền thường). */
    fun accessSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val uri = Uri.parse("package:" + context.packageName)
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---------- Đường dẫn ----------

    val root: File get() = File(Environment.getExternalStorageDirectory(), FOLDER)

    /** Tên thư mục của một tài khoản: dùng id ổn định của tài khoản (code-<email> / web-<email>) để cài lại vẫn khớp. */
    private fun ownerDir(account: Account?): String =
        if (account == null) GUEST else ACCOUNTS + "/" + account.id.map { if (it.isLetterOrDigit() || it in "@._-") it else '_' }.joinToString("")

    private fun featureFile(feature: String) = feature.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("") + ".json"

    private fun external(feature: String, account: Account?) = File(File(root, ownerDir(account)), featureFile(feature))

    private fun mirror(feature: String, account: Account?): File? {
        val ctx = appContext ?: return null
        return File(File(File(ctx.filesDir, MIRROR), ownerDir(account)), featureFile(feature))
    }

    // ---------- Đọc / ghi ----------

    /** Đọc dữ liệu của [feature] cho [account] (null = khách). Ưu tiên thư mục NaNTube, không có thì bản dự phòng. */
    @Synchronized
    fun read(feature: String, account: Account?): String? {
        if (hasAccess()) {
            val file = external(feature, account)
            val text = runCatching { if (file.isFile) file.readText() else null }.getOrNull()
            if (text != null) return text
        }
        return runCatching { mirror(feature, account)?.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    /** Ghi dữ liệu: luôn ghi bản dự phòng trong app, và ghi vào thư mục NaNTube nếu đã có quyền. Trả true nếu đã ra thư mục NaNTube. */
    @Synchronized
    fun write(feature: String, account: Account?, text: String): Boolean {
        mirror(feature, account)?.let { runCatching { atomicWrite(it, text) } }
        if (!hasAccess()) return false
        return runCatching { atomicWrite(external(feature, account), text) }.isSuccess
    }

    fun readJson(feature: String, account: Account?): Any? {
        val text = read(feature, account)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { if (text.startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull()
    }

    fun writeJson(feature: String, account: Account?, json: Any): Boolean = write(feature, account, json.toString())

    /** Xoá dữ liệu một tính năng của một tài khoản (cả bản dự phòng lẫn thư mục NaNTube). */
    @Synchronized
    fun delete(feature: String, account: Account?) {
        mirror(feature, account)?.delete()
        if (hasAccess()) runCatching { external(feature, account).delete() }
    }

    /** Ghi qua tệp tạm rồi đổi tên: mất điện / app bị tắt giữa chừng cũng không làm hỏng tệp cũ. */
    private fun atomicWrite(target: File, text: String) {
        val dir = target.parentFile ?: return
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) error("Không tạo được thư mục ${dir.path}")
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Không ghi được ${target.path}")
            }
        }
    }

    /**
     * Chép các bản dự phòng sang thư mục NaNTube. Tệp đã có ở NaNTube mà mới hơn bản dự phòng thì giữ nguyên
     * (đó là dữ liệu từ lần cài trước), chỉ chép khi chưa có hoặc bản dự phòng mới hơn.
     */
    private fun syncMirrorToExternal() {
        val ctx = appContext ?: return
        val base = File(ctx.filesDir, MIRROR)
        base.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.forEach { src ->
            runCatching {
                val dst = File(root, src.relativeTo(base).path)
                if (!dst.exists() || src.lastModified() > dst.lastModified()) atomicWrite(dst, src.readText())
            }
        }
    }
}
