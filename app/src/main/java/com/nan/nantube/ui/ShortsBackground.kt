package com.nan.nantube.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Điều khiển việc Short phát khi rời app, viết lại theo meetube (MobilePlaybackActivity + MobilePlaybackService + BackgroundPlay).
 * Mọi quyết định nằm ở đây và do [com.nan.nantube.MainActivity] gọi đúng chỗ, không còn rải trong các hàm Compose:
 *
 *  1. App còn hiện, Short đang phát, người dùng sắp rời ([keepAlive], gọi ở onUserLeaveHint / onPause): bật dịch vụ nền
 *     [ShortsPlaybackService] NGAY lúc này (Android 12+ cấm bật dịch vụ nền từ lúc app đã ẩn), như meetube bật dịch vụ khi trình phát
 *     còn trên màn hình.
 *  2. Rời bằng Home / cử chỉ và bật PiP: MainActivity vào PiP của hệ thống. Cửa sổ PiP vẫn là chính bộ phát Short trong app, video phát
 *     tiếp, nút Trước / Phát-Tạm dừng / Tiếp điều khiển bộ phát đó.
 *  3. App ẩn hẳn mà không có PiP (tắt màn hình, sang app khác, tắt PiP trong cài đặt): [onAppHidden] chuyển Short đang phát thành một
 *     "phiên nền" ([ShortsMini.beginBackground]) rồi chỉ phát âm thanh (tắt luồng hình như setBackgroundAudioMode của meetube). Compose
 *     không có khung hình lúc app ẩn nên phiên nền tự chạy các việc nạp video kế / chuyển video / tự vuốt, không chờ giao diện.
 *  4. App hiện lại ([onAppShown]): phiên nền trả về bộ phát toàn màn hình, cuộn tới đúng video đang phát ([PipState.returnId]).
 *  5. Cửa sổ PiP bị đóng (X) hoặc bấm Đóng ở thông báo / thoát app bằng Back: [stopAll] dừng hẳn, như meetube đóng video khi tắt PiP.
 */
object ShortsBackground {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var endJob: Job? = null

    /** Cửa sổ app đang hiện (MainActivity onStart / onStop). PiP vẫn tính là hiện. */
    @Volatile
    var appVisible = true
        private set

    fun attach(context: Context) {
        appContext = context.applicationContext
        // Hết video (chỉ xảy ra khi bật tự vuốt): lúc app ẩn thì phiên nền tự sang video kế.
        ShortsPool.endedListener = { id -> onSlotEnded(id) }
    }

    // ---- Lệnh từ thông báo / màn khóa / tai nghe / nút PiP ---------------------------------------------------------

    /** Video đang phát: của phiên (thẻ mini / phiên nền) nếu có, không thì Short đang hiện. */
    val currentVideo: VideoItem? get() = if (ShortsMini.open) ShortsMini.current else PipState.video

    /** Biểu tượng Phát / Tạm dừng phải theo đúng thực tế. */
    val isPlaying: Boolean
        get() = if (ShortsMini.open) !ShortsMini.shownPaused else PipState.playingShort

    fun prev() {
        if (ShortsMini.open) ShortsMini.prev() else PipState.controls?.prev()
    }

    fun next() {
        if (ShortsMini.open) ShortsMini.next() else PipState.controls?.next()
    }

    fun toggle() {
        if (ShortsMini.open) ShortsMini.toggle() else PipState.controls?.toggle()
    }

    // ---- Vòng đời app (MainActivity gọi) ------------------------------------------------------------------------

    /** Short đang phát mà app sắp rời: bật dịch vụ nền khi app còn hiện. An toàn khi gọi nhiều lần. */
    fun keepAlive() {
        val context = appContext ?: return
        if (isPlaying && currentVideo != null) ShortsPlaybackService.ensure(context)
    }

    /**
     * onStop mà không ở PiP: app không còn hiện. Short đang phát thì thành phiên nền chỉ phát âm thanh; đang tạm dừng thì đứng yên.
     * Gọi TRƯỚC `super.onStop()` để các bộ quan sát vòng đời của giao diện thấy phiên đã mở.
     */
    fun onAppHidden() {
        appVisible = false
        val context = appContext ?: return
        if (!ShortsMini.open) {
            if (!PipState.playingShort) {
                ShortsPool.pauseAll()
                return
            }
            val (list, at) = PipState.snapshot?.invoke() ?: run {
                ShortsPool.pauseAll()
                return
            }
            if (list.isEmpty()) {
                ShortsPool.pauseAll()
                return
            }
            ShortsMini.beginBackground(list, at)
        } else if (ShortsMini.shownPaused) {
            return
        }
        BackgroundPlay.played = true
        ShortsPool.enterAudioOnly(true)
        applyPlayback()
        ShortsPlaybackService.ensure(context)
    }

