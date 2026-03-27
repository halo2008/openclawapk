package com.ksinfra.clawapk.websocket.di

import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.websocket.adapter.DeviceIdentity
import com.ksinfra.clawapk.websocket.adapter.OkHttpOpenClawGateway
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val webSocketModule = module {
    single { DeviceIdentity(androidContext()) }
    single<OpenClawGateway> { OkHttpOpenClawGateway(get(), get()) }
}
