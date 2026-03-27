package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.SpeechToTextPort

class StartVoiceInputUseCase(
    private val stt: SpeechToTextPort
) {
    operator fun invoke(language: Language = Language.POLISH) {
        val languageCode = when (language) {
            Language.POLISH -> "pl-PL"
            Language.ENGLISH -> "en-US"
        }
        stt.startListening(languageCode)
    }
}
