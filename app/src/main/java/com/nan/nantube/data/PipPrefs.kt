package com.nan.nantube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import org.json.JSONObject

/**
 * Cài đặt phát nền của Short (Thử nghiệm các tính năng mới), theo `MiniPip.isAutoEnabled` của meetube:
 * - [autoPip] bật (mặc định): rời app lúc Short đang phát thì thu thành cửa sổ PiP của hệ thống;
 * - tắt: rời app thì Short phát tiếp chỉ âm thanh (dịch vụ nền + thông báo media).
 *
 * Lưu qua [NanStorage] (thư mục NaNTube) nên gỡ app cài lại vẫn còn.
 */
object PipPrefs {
    private const val FEATURE = "app_settings"
    private const val KEY = "autoPip"

    var autoPip by mutableStateOf(true)
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
        autoPip = json.optBoolean(KEY, true)
    }

    fun updateAutoPip(on: Boolean) {
        autoPip = on
        val text = JSONObject().put(KEY, on).toString()
        Thread { runCatching { NanStorage.write(FEATURE, null, text) } }.start()
    }
}
