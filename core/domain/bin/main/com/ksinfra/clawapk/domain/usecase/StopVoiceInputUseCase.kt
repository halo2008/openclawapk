package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.port.SpeechToTextPort

class StopVoiceInputUseCase(
    private val stt: SpeechToTextPort
) {
    operator fun invoke() {
        stt.stopListening()
    }
}
