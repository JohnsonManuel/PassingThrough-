package com.passingthrough.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    const val CHANNEL_TRAIN_MODE = "train_mode"
    const val CHANNEL_TOWN_ALERTS = "town_alerts"
    const val ONGOING_ID = 1
    /** One shared ID so only the latest town stays in the notification shade. */
    const val TOWN_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRAIN_MODE, "Train mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while train mode is following your journey"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TOWN_ALERTS, "New town alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A notification for each new town you pass"
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun ongoing(context: Context, town: String?): Notification {
        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, TrainModeService::class.java).setAction(TrainModeService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_TRAIN_MODE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Train mode is on")
            .setContentText(town?.let { "Currently near $it" } ?: "Looking for your location…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateOngoing(context: Context, town: String?) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(ONGOING_ID, ongoing(context, town))
        } catch (e: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }

    @SuppressLint("MissingPermission")
    fun showTown(context: Context, town: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_TOWN_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Now passing $town")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TOWN_ID, notification)
        } catch (e: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }
}
