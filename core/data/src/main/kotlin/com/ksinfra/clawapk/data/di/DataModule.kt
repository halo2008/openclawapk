package com.ksinfra.clawapk.data.di

import com.ksinfra.clawapk.data.adapter.AndroidVibrationAdapter
import com.ksinfra.clawapk.data.adapter.DataStoreSettingsAdapter
import com.ksinfra.clawapk.data.adapter.ExoPlayerAdapter
import com.ksinfra.clawapk.domain.port.AudioPlayerPort
import com.ksinfra.clawapk.domain.port.SettingsPort
import com.ksinfra.clawapk.domain.port.VibrationPort
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

@androidx.media3.common.util.UnstableApi
val dataModule = module {
    single<AudioPlayerPort> { ExoPlayerAdapter(androidContext()) }
    single<VibrationPort> { AndroidVibrationAdapter(androidContext()) }
    single<SettingsPort> { DataStoreSettingsAdapter(androidContext()) }
}
