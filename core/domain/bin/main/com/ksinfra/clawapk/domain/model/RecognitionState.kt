package com.ksinfra.clawapk.domain.model

sealed class RecognitionState {
    data object Idle : RecognitionState()
    data object Listening : RecognitionState()
    data class Result(val text: String) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}
