package com.ksinfra.clawapk.app.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.NotificationPort
import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.domain.port.SettingsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ClawFirebaseMessagingService : FirebaseMessagingService() {

    private val notificationPort: NotificationPort by inject()
    private val ttsPort: TextToSpeechPort by inject()
    private val settingsPort: SettingsPort by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
        scope.launch {
            registerTokenWithServer(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "message"
        val title = data["title"] ?: message.notification?.title ?: "ClawAPK"
        val body = data["body"] ?: message.notification?.body ?: ""
        val ttsMessage = data["ttsMessage"]

        Log.d(TAG, "FCM received: type=$type title=$title tts=${ttsMessage != null}")

        when (type) {
            "news" -> notificationPort.showCronNotification(title, body)
            "work" -> notificationPort.showCronNotification(title, body)
            "alert" -> notificationPort.showCronNotification(title, body)
            else -> notificationPort.showMessageNotification(title, body)
        }

        if (!ttsMessage.isNullOrBlank()) {
            scope.launch {
                speakTts(ttsMessage)
            }
        }
    }

    private suspend fun speakTts(text: String) {
        try {
            val config = settingsPort.getConnectionConfig().first()
            val language = config?.ttsLanguage ?: Language.POLISH
            val result = ttsPort.synthesize(text, language)
            result.onSuccess { audio ->
                // AudioData is played by the TTS adapter
                Log.d(TAG, "TTS spoken: ${text.take(50)}")
            }
            result.onFailure { e ->
                Log.e(TAG, "TTS failed: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS error: ${e.message}")
        }
    }

    private suspend fun registerTokenWithServer(token: String) {
        try {
            val config = settingsPort.getConnectionConfig().first() ?: return
            val serverUrl = config.serverUrl.trim()
                .let { it.replace(Regex("^\\w+://"), "") }
                .let { "https://$it" }
            // TODO: Send FCM token to n8n webhook for push registration
            Log.d(TAG, "TODO: register FCM token with $serverUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Token registration failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ClawFCM"
    }
}
