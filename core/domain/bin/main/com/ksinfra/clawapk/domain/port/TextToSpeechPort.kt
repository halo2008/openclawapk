package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.TtsVoiceInfo

interface TextToSpeechPort {
    suspend fun synthesize(text: String, language: Language): Result<AudioData>
    fun getAvailableVoices(): List<TtsVoiceInfo> = emptyList()
    fun setVoice(voiceName: String) {}
}
