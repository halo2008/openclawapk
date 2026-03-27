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
        val PIPER_URL = stringPreferencesKey("piper_url")
        val KOKORO_URL = stringPreferencesKey("kokoro_url")
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
                "cloudflare" -> AuthMode.CloudflareAccess(authValue)
                "device_pairing" -> AuthMode.DevicePairing
                else -> AuthMode.None
            }

            ConnectionConfig(
                serverUrl = url,
                authMode = authMode,
                ttsLanguage = language,
                piperUrl = prefs[Keys.PIPER_URL] ?: "",
                kokoroUrl = prefs[Keys.KOKORO_URL] ?: ""
            )
        }
    }

    override suspend fun saveConnectionConfig(config: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = config.serverUrl
            prefs[Keys.TTS_LANGUAGE] = config.ttsLanguage.name
            prefs[Keys.PIPER_URL] = config.piperUrl
            prefs[Keys.KOKORO_URL] = config.kokoroUrl

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
                is AuthMode.CloudflareAccess -> {
                    prefs[Keys.AUTH_TYPE] = "cloudflare"
                    prefs[Keys.AUTH_VALUE] = mode.cfCookie
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
