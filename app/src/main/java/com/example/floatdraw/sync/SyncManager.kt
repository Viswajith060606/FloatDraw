package com.example.floatdraw.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class SyncManager private constructor(context: Context) : WebSocketManager.WebSocketListenerCallback {

    companion object {
        const val PRODUCTION_SERVER_URL = "https://floatdrawserver.onrender.com"

        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    interface SyncEventListener {
        fun onRemoteStrokeStart(strokeId: String, deviceId: String, color: Int, width: Float, alpha: Float, tool: String, normX: Float, normY: Float) {}
        fun onRemoteStrokeStart(strokeId: String, deviceId: String, color: Int, width: Float, normX: Float, normY: Float) {}
        fun onRemoteStrokeUpdate(strokeId: String, normX: Float, normY: Float) {}
        fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float) {}
        fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float, expiresAt: Long) { onRemoteStrokeEnd(strokeId, normX, normY) }
        fun onRemoteClear() {}
        fun onSnapshotReceived(strokes: List<RemoteStroke>) {}
        fun onConnectionStateChanged(state: ConnectionState) {}
        fun onPeerStatusChanged(peerConnected: Boolean, deviceCount: Int) {}
        fun onPartnerNameChanged(partnerName: String) {}
        fun onUserLeft(partnerName: String) {}
        fun onSessionEnded(reason: String) {}
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("floatdraw_sync", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    val webSocketManager = WebSocketManager(this)

    var deviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    var displayName: String = prefs.getString("display_name", "You") ?: "You"
        set(value) {
            val clean = value.trim().take(20).ifEmpty { "You" }
            field = clean
            prefs.edit().putString("display_name", clean).apply()
            sendDisplayNameUpdate()
        }

    var partnerDisplayName: String = "Partner"
        private set

    var serverUrl: String = prefs.getString("server_url", PRODUCTION_SERVER_URL) ?: PRODUCTION_SERVER_URL
        set(value) {
            field = value
            prefs.edit().putString("server_url", value).apply()
        }

    var currentSessionId: String? = prefs.getString("session_id", null)
        private set(value) {
            field = value
            prefs.edit().putString("session_id", value).apply()
        }

    var currentPairingCode: String? = prefs.getString("pairing_code", null)
        private set(value) {
            field = value
            prefs.edit().putString("pairing_code", value).apply()
        }

    var peerConnected: Boolean = false
        private set

    var deviceCount: Int = 1
        private set

    private val listeners = CopyOnWriteArrayList<SyncEventListener>()

    fun registerListener(listener: SyncEventListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: SyncEventListener) {
        listeners.remove(listener)
    }

    fun createSession(callback: (Result<CreateSessionResponse>) -> Unit) {
        createSession(PRODUCTION_SERVER_URL, callback)
    }

    fun createSession(serverUrl: String = PRODUCTION_SERVER_URL, callback: (Result<CreateSessionResponse>) -> Unit) {
        this.serverUrl = serverUrl
        val httpUrl = buildHttpUrl(serverUrl, "/api/session/create")

        val request = Request.Builder()
            .url(httpUrl)
            .post("".toRequestBody(null))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val res = gson.fromJson(body, CreateSessionResponse::class.java)
                        currentSessionId = res.sessionId
                        currentPairingCode = res.pairingCode
                        deviceId = res.deviceId
                        prefs.edit().putString("device_id", deviceId).apply()

                        webSocketManager.connect(serverUrl, res.sessionId, res.deviceId)

                        mainHandler.post { callback(Result.success(res)) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(Result.failure(e)) }
                    }
                } else {
                    val errMsg = parseError(body) ?: "HTTP ${response.code}"
                    mainHandler.post { callback(Result.failure(Exception(errMsg))) }
                }
            }
        })
    }

    fun joinSession(pairingCode: String, callback: (Result<JoinSessionResponse>) -> Unit) {
        joinSession(PRODUCTION_SERVER_URL, pairingCode, callback)
    }

    fun joinSession(serverUrl: String = PRODUCTION_SERVER_URL, pairingCode: String, callback: (Result<JoinSessionResponse>) -> Unit) {
        this.serverUrl = serverUrl
        val httpUrl = buildHttpUrl(serverUrl, "/api/session/join")

        val joinReq = JoinSessionRequest(pairingCode = pairingCode, deviceId = deviceId)
        val jsonBody = gson.toJson(joinReq)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(httpUrl)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val res = gson.fromJson(body, JoinSessionResponse::class.java)
                        currentSessionId = res.sessionId
                        currentPairingCode = pairingCode.uppercase()
                        deviceId = res.deviceId
                        prefs.edit().putString("device_id", deviceId).apply()

                        webSocketManager.connect(serverUrl, res.sessionId, res.deviceId)

                        mainHandler.post { callback(Result.success(res)) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(Result.failure(e)) }
                    }
                } else {
                    val errMsg = parseError(body) ?: "HTTP ${response.code}"
                    mainHandler.post { callback(Result.failure(Exception(errMsg))) }
                }
            }
        })
    }

    fun reconnectExistingSession() {
        val sId = currentSessionId ?: return
        webSocketManager.connect(serverUrl, sId, deviceId)
    }

    fun leaveSession() {
        val sId = currentSessionId
        if (sId != null && webSocketManager.connectionState == ConnectionState.CONNECTED) {
            val msg = SyncMessage(
                type = "leave_session",
                sessionId = sId,
                deviceId = deviceId,
                displayName = displayName
            )
            webSocketManager.sendMessage(msg)
        }

        webSocketManager.disconnect()
        currentSessionId = null
        currentPairingCode = null
        peerConnected = false
        deviceCount = 1
        partnerDisplayName = "Partner"
        listeners.forEach { it.onPeerStatusChanged(false, 1) }
    }

    fun sendDisplayNameUpdate() {
        val sId = currentSessionId ?: return
        if (webSocketManager.connectionState == ConnectionState.CONNECTED) {
            val msg = SyncMessage(
                type = "display_name_update",
                sessionId = sId,
                deviceId = deviceId,
                displayName = displayName
            )
            webSocketManager.sendMessage(msg)
        }
    }

    fun sendStrokeStart(
        strokeId: String,
        normX: Float,
        normY: Float,
        color: Int,
        width: Float,
        alpha: Float = 1.0f,
        tool: String = "pen"
    ) {
        val sId = currentSessionId ?: return
        val msg = SyncMessage(
            type = "stroke_start",
            sessionId = sId,
            deviceId = deviceId,
            strokeId = strokeId,
            color = color,
            width = width,
            alpha = alpha,
            tool = tool,
            x = normX,
            y = normY
        )
        webSocketManager.sendMessage(msg)
    }

    fun sendStrokeUpdate(strokeId: String, normX: Float, normY: Float) {
        val sId = currentSessionId ?: return
        val msg = SyncMessage(
            type = "stroke_update",
            sessionId = sId,
            deviceId = deviceId,
            strokeId = strokeId,
            x = normX,
            y = normY
        )
        webSocketManager.sendMessage(msg)
    }

    fun sendStrokeEnd(strokeId: String, normX: Float, normY: Float) {
        val sId = currentSessionId ?: return
        val msg = SyncMessage(
            type = "stroke_end",
            sessionId = sId,
            deviceId = deviceId,
            strokeId = strokeId,
            x = normX,
            y = normY
        )
        webSocketManager.sendMessage(msg)
    }



    fun sendClear() {
        val sId = currentSessionId ?: return
        val msg = SyncMessage(
            type = "clear",
            sessionId = sId,
            deviceId = deviceId
        )
        webSocketManager.sendMessage(msg)
    }

    fun requestSnapshot() {
        val sId = currentSessionId ?: return
        val msg = SyncMessage(
            type = "canvas_state_request",
            sessionId = sId,
            deviceId = deviceId
        )
        webSocketManager.sendMessage(msg)
    }

    override fun onConnectionStateChanged(state: ConnectionState) {
        listeners.forEach { it.onConnectionStateChanged(state) }
        if (state == ConnectionState.CONNECTED) {
            sendDisplayNameUpdate()
            requestSnapshot()
        }
    }

    override fun onReconnected() {
        sendDisplayNameUpdate()
        requestSnapshot()
    }

    override fun onMessageReceived(message: SyncMessage) {
        when (message.type) {
            "stroke_start" -> {
                val sId = message.strokeId ?: return
                val dId = message.deviceId ?: return
                val x = message.x ?: return
                val y = message.y ?: return
                val color = message.color ?: -65536
                val width = message.width ?: 8f
                val alpha = message.alpha ?: 1.0f
                val tool = message.tool ?: "pen"

                if (dId != deviceId) {
                    listeners.forEach {
                        it.onRemoteStrokeStart(sId, dId, color, width, alpha, tool, x, y)
                    }
                }
            }

            "stroke_update" -> {
                val sId = message.strokeId ?: return
                val dId = message.deviceId ?: return
                val x = message.x ?: return
                val y = message.y ?: return

                if (dId != deviceId) {
                    listeners.forEach { it.onRemoteStrokeUpdate(sId, x, y) }
                }
            }

            "stroke_end" -> {
                val sId = message.strokeId ?: return
                val dId = message.deviceId ?: return
                val x = message.x ?: return
                val y = message.y ?: return
                val rem = message.remainingMs ?: 10000L

                if (dId != deviceId) {
                    listeners.forEach { it.onRemoteStrokeEnd(sId, x, y, rem) }
                }
            }

            "session_ended", "user_left" -> {
                webSocketManager.disconnect()
                currentSessionId = null
                currentPairingCode = null
                peerConnected = false
                deviceCount = 1
                partnerDisplayName = "Partner"
                val reason = message.message ?: "Partner disconnected from session"
                listeners.forEach {
                    it.onSessionEnded(reason)
                    it.onPeerStatusChanged(false, 1)
                }
            }

            "clear" -> {
                listeners.forEach { it.onRemoteClear() }
            }

            "canvas_state_snapshot" -> {
                val strokes = message.strokes?.filter { (it.remainingMs ?: 10000L) > 0L } ?: emptyList()
                updateDeviceNamesMap(message.deviceNames)
                listeners.forEach { it.onSnapshotReceived(strokes) }
            }

            "peer_status" -> {
                val wasConnected = peerConnected && deviceCount >= 2
                peerConnected = message.peerConnected ?: false
                deviceCount = message.deviceCount ?: 1
                updateDeviceNamesMap(message.deviceNames)

                if (wasConnected && (!peerConnected || deviceCount < 2)) {
                    // Partner disconnected! Terminate session for both devices instead of going to waiting state
                    webSocketManager.disconnect()
                    currentSessionId = null
                    currentPairingCode = null
                    peerConnected = false
                    deviceCount = 1
                    listeners.forEach {
                        it.onSessionEnded("Partner disconnected from session")
                        it.onPeerStatusChanged(false, 1)
                    }
                } else {
                    listeners.forEach { it.onPeerStatusChanged(peerConnected, deviceCount) }
                }
            }
        }
    }

    private fun updateDeviceNamesMap(namesMap: Map<String, String>?) {
        if (namesMap.isNullOrEmpty()) return
        val otherEntry = namesMap.entries.find { it.key != deviceId }
        if (otherEntry != null && otherEntry.value.isNotBlank()) {
            partnerDisplayName = otherEntry.value.trim()
            listeners.forEach { it.onPartnerNameChanged(partnerDisplayName) }
        }
    }

    private fun buildHttpUrl(serverUrl: String, path: String): String {
        var base = serverUrl.trim()
        if (base.startsWith("ws://")) {
            base = "http://" + base.substring(5)
        } else if (base.startsWith("wss://")) {
            base = "https://" + base.substring(6)
        } else if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length - 1)
        }
        return "$base$path"
    }

    private fun parseError(json: String?): String? {
        if (json.isNull_or_empty()) return null
        return try {
            val res = gson.fromJson(json, ErrorResponse::class.java)
            res?.error
        } catch (_: Exception) {
            null
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()
}
