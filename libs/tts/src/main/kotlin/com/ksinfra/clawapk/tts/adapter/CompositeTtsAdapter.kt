package com.ksinfra.clawapk.tts.adapter

import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.domain.port.TextToSpeechPort

class CompositeTtsAdapter(
    private val gateway: OpenClawGateway
) : TextToSpeechPort {

    override suspend fun synthesize(text: String, language: Language): Result<AudioData> {
        return gateway.ttsConvert(text)
    }
}
