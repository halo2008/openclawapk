package com.ksinfra.clawapk.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ksinfra.clawapk.domain.model.AuthMode
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.model.TtsVoiceInfo
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.domain.port.SettingsPort
import com.ksinfra.clawapk.domain.port.TextToSpeechPort
import com.ksinfra.clawapk.domain.usecase.ConnectToOpenClawUseCase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsViewModel(
    private val settingsPort: SettingsPort,
    private val connectToOpenClaw: ConnectToOpenClawUseCase,
    private val gateway: OpenClawGateway,
    private val ttsPort: TextToSpeechPort,
    private val deviceIdProvider: () -> String = { "unknown" }
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _authType = MutableStateFlow("none")
    val authType: StateFlow<String> = _authType.asStateFlow()

    private val _authValue = MutableStateFlow("")
    val authValue: StateFlow<String> = _authValue.asStateFlow()

    private val _ttsLanguage = MutableStateFlow("POLISH")
    val ttsLanguage: StateFlow<String> = _ttsLanguage.asStateFlow()

    private val _gatewayToken = MutableStateFlow("")
    val gatewayToken: StateFlow<String> = _gatewayToken.asStateFlow()

    private val _cfCookie = MutableStateFlow("")
    val cfCookie: StateFlow<String> = _cfCookie.asStateFlow()

    private val _ttsVoiceName = MutableStateFlow("")
    val ttsVoiceName: StateFlow<String> = _ttsVoiceName.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    private val _fcmStatus = MutableStateFlow("")
    val fcmStatus: StateFlow<String> = _fcmStatus.asStateFlow()

    init {
        loadSettings()
    }

    fun onServerUrlChanged(url: String) { _serverUrl.value = url }
    fun onAuthTypeChanged(type: String) {
        _authType.value = type
        if (type == "none" || type == "device_pairing") _authValue.value = ""
    }
    fun onAuthValueChanged(value: String) { _authValue.value = value }
    fun onCfCookieObtained(cookie: String) { _cfCookie.value = cookie }
    fun onTtsLanguageChanged(language: String) { _ttsLanguage.value = language }
    fun onGatewayTokenChanged(token: String) { _gatewayToken.value = token }
    fun onTtsVoiceNameChanged(voiceName: String) { _ttsVoiceName.value = voiceName }

    fun onForceFcmRegistration() {
        viewModelScope.launch {
            _fcmStatus.value = "..."
            try {
                val token = suspendCancellableCoroutine<String> { cont ->
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { cont.resume(it, null) }
                        .addOnFailureListener { cont.resume("", null) }
                }
                if (token.isBlank()) { _fcmStatus.value = "Error: no token"; return@launch }
                val serverUrl = _serverUrl.value.trim()
                    .replace(Regex("^\\w+://"), "")
                    .replace(Regex("/.*$"), "")
                val parts = serverUrl.split(".")
                val n8nHost = if (parts.size >= 3) "n8n." + parts.drop(1).joinToString(".") else serverUrl
                val registerUrl = "https://$n8nHost/webhook/register-device"
                val deviceId = deviceIdProvider()
                val json = """{"fcmToken":"$token","deviceId":"$deviceId","platform":"android"}"""
                val code = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url(registerUrl)
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(request).execute().code
                }
                _fcmStatus.value = if (code in 200..299) "OK" else "Error: $code"
            } catch (e: Exception) {
                _fcmStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun onSave() {
        viewModelScope.launch {
            val config = buildConfig()
            settingsPort.saveConnectionConfig(config)
            connectToOpenClaw(config)
            ttsPort.setVoice(_ttsVoiceName.value)
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _availableVoices.value = ttsPort.getAvailableVoices()

            settingsPort.getConnectionConfig().collect { config ->
                if (config != null) {
                    _serverUrl.value = config.serverUrl
                    _ttsLanguage.value = config.ttsLanguage.name
                    _ttsVoiceName.value = config.ttsVoiceName
                    _gatewayToken.value = config.gatewayToken
                    _cfCookie.value = config.cfCookie
                    ttsPort.setVoice(config.ttsVoiceName)
                    when (val mode = config.authMode) {
                        is AuthMode.Token -> { _authType.value = "token"; _authValue.value = mode.token }
                        is AuthMode.Password -> { _authType.value = "password"; _authValue.value = mode.password }
                        is AuthMode.DeviceToken -> { _authType.value = "device_token"; _authValue.value = mode.token }
                        is AuthMode.DevicePairing -> _authType.value = "device_pairing"
                        is AuthMode.None -> _authType.value = "none"
                    }
                }
            }
        }
    }

    private fun buildConfig(): ConnectionConfig {
        val authMode = when (_authType.value) {
            "token" -> AuthMode.Token(_authValue.value)
            "password" -> AuthMode.Password(_authValue.value)
            "device_pairing" -> AuthMode.DevicePairing
            else -> AuthMode.None
        }
        return ConnectionConfig(
            serverUrl = _serverUrl.value,
            authMode = authMode,
            gatewayToken = _gatewayToken.value,
            cfCookie = _cfCookie.value,
            ttsLanguage = Language.valueOf(_ttsLanguage.value),
            ttsVoiceName = _ttsVoiceName.value
        )
    }
}
