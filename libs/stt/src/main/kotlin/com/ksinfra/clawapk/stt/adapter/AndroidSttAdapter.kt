package com.ksinfra.clawapk.stt.adapter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ksinfra.clawapk.domain.model.RecognitionState
import com.ksinfra.clawapk.domain.port.SpeechToTextPort
import com.ksinfra.clawapk.stt.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSttAdapter(
    private val context: Context
) : SpeechToTextPort {

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    override val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    override fun startListening(languageCode: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionState.value = RecognitionState.Error(context.getString(R.string.stt_unavailable))
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _recognitionState.value = RecognitionState.Listening
        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
        _recognitionState.value = RecognitionState.Idle
    }

    private fun createListener() = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                _recognitionState.value = RecognitionState.Result(text)
            }
            _recognitionState.value = RecognitionState.Idle
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.stt_no_match)
                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.stt_network_error)
                SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.stt_audio_error)
                else -> context.getString(R.string.stt_error, error)
            }
            _recognitionState.value = RecognitionState.Error(message)
            _recognitionState.value = RecognitionState.Idle
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
