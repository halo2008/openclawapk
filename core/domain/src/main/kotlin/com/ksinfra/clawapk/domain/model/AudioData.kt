package com.ksinfra.clawapk.domain.model

data class AudioData(
    val bytes: ByteArray,
    val mimeType: String = "audio/wav",
    val sampleRate: Int = 22050
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sampleRate
        return result
    }
}
