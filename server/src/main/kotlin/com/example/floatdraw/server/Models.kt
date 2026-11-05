package com.example.floatdraw.server

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

data class ServerPoint(
    val x: Float,
    val y: Float
)

data class ServerStroke(
    val strokeId: String,
    val deviceId: String,
    val color: Int,
    val width: Float,
    val alpha: Float = 1.0f,
    val tool: String = "pen",
    val points: MutableList<ServerPoint> = mutableListOf(),
    val seq: Long,
    var expiresAt: Long = 0L,
    var remainingMs: Long? = null
)

data class SocketMessage(
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
    val strokes: List<ServerStroke>? = null,
    val message: String? = null,
    val expiresAt: Long? = null,
    val remainingMs: Long? = null
)
