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
    val SEEK_STEPS = listOf(10, 15, 20, 25, 30)

    /** Các mức tự ẩn nút của trình phát hỗ trợ tiếp cận; 0 = không bao giờ. */
    val HIDE_STEPS = listOf(0, 3, 5, 10)

    /** Các mức tự tắt trình phát hỗ trợ tiếp cận (ngày); 0 = không bao giờ. */
    val AUTO_OFF_DAYS = listOf(0, 1, 3, 7, 30)

    var autoplay by mutableStateOf(true)
        private set
    var ambient by mutableStateOf(true)
        private set
    var stableVolume by mutableStateOf(true)
        private set
    var seekSeconds by mutableIntStateOf(SEEK_STEPS.first())
        private set
    /**
     * Trình phát hỗ trợ tiếp cận (Cài đặt → Hỗ trợ tiếp cận): bật thì tua bằng hai nút ±[seekSeconds] giây ở giữa trình phát
     * (không còn nhấn đúp), chạm vào video không ẩn được nút, chỉ nút X ở góc trên mới ẩn.
     */
    var a11yPlayer by mutableStateOf(false)
        private set

    /** Số giây rồi các nút tự ẩn khi đang phát và [a11yPlayer] bật; 0 = không bao giờ (chỉ ẩn bằng nút X). */
    var hideControlsSeconds by mutableIntStateOf(0)
        private set

    /** Số ngày rồi [a11yPlayer] tự tắt kể từ lúc bật; 0 = không bao giờ. */
    var a11yAutoOffDays by mutableIntStateOf(0)
        private set

    /** Lúc bật [a11yPlayer] (ms), để tính hạn tự tắt. 0 = chưa biết. */
    private var a11yEnabledAt = 0L
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
            seekSeconds = json?.optInt("seekSeconds", SEEK_STEPS.first())?.takeIf { it in SEEK_STEPS } ?: SEEK_STEPS.first()
            a11yPlayer = json?.optBoolean("a11yPlayer", false) ?: false
            hideControlsSeconds = json?.optInt("hideControlsSeconds", 0)?.takeIf { it in HIDE_STEPS } ?: 0
            a11yAutoOffDays = json?.optInt("a11yAutoOffDays", 0)?.takeIf { it in AUTO_OFF_DAYS } ?: 0
            a11yEnabledAt = json?.optLong("a11yEnabledAt", 0L) ?: 0L
            captionKey = json?.optString("captionKey")?.ifBlank { null }
            captionLast = json?.optString("captionLast")?.ifBlank { null } ?: captionKey
            // Sau cùng, khi mọi trường đã nạp xong: checkA11yExpiry có thể ghi lại cả tệp.
            checkA11yExpiry()
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

    fun updateSeekSeconds(seconds: Int) {
        seekSeconds = seconds
        save()
    }

    fun updateA11yPlayer(on: Boolean) {
        a11yPlayer = on
        a11yEnabledAt = if (on) System.currentTimeMillis() else 0L
        save()
    }

    /** Đổi hạn tự tắt: tính lại từ bây giờ nếu trình phát hỗ trợ tiếp cận đang bật. */
    fun updateA11yAutoOffDays(days: Int) {
        a11yAutoOffDays = days
        if (a11yPlayer) a11yEnabledAt = System.currentTimeMillis()
        save()
    }

    /**
     * Đã quá hạn tự tắt thì tắt trình phát hỗ trợ tiếp cận và lưu. Gọi khi nạp cài đặt, khi mở màn Hỗ trợ tiếp cận
     * và khi trình phát dựng lên. Bản lưu cũ chưa có mốc bật thì lấy bây giờ làm mốc.
     */
    fun checkA11yExpiry() {
        if (!a11yPlayer || a11yAutoOffDays <= 0) return
        val now = System.currentTimeMillis()
        if (a11yEnabledAt <= 0L) {
            a11yEnabledAt = now
            save()
            return
        }
        if (now - a11yEnabledAt >= a11yAutoOffDays * 86_400_000L) {
            a11yPlayer = false
            a11yEnabledAt = 0L
            save()
        }
    }

    fun updateHideControlsSeconds(seconds: Int) {
        hideControlsSeconds = seconds
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
            .put("seekSeconds", seekSeconds)
            .put("a11yPlayer", a11yPlayer)
            .put("hideControlsSeconds", hideControlsSeconds)
            .put("a11yAutoOffDays", a11yAutoOffDays)
            .put("a11yEnabledAt", a11yEnabledAt)
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
