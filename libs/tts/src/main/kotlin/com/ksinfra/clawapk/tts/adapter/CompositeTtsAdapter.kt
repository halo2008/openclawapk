package com.ksinfra.clawapk.tts.adapter

import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.TextToSpeechPort

class CompositeTtsAdapter(
    private val piperAdapter: PiperTtsAdapter,
    private val kokoroAdapter: KokoroTtsAdapter
) : TextToSpeechPort {

    override suspend fun synthesize(text: String, language: Language): Result<AudioData> {
        return when (language) {
            Language.POLISH -> piperAdapter.synthesize(text)
            Language.ENGLISH -> kokoroAdapter.synthesize(text)
        }
    }
}
