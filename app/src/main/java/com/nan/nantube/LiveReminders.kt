package com.nan.nantube

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.imageLoader
import coil.request.ImageRequest
import com.nan.nantube.data.InnerTube
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.findKey
import com.nan.nantube.data.isWatchingText
import com.nan.nantube.data.ytText
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Nhắc buổi live sắp diễn ra do CHÍNH APP theo dõi (không nhờ YouTube báo): lưu video đã bấm "Thông báo cho tôi",
 * một WorkManager kiểm tra định kỳ (xa giờ thì thưa, gần giờ thì dày) và đăng thông báo khi buổi phát thật sự bắt đầu.
 * Bấm thông báo mở thẳng video trong app.
 */
object LiveReminders {
    private const val PREFS = "nantube_live_reminders"
    private const val KEY = "all"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString(KEY, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun save(context: Context, all: JSONObject) {
        prefs(context).edit().putString(KEY, all.toString()).apply()
    }

    fun isOn(context: Context, videoId: String): Boolean = load(context).has(videoId)

    internal fun get(context: Context, videoId: String): JSONObject? = load(context).optJSONObject(videoId)

    /** Bật nhắc cho [video]; [startMs] = giờ bắt đầu nếu biết (0 / null = chưa biết, worker tự dò). */
    fun add(context: Context, video: VideoItem, startMs: Long?) {
        val all = load(context)
        all.put(
            video.id,
            JSONObject().put("title", video.title).put("channel", video.channel.orEmpty())
                .put("start", startMs ?: 0L).put("created", System.currentTimeMillis())
        )
        save(context, all)
        LiveReminderWorker.enqueue(context, video.id, 0L)
    }

    fun remove(context: Context, videoId: String) {
        val all = load(context)
        if (all.has(videoId)) {
            all.remove(videoId)
            save(context, all)
        }
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(LiveReminderWorker.workName(videoId))
    }

    internal fun updateStart(context: Context, videoId: String, startMs: Long) {
        val all = load(context)
        all.optJSONObject(videoId)?.put("start", startMs)
        save(context, all)
    }
}

class LiveReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getString("id") ?: return Result.success()
        val reminder = LiveReminders.get(ctx, id) ?: return Result.success()
        val now = System.currentTimeMillis()
        var start = reminder.optLong("start")
        val created = reminder.optLong("created")
        // Quá hạn (qua giờ bắt đầu 6 tiếng, hoặc không biết giờ mà đã chờ cả ngày): thôi theo dõi.
        if (if (start > 0) now > start + 6 * HOUR else now > created + 24 * HOUR) {
            LiveReminders.remove(ctx, id)
            return Result.success()
        }
        var live: Boolean? = null
        try {
            val root = InnerTube.call("next", JSONObject().put("videoId", id), null)
            val counter = findKey(root, "videoViewCountRenderer")
            live = counter?.optBoolean("isLive") == true || isWatchingText(ytText(counter?.opt("viewCount")))
            if (start <= 0L) {
                findKey(root, "upcomingEventData")?.optString("startTime")?.toLongOrNull()?.takeIf { it > 0 }?.let {
                    start = it * 1000
                    LiveReminders.updateStart(ctx, id, start)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // mất mạng: lần sau thử lại
        }
        if (live == true) {
            post(ctx, id, reminder.optString("title"), reminder.optString("channel"))
            LiveReminders.remove(ctx, id)
            return Result.success()
        }
        val remaining = start - now
        val delay = when {
            start <= 0L -> 5 * MINUTE
            remaining > 20 * MINUTE -> minOf(15 * MINUTE, remaining - 10 * MINUTE)
            remaining > 0 -> maxOf(30_000L, remaining)
            else -> MINUTE // đã qua giờ mà chưa lên sóng: chủ kênh trễ, hỏi dày
        }
        enqueue(ctx, id, delay)
        return Result.success()
    }

    private suspend fun post(ctx: Context, videoId: String, title: String, channel: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nhắc buổi phát trực tiếp", NotificationManager.IMPORTANCE_HIGH))
        }
        val video = VideoItem(id = videoId, title = title, channel = channel.ifBlank { null }, duration = null, views = null, isShort = false, live = true)
        val notifId = ("live:" + videoId).hashCode()
        val tap = PendingIntent.getActivity(
            ctx, notifId, MainActivity.openVideoIntent(ctx, video), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (channel.isBlank()) "Đang phát trực tiếp" else "$channel đang phát trực tiếp")
            .setContentText(title)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        picture(ctx, "https://i.ytimg.com/vi/$videoId/mqdefault.jpg")?.let {
            builder.setLargeIcon(it)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(it).bigLargeIcon(null as Bitmap?))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private suspend fun picture(ctx: Context, url: String): Bitmap? = try {
        (ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build()).drawable as? BitmapDrawable)?.bitmap
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CHANNEL_ID = "live_reminders"
        private const val MINUTE = 60_000L
        private const val HOUR = 3_600_000L

        fun workName(videoId: String) = "live_reminder_$videoId"

        fun enqueue(context: Context, videoId: String, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<LiveReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to videoId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName(videoId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
