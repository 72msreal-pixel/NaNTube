package com.nan.nantube.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import coil.Coil
import coil.request.ImageRequest
import com.nan.nantube.MainActivity
import com.nan.nantube.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dịch vụ nền của popup Short (làm theo MobilePlaybackService + BackgroundPlay của meetube): popup còn mở thì Short phát tiếp (chỉ
 * âm thanh) khi sang app khác / tắt màn hình, cho tới khi đóng popup.
 *
 * Có MediaSession + thông báo kiểu media (ảnh bìa, tên, kênh, thanh tua, Trước / Phát-Tạm dừng / Tiếp) nên hiện thành thẻ "Thông báo trực tiếp"
 * ở màn khóa / bảng điều khiển nhanh, có "Đầu ra media", điều khiển được từ tai nghe, như meetube và trình phát nhạc.
 *
 * Lúc app không hiện, cây Compose không có khung hình nên không tự chạy được các hiệu ứng (nạp video kế, chuyển video khi hết...):
 * dịch vụ làm thay phần đó ([driveBackground]); app hiện lại thì Compose làm tiếp.
 */
class ShortsAudioService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var session: MediaSession? = null
    private var art: Bitmap? = null
    private var artId: String? = null
    private var artJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "NaNTubeShorts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (ShortsMini.shownPaused) ShortsMini.toggle()
                }

                override fun onPause() {
                    if (!ShortsMini.shownPaused) ShortsMini.toggle()
                }

                override fun onSkipToNext() = ShortsMini.next()
                override fun onSkipToPrevious() = ShortsMini.prev()
                override fun onStop() = ShortsMini.closeAndStop()
                override fun onSeekTo(pos: Long) {
                    ShortsMini.current?.let { ShortsPool.slot(it.id)?.player?.seekTo(pos) }
                    updateState()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> ShortsMini.prev()
            ACTION_TOGGLE -> ShortsMini.toggle()
            ACTION_NEXT -> ShortsMini.next()
            ACTION_CLOSE -> ShortsMini.closeAndStop()
        }
        updateMetadata()
        updateState()
        foreground(notification())
        if (!ShortsMini.open) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                snapshotFlow { ShortsMini.open }.distinctUntilChanged().collect { open -> if (!open) shutdown() }
            }
            scope.launch {
                snapshotFlow { Triple(ShortsMini.current?.id, ShortsMini.shownPaused, ShortsMini.current?.let { ShortsPool.slot(it.id)?.ready }) }
                    .distinctUntilChanged()
                    .collect {
                        if (!ShortsMini.open) return@collect
                        updateMetadata()
                        updateState()
                        refresh()
                    }
            }
            // Short lặp lại / vừa chuyển video: giữ vị trí trên thanh tua của thẻ media đúng.
            scope.launch {
                while (isActive) {
                    delay(2_000)
                    if (!ShortsMini.shownPaused) updateState()
                }
            }
            driveBackground()
        }
        return START_NOT_STICKY
    }

    /** Khi app không hiện: giữ ô phát video này + kế + trước, phát video đang chọn và tự sang Short kế khi hết (nếu bật tự vuốt). */
    private fun driveBackground() {
        var endJob: Job? = null
        scope.launch {
            snapshotFlow { Triple(ShortsMini.appVisible, ShortsMini.current?.id, ShortsMini.paused) }
                .distinctUntilChanged()
                .collect { (visible, id, paused) ->
                    if (visible || id == null) return@collect
                    val list = ShortsMini.list
                    val idx = ShortsMini.index
                    ShortsPool.window(listOfNotNull(list.getOrNull(idx), list.getOrNull(idx + 1), list.getOrNull(idx - 1)))
                    ShortsPool.setActive(id, !paused)
                }
        }
        scope.launch {
            snapshotFlow {
                val id = ShortsMini.current?.id
                // Cửa sổ nổi (PipOverlay) tự chuyển video của nó; ở đây chỉ lo khi không có cửa sổ nào.
                !ShortsMini.appVisible && !PipOverlay.active && ShortsPool.autoScroll && id != null && ShortsPool.slot(id)?.ended == true
            }.distinctUntilChanged().collect { finished ->
                endJob?.cancel()
                if (!finished) return@collect
                endJob = launch {
                    // Chờ chút cho hết hẳn tiếng rồi mới sang video kế (cùng độ trễ với tự vuốt trong app).
                    delay(AUTO_SWIPE_TAIL_MS)
                    if (!ShortsMini.appVisible) ShortsMini.next()
                }
            }
        }
    }

    // ---- MediaSession ----------------------------------------------------------------------------

    private fun updateState() {
        val s = session ?: return
        val slot = ShortsMini.current?.let { ShortsPool.slot(it.id) }
        val playing = !ShortsMini.shownPaused
        val pos = (slot?.player?.currentPosition ?: 0L).coerceAtLeast(0L)
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )
    }

    private fun updateMetadata() {
        val s = session ?: return
        val video = ShortsMini.current ?: return
        val duration = ShortsPool.slot(video.id)?.player?.duration?.takeIf { it > 0 } ?: -1L
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, video.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, video.channel ?: "Short")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Shorts")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        if (artId == video.id) art?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        s.setMetadata(builder.build())
        if (artId != video.id) loadArt(video.id, video.thumbnail)
    }

    /** Ảnh bìa lấy bằng Coil ngoài luồng chính; xong thì cập nhật lại thẻ media. */
    private fun loadArt(id: String, url: String) {
        artJob?.cancel()
        artJob = scope.launch {
            val result = Coil.imageLoader(this@ShortsAudioService).execute(
                ImageRequest.Builder(this@ShortsAudioService).data(url).allowHardware(false).size(720).build()
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@launch
            if (ShortsMini.current?.id != id) return@launch
            art = bitmap
            artId = id
            updateMetadata()
            refresh()
        }
    }

    // ---- Thông báo ---------------------------------------------------------------------------------

    private fun refresh() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }

    private fun shutdown() {
        artJob?.cancel()
        scope.cancel()
        started = false
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            isActive = false
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun foreground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Không lên được dịch vụ nền (hạn chế của hãng): vẫn phát, hệ thống có thể dừng sớm khi sang app khác.
        }
    }

    private fun action(name: String, request: Int, icon: Int, label: String): Notification.Action {
        val intent = PendingIntent.getService(
            this, request, Intent(this, ShortsAudioService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Short phát nền", NotificationManager.IMPORTANCE_LOW))
        }
        val tap = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val video = ShortsMini.current
        val paused = ShortsMini.shownPaused
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(video?.title ?: "NaNTube")
            .setContentText(video?.channel ?: "Short")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(ACTION_PREV, 1, R.drawable.ic_pip_prev, "Short trước"))
            .addAction(
                if (paused) action(ACTION_TOGGLE, 2, R.drawable.ic_pip_play, "Phát")
                else action(ACTION_TOGGLE, 2, R.drawable.ic_pip_pause, "Tạm dừng")
            )
            .addAction(action(ACTION_NEXT, 3, R.drawable.ic_pip_next, "Short tiếp"))
            .addAction(action(ACTION_CLOSE, 4, R.drawable.ic_pip_close, "Đóng"))
        if (artId == video?.id) art?.let { builder.setLargeIcon(it) }
        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        session?.let { style.setMediaSession(it.sessionToken) }
        builder.setStyle(style)
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "shorts_audio"
        private const val NOTIFICATION_ID = 7312
        private const val ACTION_PREV = "com.nan.nantube.action.SHORTS_AUDIO_PREV"
        private const val ACTION_TOGGLE = "com.nan.nantube.action.SHORTS_AUDIO_TOGGLE"
        private const val ACTION_NEXT = "com.nan.nantube.action.SHORTS_AUDIO_NEXT"
        private const val ACTION_CLOSE = "com.nan.nantube.action.SHORTS_AUDIO_CLOSE"

        /** Gọi lúc mở popup (app đang hiện nên được phép bật dịch vụ nền). Tự dừng khi popup đóng. */
        fun start(context: Context) {
            try {
                val intent = Intent(context, ShortsAudioService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) {
                // Không bật được: popup vẫn phát bình thường khi app hiện.
            }
        }
    }
}
