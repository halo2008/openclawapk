package com.ksinfra.clawapk.domain.model

data class Session(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long
)
