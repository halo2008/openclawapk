package com.ksinfra.clawapk.tts.adapter

import com.ksinfra.clawapk.domain.model.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class KokoroTtsAdapter(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String): Result<AudioData> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonBody = buildJsonObject {
                put("input", text)
                put("voice", "af_bella")
                put("response_format", "wav")
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/v1/audio/speech")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Kokoro TTS failed: ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw RuntimeException("Empty response from Kokoro")
            AudioData(bytes = bytes, mimeType = "audio/wav")
        }
    }
}
