package com.ksinfra.clawapk.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ksinfra.clawapk.chat.R
import com.ksinfra.clawapk.chat.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCfAuth: () -> Unit = {}
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val authType by viewModel.authType.collectAsState()
    val authValue by viewModel.authValue.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val piperUrl by viewModel.piperUrl.collectAsState()
    val kokoroUrl by viewModel.kokoroUrl.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text(stringResource(R.string.settings_server_url)) },
                placeholder = { Text(stringResource(R.string.settings_server_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            AuthTypeDropdown(
                selectedType = authType,
                onTypeSelected = viewModel::onAuthTypeChanged
            )

            if (authType == "cloudflare") {
                Spacer(modifier = Modifier.height(8.dp))
                val cfStatus = if (authValue.isNotBlank())
                    stringResource(R.string.settings_cf_authenticated)
                else
                    stringResource(R.string.settings_cf_login)

                OutlinedButton(
                    onClick = { onNavigateToCfAuth() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(cfStatus)
                }
            } else if (authType != "none" && authType != "device_pairing") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = authValue,
                    onValueChange = viewModel::onAuthValueChanged,
                    label = {
                        Text(
                            if (authType == "password") stringResource(R.string.settings_auth_value_password)
                            else stringResource(R.string.settings_auth_value_token)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TtsLanguageDropdown(
                selectedLanguage = ttsLanguage,
                onLanguageSelected = viewModel::onTtsLanguageChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = piperUrl,
                onValueChange = viewModel::onPiperUrlChanged,
                label = { Text(stringResource(R.string.settings_piper_url)) },
                placeholder = { Text("https://piper.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = kokoroUrl,
                onValueChange = viewModel::onKokoroUrlChanged,
                label = { Text(stringResource(R.string.settings_kokoro_url)) },
                placeholder = { Text("https://kokoro.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthTypeDropdown(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val options = listOf(
        "none" to stringResource(R.string.settings_auth_none),
        "cloudflare" to stringResource(R.string.settings_auth_cloudflare),
        "token" to stringResource(R.string.settings_auth_token),
        "password" to stringResource(R.string.settings_auth_password),
        "device_pairing" to stringResource(R.string.settings_auth_device_pairing)
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = options.find { it.first == selectedType }?.second ?: stringResource(R.string.settings_auth_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_auth_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onTypeSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsLanguageDropdown(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val options = listOf(
        "POLISH" to stringResource(R.string.settings_tts_polish),
        "ENGLISH" to stringResource(R.string.settings_tts_english)
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = options.find { it.first == selectedLanguage }?.second ?: stringResource(R.string.settings_tts_polish),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_tts_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onLanguageSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
