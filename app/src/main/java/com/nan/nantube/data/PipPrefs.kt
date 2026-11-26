package com.nan.nantube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import org.json.JSONObject

/**
 * Cài đặt popup / phát nền của Short (Thử nghiệm các tính năng mới):
 * - [popup] (mặc định bật): xem Short dạng popup, cả trong app (thẻ nổi) lẫn ngoài app (PiP). Tắt thì không tạo popup nào và bấm nút
 *   Hình trong hình sẽ mở trang cài đặt này;
 * - [background] (mặc định bật): hỗ trợ phát nền. Tắt thì rời app / tắt màn hình là video dừng, không còn thẻ media;
 * - [unrestricted] (mặc định bật): chạy nền không giới hạn (miễn tối ưu pin). Tắt thì app không xin / nhắc quyền này nữa.
 *
 * Lưu qua [NanStorage] (thư mục NaNTube) nên gỡ app cài lại vẫn còn.
 */
object PipPrefs {
    private const val FEATURE = "app_settings"
    private const val KEY_POPUP = "autoPip"
    private const val KEY_BACKGROUND = "backgroundPlay"
    private const val KEY_UNRESTRICTED = "unrestricted"

    var popup by mutableStateOf(true)
        private set
    var background by mutableStateOf(true)
        private set
    var unrestricted by mutableStateOf(true)
        private set

    private var hooked = false

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
        val json = NanStorage.readJson(FEATURE, null) as? JSONObject ?: return
        popup = json.optBoolean(KEY_POPUP, true)
        background = json.optBoolean(KEY_BACKGROUND, true)
        unrestricted = json.optBoolean(KEY_UNRESTRICTED, true)
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

    private fun save() {
        val text = JSONObject()
            .put(KEY_POPUP, popup)
            .put(KEY_BACKGROUND, background)
            .put(KEY_UNRESTRICTED, unrestricted)
            .toString()
        Thread { runCatching { NanStorage.write(FEATURE, null, text) } }.start()
    }
}
