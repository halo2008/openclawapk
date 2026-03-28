package com.ksinfra.clawapk.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.Message
import com.ksinfra.clawapk.domain.model.MessageStatus
import com.ksinfra.clawapk.domain.model.CronJobInfo
import com.ksinfra.clawapk.domain.model.ModelConfig
import com.ksinfra.clawapk.domain.model.ModelInfo
import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.model.RecognitionState
import com.ksinfra.clawapk.domain.model.Sender
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.domain.port.SettingsPort
import com.ksinfra.clawapk.domain.port.SpeechToTextPort
import com.ksinfra.clawapk.domain.usecase.ConnectToOpenClawUseCase
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
    private val observeConnectionState: ObserveConnectionStateUseCase,
    private val speakResponse: SpeakResponseUseCase,
    private val startVoiceInput: StartVoiceInputUseCase,
    private val stopVoiceInput: StopVoiceInputUseCase,
    private val connectToOpenClaw: ConnectToOpenClawUseCase,
    private val settingsPort: SettingsPort,
    private val stt: SpeechToTextPort,
    private val gateway: OpenClawGateway,
    private val ttsLanguage: Language
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = observeConnectionState()
    val recognitionState: StateFlow<RecognitionState> = stt.recognitionState

    private val _voiceOutputEnabled = MutableStateFlow(true)
    val voiceOutputEnabled: StateFlow<Boolean> = _voiceOutputEnabled.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _currentModel = MutableStateFlow("")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _modelConfig = MutableStateFlow(ModelConfig(primary = "", fallbacks = emptyList()))
    val modelConfig: StateFlow<ModelConfig> = _modelConfig.asStateFlow()

    private val _cronJobs = MutableStateFlow<List<CronJobInfo>>(emptyList())
    val cronJobs: StateFlow<List<CronJobInfo>> = _cronJobs.asStateFlow()

    private val _ttft = MutableStateFlow<Long?>(null)
    val ttft: StateFlow<Long?> = _ttft.asStateFlow()

    private val _contextInfo = MutableStateFlow("")
    val contextInfo: StateFlow<String> = _contextInfo.asStateFlow()

    private var streamStartTime: Long = 0

    // Track streaming message being built
    private var streamingMessageId: String? = null
    private var lastStreamRunId: String? = null

    init {
        autoConnect()
        observeAllEvents()
        observeVoiceInput()
        loadOnConnect()
    }

    private fun autoConnect() {
        viewModelScope.launch {
            settingsPort.getConnectionConfig().collect { config ->
                if (config != null && config.serverUrl.isNotBlank()) {
                    val currentState = connectionState.value
                    if (currentState is ConnectionState.Disconnected || currentState is ConnectionState.Error) {
                        connectToOpenClaw(config)
                    }
                }
            }
        }
    }

    private var hasLoadedInitialData = false

    private fun loadOnConnect() {
        viewModelScope.launch {
            // Wait until we see Connected state
            connectionState.collect { state ->
                if (state is ConnectionState.Connected && !hasLoadedInitialData) {
                    hasLoadedInitialData = true
                    kotlinx.coroutines.delay(300)
                    loadInitialData()
                } else if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    hasLoadedInitialData = false
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            loadChatHistory()
            loadModels()
            loadCurrentModel()
            onLoadCronJobs()
        }
    }

    private fun loadCurrentModel() {
        viewModelScope.launch {
            gateway.getModelConfig().onSuccess { config ->
                _modelConfig.value = config
                if (config.primary.isNotBlank()) _currentModel.value = config.primary
            }
        }
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        streamStartTime = System.currentTimeMillis()
        _ttft.value = null
        _isThinking.value = true

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

    fun onClearChat() {
        viewModelScope.launch {
            gateway.resetSession()
            _messages.value = emptyList()
            streamingMessageId = null
            lastStreamRunId = null
        }
    }

    fun onLoadCronJobs() {
        viewModelScope.launch {
            gateway.listCronJobs().onSuccess { jobs ->
                _cronJobs.value = jobs
            }
        }
    }

    fun onSelectModel(modelKey: String) {
        viewModelScope.launch {
            gateway.setDefaultModel(modelKey).onSuccess {
                _currentModel.value = modelKey
            }
        }
    }

    fun onSetPrimaryModel(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            // Move old primary to fallbacks if it was set, remove new primary from fallbacks
            val newFallbacks = buildList {
                if (current.primary.isNotBlank() && current.primary != modelKey) add(current.primary)
                addAll(current.fallbacks.filter { it != modelKey && it != current.primary })
            }
            val newConfig = ModelConfig(primary = modelKey, fallbacks = newFallbacks)
            gateway.setModelConfig(newConfig).onSuccess {
                _modelConfig.value = newConfig
                _currentModel.value = modelKey
            }
        }
    }

    fun onReorderModels(primary: String, fallbacks: List<String>) {
        viewModelScope.launch {
            val newConfig = ModelConfig(primary = primary, fallbacks = fallbacks)
            gateway.setModelConfig(newConfig).onSuccess {
                _modelConfig.value = newConfig
                _currentModel.value = primary
            }
        }
    }

    fun onRemoveFallback(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            val newConfig = current.copy(fallbacks = current.fallbacks.filter { it != modelKey })
            gateway.setModelConfig(newConfig).onSuccess {
                _modelConfig.value = newConfig
            }
        }
    }

    fun onAddFallback(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            if (modelKey != current.primary && modelKey !in current.fallbacks) {
                val newConfig = current.copy(fallbacks = current.fallbacks + modelKey)
                gateway.setModelConfig(newConfig).onSuccess {
                    _modelConfig.value = newConfig
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

    private fun loadChatHistory() {
        viewModelScope.launch {
            gateway.getChatHistory().onSuccess { history ->
                if (history.isNotEmpty() && _messages.value.isEmpty()) {
                    val loaded = history.map { msg ->
                        Message(
                            id = generateId(),
                            content = msg.content,
                            sender = if (msg.role == "assistant") Sender.AGENT else Sender.USER,
                            status = MessageStatus.SENT
                        )
                    }
                    _messages.value = loaded
                    _contextInfo.value = "${loaded.size} msgs"
                }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            gateway.listModels().onSuccess { models ->
                _availableModels.value = models
            }
        }
    }

    private fun observeAllEvents() {
        viewModelScope.launch {
            gateway.events.collect { event ->
                when (event) {
                    is OpenClawEvent.AgentStreaming -> handleStreaming(event)
                    is OpenClawEvent.AgentStreamEnd -> handleStreamEnd(event)
                    is OpenClawEvent.AgentResponse -> handleFinalResponse(event)
                    is OpenClawEvent.AgentError -> handleError(event)
                    else -> { /* cron, session, etc handled elsewhere */ }
                }
            }
        }
    }

    private fun handleStreaming(event: OpenClawEvent.AgentStreaming) {
        _isThinking.value = false
        _isStreaming.value = true

        // Measure TTFT
        if (lastStreamRunId != event.runId && streamStartTime > 0) {
            _ttft.value = System.currentTimeMillis() - streamStartTime
        }

        if (lastStreamRunId != event.runId) {
            // New streaming message
            lastStreamRunId = event.runId
            val msgId = generateId()
            streamingMessageId = msgId
            _messages.update { it + Message(
                id = msgId,
                content = event.textDelta,
                sender = Sender.AGENT,
                status = MessageStatus.SENDING
            )}
        } else {
            // Append to existing streaming message
            val msgId = streamingMessageId ?: return
            _messages.update { msgs ->
                msgs.map { msg ->
                    if (msg.id == msgId) msg.copy(content = event.textDelta)
                    else msg
                }
            }
        }
    }

    private fun handleStreamEnd(event: OpenClawEvent.AgentStreamEnd) {
        _isStreaming.value = false
        _isThinking.value = false
        val msgId = streamingMessageId ?: return
        _messages.update { msgs ->
            msgs.map { msg ->
                if (msg.id == msgId) msg.copy(status = MessageStatus.SENT)
                else msg
            }
        }
        streamingMessageId = null
    }

    private fun handleFinalResponse(event: OpenClawEvent.AgentResponse) {
        _isStreaming.value = false
        val msgId = streamingMessageId
        if (msgId != null) {
            // Replace streaming message with final text
            _messages.update { msgs ->
                msgs.map { msg ->
                    if (msg.id == msgId) msg.copy(content = event.text, status = MessageStatus.SENT)
                    else msg
                }
            }
            streamingMessageId = null
            lastStreamRunId = null
        } else {
            // No streaming was happening, add as new message
            _messages.update { it + Message(
                id = generateId(),
                content = event.text,
                sender = Sender.AGENT
            )}
        }

        // Update context info
        val msgCount = _messages.value.size
        _contextInfo.value = "$msgCount messages"

        if (_voiceOutputEnabled.value) {
            viewModelScope.launch {
                speakResponse(event.text, ttsLanguage)
            }
        }
    }

    private fun handleError(event: OpenClawEvent.AgentError) {
        _isThinking.value = false
        _isStreaming.value = false
        streamingMessageId = null
        lastStreamRunId = null

        _messages.update { it + Message(
            id = generateId(),
            content = event.error,
            sender = Sender.AGENT,
            status = MessageStatus.ERROR
        )}
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
