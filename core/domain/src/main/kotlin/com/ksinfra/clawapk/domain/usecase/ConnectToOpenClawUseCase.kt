package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.port.OpenClawGateway

class ConnectToOpenClawUseCase(
    private val gateway: OpenClawGateway
) {
    suspend operator fun invoke(config: ConnectionConfig) {
        gateway.connect(config)
    }
}
