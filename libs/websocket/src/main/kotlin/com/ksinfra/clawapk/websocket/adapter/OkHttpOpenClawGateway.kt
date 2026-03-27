package com.ksinfra.clawapk.websocket.adapter

import com.ksinfra.clawapk.common.CoroutineDispatchers
import com.ksinfra.clawapk.common.generateId
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
    dispatchers: CoroutineDispatchers
) : OpenClawGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
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
        })
    }

    override suspend fun listSessions(): Result<List<Session>> {
        return sendRequest("sessions.list", null).map { responseText ->
            // Parse sessions from response - simplified
            emptyList()
        }
    }

    private suspend fun sendRequest(method: String, params: JsonObject?): Result<String> {
        val ws = webSocket ?: return Result.failure(IllegalStateException("Not connected"))
        val id = generateId()
        val frame = RequestFrame(id = id, method = method, params = params)
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        val frameJson = json.encodeToString(RequestFrame.serializer(), frame)
        ws.send(frameJson)

        return try {
            val response = deferred.await()
            if (response.ok) {
                Result.success(response.payload?.toString() ?: "")
            } else {
                Result.failure(
                    RuntimeException(response.error?.message ?: "Unknown error")
                )
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Result.failure(e)
        }
    }

    private fun doConnect(config: ConnectionConfig) {
        _connectionState.value = ConnectionState.Connecting

        val wsUrl = config.serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .let { if (!it.startsWith("ws")) "wss://$it" else it }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val connectParams = buildConnectParams(config)
                val paramsJson = json.encodeToString(ConnectParams.serializer(), connectParams)

                val handshakeFrame = RequestFrame(
                    id = generateId(),
                    method = "connect",
                    params = json.parseToJsonElement(paramsJson)
                )
                webSocket.send(json.encodeToString(RequestFrame.serializer(), handshakeFrame))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        val element = json.parseToJsonElement(text).jsonObject
        val type = element["type"]?.jsonPrimitive?.content

        when (type) {
            "hello-ok" -> {
                val connId = element["server"]?.jsonObject?.get("connId")?.jsonPrimitive?.content ?: ""
                _connectionState.value = ConnectionState.Connected(connId)
            }
            "res" -> {
                val response = json.decodeFromString(ResponseFrame.serializer(), text)
                pendingRequests.remove(response.id)?.complete(response)
            }
            "event" -> {
                val event = json.decodeFromString(EventFrame.serializer(), text)
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(frame: EventFrame) {
        val openClawEvent = when (frame.event) {
            "agent" -> {
                val text = frame.payload?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                val sessionId = frame.payload?.jsonObject?.get("sessionId")?.jsonPrimitive?.content
                OpenClawEvent.AgentResponse(text, sessionId)
            }
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
            "tick" -> OpenClawEvent.Tick(System.currentTimeMillis())
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

    private fun buildConnectParams(config: ConnectionConfig): ConnectParams {
        val authParams = when (val mode = config.authMode) {
            is AuthMode.Token -> AuthParams(token = mode.token)
            is AuthMode.Password -> AuthParams(password = mode.password)
            is AuthMode.DeviceToken -> AuthParams(deviceToken = mode.token)
            is AuthMode.DevicePairing -> AuthParams()
            is AuthMode.None -> null
        }

        return ConnectParams(
            client = ClientInfo(),
            auth = authParams
        )
    }

    private fun parseActions(element: JsonElement?): Set<CronAction> {
        if (element == null) return setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)

        return try {
            val array = element.jsonArray
            array.mapNotNull { item ->
                when (item.jsonPrimitive.content.lowercase()) {
                    "notify" -> CronAction.NOTIFY
                    "speak" -> CronAction.SPEAK
                    "sound", "play_sound" -> CronAction.PLAY_SOUND
                    "vibrate" -> CronAction.VIBRATE
                    "all" -> null // handled below
                    else -> null
                }
            }.toSet().ifEmpty {
                // If "all" was in the list or array was empty
                setOf(CronAction.NOTIFY, CronAction.SPEAK, CronAction.VIBRATE)
            }
        } catch (_: Exception) {
            // If it's a single string instead of array
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
        private const val RECONNECT_DELAY_MS = 5000L
    }
}
