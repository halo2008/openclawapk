package com.ksinfra.clawapk.domain.model

sealed class OpenClawEvent {
    data class AgentResponse(val text: String, val sessionKey: String?) : OpenClawEvent()
    data class AgentStreaming(val runId: String, val textDelta: String, val sessionKey: String? = null) : OpenClawEvent()
    data class AgentStreamEnd(val runId: String, val sessionKey: String? = null) : OpenClawEvent()
    data class AgentError(val runId: String, val error: String, val sessionKey: String? = null) : OpenClawEvent()
    data class CronFired(val event: CronEvent) : OpenClawEvent()
    data class SessionChanged(val sessionId: String) : OpenClawEvent()
    data class Tick(val timestamp: Long) : OpenClawEvent()
    data object Shutdown : OpenClawEvent()
}
