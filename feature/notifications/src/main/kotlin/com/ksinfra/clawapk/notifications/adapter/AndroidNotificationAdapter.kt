package com.ksinfra.clawapk.notifications.adapter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ksinfra.clawapk.domain.port.NotificationPort
import com.ksinfra.clawapk.notifications.R

class AndroidNotificationAdapter(
    private val context: Context
) : NotificationPort {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val appIcon = context.applicationInfo.icon
    private var notificationId = 1000

    init {
        createChannels()
    }

    override fun showMessageNotification(title: String, body: String) {
        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(appIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    override fun showCronNotification(jobName: String, message: String) {
        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_CRON)
            .setSmallIcon(appIcon)
            .setContentTitle(context.getString(R.string.notification_cron_prefix, jobName))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createChannels() {
        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val cronChannel = NotificationChannel(
            CHANNEL_CRON,
            context.getString(R.string.notification_channel_cron),
            NotificationManager.IMPORTANCE_HIGH
        )
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannels(listOf(messageChannel, cronChannel, serviceChannel))
    }

    companion object {
        const val CHANNEL_MESSAGES = "clawapk_messages"
        const val CHANNEL_CRON = "clawapk_cron"
        const val CHANNEL_SERVICE = "clawapk_service"
    }
}
