package com.nan.nantube.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Cài đặt "Cải thiện trải nghiệm dùng PiP" (Thử nghiệm các tính năng mới). Bật thì khi rời app, Short đang phát ở cửa sổ mini
 * sẽ thành cửa sổ nổi do app tự vẽ (cần quyền "Hiển thị trên các ứng dụng khác") thay cho PiP của hệ thống.
 *
 * Lưu qua [NanStorage] (thư mục NaNTube) nên gỡ app cài lại vẫn còn. Đang bật mà thiếu quyền thì app xin lại;
 * người dùng từ chối thì tự tắt.
 */
object PipPrefs {
    private const val FEATURE = "app_settings"
    private const val KEY = "enhancedPip"

    var enhanced by mutableStateOf(false)
        private set

    /** MainActivity gán: mở màn cấp quyền "Hiển thị trên các ứng dụng khác" của app. */
    var openOverlaySettings: (() -> Unit)? = null

    private var asking = false
    private var hooked = false
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        NanStorage.init(context)
        load()
        if (!hooked) {
            hooked = true
            // Cấp quyền tệp xong thì đọc lại: cài đặt của lần cài trước có thể đã nằm sẵn ở thư mục NaNTube.
            NanStorage.addReloadListener {
                load()
                ensurePermission()
            }
        }
    }

    private fun load() {
        val json = NanStorage.readJson(FEATURE, null) as? JSONObject ?: return
        enhanced = json.optBoolean(KEY, false)
    }

    private fun save() {
        val text = JSONObject().put(KEY, enhanced).toString()
        Thread { runCatching { NanStorage.write(FEATURE, null, text) } }.start()
    }

    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** Công tắc trong cài đặt. Bật: có quyền thì bật luôn, chưa có thì mở màn cấp quyền và chỉ bật nếu được cấp. */
    fun setEnabled(context: Context, on: Boolean) {
        if (!on) {
            enhanced = false
            save()
            return
        }
        if (canDraw(context)) {
            enhanced = true
            save()
        } else {
            request()
        }
    }

    /** Gọi ở onResume / sau khi nạp lại cài đặt: đang bật mà mất quyền thì xin lại. */
    fun ensurePermission() {
        val context = appContext ?: return
        if (enhanced && !canDraw(context)) request()
    }

    private fun request() {
        if (asking) return
        val open = openOverlaySettings ?: return
        asking = true
        open()
    }

    /** Người dùng quay lại từ màn cấp quyền: có quyền thì bật, không thì tắt. */
    fun onPermissionResult() {
        asking = false
        val context = appContext ?: return
        val granted = canDraw(context)
        if (enhanced != granted) {
            enhanced = granted
            save()
        } else if (granted) {
            save()
        }
    }
}
