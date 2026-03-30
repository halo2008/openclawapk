package com.ksinfra.clawapk.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.ksinfra.clawapk.chat.R
import androidx.compose.material.icons.filled.Stop
import com.ksinfra.clawapk.chat.viewmodel.ChatViewModel
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.CronJobInfo
import com.ksinfra.clawapk.domain.model.Message
import com.ksinfra.clawapk.domain.model.ModelConfig
import com.ksinfra.clawapk.domain.model.ModelInfo
import com.ksinfra.clawapk.domain.model.MessageStatus
import com.ksinfra.clawapk.domain.model.RecognitionState
import com.ksinfra.clawapk.domain.model.Sender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val recognitionState by viewModel.recognitionState.collectAsState()
    val voiceOutputEnabled by viewModel.voiceOutputEnabled.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val modelConfig by viewModel.modelConfig.collectAsState()
    val configuredProviders by viewModel.configuredProviders.collectAsState()
    val cronJobs by viewModel.cronJobs.collectAsState()
    val ttft by viewModel.ttft.collectAsState()
    val contextInfo by viewModel.contextInfo.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val systemMessages by viewModel.systemMessages.collectAsState()
    val configSaving by viewModel.configSaving.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val speakingMessageId by viewModel.speakingMessageId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showCronDialog by remember { mutableStateOf(false) }
    var showAddProviderDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.chat_title))
                            Spacer(modifier = Modifier.width(8.dp))
                            ConnectionIndicator(connectionState)
                        }
                        if (currentModel.isNotBlank()) {
                            Text(
                                text = currentModel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Clear chat - always visible
                    IconButton(onClick = { viewModel.onClearChat() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat")
                    }
                    // Mute/unmute - always visible
                    IconButton(onClick = { viewModel.onToggleVoiceOutput() }) {
                        Icon(
                            imageVector = if (voiceOutputEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = stringResource(R.string.chat_toggle_voice_output)
                        )
                    }
                    // Overflow menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_change_model)) },
                                onClick = { showMenu = false; showModelMenu = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_add_provider)) },
                                onClick = { showMenu = false; showAddProviderDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_cron_jobs)) },
                                onClick = { showMenu = false; viewModel.onLoadCronJobs(); showCronDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_settings)) },
                                onClick = { showMenu = false; onNavigateToSettings() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Tab bar: Chat / System
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chat") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("System")
                            if (systemMessages.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge { Text("${systemMessages.size}") }
                            }
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                // Chat tab
                if (isThinking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isStreaming) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                MessageList(
                    messages = messages,
                    speakingMessageId = speakingMessageId,
                    onToggleSpeak = viewModel::onToggleSpeakMessage,
                    modifier = Modifier.weight(1f)
                )
                // Info bar: ctx + TTFT + model
                if (contextInfo.isNotBlank() || ttft != null || currentModel.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (contextInfo.isNotBlank()) {
                            Text(
                                text = "CTX: $contextInfo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (ttft != null) {
                            Text(
                                text = "TTFT: ${ttft}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentModel.isNotBlank()) {
                            Text(
                                text = currentModel.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                ChatInput(
                    recognitionState = recognitionState,
                    onSendMessage = viewModel::onSendMessage,
                    onToggleVoice = viewModel::onToggleVoiceInput
                )
            } else {
                // System tab
                if (systemMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak wiadomości systemowych",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MessageList(
                        messages = systemMessages,
                        speakingMessageId = null,
                        onToggleSpeak = { _, _ -> },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showModelMenu) {
        ModelConfigDialog(
            modelConfig = modelConfig,
            availableModels = availableModels,
            isSaving = configSaving,
            onSetPrimary = viewModel::onSetPrimaryModel,
            onReorder = viewModel::onReorderModels,
            onAddFallback = viewModel::onAddFallback,
            onRemoveFallback = viewModel::onRemoveFallback,
            onDismiss = { showModelMenu = false }
        )
    }

    if (showCronDialog) {
        CronJobsDialog(
            jobs = cronJobs,
            onDismiss = { showCronDialog = false }
        )
    }

    val allModels by viewModel.allModels.collectAsState()

    if (showAddProviderDialog) {
        AddProviderDialog(
            availableModels = allModels,
            configuredProviders = configuredProviders,
            onAddProvider = { provider, apiKey ->
                viewModel.onAddProviderKey(provider, apiKey)
                showAddProviderDialog = false
            },
            onDismiss = { showAddProviderDialog = false }
        )
    }
}

@Composable
private fun CronJobsDialog(
    jobs: List<CronJobInfo>,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_cron_jobs)) },
        text = {
            if (jobs.isEmpty()) {
                Text(stringResource(R.string.cron_no_jobs))
            } else {
                LazyColumn {
                    items(jobs, key = { it.id }) { job ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = job.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = job.schedule,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (job.enabled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (job.enabled) stringResource(R.string.cron_active) else stringResource(R.string.cron_disabled),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (job.lastRun != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.cron_last_run, job.lastRun ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val color = when (state) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting, is ConnectionState.Reconnecting -> MaterialTheme.colorScheme.tertiary
        is ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
        is ConnectionState.Error -> MaterialTheme.colorScheme.error
    }
    val label = when (state) {
        is ConnectionState.Connected -> stringResource(R.string.connection_connected)
        is ConnectionState.Connecting -> stringResource(R.string.connection_connecting)
        is ConnectionState.Reconnecting -> stringResource(R.string.connection_reconnecting)
        is ConnectionState.Disconnected -> stringResource(R.string.connection_disconnected)
        is ConnectionState.Error -> state.reason
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ModelConfigDialog(
    modelConfig: ModelConfig,
    availableModels: List<ModelInfo>,
    isSaving: Boolean = false,
    onSetPrimary: (String) -> Unit,
    onReorder: (String, List<String>) -> Unit,
    onAddFallback: (String) -> Unit,
    onRemoveFallback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // All active model keys (primary + fallbacks)
    val activeKeys = remember(modelConfig) {
        buildList {
            if (modelConfig.primary.isNotBlank()) add(modelConfig.primary)
            addAll(modelConfig.fallbacks)
        }
    }

    fun modelName(key: String): String {
        val model = availableModels.find { it.key == key }
        return model?.let { "${it.provider}/${it.name}" } ?: key
    }

    fun swapFallback(index: Int, direction: Int) {
        val list = modelConfig.fallbacks.toMutableList()
        val newIndex = index + direction
        if (newIndex in list.indices) {
            val tmp = list[index]
            list[index] = list[newIndex]
            list[newIndex] = tmp
            onReorder(modelConfig.primary, list)
        }
    }

    fun promoteToPrimary(fallbackKey: String) {
        val newFallbacks = buildList {
            add(modelConfig.primary)
            addAll(modelConfig.fallbacks.filter { it != fallbackKey })
        }.filter { it.isNotBlank() }
        onReorder(fallbackKey, newFallbacks)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_config_title)) },
        text = {
            LazyColumn {
                if (isSaving) {
                    item(key = "saving_indicator") {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                }
                // Primary model
                item(key = "primary_header") {
                    Text(
                        text = stringResource(R.string.model_primary),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                item(key = "primary_model") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (modelConfig.primary.isNotBlank()) modelName(modelConfig.primary) else stringResource(R.string.model_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Fallbacks header
                item(key = "fallback_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.model_fallbacks, modelConfig.fallbacks.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.model_add_fallback), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (modelConfig.fallbacks.isEmpty()) {
                    item(key = "no_fallbacks") {
                        Text(
                            text = stringResource(R.string.model_no_fallbacks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                // Fallback list
                items(modelConfig.fallbacks.size, key = { "fb_${modelConfig.fallbacks[it]}" }) { index ->
                    val fbKey = modelConfig.fallbacks[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = modelName(fbKey),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Set as primary
                        IconButton(onClick = { promoteToPrimary(fbKey) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.model_set_primary), modifier = Modifier.size(16.dp))
                        }
                        // Move up
                        IconButton(
                            onClick = { swapFallback(index, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.model_move_up), modifier = Modifier.size(16.dp))
                        }
                        // Move down
                        IconButton(
                            onClick = { swapFallback(index, 1) },
                            enabled = index < modelConfig.fallbacks.size - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.model_move_down), modifier = Modifier.size(16.dp))
                        }
                        // Remove
                        IconButton(onClick = { onRemoveFallback(fbKey) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.model_remove), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    // Add fallback dialog - shows available models not yet in config
    if (showAddDialog) {
        val unusedModels = availableModels.filter { it.key !in activeKeys }
        val groupedModels = unusedModels.groupBy { it.provider }
        var expandedProvider by remember { mutableStateOf<String?>(null) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.model_add_fallback)) },
            text = {
                if (unusedModels.isEmpty()) {
                    Text(stringResource(R.string.model_all_configured))
                } else {
                    LazyColumn {
                        groupedModels.forEach { (provider, models) ->
                            item(key = "add_header_$provider") {
                                TextButton(
                                    onClick = { expandedProvider = if (expandedProvider == provider) null else provider },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${if (expandedProvider == provider) "▾" else "▸"} $provider (${models.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (expandedProvider == provider) {
                                items(models.size, key = { "add_${models[it].key}" }) { i ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                models[i].name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                        },
                                        onClick = {
                                            onAddFallback(models[i].key)
                                            showAddDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun AddProviderDialog(
    availableModels: List<ModelInfo>,
    configuredProviders: Set<String>,
    onAddProvider: (provider: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Get all known providers from models list, minus already configured
    val allProviders = remember(availableModels) {
        availableModels.map { it.provider }.distinct().sorted()
    }
    val unconfiguredProviders = remember(allProviders, configuredProviders) {
        allProviders.filter { it !in configuredProviders }
    }

    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_title)) },
        text = {
            Column {
                if (selectedProvider == null) {
                    if (unconfiguredProviders.isEmpty()) {
                        Text("All providers are already configured")
                    } else {
                        Text(
                            text = "Select a provider to add:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn {
                            items(unconfiguredProviders.size, key = { unconfiguredProviders[it] }) { i ->
                                val provider = unconfiguredProviders[i]
                                val modelCount = availableModels.count { it.provider == provider }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedProvider = provider }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = provider,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$modelCount models",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                } else {
                    Text(
                        text = selectedProvider!!,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    val providerModels = availableModels.filter { it.provider == selectedProvider }
                    Text(
                        text = "Models: ${providerModels.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.provider_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (selectedProvider != null) {
                TextButton(
                    onClick = { onAddProvider(selectedProvider!!, apiKey) },
                    enabled = apiKey.isNotBlank()
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedProvider != null) {
                    selectedProvider = null
                    apiKey = ""
                } else {
                    onDismiss()
                }
            }) {
                Text(if (selectedProvider != null) stringResource(R.string.back) else stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MessageList(
    messages: List<Message>,
    speakingMessageId: String?,
    onToggleSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                isSpeaking = speakingMessageId == message.id,
                onToggleSpeak = { onToggleSpeak(message.id, message.content) }
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isSpeaking: Boolean = false, onToggleSpeak: (() -> Unit)? = null) {
    val isUser = message.sender == Sender.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isUser) 12.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 12.dp
    )

    val smallerTypography = markdownTypography(
        h1 = MaterialTheme.typography.titleMedium,
        h2 = MaterialTheme.typography.titleSmall,
        h3 = MaterialTheme.typography.labelLarge,
        h4 = MaterialTheme.typography.labelMedium,
        h5 = MaterialTheme.typography.labelSmall,
        h6 = MaterialTheme.typography.labelSmall,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .then(if (isUser) Modifier.widthIn(max = 280.dp) else Modifier.fillMaxWidth())
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (message.status == MessageStatus.SENDING) {
                    // Plain text during streaming to avoid re-render flicker
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Markdown(
                        content = message.content,
                        typography = smallerTypography,
                    )
                }
                if (!isUser && message.status != MessageStatus.SENDING && onToggleSpeak != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.chat_speak_message),
                                modifier = Modifier.size(16.dp),
                                tint = if (isSpeaking) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    recognitionState: RecognitionState,
    onSendMessage: (String) -> Unit,
    onToggleVoice: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isListening = recognitionState is RecognitionState.Listening

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                }
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onToggleVoice,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer
                )
        ) {
            if (isListening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.chat_voice_input)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSendMessage(text)
                    text = ""
                }
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
