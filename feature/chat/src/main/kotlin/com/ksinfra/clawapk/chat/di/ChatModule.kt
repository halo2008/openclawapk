package com.ksinfra.clawapk.chat.di

import com.ksinfra.clawapk.chat.viewmodel.ChatViewModel
import com.ksinfra.clawapk.chat.viewmodel.SettingsViewModel
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.usecase.ConnectToOpenClawUseCase
import com.ksinfra.clawapk.domain.usecase.DisconnectFromOpenClawUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveAgentResponsesUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveConnectionStateUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveCronEventsUseCase
import com.ksinfra.clawapk.domain.usecase.HandleCronEventUseCase
import com.ksinfra.clawapk.domain.usecase.SendMessageUseCase
import com.ksinfra.clawapk.domain.usecase.SpeakResponseUseCase
import com.ksinfra.clawapk.domain.usecase.StartVoiceInputUseCase
import com.ksinfra.clawapk.domain.usecase.StopVoiceInputUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    // Use cases
    factory { ConnectToOpenClawUseCase(get()) }
    factory { DisconnectFromOpenClawUseCase(get()) }
    factory { ObserveConnectionStateUseCase(get()) }
    factory { SendMessageUseCase(get()) }
    factory { ObserveAgentResponsesUseCase(get()) }
    factory { SpeakResponseUseCase(get(), get()) }
    factory { StartVoiceInputUseCase(get()) }
    factory { StopVoiceInputUseCase(get()) }
    factory { ObserveCronEventsUseCase(get()) }
    factory { HandleCronEventUseCase(get(), get(), get(), get()) }

    // ViewModels
    viewModel {
        ChatViewModel(
            sendMessage = get(),
            observeAgentResponses = get(),
            observeConnectionState = get(),
            speakResponse = get(),
            startVoiceInput = get(),
            stopVoiceInput = get(),
            connectToOpenClaw = get(),
            settingsPort = get(),
            stt = get(),
            ttsLanguage = Language.POLISH
        )
    }

    viewModel {
        SettingsViewModel(
            settingsPort = get(),
            connectToOpenClaw = get(),
            gateway = get()
        )
    }
}
