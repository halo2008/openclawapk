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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        val textToSpeak = if (!ttsMessage.isNullOrBlank()) {
            ttsMessage
        } else {
            TTS_FALLBACK[type] ?: TTS_FALLBACK["message"]
        }

        if (!textToSpeak.isNullOrBlank()) {
            scope.launch {
                speakTts(textToSpeak)
            }
        }
    }

    companion object {
        private const val TAG = "ClawFCM"

        private val TTS_FALLBACK = mapOf(
            "news" to "Nowe wiadomości.",
            "work" to "Nowe zadanie.",
            "alert" to "Nowe powiadomienie.",
            "message" to "Nowa wiadomość."
        )
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
            val baseUrl = config.serverUrl.trim()
                .let { it.replace(Regex("^\\w+://"), "") }
                .let { it.replace(Regex("/.*$"), "") }
            // Derive n8n URL from server URL (replace first subdomain with n8n)
            val parts = baseUrl.split(".")
            val n8nHost = if (parts.size >= 3) "n8n." + parts.drop(1).joinToString(".") else baseUrl
            val registerUrl = "https://$n8nHost/webhook/register-device"

            val deviceId = loadDeviceId()
            val json = """{"fcmToken":"$token","deviceId":"$deviceId","platform":"android"}"""
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(registerUrl)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "FCM token registered: ${response.code} ${response.body?.string()?.take(100)}")
        } catch (e: Exception) {
            Log.e(TAG, "Token registration failed: ${e.message}")
        }
    }

    private fun loadDeviceId(): String {
        val prefs = getSharedPreferences("openclaw_device", MODE_PRIVATE)
        return prefs.getString("device_id", "unknown") ?: "unknown"
    }

}
