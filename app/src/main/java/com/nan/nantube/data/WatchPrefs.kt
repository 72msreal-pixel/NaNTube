package com.nan.nantube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cài đặt của màn xem video dài, lưu theo từng tài khoản qua [NanStorage]:
 * NaNTube/accounts/<id tài khoản>/watch_settings.json, chưa đăng nhập thì NaNTube/guest/watch_settings.json.
 * Đổi tài khoản thì nạp lại theo tài khoản mới (chưa có tệp thì về mặc định).
 *
 * - [autoplay]: hết video tự phát video đề xuất đầu tiên (công tắc trên thanh trên của trình phát);
 * - [ambient]: chế độ môi trường xung quanh (ánh sáng mờ từ khung hình tràn xuống nền trang, theo AmbientGlow của meetube);
 * - [stableVolume]: âm lượng ổn định, hạ bớt video quá to theo độ ồn YouTube đo (theo AudioLoudness của meetube);
 * - [captionKey]: phụ đề đang bật ([CaptionTrack.key], null = tắt), [captionLast] là bản gần nhất để nút CC bật lại đúng bản đó.
 */
object WatchPrefs {
    private const val FEATURE = "watch_settings"

    var autoplay by mutableStateOf(true)
        private set
    var ambient by mutableStateOf(true)
        private set
    var stableVolume by mutableStateOf(true)
        private set
    var captionKey by mutableStateOf<String?>(null)
        private set
    var captionLast by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var account: Account? = null
    private var ownerId: String? = null
    private var hooked = false

    /** Gọi khi màn xem video mở hoặc đổi tài khoản: nạp cài đặt của [account] (null = khách). */
    fun bind(account: Account?) {
        val id = account?.id.orEmpty()
        if (ownerId == id) return
        ownerId = id
        this.account = account
        if (!hooked) {
            hooked = true
            // Cấp quyền tệp xong thì đọc lại: cài đặt lần cài trước có thể đã nằm sẵn ở thư mục NaNTube.
            NanStorage.addReloadListener { scope.launch { reload() } }
        }
        reload()
    }

    private fun reload() {
        val owner = account
        val id = ownerId
        scope.launch {
            val json = withContext(Dispatchers.IO) { NanStorage.readJson(FEATURE, owner) as? JSONObject }
            if (ownerId != id) return@launch // trong lúc đọc đã đổi sang tài khoản khác
            autoplay = json?.optBoolean("autoplay", true) ?: true
            ambient = json?.optBoolean("ambient", true) ?: true
            stableVolume = json?.optBoolean("stableVolume", true) ?: true
            captionKey = json?.optString("captionKey")?.ifBlank { null }
            captionLast = json?.optString("captionLast")?.ifBlank { null } ?: captionKey
        }
    }

    fun updateAutoplay(on: Boolean) {
        autoplay = on
        save()
    }

    fun updateAmbient(on: Boolean) {
        ambient = on
        save()
    }

    fun updateStableVolume(on: Boolean) {
        stableVolume = on
        save()
    }

    /** [key] null = tắt phụ đề; còn lại là bản được chọn (cũng thành bản "gần nhất"). */
    fun setCaption(key: String?) {
        captionKey = key
        if (key != null) captionLast = key
        save()
    }

    private fun save() {
        val owner = account
        val json = JSONObject()
            .put("autoplay", autoplay)
            .put("ambient", ambient)
            .put("stableVolume", stableVolume)
            .put("captionKey", captionKey ?: "")
            .put("captionLast", captionLast ?: "")
        scope.launch(Dispatchers.IO) { NanStorage.writeJson(FEATURE, owner, json) }
    }
}

/**
 * Hẹn giờ ngủ của trình phát (theo SleepTimer của meetube): tạm dừng sau N phút hoặc khi hết video hiện tại.
 * Chỉ trong phiên, không lưu; nằm ở mức toàn app nên đổi sang video khác (tự phát, bấm đề xuất) vẫn đếm tiếp.
 */
object SleepTimer {
    const val OFF = 0
    const val END_OF_VIDEO = -1
    val PRESETS = listOf(OFF, 10, 15, 20, 30, 45, 60, 90, END_OF_VIDEO)

    /** Mức đang chọn: số phút, [OFF] hoặc [END_OF_VIDEO]. */
    var picked by mutableIntStateOf(OFF)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var pause: (() -> Unit)? = null

    val endOfVideo: Boolean get() = picked == END_OF_VIDEO

    /** Trình phát đang xem đăng ký lệnh tạm dừng của nó (và hủy khi rời). */
    fun attach(action: () -> Unit) {
        pause = action
    }

    fun detach(action: () -> Unit) {
        if (pause === action) pause = null
    }

    fun set(minutes: Int) {
        job?.cancel()
        picked = minutes
        if (minutes > 0) {
            job = scope.launch {
                delay(minutes * 60_000L)
                expire()
            }
        }
    }

    /** Hết giờ (hoặc video đã dừng ở cuối với [END_OF_VIDEO]): tạm dừng và tắt hẹn giờ. */
    fun expire() {
        job?.cancel()
        picked = OFF
        pause?.invoke()
    }

    fun label(minutes: Int): String = when (minutes) {
        OFF -> "Đang tắt"
        END_OF_VIDEO -> "Cuối video"
        else -> "$minutes phút"
    }
}
