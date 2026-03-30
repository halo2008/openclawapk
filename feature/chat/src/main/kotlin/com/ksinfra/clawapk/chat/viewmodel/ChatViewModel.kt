package com.ksinfra.clawapk.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.Message
import com.ksinfra.clawapk.domain.model.MessageChannel
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
import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.domain.usecase.ConnectToOpenClawUseCase
import com.ksinfra.clawapk.domain.usecase.ObserveConnectionStateUseCase
import com.ksinfra.clawapk.domain.usecase.SendMessageUseCase
import com.ksinfra.clawapk.domain.usecase.SpeakResponseUseCase
import com.ksinfra.clawapk.domain.usecase.StartVoiceInputUseCase
import com.ksinfra.clawapk.domain.usecase.StopVoiceInputUseCase
import com.ksinfra.clawapk.common.generateId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val ttsPort: TextToSpeechPort,
    private val ttsLanguage: Language
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _systemMessages = MutableStateFlow<List<Message>>(emptyList())
    val systemMessages: StateFlow<List<Message>> = _systemMessages.asStateFlow()

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

    private val _allModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val allModels: StateFlow<List<ModelInfo>> = _allModels.asStateFlow()

    private val _configuredProviders = MutableStateFlow<Set<String>>(emptySet())
    val configuredProviders: StateFlow<Set<String>> = _configuredProviders.asStateFlow()

    private val _cronJobs = MutableStateFlow<List<CronJobInfo>>(emptyList())
    val cronJobs: StateFlow<List<CronJobInfo>> = _cronJobs.asStateFlow()

    private val _ttft = MutableStateFlow<Long?>(null)
    val ttft: StateFlow<Long?> = _ttft.asStateFlow()

    private val _contextInfo = MutableStateFlow("")
    val contextInfo: StateFlow<String> = _contextInfo.asStateFlow()

    private val _configSaving = MutableStateFlow(false)
    val configSaving: StateFlow<Boolean> = _configSaving.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private var streamStartTime: Long = 0

    // Track streaming message being built
    private var streamingMessageId: String? = null
    private var lastStreamRunId: String? = null

    // TTS only when user initiated the conversation (not cron/system events)
    private var userSentLastMessage = false

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
        userSentLastMessage = true
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

    private suspend fun saveModelConfig(newConfig: ModelConfig): Boolean {
        _configSaving.value = true
        return try {
            gateway.setModelConfig(newConfig)
                .onSuccess {
                    _modelConfig.value = newConfig
                    if (newConfig.primary.isNotBlank()) _currentModel.value = newConfig.primary
                }
                .onFailure { e ->
                    _uiMessage.tryEmit("Nie udało się zapisać: ${e.message}")
                }
                .isSuccess
        } finally {
            _configSaving.value = false
        }
    }

    fun onSetPrimaryModel(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            val newFallbacks = buildList {
                if (current.primary.isNotBlank() && current.primary != modelKey) add(current.primary)
                addAll(current.fallbacks.filter { it != modelKey && it != current.primary })
            }
            saveModelConfig(ModelConfig(primary = modelKey, fallbacks = newFallbacks))
        }
    }

    fun onReorderModels(primary: String, fallbacks: List<String>) {
        viewModelScope.launch {
            saveModelConfig(ModelConfig(primary = primary, fallbacks = fallbacks))
        }
    }

    fun onRemoveFallback(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            saveModelConfig(current.copy(fallbacks = current.fallbacks.filter { it != modelKey }))
        }
    }

    fun onAddFallback(modelKey: String) {
        viewModelScope.launch {
            val current = _modelConfig.value
            if (modelKey != current.primary && modelKey !in current.fallbacks) {
                saveModelConfig(current.copy(fallbacks = current.fallbacks + modelKey))
            }
        }
    }

    fun onAddProviderKey(provider: String, apiKey: String) {
        viewModelScope.launch {
            gateway.setProviderApiKey(provider, apiKey).onSuccess {
                // Refresh models and providers after adding key
                loadModels()
                loadCurrentModel()
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

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    fun onToggleSpeakMessage(messageId: String, text: String) {
        if (_speakingMessageId.value == messageId) {
            ttsPort.stop()
            _speakingMessageId.value = null
        } else {
            ttsPort.stop()
            _speakingMessageId.value = messageId
            viewModelScope.launch {
                val clean = stripMarkdown(text)
                speakResponse(clean, ttsLanguage)
                _speakingMessageId.value = null
            }
        }
    }

    private fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("#{1,6}\\s*"), "")           // headers
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")   // bold
            .replace(Regex("\\*(.+?)\\*"), "$1")          // italic
            .replace(Regex("__(.+?)__"), "$1")            // bold alt
            .replace(Regex("_(.+?)_"), "$1")              // italic alt
            .replace(Regex("~~(.+?)~~"), "$1")            // strikethrough
            .replace(Regex("`(.+?)`"), "$1")              // inline code
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")  // list bullets
            .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "") // numbered lists
            .replace(Regex("\\[(.+?)]\\(.+?\\)"), "$1")  // links
            .trim()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            gateway.getChatHistory().onSuccess { history ->
                if (history.isNotEmpty() && _messages.value.isEmpty()) {
                    val loaded = history
                        .filter { msg ->
                            // Filter out cron system events (instructions)
                            !(msg.role == "user" && msg.content.trimStart().startsWith("System:"))
                        }
                        .map { msg ->
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
            gateway.getConfiguredProviders().onSuccess { providers ->
                _configuredProviders.value = providers
            }
            gateway.listModels().onSuccess { models ->
                _allModels.value = models
                val configured = _configuredProviders.value
                _availableModels.value = if (configured.isEmpty()) models
                    else models.filter { it.provider in configured }
            }
        }
    }

    private fun observeAllEvents() {
        viewModelScope.launch {
            gateway.events.collect { event ->
                when (event) {
                    is OpenClawEvent.AgentStreaming -> {
                        if (isMainSession(event.sessionKey)) handleStreaming(event)
                        else addSystemMessage(stripSystemTags(event.textDelta), event.sessionKey)
                    }
                    is OpenClawEvent.AgentStreamEnd -> {
                        if (isMainSession(event.sessionKey)) handleStreamEnd(event)
                        else finalizeSystemMessage(event.sessionKey)
                    }
                    is OpenClawEvent.AgentResponse -> {
                        if (isMainSession(event.sessionKey)) handleFinalResponse(event)
                        else {
                            finalizeSystemMessage(event.sessionKey)
                            addSystemMessage(stripSystemTags(event.text), event.sessionKey)
                            finalizeSystemMessage(event.sessionKey)
                        }
                    }
                    is OpenClawEvent.AgentError -> {
                        if (isMainSession(event.sessionKey)) handleError(event)
                        else {
                            finalizeSystemMessage(event.sessionKey)
                            addSystemMessage("Error: ${event.error}", event.sessionKey)
                            finalizeSystemMessage(event.sessionKey)
                        }
                    }
                    else -> { /* cron, session, etc handled elsewhere */ }
                }
            }
        }
    }

    private val systemStreamIds = mutableMapOf<String, String>()

    private fun addSystemMessage(text: String, sessionKey: String?) {
        if (text.isBlank()) return
        val key = sessionKey ?: "unknown"
        val existingId = systemStreamIds[key]

        if (existingId != null) {
            _systemMessages.update { messages ->
                messages.map { msg ->
                    if (msg.id == existingId) msg.copy(content = msg.content + text)
                    else msg
                }
            }
        } else {
            val newId = generateId()
            systemStreamIds[key] = newId
            _systemMessages.update { it + Message(
                id = newId,
                content = text,
                sender = Sender.AGENT,
                channel = MessageChannel.SYSTEM
            )}
        }
    }

    private fun finalizeSystemMessage(sessionKey: String?) {
        val key = sessionKey ?: "unknown"
        systemStreamIds.remove(key)
    }

    private fun handleStreaming(event: OpenClawEvent.AgentStreaming) {
        _isThinking.value = false
        _isStreaming.value = true

        val cleanText = stripSystemTags(event.textDelta)
        if (cleanText.isBlank()) return

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
                content = cleanText,
                sender = Sender.AGENT,
                status = MessageStatus.SENDING
            )}
        } else {
            // Append to existing streaming message
            val msgId = streamingMessageId ?: return
            _messages.update { msgs ->
                msgs.map { msg ->
                    if (msg.id == msgId) msg.copy(content = cleanText)
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
        val cleanText = stripSystemTags(event.text)
        val msgId = streamingMessageId
        if (msgId != null) {
            // Replace streaming message with final text
            if (cleanText.isNotBlank()) {
                _messages.update { msgs ->
                    msgs.map { msg ->
                        if (msg.id == msgId) msg.copy(content = cleanText, status = MessageStatus.SENT)
                        else msg
                    }
                }
            }
            streamingMessageId = null
            lastStreamRunId = null
        } else if (cleanText.isNotBlank()) {
            // No streaming was happening, add as new message
            _messages.update { it + Message(
                id = generateId(),
                content = cleanText,
                sender = Sender.AGENT
            )}
        }

        // Update context info
        val msgCount = _messages.value.size
        _contextInfo.value = "$msgCount messages"

        if (_voiceOutputEnabled.value && userSentLastMessage) {
            viewModelScope.launch {
                speakResponse(event.text, ttsLanguage)
            }
        }
        userSentLastMessage = false
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

    companion object {
        private const val MAIN_SESSION_KEY = "agent:main:main"

        private val SYSTEM_TAG_REGEX = Regex("<system-reminder>[\\s\\S]*?</system-reminder>")
        private val XML_TAG_REGEX = Regex("<(?:user-prompt-submit-hook|tool-result|command-name)[^>]*>[\\s\\S]*?</(?:user-prompt-submit-hook|tool-result|command-name)>")

        fun isMainSession(sessionKey: String?): Boolean {
            return sessionKey == null || sessionKey == MAIN_SESSION_KEY || sessionKey.isEmpty()
        }

        fun stripSystemTags(text: String): String {
            return text
                .replace(SYSTEM_TAG_REGEX, "")
                .replace(XML_TAG_REGEX, "")
                .trim()
        }
    }
}
