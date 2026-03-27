package com.ksinfra.clawapk.domain.model

sealed class OpenClawEvent {
    data class AgentResponse(val text: String, val sessionId: String?) : OpenClawEvent()
    data class CronFired(val event: CronEvent) : OpenClawEvent()
    data class SessionChanged(val sessionId: String) : OpenClawEvent()
    data class Tick(val timestamp: Long) : OpenClawEvent()
    data object Shutdown : OpenClawEvent()
}
