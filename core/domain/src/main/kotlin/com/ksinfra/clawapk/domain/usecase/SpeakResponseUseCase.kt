package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.AudioPlayerPort
import com.ksinfra.clawapk.domain.port.TextToSpeechPort

class SpeakResponseUseCase(
    private val tts: TextToSpeechPort,
    private val audioPlayer: AudioPlayerPort
) {
    suspend operator fun invoke(text: String, language: Language): Result<Unit> {
        return tts.synthesize(text, language).mapCatching { audioData ->
            if (audioData.bytes.isNotEmpty()) {
                audioPlayer.play(audioData)
            }
        }
    }
}
