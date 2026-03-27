package com.ksinfra.clawapk.notifications.di

import com.ksinfra.clawapk.domain.port.NotificationPort
import com.ksinfra.clawapk.notifications.adapter.AndroidNotificationAdapter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val notificationsModule = module {
    single<NotificationPort> { AndroidNotificationAdapter(androidContext()) }
}
