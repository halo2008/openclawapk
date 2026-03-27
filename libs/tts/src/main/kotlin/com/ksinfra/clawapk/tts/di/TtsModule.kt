package com.ksinfra.clawapk.tts.di

import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.tts.adapter.CompositeTtsAdapter
import org.koin.dsl.module

val ttsModule = module {
    single<TextToSpeechPort> { CompositeTtsAdapter(get()) }
}