    /** onStart: app hiện lại. Bật lại luồng hình; phiên nền (nếu có) trả về bộ phát toàn màn hình. */
    fun onAppShown() {
        appVisible = true
        endJob?.cancel()
        ShortsPool.enterAudioOnly(false)
        if (ShortsMini.background) {
            val id = ShortsMini.current?.id
            ShortsMini.endBackground()
            PipState.returnId = id
        }
    }

    /** onResume: app đã ở trước mặt, không cần thông báo nền nữa (thẻ mini còn mở thì dịch vụ ở lại tới khi đóng). */
    fun onResumed() {
        val context = appContext ?: return
        if (!ShortsMini.open && !PipState.inPip) ShortsPlaybackService.stop(context)
    }

    /** Cửa sổ PiP bị đóng / bấm Đóng ở thông báo / thoát app: dừng hẳn, không để âm thanh chạy mồ côi. */
    fun stopAll() {
        endJob?.cancel()
        if (ShortsMini.open) ShortsMini.closeAndStop() else ShortsPool.pauseAll()
        ShortsPool.enterAudioOnly(false)
        appContext?.let { ShortsPlaybackService.stop(it) }
    }

    // ---- Phiên nền ---------------------------------------------------------------------------------------------

    /** Phiên đổi (Tiếp / Trước / Phát-Tạm dừng) lúc app ẩn: giao diện không chạy nên làm ngay, không chờ Compose. */
    fun onSessionChanged() {
        if (appVisible || !ShortsMini.open) return
        applyPlayback()
        ShortsPlaybackService.refresh()
    }

    /** Giữ ô phát của video này + kế + trước, chỉ phát video đang chọn (và chỉ khi không tạm dừng). */
    private fun applyPlayback() {
        val id = ShortsMini.current?.id ?: return
        val list = ShortsMini.list
        val idx = ShortsMini.index
        ShortsPool.window(listOfNotNull(list.getOrNull(idx), list.getOrNull(idx + 1), list.getOrNull(idx - 1)))
        ShortsPool.setActive(id, !ShortsMini.paused)
    }

    private fun onSlotEnded(id: String) {
        if (appVisible || !ShortsMini.open || PipState.inPip) return
        if (ShortsMini.current?.id != id) return
        endJob?.cancel()
        endJob = scope.launch {
            // Chờ chút cho hết hẳn tiếng rồi mới sang video kế (cùng độ trễ với tự vuốt trong app).
            delay(AUTO_SWIPE_TAIL_MS)
            if (!appVisible && ShortsMini.current?.id == id) ShortsMini.next()
        }
    }
}

/**
 * Xin miễn tối ưu pin (theo BackgroundPlay của meetube): dịch vụ media là đủ trên Android gốc, nhưng trình quản lý pin của hãng
 * (realme / ColorOS, Xiaomi...) vẫn đóng băng app vài phút sau khi tắt màn hình. Hộp thoại hệ thống này là công tắc duy nhất chúng nghe.
 */
object BackgroundPlay {
    private const val PREFS = "nantube_background"
    private const val KEY_ASKED = "battery_asked"

    /** Đã có một phiên phát nền trong tiến trình này: lần về app sau đó hỏi một lần. */
    @Volatile
    var played = false

    fun isExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Hộp thoại hệ thống "Cho phép ứng dụng luôn chạy nền?", không có thì mở danh sách cài đặt pin. */
    @SuppressLint("BatteryLife") // app media cài ngoài cửa hàng: phát nền là tính năng chính
    fun request(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
            )
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                // máy không có màn cài đặt pin
            }
        }
    }

    /** Đúng một lần, sau khi đã phát nền: chỉ hỏi khi app còn bị tối ưu pin. */
    fun askOnce(activity: Activity) {
        if (!played || isExempt(activity)) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        request(activity)
    }
}
