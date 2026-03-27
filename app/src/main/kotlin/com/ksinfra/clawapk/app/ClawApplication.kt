package com.ksinfra.clawapk.app

import android.app.Application
import com.ksinfra.clawapk.app.di.appModule
import com.ksinfra.clawapk.chat.di.chatModule
import com.ksinfra.clawapk.data.di.dataModule
import com.ksinfra.clawapk.notifications.di.notificationsModule
import com.ksinfra.clawapk.stt.di.sttModule
import com.ksinfra.clawapk.tts.di.ttsModule
import com.ksinfra.clawapk.websocket.di.webSocketModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ClawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ClawApplication)
            modules(
                appModule,
                dataModule,
                webSocketModule,
                ttsModule,
                sttModule,
                chatModule,
                notificationsModule
            )
        }
    }
}
