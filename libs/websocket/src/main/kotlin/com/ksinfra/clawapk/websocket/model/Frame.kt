package com.ksinfra.clawapk.websocket.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RequestFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class ResponseFrame(
    val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: FrameError? = null
)

@Serializable
data class EventFrame(
    val type: String = "event",
    val event: String,
    val seq: Int? = null,
    val payload: JsonElement? = null
)

@Serializable
data class FrameError(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val retryable: Boolean? = null,
    @SerialName("retryAfterMs")
    val retryAfterMs: Long? = null
)
