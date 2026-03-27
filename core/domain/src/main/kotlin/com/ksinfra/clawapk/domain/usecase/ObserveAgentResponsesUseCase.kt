package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

class ObserveAgentResponsesUseCase(
    private val gateway: OpenClawGateway
) {
    operator fun invoke(): Flow<OpenClawEvent.AgentResponse> {
        return gateway.events.filterIsInstance()
    }
}
