package com.example.floatdraw.sync

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import okhttp3.*
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.min

class WebSocketManager(private val listener: WebSocketListenerCallback) {

    interface WebSocketListenerCallback {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onMessageReceived(message: SyncMessage)
        fun onReconnected()
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()

    private var webSocket: WebSocket? = null
    private var isExplicitDisconnect = false
    private var currentUrl: String? = null
    private var currentSessionId: String? = null
    private var currentDeviceId: String? = null

    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set(value) {
            field = value
            mainHandler.post { listener.onConnectionStateChanged(value) }
        }

    private var reconnectAttempt = 0
    private var isReconnecting = false
    private val reconnectRunnable = Runnable { attemptReconnect() }

    fun connect(serverUrl: String, sessionId: String, deviceId: String) {
        disconnectInternal(resetSession = false)
        isExplicitDisconnect = false
        currentUrl = serverUrl
        currentSessionId = sessionId
        currentDeviceId = deviceId
        reconnectAttempt = 0
        isReconnecting = false

        connectionState = ConnectionState.CONNECTING
        doConnect()
    }

    private fun doConnect() {
        val baseUrl = currentUrl ?: return
        val sessionId = currentSessionId ?: return
        val deviceId = currentDeviceId ?: return

        val wsUrl = buildWsUrl(baseUrl, sessionId, deviceId)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val wasReconnecting = isReconnecting
                reconnectAttempt = 0
                isReconnecting = false
                connectionState = ConnectionState.CONNECTED
                if (wasReconnecting) {
                    mainHandler.post { listener.onReconnected() }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, SyncMessage::class.java)
                    if (msg != null) {
                        mainHandler.post { listener.onMessageReceived(msg) }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        webSocket = null
        if (isExplicitDisconnect) {
            connectionState = ConnectionState.DISCONNECTED
            return
        }

        connectionState = ConnectionState.RECONNECTING
        isReconnecting = true
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempt++
        val baseDelay = 1000L
        val maxDelay = 30000L
        val exponential = (baseDelay * Math.pow(2.0, (reconnectAttempt - 1).coerceAtMost(5).toDouble())).toLong()
        val delay = min(maxDelay, exponential) + random.nextInt(1000)

        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun attemptReconnect() {
        if (!isExplicitDisconnect && currentUrl != null) {
            doConnect()
        }
    }

    fun sendMessage(msg: SyncMessage): Boolean {
        val ws = webSocket ?: return false
        val json = gson.toJson(msg)
        return ws.send(json)
    }

    fun disconnect() {
        disconnectInternal(resetSession = true)
    }

    private fun disconnectInternal(resetSession: Boolean) {
        isExplicitDisconnect = true
        mainHandler.removeCallbacks(reconnectRunnable)
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        connectionState = ConnectionState.DISCONNECTED

        if (resetSession) {
            currentUrl = null
            currentSessionId = null
            currentDeviceId = null
        }
    }

    private fun buildWsUrl(serverUrl: String, sessionId: String, deviceId: String): String {
        var base = serverUrl.trim()
        if (base.startsWith("http://")) {
            base = "ws://" + base.substring(7)
        } else if (base.startsWith("https://")) {
            base = "wss://" + base.substring(8)
        } else if (!base.startsWith("ws://") && !base.startsWith("wss://")) {
            base = "ws://$base"
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length - 1)
        }
        return "$base/ws/draw?sessionId=$sessionId&deviceId=$deviceId"
    }
}
