package com.nan.nantube.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.nan.nantube.data.PipPrefs
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
 *
 * Video dài (màn xem [WatchScreen] hoặc thẻ nổi [WatchMini]) dùng chung đường này: cùng dịch vụ [ShortsPlaybackService], cùng hai công tắc
 * popup / phát nền, nhưng bộ phát là [WatchPlayback] (không phải [ShortsPool]); app ẩn thì chỉ phát âm thanh ([WatchPlayback.setBackgroundAudioOnly]),
 * hai nút Trước / Tiếp của thẻ media là lùi / tua 10 giây. Công tắc "Chỉ phát nền khi có popup" ([PipPrefs.onlyWithPopup]) áp cho cả Short
 * lẫn video thông thường; riêng phiên "trên đường đi" tiếp tục phát tiếng mà không cần popup.
 */
object ShortsBackground {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var endJob: Job? = null

    /** Bộ phát video dài đã bị chuyển sang chỉ-âm-thanh lúc app ẩn (để bật hình lại đúng bộ phát đó khi app hiện). */
    private var audioOnlyWatch: WatchPlayback? = null

    private const val SKIP_MS = 10_000L

    /** App đã ẩn mà được phép phát nền (đặt ở [onAppHidden] theo hai công tắc, hoặc khi bấm Phát ở thẻ media). */
    @Volatile
    private var hiddenPlayAllowed = false

    /**
     * Bộ phát có được phép phát lúc này không. App còn hiện / đang ở PiP thì được; app ẩn thì chỉ khi phát nền được phép. Mọi nơi bật tiếng
     * ([ShortsPool.setActive], [WatchPlayback]) đều hỏi đây, nên tiếng không thể "kẹt" lúc app ẩn khi người dùng đã tắt phát nền / popup:
     * trước đây chỉ có một lệnh dừng ở onStop, lệnh phát lại nào tới sau (ExoPlayer tự phát lại khi lấy lại âm thanh, hiệu ứng giao diện đang chờ)
     * là phát nốt phần đã tải.
     */
    fun mayPlay(): Boolean = appVisible || PipState.inPip || hiddenPlayAllowed

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

    /** Bộ phát video dài đang hiện (thẻ nổi trước, rồi màn xem); null nếu không có hoặc đã giải phóng. */
    internal val watchPb: WatchPlayback? get() = (WatchMini.pb ?: PipState.watchPb)?.takeIf { !it.released }

    /** Phiên đang chạy là video dài: không có thẻ Short nào mở, không có Short đang phát, và có bộ phát video dài. */
    val isWatchSession: Boolean get() = !ShortsMini.open && !PipState.playingShort && watchPb != null

    /** Có popup đang mở trong app: thẻ nổi của Short (không tính phiên nền) hoặc của video dài. */
    private val popupOpen: Boolean get() = (ShortsMini.open && !ShortsMini.background) || WatchMini.open

    /** "trên đường đi" cho phép phát nền riêng phiên video dài, không đổi cài đặt phát nền chung. */
    private val onTheGoSession: Boolean get() = isWatchSession && watchPb?.onTheGo == true

    private fun backgroundAllowed(): Boolean =
        onTheGoSession || (PipPrefs.background && (!PipPrefs.onlyWithPopup || popupOpen))

    /** Video đang phát: của phiên (thẻ mini / phiên nền) nếu có, rồi video dài, không thì Short đang hiện. */
    val currentVideo: VideoItem?
        get() = when {
            ShortsMini.open -> ShortsMini.current
            isWatchSession -> watchPb?.video
            else -> PipState.video
        }

    /** Biểu tượng Phát / Tạm dừng phải theo đúng thực tế. */
    val isPlaying: Boolean
        get() = when {
            ShortsMini.open -> !ShortsMini.shownPaused
            isWatchSession -> watchPb?.let { it.wantPlay && !it.ended } == true
            else -> PipState.playingShort
        }

    /** Vị trí / thời lượng / đang đệm của phiên hiện tại, cho thẻ media. Thời lượng không rõ = -1. */
    fun positionMs(): Long {
        val id = currentVideo?.id ?: return 0L
        val player = if (isWatchSession) watchPb?.player else ShortsPool.slot(id)?.player
        return (player?.currentPosition ?: 0L).coerceAtLeast(0L)
    }

    fun durationMs(): Long {
        val id = currentVideo?.id ?: return -1L
        val player = if (isWatchSession) watchPb?.player else ShortsPool.slot(id)?.player
        return player?.duration?.takeIf { it > 0 } ?: -1L
    }

    fun isBuffering(): Boolean {
        val id = currentVideo?.id ?: return false
        return if (isWatchSession) watchPb?.buffering == true else ShortsPool.slot(id)?.buffering == true
    }

    fun seekTo(pos: Long) {
        val id = currentVideo?.id ?: return
        val player = if (isWatchSession) watchPb?.player else ShortsPool.slot(id)?.player
        player?.seekTo(pos)
    }

    /** Lùi / tua [delta] ms trong video dài. */
    private fun watchSeek(delta: Long) {
        val pb = watchPb ?: return
        val now = pb.player.currentPosition.coerceAtLeast(0L)
        val duration = pb.player.duration.takeIf { it > 0 }
        pb.seekTo((now + delta).coerceAtLeast(0L).let { if (duration != null) it.coerceAtMost(duration) else it })
    }

    fun prev() {
        if (ShortsMini.open) ShortsMini.prev() else PipState.controls?.prev()
    }

    fun next() {
        if (ShortsMini.open) ShortsMini.next() else PipState.controls?.next()
    }

