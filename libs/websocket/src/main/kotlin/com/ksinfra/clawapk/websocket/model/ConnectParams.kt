package com.ksinfra.clawapk.websocket.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectParams(
    val minProtocol: Int = 1,
    val maxProtocol: Int = 1,
    val client: ClientInfo,
    val auth: AuthParams? = null
)

@Serializable
data class ClientInfo(
    val id: String = "clawapk",
    val displayName: String = "ClawAPK",
    val version: String = "1.0.0",
    val platform: String = "android",
    val deviceFamily: String = "mobile",
    val mode: String = "ui"
)

@Serializable
data class AuthParams(
    val token: String? = null,
    val bootstrapToken: String? = null,
    val deviceToken: String? = null,
    val password: String? = null
)
