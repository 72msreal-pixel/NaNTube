package com.nan.nantube

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nan.nantube.ui.MusicPlayer

/**
 * Dịch vụ phát nhạc nền: giữ trình phát [MusicPlayer] sống khi thoát app / tắt màn hình và dựng MediaSession,
 * nên thông báo phát nhạc (ảnh bìa, tên bài, thanh tua, trước / phát-tạm dừng / sau), bảng điều khiển nhanh, màn khóa,
 * tai nghe và Bluetooth đều điều khiển được bài đang nghe.
 * Trình phát thuộc về [MusicPlayer] (không bị dịch vụ giải phóng), dịch vụ chỉ cho nó một "vỏ" phiên.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = MusicPlayer.sessionPlayer()
        if (player == null) {
            // Không còn trình phát (vd. hệ thống dựng lại dịch vụ sau khi app bị dọn): không có gì để phát.
            stopSelf()
            return
        }
        running = true
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.music_notification_channel)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_notification) }
        )
        // Chạm vào thông báo / thẻ phát nhạc thì mở lại app.
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        addSession(session!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Vuốt app khỏi đa nhiệm: đang phát thì cứ phát tiếp ở nền; đang tạm dừng thì dừng dịch vụ cho nhẹ máy. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        running = false
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"

        /** Dịch vụ đang chạy (để [MusicPlayer] biết có cần khởi động lại khi phát bài mới). */
        @Volatile
        var running = false
            private set
    }
}
