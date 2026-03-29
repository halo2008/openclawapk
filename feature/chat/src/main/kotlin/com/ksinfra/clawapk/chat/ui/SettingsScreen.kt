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
import com.ksinfra.clawapk.domain.model.TtsVoiceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCfAuth: () -> Unit = {}
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val ttsVoiceName by viewModel.ttsVoiceName.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    val gatewayToken by viewModel.gatewayToken.collectAsState()
    val cfCookie by viewModel.cfCookie.collectAsState()
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

            // Cloudflare Access — always visible
            val cfStatus = if (cfCookie.isNotBlank())
                stringResource(R.string.settings_cf_authenticated)
            else
                stringResource(R.string.settings_cf_login)

            OutlinedButton(
                onClick = { onNavigateToCfAuth() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(cfStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gateway token — always visible
            OutlinedTextField(
                value = gatewayToken,
                onValueChange = viewModel::onGatewayTokenChanged,
                label = { Text(stringResource(R.string.settings_gateway_token)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            TtsVoiceDropdown(
                selectedVoiceName = ttsVoiceName,
                availableVoices = availableVoices,
                onVoiceSelected = viewModel::onTtsVoiceNameChanged
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
private fun TtsVoiceDropdown(
    selectedVoiceName: String,
    availableVoices: List<TtsVoiceInfo>,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = availableVoices.find { it.name == selectedVoiceName }?.displayName
        ?: if (selectedVoiceName.isBlank()) stringResource(R.string.settings_tts_default) else selectedVoiceName

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_tts_voice)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_tts_default)) },
                onClick = {
                    onVoiceSelected("")
                    expanded = false
                }
            )
            availableVoices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.displayName) },
                    onClick = {
                        onVoiceSelected(voice.name)
                        expanded = false
                    }
                )
            }
        }
    }
}
