package com.ksinfra.clawapk.domain.model

data class ConnectionConfig(
    val serverUrl: String,
    val authMode: AuthMode,
    val ttsLanguage: Language = Language.POLISH
)

sealed class AuthMode {
    data class Token(val token: String) : AuthMode()
    data class Password(val password: String) : AuthMode()
    data class DeviceToken(val token: String) : AuthMode()
    data class CloudflareAccess(val cfCookie: String = "") : AuthMode()
    data object DevicePairing : AuthMode()
    data object None : AuthMode()
}
