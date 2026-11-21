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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Giống MobileSignInKeepaliveService của meetube: khi trang duyệt mã (Custom Tab) phủ lên app,
 * Android đóng băng mạng của tiến trình nên vòng poll token bị đứng. Foreground service ngắn hạn
 * giữ mạng sống để poll thành công ngay lúc người dùng bấm xác nhận.
 */
class SignInKeepaliveService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Đăng nhập", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val tap = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Đang chờ xác nhận đăng nhập")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** Hết hạn mức shortService (~3 phút, Android 14+): dừng, đăng nhập sẽ hoàn tất khi người dùng quay lại. */
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nantube_signin"
        private const val NOTIFICATION_ID = 7802

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SignInKeepaliveService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SignInKeepaliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
