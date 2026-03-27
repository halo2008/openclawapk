package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.CronAction
import com.ksinfra.clawapk.domain.model.CronEvent
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.NotificationPort
import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.domain.port.AudioPlayerPort
import com.ksinfra.clawapk.domain.port.VibrationPort

class HandleCronEventUseCase(
    private val notificationPort: NotificationPort,
    private val vibrationPort: VibrationPort,
    private val tts: TextToSpeechPort,
    private val audioPlayer: AudioPlayerPort
) {
    suspend operator fun invoke(event: CronEvent, ttsLanguage: Language = Language.POLISH) {
        for (action in event.actions) {
            when (action) {
                CronAction.NOTIFY -> {
                    notificationPort.showCronNotification(event.jobName, event.message ?: "")
                }
                CronAction.SPEAK -> {
                    val textToSpeak = event.message ?: event.jobName
                    tts.synthesize(textToSpeak, ttsLanguage)
                        .onSuccess { audioData -> audioPlayer.play(audioData) }
                }
                CronAction.PLAY_SOUND -> {
                    notificationPort.showCronNotification(event.jobName, event.message ?: "")
                }
                CronAction.VIBRATE -> {
                    vibrationPort.vibrateLong()
                }
            }
        }
    }
}
