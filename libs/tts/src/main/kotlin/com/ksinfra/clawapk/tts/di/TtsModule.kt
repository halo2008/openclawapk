package com.ksinfra.clawapk.tts.di

import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.tts.adapter.AndroidTtsAdapter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val ttsModule = module {
    single<TextToSpeechPort> { AndroidTtsAdapter(androidContext()) }
}
