package com.nan.nantube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nan.nantube.data.Account
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gate = Mutex()
    private var pending = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        val title = intent?.getStringExtra(EXTRA_TITLE)
        startAsForeground(progressNotification(title ?: "video", -1))
        if (id == null || title == null) {
            if (pending == 0) stopSelf()
            return START_NOT_STICKY
        }
        val accountId = intent?.getStringExtra(EXTRA_ACCOUNT)
        val account = AccountStore.accounts.firstOrNull { it.id == accountId }
        val video = VideoItem(id, title, null, null, null, false)
        pending++
        scope.launch {
            gate.withLock { process(account, video) }
            pending--
            if (pending == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun process(account: Account?, video: VideoItem) {
        val result = try {
            VideoDownloads.download(this, account, video) { percent ->
                notify(FOREGROUND_ID, progressNotification(video.title, percent))
            }
            "Đã tải xong: ${video.title}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Không tải được ${video.title}: ${e.message ?: "lỗi không rõ"}"
        }
        notify(video.id.hashCode(), resultNotification(result))
        Toast.makeText(applicationContext, result, Toast.LENGTH_LONG).show()
    }

    private fun startAsForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: Exception) {
        }
    }

    private fun builder(): NotificationCompat.Builder {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Tải xuống", NotificationManager.IMPORTANCE_LOW))
        }
        val tap = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentIntent(open).setSilent(true)
    }

    private fun progressNotification(title: String, percent: Int): Notification =
        builder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (percent < 0) "Đang chuẩn bị tải" else "Đang tải $percent%")
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .build()

    private fun resultNotification(text: String): Notification =
        builder()
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(text)
            .setAutoCancel(true)
            .build()

    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nantube_download"
        private const val FOREGROUND_ID = 7803
        private const val EXTRA_ID = "video_id"
        private const val EXTRA_TITLE = "video_title"
        private const val EXTRA_ACCOUNT = "account_id"

        fun start(context: Context, accountId: String?, video: VideoItem) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_ID, video.id)
                .putExtra(EXTRA_TITLE, video.title)
                .putExtra(EXTRA_ACCOUNT, accountId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
