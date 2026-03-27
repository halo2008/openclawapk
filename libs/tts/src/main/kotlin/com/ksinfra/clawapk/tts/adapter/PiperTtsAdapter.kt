package com.ksinfra.clawapk.tts.adapter

import com.ksinfra.clawapk.domain.model.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class PiperTtsAdapter(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String): Result<AudioData> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = text.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/tts?text=${java.net.URLEncoder.encode(text, "UTF-8")}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Piper TTS failed: ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw RuntimeException("Empty response from Piper")
            AudioData(bytes = bytes, mimeType = "audio/wav")
        }
    }
}
