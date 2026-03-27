package com.ksinfra.clawapk.domain.port

interface VibrationPort {
    fun vibrateShort()
    fun vibrateLong()
    fun vibratePattern(pattern: LongArray)
}
