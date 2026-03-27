package com.ksinfra.clawapk.app.di

import com.ksinfra.clawapk.common.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val appModule = module {
    single {
        CoroutineDispatchers(
            main = Dispatchers.Main,
            io = Dispatchers.IO,
            default = Dispatchers.Default
        )
    }
}
