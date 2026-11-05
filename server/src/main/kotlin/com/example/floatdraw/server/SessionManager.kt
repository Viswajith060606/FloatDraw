package com.example.floatdraw.server

import com.google.gson.Gson
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionManager {
    private val gson = Gson()
    private val random = SecureRandom()
    private val codeChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    data class Session(
        val sessionId: String,
        val pairingCode: String,
        val createdAt: Long,
        val expiresAt: Long,
        val authorizedDevices: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val connectedSockets: ConcurrentHashMap<String, WebSocketSession> = ConcurrentHashMap(),
        val displayNames: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
        val strokes: MutableList<ServerStroke> = mutableListOf(),
        val seqCounter: AtomicLong = AtomicLong(0),
        val mutex: Mutex = Mutex(),
        var isTerminated: Boolean = false
    )

    private val sessionsById = ConcurrentHashMap<String, Session>()
    private val sessionsByCode = ConcurrentHashMap<String, Session>()

    // Rate limiting per IP for join/pairing attempts
    private val ipRateLimitTracker = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val MAX_PAIR_ATTEMPTS_PER_MIN = 10

    // Rate limiting per socket for drawing events
    private val socketRateLimitTracker = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val MAX_EVENTS_PER_SEC = 120

    fun generatePairingCode(): String {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(codeChars[random.nextInt(codeChars.length)])
        }
        return sb.toString()
    }

    fun checkIpPairingRateLimit(ipAddress: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = ipRateLimitTracker[ipAddress]
        if (entry == null || now - entry.first > 60000) {
            ipRateLimitTracker[ipAddress] = Pair(now, 1)
            return true
        }
        if (entry.second >= MAX_PAIR_ATTEMPTS_PER_MIN) {
            return false
        }
        ipRateLimitTracker[ipAddress] = Pair(entry.first, entry.second + 1)
        return true
    }

    fun checkSocketRateLimit(deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = socketRateLimitTracker[deviceId]
        if (entry == null || now - entry.first > 1000) {
            socketRateLimitTracker[deviceId] = Pair(now, 1)
            return true
        }
        if (entry.second >= MAX_EVENTS_PER_SEC) {
            return false
        }
        socketRateLimitTracker[deviceId] = Pair(entry.first, entry.second + 1)
        return true
    }

    fun createSession(): CreateSessionResponse {
        cleanExpiredSessions()
        var code: String
        do {
            code = generatePairingCode()
        } while (sessionsByCode.containsKey(code))

        val sessionId = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + 10 * 60 * 1000L // 10 minutes expiry

        val session = Session(
            sessionId = sessionId,
            pairingCode = code,
            createdAt = now,
            expiresAt = expiresAt
        )
        session.authorizedDevices.add(deviceId)

        sessionsById[sessionId] = session
        sessionsByCode[code] = session

        return CreateSessionResponse(
            sessionId = sessionId,
            pairingCode = code,
            deviceId = deviceId,
            expiresAt = expiresAt
        )
    }

    fun joinSession(code: String, requestedDeviceId: String?, ipAddress: String = "unknown"): JoinSessionResponse {
        cleanExpiredSessions()

        if (!checkIpPairingRateLimit(ipAddress)) {
            throw IllegalStateException("Too many pairing attempts. Please wait a minute and try again.")
        }

        val cleanCode = code.trim().uppercase()
        val session = sessionsByCode[cleanCode]
            ?: throw IllegalArgumentException("Invalid or expired pairing code")

        if (session.isTerminated || System.currentTimeMillis() > session.expiresAt) {
            sessionsById.remove(session.sessionId)
            sessionsByCode.remove(cleanCode)
            throw IllegalArgumentException("Session has ended or expired")
        }

        val deviceId = requestedDeviceId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        if (!session.authorizedDevices.contains(deviceId)) {
            if (session.authorizedDevices.size >= 2) {
                throw IllegalStateException("Session is full (maximum 2 devices permitted)")
            }
            session.authorizedDevices.add(deviceId)
        }

        return JoinSessionResponse(
            sessionId = session.sessionId,
            deviceId = deviceId
        )
    }

    fun getSession(sessionId: String): Session? = sessionsById[sessionId]

    suspend fun registerClient(sessionId: String, deviceId: String, socket: WebSocketSession): Boolean {
        val session = sessionsById[sessionId] ?: return false
        if (session.isTerminated || !session.authorizedDevices.contains(deviceId)) return false

        session.connectedSockets[deviceId] = socket
        notifyPeerStatus(session)
        return true
    }

    suspend fun unregisterClient(sessionId: String, deviceId: String) {
        val session = sessionsById[sessionId] ?: return
        session.connectedSockets.remove(deviceId)
        socketRateLimitTracker.remove(deviceId)
        if (!session.isTerminated) {
            handleLeaveSession(session, deviceId)
        }
    }

    private suspend fun notifyPeerStatus(session: Session) {
        val count = session.connectedSockets.size
        val namesMap = session.displayNames.toMap()
        val message = SocketMessage(
            type = "peer_status",
            sessionId = session.sessionId,
            peerConnected = count > 1,
            deviceCount = count,
            deviceNames = namesMap
        )
        broadcast(session, message)
    }

    suspend fun processMessage(sessionId: String, deviceId: String, rawText: String) {
        val session = sessionsById[sessionId] ?: return
        if (session.isTerminated || !session.authorizedDevices.contains(deviceId)) return

        if (!checkSocketRateLimit(deviceId)) {
            return
        }

        val msg = try {
            gson.fromJson(rawText, SocketMessage::class.java)
        } catch (_: Exception) {
            return
        }

        when (msg?.type) {
            "display_name_update" -> handleDisplayNameUpdate(session, deviceId, msg)
            "leave_session" -> handleLeaveSession(session, deviceId)
            "stroke_start" -> handleStrokeStart(session, deviceId, msg)
            "stroke_update" -> handleStrokeUpdate(session, deviceId, msg)
            "stroke_end" -> handleStrokeEnd(session, deviceId, msg)
            "clear" -> handleClear(session, deviceId)
            "canvas_state_request" -> handleCanvasStateRequest(session, deviceId)
            "ping" -> handlePing(session, deviceId)
        }
    }

    private suspend fun handleDisplayNameUpdate(session: Session, deviceId: String, msg: SocketMessage) {
        val rawName = msg.displayName?.trim() ?: "Partner"
        val cleanName = rawName.take(20).ifEmpty { "Partner" }
        session.displayNames[deviceId] = cleanName
        notifyPeerStatus(session)
    }

    private suspend fun handleLeaveSession(session: Session, deviceId: String) {
        if (session.isTerminated) return
        session.isTerminated = true

        val endMsg = SocketMessage(
            type = "session_ended",
            sessionId = session.sessionId,
            deviceId = deviceId,
            message = "Partner explicitly left the session."
        )
        broadcast(session, endMsg)

        // Give network sockets a brief moment to flush the session_ended frame before closing
        delay(200)

        session.mutex.withLock {
            session.strokes.clear()
            session.connectedSockets.values.forEach { socket ->
                try { socket.close(CloseReason(CloseReason.Codes.NORMAL, "Session ended")) } catch (_: Exception) {}
            }
            session.connectedSockets.clear()
            session.authorizedDevices.clear()
        }
        sessionsById.remove(session.sessionId)
        sessionsByCode.values.remove(session)
    }

    private suspend fun handleStrokeStart(session: Session, deviceId: String, msg: SocketMessage) {
        val x = msg.x ?: return
        val y = msg.y ?: return
        if (x !in 0.0f..1.0f || y !in 0.0f..1.0f) return
        val strokeId = msg.strokeId ?: return
        val color = msg.color ?: -65536
        val width = msg.width ?: 8f
        val alpha = msg.alpha ?: 1.0f
        val tool = msg.tool ?: "pen"

        val seq = session.seqCounter.incrementAndGet()
        session.mutex.withLock {
            val stroke = ServerStroke(
                strokeId = strokeId,
                deviceId = deviceId,
                color = color,
                width = width,
                alpha = alpha,
                tool = tool,
                points = mutableListOf(ServerPoint(x, y)),
                seq = seq,
                expiresAt = 0L
            )
            session.strokes.add(stroke)
        }

        val echoMsg = msg.copy(
            type = "stroke_start",
            sessionId = session.sessionId,
            deviceId = deviceId,
            seq = seq
        )
        broadcast(session, echoMsg)
    }

    private suspend fun handleStrokeUpdate(session: Session, deviceId: String, msg: SocketMessage) {
        val x = msg.x ?: return
        val y = msg.y ?: return
        if (x !in 0.0f..1.0f || y !in 0.0f..1.0f) return
        val strokeId = msg.strokeId ?: return

        val seq = session.seqCounter.incrementAndGet()
        session.mutex.withLock {
            val stroke = session.strokes.find { it.strokeId == strokeId }
            stroke?.points?.add(ServerPoint(x, y))
        }

        val echoMsg = msg.copy(
            type = "stroke_update",
            sessionId = session.sessionId,
            deviceId = deviceId,
            seq = seq
        )
        broadcast(session, echoMsg)
    }

    private suspend fun handleStrokeEnd(session: Session, deviceId: String, msg: SocketMessage) {
        val x = msg.x ?: return
        val y = msg.y ?: return
        if (x !in 0.0f..1.0f || y !in 0.0f..1.0f) return
        val strokeId = msg.strokeId ?: return

        val seq = session.seqCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        val exp = now + 10000L
        val rem = 10000L
        session.mutex.withLock {
            val stroke = session.strokes.find { it.strokeId == strokeId }
            if (stroke != null) {
                stroke.points.add(ServerPoint(x, y))
                stroke.expiresAt = exp
            }
        }

        val echoMsg = SocketMessage(
            type = "stroke_end",
            sessionId = session.sessionId,
            deviceId = deviceId,
            strokeId = strokeId,
            x = x,
            y = y,
            seq = seq,
            expiresAt = exp,
            remainingMs = rem
        )
        broadcast(session, echoMsg)
    }

    private suspend fun handleClear(session: Session, deviceId: String) {
        val seq = session.seqCounter.incrementAndGet()
        session.mutex.withLock {
            session.strokes.clear()
        }

        val broadcastMsg = SocketMessage(
            type = "clear",
            sessionId = session.sessionId,
            deviceId = deviceId,
            seq = seq
        )
        broadcast(session, broadcastMsg)
    }

    private suspend fun handleCanvasStateRequest(session: Session, deviceId: String) {
        val socket = session.connectedSockets[deviceId] ?: return
        val now = System.currentTimeMillis()
        val strokesCopy: List<ServerStroke>
        val currentSeq: Long
        session.mutex.withLock {
            session.strokes.removeAll { it.expiresAt > 0L && it.expiresAt <= now }
            strokesCopy = session.strokes.map { stroke ->
                val remaining = if (stroke.expiresAt > 0L) (stroke.expiresAt - now).coerceAtLeast(0L) else 10000L
                stroke.copy(
                    points = stroke.points.toMutableList(),
                    remainingMs = remaining
                )
            }
            currentSeq = session.seqCounter.get()
        }
        val namesMap = session.displayNames.toMap()
        val snapshotMsg = SocketMessage(
            type = "canvas_state_snapshot",
            sessionId = session.sessionId,
            deviceId = deviceId,
            deviceNames = namesMap,
            strokes = strokesCopy,
            seq = currentSeq
        )
        sendToSocket(socket, snapshotMsg)
    }

    private suspend fun handlePing(session: Session, deviceId: String) {
        val socket = session.connectedSockets[deviceId] ?: return
        val pongMsg = SocketMessage(type = "pong", sessionId = session.sessionId, deviceId = deviceId)
        sendToSocket(socket, pongMsg)
    }

    private suspend fun broadcast(session: Session, message: SocketMessage) {
        val json = gson.toJson(message)
        session.connectedSockets.values.forEach { socket ->
            try {
                socket.send(Frame.Text(json))
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendToSocket(socket: WebSocketSession, message: SocketMessage) {
        try {
            val json = gson.toJson(message)
            socket.send(Frame.Text(json))
        } catch (_: Exception) {}
    }

    fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        sessionsById.values.forEach { session ->
            if (now > session.expiresAt && session.connectedSockets.isEmpty()) {
                sessionsById.remove(session.sessionId)
                sessionsByCode.remove(session.pairingCode)
            }
        }
    }
}
