package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.model.Session
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface OpenClawGateway {
    val connectionState: StateFlow<ConnectionState>
    val events: SharedFlow<OpenClawEvent>

    suspend fun connect(config: ConnectionConfig)
    suspend fun disconnect()
    suspend fun sendMessage(message: String): Result<String>
    suspend fun listSessions(): Result<List<Session>>
    suspend fun ttsConvert(text: String): Result<AudioData>
}
