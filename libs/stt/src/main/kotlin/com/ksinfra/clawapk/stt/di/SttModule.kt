package com.ksinfra.clawapk.stt.di

import com.ksinfra.clawapk.domain.port.SpeechToTextPort
import com.ksinfra.clawapk.stt.adapter.AndroidSttAdapter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sttModule = module {
    single<SpeechToTextPort> { AndroidSttAdapter(androidContext()) }
}
