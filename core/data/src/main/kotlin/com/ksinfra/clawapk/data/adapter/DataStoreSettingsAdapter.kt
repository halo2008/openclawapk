package com.ksinfra.clawapk.data.adapter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ksinfra.clawapk.domain.model.AuthMode
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.Language
import com.ksinfra.clawapk.domain.port.SettingsPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clawapk_settings")

class DataStoreSettingsAdapter(
    private val context: Context
) : SettingsPort {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val AUTH_TYPE = stringPreferencesKey("auth_type")
        val AUTH_VALUE = stringPreferencesKey("auth_value")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val CF_COOKIE = stringPreferencesKey("cf_cookie")
    }

    override fun getConnectionConfig(): Flow<ConnectionConfig?> {
        return context.dataStore.data.map { prefs ->
            val url = prefs[Keys.SERVER_URL] ?: return@map null
            val authType = prefs[Keys.AUTH_TYPE] ?: "none"
            val authValue = prefs[Keys.AUTH_VALUE] ?: ""
            val language = prefs[Keys.TTS_LANGUAGE]?.let {
                runCatching { Language.valueOf(it) }.getOrDefault(Language.POLISH)
            } ?: Language.POLISH

            val authMode = when (authType) {
                "token" -> AuthMode.Token(authValue)
                "password" -> AuthMode.Password(authValue)
                "device_token" -> AuthMode.DeviceToken(authValue)
                "device_pairing" -> AuthMode.DevicePairing
                "cloudflare" -> AuthMode.None // migrated: cf cookie is now a separate field
                else -> AuthMode.None
            }

            ConnectionConfig(
                serverUrl = url,
                authMode = authMode,
                gatewayToken = prefs[Keys.GATEWAY_TOKEN] ?: "",
                cfCookie = prefs[Keys.CF_COOKIE] ?: if (authType == "cloudflare") authValue else "",
                ttsLanguage = language,
                ttsVoiceName = prefs[Keys.TTS_VOICE_NAME] ?: ""
            )
        }
    }

    override suspend fun saveConnectionConfig(config: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = config.serverUrl
            prefs[Keys.GATEWAY_TOKEN] = config.gatewayToken
            prefs[Keys.CF_COOKIE] = config.cfCookie
            prefs[Keys.TTS_LANGUAGE] = config.ttsLanguage.name
            prefs[Keys.TTS_VOICE_NAME] = config.ttsVoiceName

            when (val mode = config.authMode) {
                is AuthMode.Token -> {
                    prefs[Keys.AUTH_TYPE] = "token"
                    prefs[Keys.AUTH_VALUE] = mode.token
                }
                is AuthMode.Password -> {
                    prefs[Keys.AUTH_TYPE] = "password"
                    prefs[Keys.AUTH_VALUE] = mode.password
                }
                is AuthMode.DeviceToken -> {
                    prefs[Keys.AUTH_TYPE] = "device_token"
                    prefs[Keys.AUTH_VALUE] = mode.token
                }
                is AuthMode.DevicePairing -> {
                    prefs[Keys.AUTH_TYPE] = "device_pairing"
                    prefs[Keys.AUTH_VALUE] = ""
                }
                is AuthMode.None -> {
                    prefs[Keys.AUTH_TYPE] = "none"
                    prefs[Keys.AUTH_VALUE] = ""
                }
            }
        }
    }

    override suspend fun clearConfig() {
        context.dataStore.edit { it.clear() }
    }
}
