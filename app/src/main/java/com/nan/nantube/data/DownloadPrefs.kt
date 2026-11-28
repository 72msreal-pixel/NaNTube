package com.nan.nantube.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cài đặt ở Cài đặt → "Nội dung tải xuống", lưu theo từng tài khoản qua [NanStorage] (tệp download_settings.json):
 * - [quality]: mức đồ họa tải sẵn (0 = "Hỏi mỗi lần", còn lại là chiều cao 1080 / 720 / ...). Mức nào video không có
 *   thì lấy mức gần nhất thấp hơn (đúng cách [VideoDownloads] chọn luồng);
 * - [wifiOnly]: bật thì chỉ bắt đầu tải khi máy đang dùng Wi-Fi / Ethernet, mạng di động thì báo và không tải.
 */
object DownloadPrefs {
    private const val FEATURE = "download_settings"

    /** Các mức chọn được; 0 = hỏi mỗi lần. */
    val QUALITY_STEPS = listOf(0, 1080, 720, 480, 360, 240, 144)

    var quality by mutableIntStateOf(0)
        private set
    var wifiOnly by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var account: Account? = null
    private var ownerId: String? = null

    /** Nạp cài đặt của [account] (null = khách); đã nạp đúng tài khoản này rồi thì không làm gì. */
    suspend fun bind(account: Account?) {
        val id = account?.id.orEmpty()
        if (ownerId == id) return
        val json = withContext(Dispatchers.IO) { NanStorage.readJson(FEATURE, account) as? JSONObject }
        this.account = account
        ownerId = id
        quality = json?.optInt("quality", 0)?.takeIf { it in QUALITY_STEPS } ?: 0
        wifiOnly = json?.optBoolean("wifiOnly", false) ?: false
    }

    fun updateQuality(height: Int) {
        quality = height
        save()
    }

    fun updateWifiOnly(on: Boolean) {
        wifiOnly = on
        save()
    }

    /** Mức đồ họa tải sẵn dạng chiều cao, null = hỏi mỗi lần / mức cao nhất. */
    val presetHeight: Int? get() = quality.takeIf { it > 0 }

    /** true = đang bật "Chỉ tải xuống qua Wi-Fi" mà máy chưa dùng Wi-Fi: không được bắt đầu tải. */
    fun blockedByWifiOnly(context: Context): Boolean = wifiOnly && !Connectivity.onWifi(context)

    fun qualityLabel(height: Int): String = when (height) {
        0 -> "Hỏi mỗi lần"
        1080 -> "1080p (Full HD)"
        720 -> "720p (HD)"
        else -> "${height}p"
    }

    private fun save() {
        val owner = account
        val json = JSONObject().put("quality", quality).put("wifiOnly", wifiOnly)
        scope.launch(Dispatchers.IO) { NanStorage.writeJson(FEATURE, owner, json) }
    }
}
