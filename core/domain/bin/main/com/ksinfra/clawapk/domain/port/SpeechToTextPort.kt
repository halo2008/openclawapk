package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.RecognitionState
import kotlinx.coroutines.flow.StateFlow

interface SpeechToTextPort {
    val recognitionState: StateFlow<RecognitionState>

    fun startListening(languageCode: String = "pl-PL")
    fun stopListening()
}
