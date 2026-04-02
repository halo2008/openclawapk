package com.ksinfra.clawapk.websocket.adapter

import com.ksinfra.clawapk.common.CoroutineDispatchers
import com.ksinfra.clawapk.common.generateId
import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.AuthMode
import com.ksinfra.clawapk.domain.model.ChatHistoryMessage
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.CronAction
import com.ksinfra.clawapk.domain.model.CronEvent
import com.ksinfra.clawapk.domain.model.CronJobInfo
import com.ksinfra.clawapk.domain.model.ModelInfo
import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.model.Session
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.websocket.model.AuthParams
import com.ksinfra.clawapk.websocket.model.ClientInfo
import com.ksinfra.clawapk.websocket.model.ConnectParams
import com.ksinfra.clawapk.websocket.model.EventFrame
import com.ksinfra.clawapk.websocket.model.FrameError
import com.ksinfra.clawapk.websocket.model.RequestFrame
import com.ksinfra.clawapk.websocket.model.ResponseFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OkHttpOpenClawGateway(
    dispatchers: CoroutineDispatchers,
    private val deviceIdentity: DeviceIdentity
) : OpenClawGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(dispatchers.io + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<OpenClawEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<OpenClawEvent> = _events.asSharedFlow()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ResponseFrame>>()
    private var webSocket: WebSocket? = null
    private var currentConfig: ConnectionConfig? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false
    private var connectNonce: String? = null
    private var connectSent = false
    @Volatile private var configBaseHash: String? = null

    override suspend fun connect(config: ConnectionConfig) {
        currentConfig = config
        shouldReconnect = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        doConnect(config)
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(message: String): Result<String> {
        return sendRequest("agent", buildJsonObject {
            put("message", message)
            put("idempotencyKey", generateId())
            put("agentId", "main")
        })
    }

    override suspend fun listSessions(): Result<List<Session>> {
        return sendRequest("sessions.list", null).map { emptyList() }
    }

    private suspend fun fetchConfig(): Result<JsonObject> {
        return sendRequest("config.get", buildJsonObject {}).mapCatching { responseJson ->
            val root = json.parseToJsonElement(responseJson).jsonObject
            // Store hash for config.patch
            root["hash"]?.jsonPrimitive?.content?.let { configBaseHash = it }
            root["_hash"]?.jsonPrimitive?.content?.let { configBaseHash = it }
            root["baseHash"]?.jsonPrimitive?.content?.let { configBaseHash = it }
            android.util.Log.d(TAG, "config.get hash=$configBaseHash keys=${root.keys}")
            root["config"]?.jsonObject ?: root
        }
    }

    private suspend fun patchConfig(raw: JsonObject): Result<String> {
        val maxRetries = 3
        var lastError: Throwable? = null

        repeat(maxRetries) { attempt ->
            // Always fetch fresh hash before each attempt
            fetchConfig()

            val params = buildJsonObject {
                put("raw", raw.toString())
                configBaseHash?.let { put("baseHash", it) }
            }

            val response = sendRequestRaw("config.patch", params)
            if (response.ok) {
                fetchConfig()
                return Result.success(response.payload?.toString() ?: "")
            }

            val error = response.error
            val msg = error?.message ?: "Unknown error"
            lastError = RuntimeException(msg)

            // Rate limit — wait the requested time and retry
            val retryMs = error?.retryAfterMs
            if (retryMs != null && retryMs > 0) {
                android.util.Log.w(TAG, "config.patch rate-limited, waiting ${retryMs}ms (attempt ${attempt + 1}/$maxRetries)")
                delay(retryMs + 500)
                return@repeat
            }

            // EBUSY / transient — short backoff and retry
            if (msg.contains("EBUSY") || msg.contains("resource busy") || error?.retryable == true) {
                val backoff = (attempt + 1) * 2000L
                android.util.Log.w(TAG, "config.patch EBUSY/retryable, waiting ${backoff}ms (attempt ${attempt + 1}/$maxRetries)")
                delay(backoff)
                return@repeat
            }

            // Hash mismatch — re-fetch and retry immediately
            if (msg.contains("config changed since last load") || msg.contains("base hash")) {
                android.util.Log.w(TAG, "config.patch hash mismatch, re-fetching (attempt ${attempt + 1}/$maxRetries)")
                return@repeat
            }

            // Non-retryable error — fail immediately
            return Result.failure(lastError!!)
        }

        return Result.failure(lastError ?: RuntimeException("config.patch failed after $maxRetries attempts"))
    }

    override suspend fun setTtsProvider(provider: String): Result<String> {
        return patchConfig(buildJsonObject {
            put("talk", buildJsonObject {
                put("provider", provider)
            })
        })
    }

    override suspend fun getChatHistory(): Result<List<ChatHistoryMessage>> {
        return sendRequest("chat.history", buildJsonObject {
            put("sessionKey", "agent:main:main")
        }).mapCatching { responseJson ->
            val element = json.parseToJsonElement(responseJson)
            val messages = element.jsonObject["messages"]?.jsonArray
                ?: element.jsonArray
                ?: return@mapCatching emptyList()
            messages.mapNotNull { msg ->
                val obj = msg.jsonObject
                val role = obj["role"]?.jsonPrimitive?.content ?: return@mapNotNull null
                // Only show user and assistant messages
                if (role != "user" && role != "assistant") return@mapNotNull null
                val content = obj["content"]?.jsonArray?.mapNotNull { block ->
                    val blockObj = block.jsonObject
                    if (blockObj["type"]?.jsonPrimitive?.content == "text") {
                        blockObj["text"]?.jsonPrimitive?.content
                    } else null
                }?.joinToString("\n") ?: return@mapNotNull null
                if (content.isBlank()) return@mapNotNull null
                // Filter out system/cron injected messages
                if (isSystemMessage(content)) return@mapNotNull null
                ChatHistoryMessage(role = role, content = content)
            }
        }
    }

    override suspend fun resetSession(): Result<String> {
        return sendRequest("sessions.reset", buildJsonObject {
            put("key", "agent:main:main")
        })
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return sendRequest("models.list", null).mapCatching { responseJson ->
            android.util.Log.d(TAG, "models.list response: ${responseJson.take(500)}")
            val element = json.parseToJsonElement(responseJson)
            val models = element.jsonObject["models"]?.jsonArray
                ?: element.jsonObject["data"]?.jsonArray
                ?: return@mapCatching emptyList()
            models.mapNotNull { model ->
                val obj = model.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: id
                val provider = obj["provider"]?.jsonPrimitive?.content ?: ""
                val key = if (provider.isNotBlank()) "$provider/$id" else id
                ModelInfo(key = key, name = name, provider = provider)
            }
        }
    }

    override suspend fun setDefaultModel(modelKey: String): Result<String> {
        return patchConfig(buildJsonObject {
            put("agents", buildJsonObject {
                put("defaults", buildJsonObject {
                    put("model", modelKey)
                })
            })
        })
    }

    override suspend fun getModelConfig(): Result<com.ksinfra.clawapk.domain.model.ModelConfig> {
        return fetchConfig().mapCatching { configRoot ->
            val modelElement = configRoot["agents"]?.jsonObject
                ?.get("defaults")?.jsonObject
                ?.get("model")
            android.util.Log.d(TAG, "modelElement: $modelElement")

            if (modelElement == null) {
                com.ksinfra.clawapk.domain.model.ModelConfig(primary = "", fallbacks = emptyList())
            } else {
                try {
                    val primary = modelElement.jsonPrimitive.content
                    com.ksinfra.clawapk.domain.model.ModelConfig(primary = primary, fallbacks = emptyList())
                } catch (_: Exception) {
                    val obj = modelElement.jsonObject
                    val primary = obj["primary"]?.jsonPrimitive?.content ?: ""
                    val fallbacks = obj["fallbacks"]?.jsonArray?.mapNotNull {
                        it.jsonPrimitive.content
                    } ?: emptyList()
                    com.ksinfra.clawapk.domain.model.ModelConfig(primary = primary, fallbacks = fallbacks)
                }
            }
        }
    }

    override suspend fun setModelConfig(config: com.ksinfra.clawapk.domain.model.ModelConfig): Result<String> {
        return patchConfig(buildJsonObject {
            put("agents", buildJsonObject {
                put("defaults", buildJsonObject {
                    if (config.fallbacks.isEmpty()) {
                        put("model", config.primary)
                    } else {
                        put("model", buildJsonObject {
                            put("primary", config.primary)
                            put("fallbacks", JsonArray(config.fallbacks.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                        })
                    }
                })
            })
        })
    }

    override suspend fun setProviderApiKey(provider: String, apiKey: String): Result<String> {
        return patchConfig(buildJsonObject {
            put("models", buildJsonObject {
                put("providers", buildJsonObject {
                    put(provider, buildJsonObject {
                        put("apiKey", apiKey)
                    })
                })
            })
        })
    }

    override suspend fun getConfiguredProviders(): Result<Set<String>> {
        return fetchConfig().mapCatching { configRoot ->
            val providers = configRoot["models"]?.jsonObject?.get("providers")?.jsonObject
            providers?.keys ?: emptySet()
        }
    }

    override suspend fun getConfig(path: String): Result<String> {
        return fetchConfig().mapCatching { configRoot ->
            var current: kotlinx.serialization.json.JsonElement = configRoot
            for (key in path.split(".")) {
                current = (current as? JsonObject)?.get(key) ?: return@mapCatching ""
            }
            try {
                current.jsonPrimitive.content
            } catch (_: Exception) {
                current.toString()
            }
        }
    }

    override suspend fun listCronJobs(): Result<List<CronJobInfo>> {
        return sendRequest("cron.list", null).mapCatching { responseJson ->
            android.util.Log.d(TAG, "cron.list response: ${responseJson.take(500)}")
            val element = json.parseToJsonElement(responseJson)
            val jobs = element.jsonObject["jobs"]?.jsonArray
                ?: element.jsonArray
                ?: return@mapCatching emptyList()
            jobs.mapNotNull { job ->
                val obj = job.jsonObject
                val scheduleElement = obj["schedule"]
                val scheduleText = try {
                    // schedule can be a string or an object with expr/tz
                    scheduleElement?.jsonPrimitive?.content
                } catch (_: Exception) {
                    val schedObj = scheduleElement?.jsonObject
                    val expr = schedObj?.get("expr")?.jsonPrimitive?.content ?: ""
                    val tz = schedObj?.get("tz")?.jsonPrimitive?.content
                    if (tz != null) "$expr ($tz)" else expr
                } ?: ""
                val state = obj["state"]?.jsonObject
                val lastRunMs = state?.get("lastRunAtMs")?.jsonPrimitive?.content?.toLongOrNull()
                val lastRunText = lastRunMs?.let {
                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                }
                val lastStatus = state?.get("lastStatus")?.jsonPrimitive?.content
                CronJobInfo(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "",
                    schedule = scheduleText,
                    enabled = obj["enabled"]?.jsonPrimitive?.content?.toBoolean() != false,
                    lastRun = if (lastRunText != null) "$lastRunText ($lastStatus)" else null
                )
            }
        }
    }

    override suspend fun ttsConvert(text: String): Result<AudioData> {
        return sendRequest("talk.speak", buildJsonObject {
            put("text", text)
        }).mapCatching { responseJson ->
            val element = json.parseToJsonElement(responseJson).jsonObject
            val audioBase64 = element["audioBase64"]?.jsonPrimitive?.content
                ?: throw RuntimeException("No audioBase64 in TTS response")
            val mimeType = element["mimeType"]?.jsonPrimitive?.content ?: "audio/mp3"
            val bytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
            AudioData(bytes = bytes, mimeType = mimeType)
        }
    }

    private suspend fun sendRequestRaw(method: String, params: JsonObject?): ResponseFrame {
        val ws = webSocket ?: return ResponseFrame(id = "", ok = false, error = FrameError(code = "DISCONNECTED", message = "Not connected"))
        val id = generateId()
        val frame = RequestFrame(id = id, method = method, params = params)
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        ws.send(json.encodeToString(RequestFrame.serializer(), frame))

        return try {
            deferred.await()
        } catch (e: Exception) {
            pendingRequests.remove(id)
            ResponseFrame(id = id, ok = false, error = FrameError(code = "EXCEPTION", message = e.message ?: "Unknown error"))
        }
    }

    private suspend fun sendRequest(method: String, params: JsonObject?): Result<String> {
        val response = sendRequestRaw(method, params)
        return if (response.ok) {
            Result.success(response.payload?.toString() ?: "")
        } else {
            Result.failure(RuntimeException(response.error?.message ?: "Unknown error"))
        }
    }

    private fun doConnect(config: ConnectionConfig) {
        _connectionState.value = ConnectionState.Connecting
        connectNonce = null
        connectSent = false
        android.util.Log.d(TAG, "Connecting to: ${config.serverUrl} auth=${config.authMode::class.simpleName}")

        val wsUrl = config.serverUrl.trim()
            .let { it.replace(Regex("^\\w+://"), "") }  // strip any scheme
            .let { "wss://$it" }                         // always use wss

        val requestBuilder = Request.Builder().url(wsUrl)

        if (config.cfCookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", "CF_Authorization=${config.cfCookie}")
        }

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.d(TAG, "WebSocket opened, waiting for challenge...")
                // Don't send connect yet - wait for connect.challenge event
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, webSocket, config)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                _connectionState.value = ConnectionState.Disconnected
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val errorMsg = when (code) {
                    403 -> "Auth expired (Cloudflare 403)"
                    401 -> "Unauthorized (401)"
                    else -> t.message ?: "Connection failed"
                }
                android.util.Log.e(TAG, "WebSocket failure: $errorMsg code=$code", t)
                _connectionState.value = ConnectionState.Error(errorMsg)
                if (shouldReconnect && code != 403) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String, ws: WebSocket, config: ConnectionConfig) {
        android.util.Log.d(TAG, "MSG: ${text.take(200)}")
        val element = json.parseToJsonElement(text).jsonObject
        val type = element["type"]?.jsonPrimitive?.content

        when (type) {
            "hello-ok" -> {
                val connId = element["server"]?.jsonObject?.get("connId")?.jsonPrimitive?.content ?: ""
                android.util.Log.d(TAG, "Connected! connId=$connId")
                _connectionState.value = ConnectionState.Connected(connId)
            }
            "event" -> {
                val event = json.decodeFromString(EventFrame.serializer(), text)
                if (event.event == "connect.challenge") {
                    val nonce = event.payload?.jsonObject?.get("nonce")?.jsonPrimitive?.content ?: ""
                    android.util.Log.d(TAG, "Got challenge nonce=$nonce")
                    connectNonce = nonce
                    sendConnect(ws, config, nonce)
                } else {
                    handleEvent(event)
                }
            }
            "res" -> {
                val response = json.decodeFromString(ResponseFrame.serializer(), text)
                val payloadType = response.payload?.jsonObject?.get("type")?.jsonPrimitive?.content
                if (response.ok && payloadType == "hello-ok") {
                    val connId = response.payload?.jsonObject?.get("server")?.jsonObject?.get("connId")?.jsonPrimitive?.content ?: ""
                    android.util.Log.d(TAG, "Connected! connId=$connId")
                    _connectionState.value = ConnectionState.Connected(connId)
                } else if (!response.ok && response.error != null) {
                    android.util.Log.e(TAG, "Response error: ${response.error.message}")
                }
                pendingRequests.remove(response.id)?.complete(response)
            }
        }
    }

    private fun sendConnect(ws: WebSocket, config: ConnectionConfig, nonce: String) {
        if (connectSent) return
        connectSent = true

        val role = "operator"
        val scopes = listOf("operator.admin", "operator.read", "operator.write", "operator.approvals", "operator.pairing")
        val clientInfo = ClientInfo(
            id = "openclaw-android",
            displayName = "ClawAPK",
            version = "1.0.0",
            platform = "android",
            mode = "ui"
        )

        val connectParams = buildJsonObject {
            put("minProtocol", 3)
            put("maxProtocol", 3)
            put("client", buildJsonObject {
                put("id", clientInfo.id)
                put("displayName", clientInfo.displayName)
                put("version", clientInfo.version)
                put("platform", clientInfo.platform)
                put("mode", clientInfo.mode)
            })
            put("role", role)
            put("scopes", kotlinx.serialization.json.JsonArray(scopes.map { kotlinx.serialization.json.JsonPrimitive(it) }))

            val keys = deviceIdentity.getOrCreateKeys()
            val signedAt = System.currentTimeMillis()
            val signToken = when (config.authMode) {
                is AuthMode.DeviceToken -> (config.authMode as AuthMode.DeviceToken).token
                is AuthMode.Token -> (config.authMode as AuthMode.Token).token
                else -> config.gatewayToken.ifBlank { null }
            }
            val signPayload = buildSignPayload(
                deviceId = keys.deviceId,
                clientId = clientInfo.id,
                clientMode = clientInfo.mode,
                role = role,
                scopes = scopes,
                signedAtMs = signedAt,
                token = signToken,
                nonce = nonce
            )
            val signature = deviceIdentity.sign(signPayload)
            android.util.Log.d(TAG, "Sending connect with device=${keys.deviceId}")
            put("device", buildJsonObject {
                put("id", keys.deviceId)
                put("publicKey", keys.publicKey)
                put("signature", signature)
                put("signedAt", signedAt)
                put("nonce", nonce)
            })

            put("caps", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("tool-events"))))

            val authParams = when (val mode = config.authMode) {
                is AuthMode.Token -> AuthParams(token = mode.token)
                is AuthMode.Password -> AuthParams(password = mode.password)
                is AuthMode.DeviceToken -> AuthParams(deviceToken = mode.token)
                else -> if (config.gatewayToken.isNotBlank()) AuthParams(token = config.gatewayToken) else null
            }
            if (authParams != null) {
                put("auth", buildJsonObject {
                    authParams.token?.let { put("token", it) }
                    authParams.password?.let { put("password", it) }
                    authParams.deviceToken?.let { put("deviceToken", it) }
                    // Send gateway token as bootstrapToken for auto-pairing new devices
                    val bootstrap = config.gatewayToken.ifBlank { null }
                    bootstrap?.let { put("bootstrapToken", it) }
                })
            }
        }

        val frame = buildJsonObject {
            put("type", "req")
            put("id", generateId())
            put("method", "connect")
            put("params", connectParams)
        }

        val frameJson = frame.toString()
        android.util.Log.d(TAG, "Sending connect auth=${config.authMode::class.simpleName}")
        ws.send(frameJson)
    }

    private fun buildSignPayload(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String
    ): String {
        // Must match OpenClaw's _e() function:
        // [v2, deviceId, clientId, clientMode, role, scopes.join(","), signedAtMs, token??"", nonce].join("|")
        return listOf(
            "v2",
            deviceId,
            clientId,
            clientMode,
            role,
            scopes.joinToString(","),
            signedAtMs.toString(),
            token ?: "",
            nonce
        ).joinToString("|")
    }

    private fun handleEvent(frame: EventFrame) {
        if (frame.event == "agent" || frame.event == "chat") {
            val p = frame.payload?.jsonObject
            android.util.Log.w(TAG, "EVENT ${frame.event} sessionKey=${p?.get("sessionKey")} stream=${p?.get("stream")} state=${p?.get("state")} keys=${p?.keys}")
        }
        val openClawEvent = when (frame.event) {
            "chat" -> {
                val payload = frame.payload?.jsonObject
                val state = payload?.get("state")?.jsonPrimitive?.content
                if (state == "final") {
                    val message = payload?.get("message")?.jsonObject
                    val content = message?.get("content")?.jsonArray
                    val text = content?.mapNotNull { block ->
                        val blockObj = block.jsonObject
                        if (blockObj["type"]?.jsonPrimitive?.content == "text") {
                            blockObj["text"]?.jsonPrimitive?.content
                        } else null
                    }?.joinToString("\n") ?: ""
                    val role = message?.get("role")?.jsonPrimitive?.content
                    val sessionKey = payload?.get("sessionKey")?.jsonPrimitive?.content
                    if (role == "assistant" && text.isNotBlank()) {
                        OpenClawEvent.AgentResponse(text, sessionKey)
                    } else null
                } else null
            }
            "agent" -> {
                val payload = frame.payload?.jsonObject
                val stream = payload?.get("stream")?.jsonPrimitive?.content
                val runId = payload?.get("runId")?.jsonPrimitive?.content ?: ""
                val sessionKey = payload?.get("sessionKey")?.jsonPrimitive?.content
                when (stream) {
                    "assistant" -> {
                        val text = payload?.get("data")?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                        if (text.isNotBlank()) OpenClawEvent.AgentStreaming(runId, text, sessionKey) else null
                    }
                    "lifecycle" -> {
                        val data = payload?.get("data")?.jsonObject
                        val phase = data?.get("phase")?.jsonPrimitive?.content
                        when (phase) {
                            "end" -> OpenClawEvent.AgentStreamEnd(runId, sessionKey)
                            "error" -> {
                                val error = data?.get("error")?.jsonPrimitive?.content ?: "Unknown error"
                                OpenClawEvent.AgentError(runId, error, sessionKey)
                            }
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            "cron" -> {
                val payload = frame.payload?.jsonObject
                val cronAction = payload?.get("action")?.jsonPrimitive?.content
                if (cronAction == "finished") {
                    val actions = parseActions(payload?.get("actions"))
                    val jobId = payload?.get("jobId")?.jsonPrimitive?.content ?: ""
                    OpenClawEvent.CronFired(
                        CronEvent(
                            jobId = jobId,
                            jobName = payload?.get("jobName")?.jsonPrimitive?.content ?: jobId,
                            message = payload?.get("summary")?.jsonPrimitive?.content
                                ?: payload?.get("message")?.jsonPrimitive?.content,
                            actions = actions
                        )
                    )
                } else {
                    null
                }
            }
            "tick" -> null // Server heartbeat — ignored to avoid unnecessary processing
            "shutdown" -> OpenClawEvent.Shutdown
            "session.message", "sessions.changed" -> {
                val sessionId = frame.payload?.jsonObject?.get("sessionId")?.jsonPrimitive?.content ?: ""
                OpenClawEvent.SessionChanged(sessionId)
            }
            else -> null
        }

        openClawEvent?.let {
            scope.launch { _events.emit(it) }
        }
    }

    private fun parseActions(element: JsonElement?): Set<CronAction> {
        // Default: only notify — TTS for cron is handled via FCM push ttsMessage
        if (element == null) return setOf(CronAction.NOTIFY)

        return try {
            element.jsonArray.mapNotNull { item ->
                when (item.jsonPrimitive.content.lowercase()) {
                    "notify" -> CronAction.NOTIFY
                    "speak" -> CronAction.SPEAK
                    "sound", "play_sound" -> CronAction.PLAY_SOUND
                    "vibrate" -> CronAction.VIBRATE
                    else -> null
                }
            }.toSet().ifEmpty {
                setOf(CronAction.NOTIFY)
            }
        } catch (_: Exception) {
            try {
                when (element.jsonPrimitive.content.lowercase()) {
                    "notify" -> setOf(CronAction.NOTIFY)
                    "speak" -> setOf(CronAction.SPEAK)
                    "sound", "play_sound" -> setOf(CronAction.PLAY_SOUND)
                    "vibrate" -> setOf(CronAction.VIBRATE)
                    else -> setOf(CronAction.NOTIFY)
                }
            } catch (_: Exception) {
                setOf(CronAction.NOTIFY)
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.Reconnecting
            delay(RECONNECT_DELAY_MS)
            currentConfig?.let { doConnect(it) }
        }
    }

    companion object {
        private const val TAG = "ClawWS"
        private const val RECONNECT_DELAY_MS = 5000L

        private val SYSTEM_PREFIXES = listOf(
            "Read HEARTBEAT.md",
            "Read heartbeat.md",
            "[System]",
            "[system]",
            "⚠️ API rate limit",
            "FailoverError:",
            "Error:",
        )

        private val SYSTEM_CONTAINS = listOf(
            "workspace context). Follow it strictly",
            "model fallback decision",
            "lane task error",
            "embedded run agent end",
            "API rate limit reached",
            "RESOURCE_EXHAUSTED",
            "model switch detected",
        )

        fun isSystemMessage(text: String): Boolean {
            val trimmed = text.trim()
            return SYSTEM_PREFIXES.any { trimmed.startsWith(it) } ||
                trimmed.startsWith("<system-reminder>") ||
                SYSTEM_CONTAINS.any { trimmed.contains(it) }
        }
    }
}
