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
import com.nan.nantube.data.AudioFormat
import com.nan.nantube.data.DownloadRequest
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem
import org.json.JSONArray
import org.json.JSONObject
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
    private var batchOk = 0
    private var batchFail = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val batchJson = intent?.getStringExtra(EXTRA_BATCH)
        if (batchJson != null) return startBatch(intent, batchJson)
        val id = intent?.getStringExtra(EXTRA_ID)
        val title = intent?.getStringExtra(EXTRA_TITLE)
        startAsForeground(progressNotification(title ?: "video", -1))
        if (id == null || title == null) {
            if (pending == 0) stopSelf()
            return START_NOT_STICKY
        }
        val accountId = intent?.getStringExtra(EXTRA_ACCOUNT)
        val account = AccountStore.accounts.firstOrNull { it.id == accountId }
        val video = VideoItem(
            id, title, intent?.getStringExtra(EXTRA_CHANNEL), intent?.getStringExtra(EXTRA_DURATION),
            null, intent?.getBooleanExtra(EXTRA_SHORT, false) == true
        )
        val request = readRequest(intent)
        pending++
        scope.launch {
            gate.withLock { process(account, video, request) }
            finishOne()
        }
        return START_NOT_STICKY
    }

    private fun readRequest(intent: Intent?) = DownloadRequest(
        audioOnly = intent?.getBooleanExtra(EXTRA_AUDIO_ONLY, false) == true,
        height = intent?.getIntExtra(EXTRA_HEIGHT, 0)?.takeIf { it > 0 },
        audioFormat = AudioFormat.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_AUDIO_FORMAT) } ?: AudioFormat.M4A,
        clipStartMs = intent?.getLongExtra(EXTRA_CLIP_START, 0) ?: 0,
        clipEndMs = intent?.getLongExtra(EXTRA_CLIP_END, 0) ?: 0
    )

    private fun finishOne() {
        pending--
        if (pending == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Tải cả danh sách phát: các video xếp hàng tải lần lượt, thông báo hiện "(3/120) tên video" rồi một thông báo tổng kết. */
    private fun startBatch(intent: Intent?, json: String): Int {
        val accountId = intent?.getStringExtra(EXTRA_ACCOUNT)
        val account = AccountStore.accounts.firstOrNull { it.id == accountId }
        val request = readRequest(intent)
        val videos = try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VideoItem(
                    id, o.optString("title").ifBlank { id }, o.optString("channel").takeIf { it.isNotBlank() },
                    o.optString("duration").takeIf { it.isNotBlank() }, null, o.optBoolean("short")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        startAsForeground(progressNotification("danh sách phát", -1))
        if (videos.isEmpty()) {
            if (pending == 0) stopSelf()
            return START_NOT_STICKY
        }
        batchOk = 0
        batchFail = 0
        pending += videos.size
        videos.forEachIndexed { i, video ->
            scope.launch {
                gate.withLock { process(account, video, request, i + 1, videos.size) }
                finishOne()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun process(account: Account?, video: VideoItem, request: DownloadRequest, batchIndex: Int = 0, batchTotal: Int = 0) {
        val batch = batchTotal > 0
        val label = if (batch) "($batchIndex/$batchTotal) ${video.title}" else video.title
        if (batch) notify(FOREGROUND_ID, progressNotification(label, -1))
        var ok = true
        val result = try {
            VideoDownloads.download(this, account, video, request) { percent ->
                notify(FOREGROUND_ID, progressNotification(label, percent))
            }
            "Đã tải xong: ${video.title}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ok = false
            "Không tải được ${video.title}: ${e.message ?: "lỗi không rõ"}"
        }
        if (batch) {
            // Tải hàng loạt: không báo từng video (tránh hàng trăm thông báo), chỉ tổng kết ở video cuối.
            if (ok) batchOk++ else batchFail++
            if (batchIndex == batchTotal) {
                val summary = "Đã tải xong $batchOk video" + if (batchFail > 0) ", $batchFail video lỗi" else ""
                notify(BATCH_RESULT_ID, resultNotification(summary))
                Toast.makeText(applicationContext, summary, Toast.LENGTH_LONG).show()
            }
            return
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (percent < 0) "Đang chuẩn bị tải" else "Đang tải $percent%")
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .build()

    private fun resultNotification(text: String): Notification =
        builder()
            .setSmallIcon(R.drawable.ic_notification)
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
        private const val BATCH_RESULT_ID = 7804
        private const val EXTRA_BATCH = "batch_videos"
        private const val EXTRA_ID = "video_id"
        private const val EXTRA_TITLE = "video_title"
        private const val EXTRA_CHANNEL = "video_channel"
        private const val EXTRA_DURATION = "video_duration"
        private const val EXTRA_SHORT = "video_short"
        private const val EXTRA_ACCOUNT = "account_id"
        private const val EXTRA_AUDIO_ONLY = "audio_only"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_AUDIO_FORMAT = "audio_format"
        private const val EXTRA_CLIP_START = "clip_start"
        private const val EXTRA_CLIP_END = "clip_end"

        /** Tải nhiều video một lượt (cả danh sách phát) bằng một yêu cầu duy nhất, theo thứ tự trong [videos]. */
        fun startBatch(context: Context, accountId: String?, videos: List<VideoItem>, request: DownloadRequest) {
            val array = JSONArray()
            videos.forEach {
                array.put(
                    JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel)
                        .put("duration", it.duration).put("short", it.isShort)
                )
            }
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_BATCH, array.toString())
                .putExtra(EXTRA_ACCOUNT, accountId)
                .putExtra(EXTRA_AUDIO_ONLY, request.audioOnly)
                .putExtra(EXTRA_HEIGHT, request.height ?: 0)
                .putExtra(EXTRA_AUDIO_FORMAT, request.audioFormat.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun start(context: Context, accountId: String?, video: VideoItem, request: DownloadRequest = DownloadRequest()) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_ID, video.id)
                .putExtra(EXTRA_TITLE, video.title)
                .putExtra(EXTRA_CHANNEL, video.channel)
                .putExtra(EXTRA_DURATION, video.duration)
                .putExtra(EXTRA_SHORT, video.isShort)
                .putExtra(EXTRA_ACCOUNT, accountId)
                .putExtra(EXTRA_AUDIO_ONLY, request.audioOnly)
                .putExtra(EXTRA_HEIGHT, request.height ?: 0)
                .putExtra(EXTRA_AUDIO_FORMAT, request.audioFormat.name)
                .putExtra(EXTRA_CLIP_START, request.clipStartMs)
                .putExtra(EXTRA_CLIP_END, request.clipEndMs)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
