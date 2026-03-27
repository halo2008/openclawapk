package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import kotlinx.coroutines.flow.StateFlow

class ObserveConnectionStateUseCase(
    private val gateway: OpenClawGateway
) {
    operator fun invoke(): StateFlow<ConnectionState> = gateway.connectionState
}
