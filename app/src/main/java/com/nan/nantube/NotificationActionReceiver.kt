package com.nan.nantube

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.NotificationItem
import com.nan.nantube.data.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hai nút của thông báo video mới ([NewUploadsWorker]):
 * - Tắt: tắt thông báo của kênh này (cùng cài đặt chuông của app) rồi gỡ thông báo;
 * - Xem sau: lưu video vào danh sách Xem sau của tài khoản rồi gỡ thông báo.
 * Nút Phát và bấm vào thông báo mở video trực tiếp nên không đi qua đây.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AccountStore.init(app)
                val account = AccountStore.accounts.firstOrNull { it.id == accountId }
                when (action) {
                    ACTION_MUTE -> {
                        ChannelNotify.set(account, channelId, channelName, false)
                        NotificationManagerCompat.from(app).cancel(notifId)
                        toast(app, "Đã tắt thông báo của kênh ${channelName ?: "này"}")
                    }
                    ACTION_LATER -> {
                        if (videoId == null) return@launch
                        try {
                            PlaylistRepository.addToWatchLater(account, videoId)
                            NotificationManagerCompat.from(app).cancel(notifId)
                            toast(app, "Đã lưu vào Xem sau")
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Giữ thông báo để người dùng thử lại.
                            toast(app, "Không lưu được vào Xem sau")
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_MUTE = "com.nan.nantube.action.NOTIF_MUTE"
        const val ACTION_LATER = "com.nan.nantube.action.NOTIF_LATER"
        private const val EXTRA_NOTIF_ID = "notifId"
        private const val EXTRA_VIDEO_ID = "videoId"
        private const val EXTRA_ACCOUNT_ID = "accountId"
        private const val EXTRA_CHANNEL_ID = "channelId"
        private const val EXTRA_CHANNEL_NAME = "channelName"

        fun intent(context: Context, action: String, accountId: String, n: NotificationItem, notifId: Int): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                // Data riêng cho từng video + nút để PendingIntent của các thông báo không bị trộn extras với nhau.
                .setData(Uri.parse("nantube://notification/$action/${n.videoId}"))
                .putExtra(EXTRA_NOTIF_ID, notifId)
                .putExtra(EXTRA_VIDEO_ID, n.videoId)
                .putExtra(EXTRA_ACCOUNT_ID, accountId)
                .putExtra(EXTRA_CHANNEL_ID, n.channelId)
                .putExtra(EXTRA_CHANNEL_NAME, n.channel ?: n.title)
    }
}
