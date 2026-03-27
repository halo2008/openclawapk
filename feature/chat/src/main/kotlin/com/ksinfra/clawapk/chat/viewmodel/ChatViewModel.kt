package com.ksinfra.clawapk.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.Message
import com.ksinfra.clawapk.domain.model.MessageStatus
import com.ksinfra.clawapk.domain.model.RecognitionState
import com.ksinfra.clawapk.domain.model.Sender
import com.ksinfra.clawapk.domain.port.SpeechToTextPort
import com.ksinfra.clawapk.domain.usecase.ObserveAgentResponsesUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveConnectionStateUseCase
import com.ksinfra.clawapk.domain.usecase.SendMessageUseCase
import com.ksinfra.clawapk.domain.usecase.SpeakResponseUseCase
import com.ksinfra.clawapk.domain.usecase.StartVoiceInputUseCase
import com.ksinfra.clawapk.domain.usecase.StopVoiceInputUseCase
import com.ksinfra.clawapk.common.generateId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sendMessage: SendMessageUseCase,
    private val observeAgentResponses: ObserveAgentResponsesUseCase,
    private val observeConnectionState: ObserveConnectionStateUseCase,
    private val speakResponse: SpeakResponseUseCase,
    private val startVoiceInput: StartVoiceInputUseCase,
    private val stopVoiceInput: StopVoiceInputUseCase,
    private val stt: SpeechToTextPort,
    private val ttsLanguage: Language
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = observeConnectionState()
    val recognitionState: StateFlow<RecognitionState> = stt.recognitionState

    private val _voiceOutputEnabled = MutableStateFlow(true)
    val voiceOutputEnabled: StateFlow<Boolean> = _voiceOutputEnabled.asStateFlow()

    init {
        observeResponses()
        observeVoiceInput()
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message(
            id = generateId(),
            content = text,
            sender = Sender.USER,
            status = MessageStatus.SENDING
        )
        _messages.update { it + userMessage }

        viewModelScope.launch {
            sendMessage(text)
                .onSuccess {
                    _messages.update { msgs ->
                        msgs.map { if (it.id == userMessage.id) it.copy(status = MessageStatus.SENT) else it }
                    }
                }
                .onFailure {
                    _messages.update { msgs ->
                        msgs.map { if (it.id == userMessage.id) it.copy(status = MessageStatus.ERROR) else it }
                    }
                }
        }
    }

    fun onToggleVoiceInput() {
        when (stt.recognitionState.value) {
            is RecognitionState.Listening -> stopVoiceInput()
            else -> startVoiceInput(ttsLanguage)
        }
    }

    fun onToggleVoiceOutput() {
        _voiceOutputEnabled.update { !it }
    }

    private fun observeResponses() {
        viewModelScope.launch {
            observeAgentResponses().collect { response ->
                val agentMessage = Message(
                    id = generateId(),
                    content = response.text,
                    sender = Sender.AGENT
                )
                _messages.update { it + agentMessage }

                if (_voiceOutputEnabled.value) {
                    speakResponse(response.text, ttsLanguage)
                }
            }
        }
    }

    private fun observeVoiceInput() {
        viewModelScope.launch {
            stt.recognitionState.collect { state ->
                if (state is RecognitionState.Result) {
                    onSendMessage(state.text)
                }
            }
        }
    }
}
