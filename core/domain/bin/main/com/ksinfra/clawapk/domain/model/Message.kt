package com.ksinfra.clawapk.domain.model

data class Message(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val channel: MessageChannel = MessageChannel.CHAT
)

enum class Sender {
    USER,
    AGENT
}

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

enum class MessageChannel {
    CHAT,
    SYSTEM
}
