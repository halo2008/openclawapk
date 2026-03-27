package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

class ObserveCronEventsUseCase(
    private val gateway: OpenClawGateway
) {
    operator fun invoke(): Flow<OpenClawEvent.CronFired> {
        return gateway.events.filterIsInstance()
    }
}
