package com.nan.nantube

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.NotificationRepository
import com.nan.nantube.data.toVideo
import java.util.concurrent.TimeUnit

/**
 * Thông báo video mới tự làm, theo NewUploadsWorker của meetube: không phải push của YouTube.
 * Mỗi giờ (có mạng, pin không yếu) đọc feed kênh đăng ký của từng tài khoản, video nào chưa thấy và kênh đang bật chuông thì đăng
 * một thông báo; bấm vào mở video. Lần chạy đầu của mỗi tài khoản chỉ ghi nhớ, không đăng, để khỏi dội cả đống video cũ.
 */
class NewUploadsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        AccountStore.init(ctx)
        val prefs = AccountStore.libraryPrefs() ?: return Result.success()
        for (account in AccountStore.accounts.toList()) {
            val items = try {
                NotificationRepository.uploadsFeed(account)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                continue // mất mạng / token hỏng: lần kiểm tra sau thử lại
            }
            val seenKey = "push_seen:${account.id}"
            val stored = prefs.getString(seenKey, null)
            val seen = LinkedHashSet<String>(stored?.split(",")?.filter { it.isNotEmpty() }.orEmpty())
            val fresh = ArrayList<com.nan.nantube.data.NotificationItem>()
            for (n in items) {
                val id = n.videoId ?: continue
                if (!seen.add(id)) continue
                if (stored != null && ChannelNotify.isOn(account, n.channelId, n.channel)) fresh.add(n)
            }
            // Máy tắt cả ngày: chỉ đăng vài video mới nhất, phần còn lại coi như đã thấy.
            fresh.take(MAX_POSTS).forEach { post(ctx, account, it) }
            prefs.edit().putString(seenKey, seen.toList().takeLast(MAX_SEEN).joinToString(",")).apply()
        }
        return Result.success()
    }

    /**
     * Một thông báo lẻ cho mỗi video, dáng như thông báo của YouTube: tên kênh ở dòng đậm, tên video bên dưới, avatar kênh, ảnh bìa lớn
     * và 3 nút Phát (mở video) / Tắt (tắt thông báo kênh này) / Xem sau (lưu vào Xem sau). Bấm vào thông báo hay nút Phát đều mở video ngay trong app.
     * Mỗi thông báo có nhóm riêng nên hệ thống không gộp chúng thành một cụm.
     */
    private suspend fun post(ctx: Context, account: com.nan.nantube.data.Account, n: com.nan.nantube.data.NotificationItem) {
        val videoId = n.videoId ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Video mới", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notifId = videoId.hashCode()
        val open = n.toVideo()?.let { MainActivity.openVideoIntent(ctx, it) }
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(n.url ?: "https://www.youtube.com/watch?v=$videoId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = PendingIntent.getActivity(ctx, notifId, open, flags)
        val play = PendingIntent.getActivity(ctx, notifId * 3 + 1, Intent(open), flags)
        val mute = PendingIntent.getBroadcast(
            ctx, notifId * 3 + 2,
            NotificationActionReceiver.intent(ctx, NotificationActionReceiver.ACTION_MUTE, account.id, n, notifId), flags
        )
        val later = PendingIntent.getBroadcast(
            ctx, notifId * 3 + 3,
            NotificationActionReceiver.intent(ctx, NotificationActionReceiver.ACTION_LATER, account.id, n, notifId), flags
        )
        val channelName = n.channel ?: n.title
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(channelName)
            .setContentText(n.body.removePrefix("Video tải lên: "))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup("nantube_upload_$videoId")
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .addAction(0, "Phát", play)
            .addAction(0, "Tắt", mute)
            .addAction(0, "Xem sau", later)
        val picture = thumbnail(ctx, n.thumbnail ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg", false)
        val avatar = n.avatar?.let { thumbnail(ctx, it, true) }
        (avatar ?: picture)?.let { builder.setLargeIcon(it) }
        if (picture != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private suspend fun thumbnail(ctx: Context, url: String, circle: Boolean): Bitmap? = try {
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false)
            .apply { if (circle) transformations(coil.transform.CircleCropTransformation()) }
            .build()
        (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CHANNEL_ID = "new_uploads"
        private const val WORK_NAME = "new_uploads"
        private const val MAX_POSTS = 5
        private const val MAX_SEEN = 400

        /** Gọi mỗi lần mở app; KEEP nên không đặt lại bộ đếm giờ của lịch đang chạy. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewUploadsWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
