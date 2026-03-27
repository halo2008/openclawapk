package com.ksinfra.clawapk.websocket.adapter

import com.ksinfra.clawapk.common.CoroutineDispatchers
import com.ksinfra.clawapk.common.generateId
import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.model.AuthMode
import com.ksinfra.clawapk.domain.model.ConnectionConfig
import com.ksinfra.clawapk.domain.model.ConnectionState
import com.ksinfra.clawapk.domain.model.CronAction
import com.ksinfra.clawapk.domain.model.CronEvent
import com.ksinfra.clawapk.domain.model.OpenClawEvent
import com.ksinfra.clawapk.domain.model.Session
import com.ksinfra.clawapk.domain.port.OpenClawGateway
import com.ksinfra.clawapk.websocket.model.AuthParams
import com.ksinfra.clawapk.websocket.model.ClientInfo
import com.ksinfra.clawapk.websocket.model.ConnectParams
import com.ksinfra.clawapk.websocket.model.EventFrame
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

    override suspend fun connect(config: ConnectionConfig) {
        currentConfig = config
        shouldReconnect = true
        reconnectJob?.cancel()
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

    override suspend fun setTtsProvider(provider: String): Result<String> {
        return sendRequest("config.patch", buildJsonObject {
            put("patch", buildJsonObject {
                put("talk", buildJsonObject {
                    put("provider", provider)
                })
            })
        })
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

    private suspend fun sendRequest(method: String, params: JsonObject?): Result<String> {
        val ws = webSocket ?: return Result.failure(IllegalStateException("Not connected"))
        val id = generateId()
        val frame = RequestFrame(id = id, method = method, params = params)
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        ws.send(json.encodeToString(RequestFrame.serializer(), frame))

        return try {
            val response = deferred.await()
            if (response.ok) {
                Result.success(response.payload?.toString() ?: "")
            } else {
                Result.failure(RuntimeException(response.error?.message ?: "Unknown error"))
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Result.failure(e)
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

        if (config.authMode is AuthMode.CloudflareAccess) {
            val cookie = (config.authMode as AuthMode.CloudflareAccess).cfCookie
            if (cookie.isNotBlank()) {
                requestBuilder.addHeader("Cookie", "CF_Authorization=$cookie")
            }
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

        val useDevicePairing = config.authMode is AuthMode.DevicePairing || config.authMode is AuthMode.None

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

            if (useDevicePairing) {
                val keys = deviceIdentity.getOrCreateKeys()
                val signedAt = System.currentTimeMillis()
                val signPayload = buildSignPayload(
                    deviceId = keys.deviceId,
                    clientId = clientInfo.id,
                    clientMode = clientInfo.mode,
                    role = role,
                    scopes = scopes,
                    signedAtMs = signedAt,
                    token = null,
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
            }

            put("caps", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("tool-events"))))

            val authParams = when (val mode = config.authMode) {
                is AuthMode.Token -> AuthParams(token = mode.token)
                is AuthMode.Password -> AuthParams(password = mode.password)
                is AuthMode.DeviceToken -> AuthParams(deviceToken = mode.token)
                else -> null
            }
            if (authParams != null) {
                put("auth", buildJsonObject {
                    authParams.token?.let { put("token", it) }
                    authParams.password?.let { put("password", it) }
                    authParams.deviceToken?.let { put("deviceToken", it) }
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
        android.util.Log.d(TAG, "Sending connect auth=${config.authMode::class.simpleName} devicePairing=$useDevicePairing")
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
            "agent" -> null // Skip streaming deltas, we use "chat" final instead
            "cron" -> {
                val payload = frame.payload?.jsonObject
                val actions = parseActions(payload?.get("actions"))
                OpenClawEvent.CronFired(
                    CronEvent(
                        jobId = payload?.get("jobId")?.jsonPrimitive?.content ?: "",
                        jobName = payload?.get("jobName")?.jsonPrimitive?.content ?: "",
                        message = payload?.get("message")?.jsonPrimitive?.content,
                        actions = actions
                    )
                )
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
        if (element == null) return setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)

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
                setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)
            }
        } catch (_: Exception) {
            try {
                when (element.jsonPrimitive.content.lowercase()) {
                    "notify" -> setOf(CronAction.NOTIFY)
                    "speak" -> setOf(CronAction.SPEAK)
                    "sound", "play_sound" -> setOf(CronAction.PLAY_SOUND)
                    "vibrate" -> setOf(CronAction.VIBRATE)
                    else -> setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)
                }
            } catch (_: Exception) {
                setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)
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
    }
}
