package com.ksinfra.clawapk.domain.model

data class CronJobInfo(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val lastRun: String?
)
