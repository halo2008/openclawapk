package com.ksinfra.clawapk.domain.model

data class CronEvent(
    val jobId: String,
    val jobName: String,
    val message: String?,
    val actions: Set<CronAction> = setOf(CronAction.NOTIFY),
    val timestamp: Long = System.currentTimeMillis()
)

enum class CronAction {
    NOTIFY,
    SPEAK,
    PLAY_SOUND,
    VIBRATE
}
