package com.ksinfra.clawapk.domain.usecase

import com.ksinfra.clawapk.domain.port.OpenClawGateway

class SendMessageUseCase(
    private val gateway: OpenClawGateway
) {
    suspend operator fun invoke(message: String): Result<String> {
        return gateway.sendMessage(message)
    }
}
