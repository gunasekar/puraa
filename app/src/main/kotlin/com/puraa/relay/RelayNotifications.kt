package com.puraa.relay

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

/**
 * The relay has no long-running foreground service. The only time it
 * needs a notification is the brief window while [RelayWorker] posts a
 * batch to Telegram: on Android < 12, WorkManager runs expedited work as
 * a short-lived foreground service and asks the worker for a
 * [ForegroundInfo]. On Android 12+ expedited work runs as a job and this
 * notification is never shown.
 */
object RelayNotifications {

    private const val CHANNEL_ID = "puraa_relay"
    private const val FOREGROUND_NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName("Puraa relay")
            .setDescription("Shown briefly while forwarding an SMS")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun foregroundInfo(context: Context): ForegroundInfo {
        ensureChannel(context)
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Puraa")
            .setContentText("Forwarding SMS…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}
