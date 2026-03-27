package com.ksinfra.clawapk.domain.port

import com.ksinfra.clawapk.domain.model.AudioData

interface AudioPlayerPort {
    suspend fun play(audioData: AudioData)
    fun stop()
    fun isPlaying(): Boolean
}
