package dev.notiq.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.notiq.MainActivity
import dev.notiq.R
import dev.notiq.data.NotificationRecord

class NotificationRelay(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.relay_channel), NotificationManager.IMPORTANCE_HIGH))
    }

    fun available(): Boolean = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(record: NotificationRecord, source: Notification?): Boolean {
        if (!available()) return false
        val open = source?.contentIntent ?: PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notiq)
            .setContentTitle(record.title.ifBlank { record.appName })
            .setContentText(record.body)
            .setStyle(Notification.BigTextStyle().bigText(record.body))
            .setSubText(record.appName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        return try {
            manager.notify(record.sourceKey.ifBlank { record.id }, 1, notification)
            true
        } catch (_: SecurityException) { false }
    }

    companion object { const val CHANNEL = "filtered_notifications" }
}
