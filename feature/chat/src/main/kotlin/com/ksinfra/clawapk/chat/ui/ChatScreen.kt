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
import androidx.compose.material3.Scaffold
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
import com.ksinfra.clawapk.chat.R
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
    val cronJobs by viewModel.cronJobs.collectAsState()
    val ttft by viewModel.ttft.collectAsState()
    val contextInfo by viewModel.contextInfo.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showCronDialog by remember { mutableStateOf(false) }

    Scaffold(
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
                                text = { Text("Change Model") },
                                onClick = { showMenu = false; showModelMenu = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Cron Jobs") },
                                onClick = { showMenu = false; viewModel.onLoadCronJobs(); showCronDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
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
        }
    }

    if (showModelMenu) {
        ModelConfigDialog(
            modelConfig = modelConfig,
            availableModels = availableModels,
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
}

@Composable
private fun CronJobsDialog(
    jobs: List<CronJobInfo>,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cron Jobs") },
        text = {
            if (jobs.isEmpty()) {
                Text("No cron jobs configured")
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
                                    text = if (job.enabled) "Active" else "Disabled",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (job.lastRun != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Last: ${job.lastRun}",
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
                Text("Close")
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
        title = { Text("Model Configuration") },
        text = {
            LazyColumn {
                // Primary model
                item(key = "primary_header") {
                    Text(
                        text = "Primary",
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
                            text = if (modelConfig.primary.isNotBlank()) modelName(modelConfig.primary) else "Not set",
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
                            text = "Fallbacks (${modelConfig.fallbacks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add fallback", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (modelConfig.fallbacks.isEmpty()) {
                    item(key = "no_fallbacks") {
                        Text(
                            text = "No fallbacks configured",
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
                            Icon(Icons.Default.Star, contentDescription = "Set primary", modifier = Modifier.size(16.dp))
                        }
                        // Move up
                        IconButton(
                            onClick = { swapFallback(index, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                        }
                        // Move down
                        IconButton(
                            onClick = { swapFallback(index, 1) },
                            enabled = index < modelConfig.fallbacks.size - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                        }
                        // Remove
                        IconButton(onClick = { onRemoveFallback(fbKey) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    // Add fallback dialog - shows available models not yet in config
    if (showAddDialog) {
        val unusedModels = availableModels.filter { it.key !in activeKeys }
        val groupedModels = unusedModels.groupBy { it.provider }
        var expandedProvider by remember { mutableStateOf<String?>(null) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Fallback") },
            text = {
                if (unusedModels.isEmpty()) {
                    Text("All available models are already configured")
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
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
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
            MessageBubble(message)
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (message.status == MessageStatus.SENDING && message.sender == Sender.AGENT) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
