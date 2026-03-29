package com.ksinfra.clawapk.tts.adapter

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.TtsVoiceInfo
import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class AndroidTtsAdapter(context: Context) : TextToSpeechPort {

    private val utteranceCounter = AtomicInteger(0)
    private var ttsReady = false
    private var selectedVoiceName: String? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }

    override fun getAvailableVoices(): List<TtsVoiceInfo> {
        if (!ttsReady) return emptyList()
        val supportedLocales = setOf(
            Locale("pl", "PL"),
            Locale("en", "US"),
            Locale("en", "GB")
        )
        return tts.voices
            ?.filter { voice ->
                !voice.isNetworkConnectionRequired &&
                    supportedLocales.any { it.language == voice.locale.language && it.country == voice.locale.country }
            }
            ?.map { voice ->
                val lang = if (voice.locale.language == "pl") Language.POLISH else Language.ENGLISH
                val country = voice.locale.displayCountry
                TtsVoiceInfo(
                    name = voice.name,
                    displayName = "${voice.locale.displayLanguage} ($country) — ${voice.name.substringAfterLast("-", voice.name)}",
                    language = lang
                )
            }
            ?.sortedWith(compareBy({ it.language.name }, { it.name }))
            ?: emptyList()
    }

    override fun setVoice(voiceName: String) {
        selectedVoiceName = voiceName.ifBlank { null }
    }

    override suspend fun synthesize(text: String, language: Language): Result<AudioData> {
        if (!ttsReady) {
            return Result.failure(IllegalStateException("Android TTS not initialized"))
        }

        val locale = when (language) {
            Language.POLISH -> Locale("pl", "PL")
            Language.ENGLISH -> Locale.US
        }

        // Apply selected voice or fall back to locale default
        val voice = selectedVoiceName?.let { name ->
            tts.voices?.find { it.name == name }
        }
        if (voice != null) {
            tts.voice = voice
        } else {
            tts.language = locale
        }

        return runCatching {
            suspendCancellableCoroutine { cont ->
                val utteranceId = "tts_${utteranceCounter.incrementAndGet()}"

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utteranceId) {
                            cont.resume(AudioData(byteArrayOf(), ""))
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(id: String?) {
                        if (id == utteranceId) {
                            cont.resume(AudioData(byteArrayOf(), ""))
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) {
                            cont.resume(AudioData(byteArrayOf(), ""))
                        }
                    }
                })

                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    cont.resume(AudioData(byteArrayOf(), ""))
                }

                cont.invokeOnCancellation {
                    tts.stop()
                }
            }
        }
    }
}
