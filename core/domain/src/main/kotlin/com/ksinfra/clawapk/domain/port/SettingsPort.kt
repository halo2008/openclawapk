package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.ConnectionConfig
import kotlinx.coroutines.flow.Flow

interface SettingsPort {
    fun getConnectionConfig(): Flow<ConnectionConfig?>
    suspend fun saveConnectionConfig(config: ConnectionConfig)
    suspend fun clearConfig()
}
