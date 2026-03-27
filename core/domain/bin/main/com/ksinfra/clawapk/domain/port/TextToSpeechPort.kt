package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.Language

interface TextToSpeechPort {
    suspend fun synthesize(text: String, language: Language): Result<AudioData>
}
