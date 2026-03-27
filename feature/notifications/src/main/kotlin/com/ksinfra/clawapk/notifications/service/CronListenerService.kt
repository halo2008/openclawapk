package com.ksinfra.clawapk.notifications.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.SettingsPort
import com.ksinfra.clawapk.domain.usecase.HandleCronEventUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveCronEventsUseCase
import com.ksinfra.clawapk.notifications.R
import com.ksinfra.clawapk.notifications.adapter.AndroidNotificationAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CronListenerService : LifecycleService() {

    private val observeCronEvents: ObserveCronEventsUseCase by inject()
    private val handleCronEvent: HandleCronEventUseCase by inject()
    private val settingsPort: SettingsPort by inject()

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundNotification()
        observeCron()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AndroidNotificationAdapter.CHANNEL_SERVICE) == null) {
            val channel = NotificationChannel(
                AndroidNotificationAdapter.CHANNEL_SERVICE,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AndroidNotificationAdapter.CHANNEL_SERVICE)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun observeCron() {
        lifecycleScope.launch {
            observeCronEvents().collect { cronFired ->
                val language = settingsPort.getConnectionConfig().first()?.ttsLanguage ?: Language.POLISH
                handleCronEvent(cronFired.event, language)
            }
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }
}
