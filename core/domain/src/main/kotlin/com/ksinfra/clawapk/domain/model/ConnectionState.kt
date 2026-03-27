package com.ksinfra.clawapk.domain.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val connId: String) : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
    data object Reconnecting : ConnectionState()
}
