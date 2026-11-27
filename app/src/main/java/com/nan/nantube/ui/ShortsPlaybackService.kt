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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dịch vụ media nền của Short (theo MobilePlaybackService của meetube): giữ tiến trình sống và có MediaSession + thông báo media
 * (ảnh bìa, tên, kênh, thanh tua, Trước / Phát-Tạm dừng / Tiếp / Đóng) khi Short phát lúc app ở PiP, tắt màn hình hoặc sang app khác.
 *
 * Dịch vụ KHÔNG sở hữu trình phát: các ExoPlayer nằm trong [ShortsPool] (sống ngoài Activity). Dịch vụ chỉ nối chúng với MediaSession
 * và điều khiển qua [ShortsBackground]. [ShortsBackground.keepAlive] bật dịch vụ lúc app còn hiện, nên `startForeground` luôn được
 * gọi từ trạng thái nền trước hợp lệ (Android 12+ cấm bật từ lúc app đã ẩn).
 */
class ShortsPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var art: Bitmap? = null
    private var artId: String? = null
    private var artJob: Job? = null
    private var ticker: Job? = null
    private var foregroundNow = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = MediaSession(this, "NaNTubeShorts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!ShortsBackground.isPlaying) ShortsBackground.toggle()
                }

                override fun onPause() {
                    if (ShortsBackground.isPlaying) ShortsBackground.toggle()
                }

                override fun onSkipToNext() = ShortsBackground.mediaNext()
                override fun onSkipToPrevious() = ShortsBackground.mediaPrev()
                override fun onStop() = ShortsBackground.stopAll()
                override fun onSeekTo(pos: Long) {
                    ShortsBackground.seekTo(pos)
                    updateState()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() bắt buộc phải có startForeground() ngay, kể cả khi sắp tự tắt.
        updateMetadata()
        updateState()
        foreground(notification())
        when (intent?.action) {
            ACTION_PREV -> ShortsBackground.mediaPrev()
            ACTION_TOGGLE -> ShortsBackground.toggle()
            ACTION_NEXT -> ShortsBackground.mediaNext()
            ACTION_CLOSE -> {
                ShortsBackground.stopAll()
                return START_NOT_STICKY
            }
        }
        if (ShortsBackground.currentVideo == null) {
            shutdown()
            return START_NOT_STICKY
        }
        if (ticker == null) {
            // Như media3 đẩy trạng thái mỗi khi player đổi: cập nhật định kỳ để thanh tua, biểu tượng và ảnh bìa luôn đúng
            // (không phụ thuộc Compose, vốn không chạy lúc app ẩn).
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (ShortsBackground.currentVideo == null) {
                        shutdown()
                        return@launch
                    }
                    refresh()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ---- MediaSession ----------------------------------------------------------------------------

    private fun updateState() {
        val s = session ?: return
        val playing = ShortsBackground.isPlaying
        val pos = ShortsBackground.positionMs()
        val state = when {
            ShortsBackground.isBuffering() && playing -> PlaybackState.STATE_BUFFERING
            playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(state, pos, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )
    }

    private fun updateMetadata() {
        val s = session ?: return
        val video = ShortsBackground.currentVideo ?: return
        val duration = ShortsBackground.durationMs()
        val watch = ShortsBackground.isWatchSession
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, video.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, video.channel ?: if (watch) "Video" else "Short")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, if (watch) "Video" else "Shorts")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        if (artId == video.id) art?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        s.setMetadata(builder.build())
        if (artId != video.id) loadArt(video.id, video.thumbnail)
    }

    /** Ảnh bìa lấy bằng Coil ngoài luồng chính; xong thì cập nhật lại thẻ media. */
    private fun loadArt(id: String, url: String) {
        artJob?.cancel()
        artJob = scope.launch {
            val result = Coil.imageLoader(this@ShortsPlaybackService).execute(
                ImageRequest.Builder(this@ShortsPlaybackService).data(url).allowHardware(false).size(720).build()
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@launch
            if (ShortsBackground.currentVideo?.id != id) return@launch
            art = bitmap
            artId = id
            updateMetadata()
            refreshNotification()
        }
    }

    // ---- Thông báo ---------------------------------------------------------------------------------

    /** Đẩy lại metadata + trạng thái + thông báo (video đổi, bấm Phát / Tạm dừng, thanh tua chạy). */
    fun refresh() {
        updateMetadata()
        updateState()
        refreshNotification()
    }

    private fun refreshNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }

    private fun shutdown() {
        ticker?.cancel()
        ticker = null
        artJob?.cancel()
        if (foregroundNow) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            foregroundNow = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
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
            foregroundNow = true
        } catch (_: Exception) {
            // Không lên được dịch vụ nền (hạn chế của hãng / app đã ẩn): vẫn phát, hệ thống có thể dừng sớm khi sang app khác.
            foregroundNow = false
        }
    }

    private fun action(name: String, request: Int, icon: Int, label: String): Notification.Action {
        val intent = PendingIntent.getService(
            this, request, Intent(this, ShortsPlaybackService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Short / video phát nền", NotificationManager.IMPORTANCE_LOW))
        }
        val tap = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val video = ShortsBackground.currentVideo
        val playing = ShortsBackground.isPlaying
        val watch = ShortsBackground.isWatchSession
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(video?.title ?: "NaNTube")
            .setContentText(video?.channel ?: if (watch) "Video" else "Short")
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(ACTION_PREV, 1, R.drawable.ic_pip_prev, if (watch) "Lùi 10 giây" else "Short trước"))
            .addAction(
                if (playing) action(ACTION_TOGGLE, 2, R.drawable.ic_pip_pause, "Tạm dừng")
                else action(ACTION_TOGGLE, 2, R.drawable.ic_pip_play, "Phát")
            )
            .addAction(action(ACTION_NEXT, 3, R.drawable.ic_pip_next, if (watch) "Tua 10 giây" else "Short tiếp"))
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

        private var instance: ShortsPlaybackService? = null

        /** Bật dịch vụ lúc app còn hiện (xem [ShortsBackground.keepAlive]). Gọi lại thì chỉ làm mới thông báo. */
        fun ensure(context: Context) {
            try {
                val intent = Intent(context, ShortsPlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) {
                // Không bật được: Short vẫn phát bình thường khi app hiện.
            }
        }

        fun stop(context: Context) {
            if (instance == null) return
            runCatching { context.stopService(Intent(context, ShortsPlaybackService::class.java)) }
        }

        fun refresh() {
            instance?.refresh()
        }
    }
}
