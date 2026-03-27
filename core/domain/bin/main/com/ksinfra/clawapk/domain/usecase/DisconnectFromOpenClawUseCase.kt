package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.port.OpenClawGateway

class DisconnectFromOpenClawUseCase(
    private val gateway: OpenClawGateway
) {
    suspend operator fun invoke() {
        gateway.disconnect()
    }
}
