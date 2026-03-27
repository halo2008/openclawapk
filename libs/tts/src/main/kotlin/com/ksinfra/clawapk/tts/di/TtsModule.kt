package com.ksinfra.clawapk.tts.di

import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.tts.adapter.CompositeTtsAdapter
import com.ksinfra.clawapk.tts.adapter.KokoroTtsAdapter
import com.ksinfra.clawapk.tts.adapter.PiperTtsAdapter
import org.koin.dsl.module

val ttsModule = module {
    single { PiperTtsAdapter(baseUrl = "https://piper.example.com") }
    single { KokoroTtsAdapter(baseUrl = "https://kokoro.example.com") }
    single<TextToSpeechPort> { CompositeTtsAdapter(get(), get()) }
}
