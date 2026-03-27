package com.ksinfra.clawapk.websocket.di

import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.websocket.adapter.OkHttpOpenClawGateway
import org.koin.dsl.module

val webSocketModule = module {
    single<OpenClawGateway> { OkHttpOpenClawGateway(get()) }
}