    fun toggle() {
        // Bấm Phát ở thẻ media lúc app ẩn, kể cả phiên "trên đường đi".
        if (!appVisible && backgroundAllowed()) hiddenPlayAllowed = true
        when {
            ShortsMini.open -> ShortsMini.toggle()
            isWatchSession -> watchPb?.toggle()
            else -> PipState.controls?.toggle()
        }
    }

    /** Nút Trước / Tiếp của thẻ media: Short thì sang Short khác, video dài thì lùi / tua 10 giây (đổi video cần giao diện, lúc app ẩn không chạy). */
    fun mediaPrev() {
        if (isWatchSession) watchSeek(-SKIP_MS) else prev()
    }

    fun mediaNext() {
        if (isWatchSession) watchSeek(SKIP_MS) else next()
    }

    // ---- Vòng đời app (MainActivity gọi) ------------------------------------------------------------------------

    /** Short đang phát mà app sắp rời: bật dịch vụ nền khi app còn hiện. An toàn khi gọi nhiều lần. */
    fun keepAlive() {
        val context = appContext ?: return
        // PiP sắp mở cũng tính là popup. Phiên "trên đường đi" không cần PiP.
        if (!backgroundAllowed() && !(PipPrefs.background && PipPrefs.popup && PipState.armed)) return
        if (isPlaying && currentVideo != null) ShortsPlaybackService.ensure(context)
    }

    /**
     * onStop mà không ở PiP: app không còn hiện. Short đang phát thì thành phiên nền chỉ phát âm thanh; đang tạm dừng thì đứng yên.
     * Gọi TRƯỚC `super.onStop()` để các bộ quan sát vòng đời của giao diện thấy phiên đã mở.
     */
    fun onAppHidden() {
        appVisible = false
        hiddenPlayAllowed = backgroundAllowed()
        val context = appContext ?: return
        if (!hiddenPlayAllowed) {
            // Tắt "Hỗ trợ phát nền video", hoặc bật "Chỉ phát nền khi có popup" mà không có popup: ẩn app là dừng, không phiên nền, không thẻ media.
            if (ShortsMini.open) ShortsMini.paused = true
            ShortsPool.pauseAll()
            watchPb?.player?.playWhenReady = false
            ShortsPlaybackService.stop(context)
            return
        }
        if (isWatchSession) {
            // Video dài: chỉ phát âm thanh + thẻ media; đang tạm dừng thì đứng yên.
            val pb = watchPb ?: return
            if (!pb.wantPlay) return
            BackgroundPlay.played = true
            pb.setBackgroundAudioOnly(true)
            audioOnlyWatch = pb
            ShortsPlaybackService.ensure(context)
            return
        }
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

    /** onStart: gỡ chế độ chỉ âm thanh tạm thời; "trên đường đi" vẫn giữ. Trả phiên Short nền về màn hình. */
    fun onAppShown() {
        appVisible = true
        hiddenPlayAllowed = false
        endJob?.cancel()
        ShortsPool.enterAudioOnly(false)
        audioOnlyWatch?.setBackgroundAudioOnly(false)
        audioOnlyWatch = null
        if (ShortsMini.background) {
            val id = ShortsMini.current?.id
            ShortsMini.endBackground()
            PipState.returnId = id
        }
    }

    /** onResume: app đã ở trước mặt, không cần thông báo nền nữa (thẻ mini còn mở thì dịch vụ ở lại tới khi đóng). */
    fun onResumed() {
        val context = appContext ?: return
        if (!ShortsMini.open && !WatchMini.open && !PipState.inPip) ShortsPlaybackService.stop(context)
    }

    /** Cửa sổ PiP bị đóng / bấm Đóng ở thông báo / thoát app: dừng hẳn, không để âm thanh chạy mồ côi. */
    fun stopAll() {
        endJob?.cancel()
        PipState.watchStop?.invoke()
        if (ShortsMini.open) ShortsMini.closeAndStop() else ShortsPool.pauseAll()
        ShortsPool.enterAudioOnly(false)
        // Video dài: thẻ nổi thì đóng hẳn (WatchMini.close giải phóng bộ phát); ở màn xem thì watchStop ở trên đã dừng tiếng.
        WatchMini.close()
        audioOnlyWatch?.setBackgroundAudioOnly(false)
        audioOnlyWatch = null
        appContext?.let { ShortsPlaybackService.stop(it) }
    }

    // ---- Phiên nền ---------------------------------------------------------------------------------------------

    /** Phiên đổi (Tiếp / Trước / Phát-Tạm dừng) lúc app ẩn: giao diện không chạy nên làm ngay, không chờ Compose. */
    fun onSessionChanged() {
        if (!ShortsMini.open) {
            // Video dài: chỉ cần làm mới thẻ media (bộ phát tự chạy, không cần giao diện).
            if (watchPb != null) ShortsPlaybackService.refresh()
            return
        }
        // Thẻ media (thông báo / màn khóa / bảng cài đặt nhanh) luôn phải theo đúng video + trạng thái, kể cả lúc app còn hiện.
        if (!appVisible) applyPlayback()
        ShortsPlaybackService.refresh()
    }

    /** Popup Short vừa mở (app còn hiện nên bật dịch vụ nền hợp lệ): hiện thẻ điều khiển media ngay, không đợi rời app / tắt màn hình. */
    fun onPopupOpened() {
        val context = appContext ?: return
        if ((ShortsMini.open || WatchMini.open) && PipPrefs.background) ShortsPlaybackService.ensure(context)
    }

    /** Popup đóng khi app đang hiện: gỡ thẻ media (rời app thì [stopAll] / phiên nền lo). */
    fun onPopupClosed() {
        val context = appContext ?: return
        if (appVisible && !ShortsMini.open && !WatchMini.open && !PipState.inPip) ShortsPlaybackService.stop(context)
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
        if (!PipPrefs.unrestricted || !played || isExempt(activity)) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        request(activity)
    }
}
