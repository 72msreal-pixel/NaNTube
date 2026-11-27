package com.nan.nantube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import org.json.JSONObject

/**
 * Cài đặt popup / phát nền của Short VÀ video dài (Thử nghiệm các tính năng mới):
 * - [popup] (mặc định bật): xem Short / video dạng popup, cả trong app (thẻ nổi) lẫn ngoài app (PiP). Tắt thì không tạo popup nào và
 *   bấm nút Hình trong hình sẽ mở trang cài đặt này;
 * - [background] (mặc định bật): hỗ trợ phát nền. Tắt thì rời app / tắt màn hình là video dừng, không còn thẻ media;
 * - [onlyWithPopup] (mặc định tắt): chỉ phát nền khi video đang ở popup (thẻ nổi, hoặc cửa sổ PiP khi vừa rời app). Đang xem toàn màn hình
 *   mà ẩn app (không vào được PiP) thì dừng thay vì phát nền;
 * - [unrestricted] (mặc định bật): chạy nền không giới hạn (miễn tối ưu pin). Tắt thì app không xin / nhắc quyền này nữa.
 *
 * Lưu theo từng tài khoản qua [NanStorage]: NaNTube/accounts/<id>/app_settings.json (chưa đăng nhập: NaNTube/guest/app_settings.json),
 * nên gỡ app cài lại vẫn còn và mỗi tài khoản có bộ công tắc riêng. Tài khoản chưa có tệp riêng thì lấy tạm từ tệp chung của bản cũ.
 */
object PipPrefs {
    private const val FEATURE = "app_settings"
    private const val KEY_POPUP = "autoPip"
    private const val KEY_BACKGROUND = "backgroundPlay"
    private const val KEY_UNRESTRICTED = "unrestricted"
    private const val KEY_ONLY_POPUP = "backgroundOnlyWithPopup"

    var popup by mutableStateOf(true)
        private set
    var background by mutableStateOf(true)
        private set
    var unrestricted by mutableStateOf(true)
        private set
    var onlyWithPopup by mutableStateOf(false)
        private set

    private var hooked = false
    private var account: Account? = null

    /** Đổi tài khoản (AppRoot gọi): nạp bộ công tắc của [account] (null = khách). */
    fun bind(account: Account?) {
        if (this.account?.id == account?.id && bound) return
        this.account = account
        bound = true
        load()
    }

    private var bound = false

    fun init(context: Context) {
        NanStorage.init(context)
        load()
        if (!hooked) {
            hooked = true
            // Cấp quyền tệp xong thì đọc lại: cài đặt của lần cài trước có thể đã nằm sẵn ở thư mục NaNTube.
            NanStorage.addReloadListener { load() }
        }
    }

    private fun load() {
        val json = (NanStorage.readJson(FEATURE, account) as? JSONObject
            ?: if (account != null) NanStorage.readJson(FEATURE, null) as? JSONObject else null)
        // Chưa có tệp nào: về mặc định (đổi sang tài khoản mới không được giữ công tắc của tài khoản trước).
        if (json == null) {
            popup = true; background = true; unrestricted = true; onlyWithPopup = false
            return
        }
        popup = json.optBoolean(KEY_POPUP, true)
        background = json.optBoolean(KEY_BACKGROUND, true)
        unrestricted = json.optBoolean(KEY_UNRESTRICTED, true)
        onlyWithPopup = json.optBoolean(KEY_ONLY_POPUP, false)
    }

    fun updatePopup(on: Boolean) {
        popup = on
        save()
    }

    fun updateBackground(on: Boolean) {
        background = on
        save()
    }

    fun updateUnrestricted(on: Boolean) {
        unrestricted = on
        save()
    }

    fun updateOnlyWithPopup(on: Boolean) {
        onlyWithPopup = on
        save()
    }

    private fun save() {
        val text = JSONObject()
            .put(KEY_POPUP, popup)
            .put(KEY_BACKGROUND, background)
            .put(KEY_UNRESTRICTED, unrestricted)
            .put(KEY_ONLY_POPUP, onlyWithPopup)
            .toString()
        val owner = account
        Thread { runCatching { NanStorage.write(FEATURE, owner, text) } }.start()
    }
}
