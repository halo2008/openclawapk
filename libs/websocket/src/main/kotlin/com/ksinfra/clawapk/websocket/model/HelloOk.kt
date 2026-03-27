package com.ksinfra.clawapk.websocket.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HelloOk(
    val type: String,
    val protocol: Int,
    val server: ServerInfo,
    val features: Features? = null,
    val auth: IssuedAuth? = null,
    val policy: Policy? = null
)

@Serializable
data class ServerInfo(
    val version: String,
    val connId: String
)

@Serializable
data class Features(
    val methods: List<String> = emptyList(),
    val events: List<String> = emptyList()
)

@Serializable
data class IssuedAuth(
    val deviceToken: String? = null,
    val role: String? = null,
    val scopes: List<String> = emptyList()
)

@Serializable
data class Policy(
    val maxPayload: Long? = null,
    val maxBufferedBytes: Long? = null,
    val tickIntervalMs: Long? = null
)
