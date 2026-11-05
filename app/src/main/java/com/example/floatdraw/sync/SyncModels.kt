package com.example.floatdraw.sync

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class RemotePoint(
    val x: Float,
    val y: Float
)

data class RemoteStroke(
    val strokeId: String,
    val deviceId: String,
    val color: Int,
    val width: Float,
    val alpha: Float = 1.0f,
    val tool: String = "pen",
    val points: List<RemotePoint> = emptyList(),
    val seq: Long = 0,
    val expiresAt: Long = 0L,
    val remainingMs: Long? = null
)

data class SyncMessage(
    val type: String,
    val sessionId: String? = null,
    val deviceId: String? = null,
    val displayName: String? = null,
    val deviceNames: Map<String, String>? = null,
    val strokeId: String? = null,
    val targetDeviceId: String? = null,
    val color: Int? = null,
    val width: Float? = null,
    val alpha: Float? = null,
    val tool: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val seq: Long? = null,
    val peerConnected: Boolean? = null,
    val deviceCount: Int? = null,
    val strokes: List<RemoteStroke>? = null,
    val message: String? = null,
    val expiresAt: Long? = null,
    val remainingMs: Long? = null
)

data class CreateSessionResponse(
    val sessionId: String,
    val pairingCode: String,
    val deviceId: String,
    val expiresAt: Long
)

data class JoinSessionRequest(
    val pairingCode: String,
    val deviceId: String? = null
)

data class JoinSessionResponse(
    val sessionId: String,
    val deviceId: String
)

data class ErrorResponse(
    val error: String
)
