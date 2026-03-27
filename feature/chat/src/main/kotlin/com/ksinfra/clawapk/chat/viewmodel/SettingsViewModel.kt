package com.ksinfra.clawapk.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ksinfra.clawapk.domain.model.AuthMode
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.SettingsPort
import com.ksinfra.clawapk.domain.usecase.ConnectToOpenClawUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsPort: SettingsPort,
    private val connectToOpenClaw: ConnectToOpenClawUseCase
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _authType = MutableStateFlow("none")
    val authType: StateFlow<String> = _authType.asStateFlow()

    private val _authValue = MutableStateFlow("")
    val authValue: StateFlow<String> = _authValue.asStateFlow()

    private val _ttsLanguage = MutableStateFlow("POLISH")
    val ttsLanguage: StateFlow<String> = _ttsLanguage.asStateFlow()

    init {
        loadSettings()
    }

    fun onServerUrlChanged(url: String) {
        _serverUrl.value = url
    }

    fun onAuthTypeChanged(type: String) {
        _authType.value = type
        if (type == "none" || type == "device_pairing") {
            _authValue.value = ""
        }
    }

    fun onAuthValueChanged(value: String) {
        _authValue.value = value
    }

    fun onTtsLanguageChanged(language: String) {
        _ttsLanguage.value = language
    }

    fun onSave() {
        viewModelScope.launch {
            val config = buildConfig()
            settingsPort.saveConnectionConfig(config)
            connectToOpenClaw(config)
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsPort.getConnectionConfig().collect { config ->
                if (config != null) {
                    _serverUrl.value = config.serverUrl
                    _ttsLanguage.value = config.ttsLanguage.name
                    when (val mode = config.authMode) {
                        is AuthMode.Token -> {
                            _authType.value = "token"
                            _authValue.value = mode.token
                        }
                        is AuthMode.Password -> {
                            _authType.value = "password"
                            _authValue.value = mode.password
                        }
                        is AuthMode.DeviceToken -> {
                            _authType.value = "device_token"
                            _authValue.value = mode.token
                        }
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
            ttsLanguage = Language.valueOf(_ttsLanguage.value)
        )
    }
}
