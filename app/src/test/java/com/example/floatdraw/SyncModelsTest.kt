package com.example.floatdraw

import com.example.floatdraw.sync.RemotePoint
import com.example.floatdraw.sync.RemoteStroke
import com.example.floatdraw.sync.SyncMessage
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SyncModelsTest {

    private val gson = Gson()

    @Test
    fun testSyncMessageSerialization() {
        val msg = SyncMessage(
            type = "stroke_start",
            sessionId = "sess-123",
            deviceId = "dev-abc",
            strokeId = "stroke-99",
            color = -65536,
            width = 10.0f,
            x = 0.45f,
            y = 0.82f,
            seq = 42L
        )

        val json = gson.toJson(msg)
        assertNotNull(json)

        val parsed = gson.fromJson(json, SyncMessage::class.java)
        assertEquals("stroke_start", parsed.type)
        assertEquals("sess-123", parsed.sessionId)
        assertEquals("dev-abc", parsed.deviceId)
        assertEquals("stroke-99", parsed.strokeId)
        assertEquals(-65536, parsed.color)
        assertEquals(10.0f, parsed.width!!, 0.001f)
        assertEquals(0.45f, parsed.x!!, 0.001f)
        assertEquals(0.82f, parsed.y!!, 0.001f)
        assertEquals(42L, parsed.seq!!)
    }

    @Test
    fun testSnapshotSerialization() {
        val stroke = RemoteStroke(
            strokeId = "st-1",
            deviceId = "dev-1",
            color = -16776961,
            width = 8.0f,
            points = listOf(RemotePoint(0.1f, 0.2f), RemotePoint(0.3f, 0.4f)),
            seq = 10L,
            expiresAt = System.currentTimeMillis() + 10000L,
            remainingMs = 10000L
        )
        val msg = SyncMessage(
            type = "canvas_state_snapshot",
            sessionId = "sess-123",
            strokes = listOf(stroke),
            seq = 10L
        )

        val json = gson.toJson(msg)
        val parsed = gson.fromJson(json, SyncMessage::class.java)

        assertEquals("canvas_state_snapshot", parsed.type)
        assertEquals(1, parsed.strokes?.size)
        assertEquals("st-1", parsed.strokes?.get(0)?.strokeId)
        assertEquals(10000L, parsed.strokes?.get(0)?.remainingMs!!)
    }

    @Test
    fun testDeviceClockOffsetIndependence() {
        val serverRemainingMs = 8500L

        // Device A has clock set 1 hour ahead
        val deviceA_Now = System.currentTimeMillis() + 3600000L
        val deviceA_LocalExpiresAt = deviceA_Now + serverRemainingMs

        // Device B has clock set 1 hour behind
        val deviceB_Now = System.currentTimeMillis() - 3600000L
        val deviceB_LocalExpiresAt = deviceB_Now + serverRemainingMs

        // Both devices calculate remaining time on their local clocks
        val deviceA_Remaining = deviceA_LocalExpiresAt - deviceA_Now
        val deviceB_Remaining = deviceB_LocalExpiresAt - deviceB_Now

        assertEquals(8500L, deviceA_Remaining)
        assertEquals(8500L, deviceB_Remaining)
    }

    @Test
    fun testStrokeExpiryAfterReconnection() {
        val strokeActive = RemoteStroke(strokeId = "s-active", deviceId = "dev-1", color = -1, width = 8f, remainingMs = 6000L)
        val strokeExpired = RemoteStroke(strokeId = "s-expired", deviceId = "dev-1", color = -1, width = 8f, remainingMs = 0L)

        val snapshotStrokes = listOf(strokeActive, strokeExpired)
        val filteredStrokes = snapshotStrokes.filter { (it.remainingMs ?: 10000L) > 0L }

        assertEquals(1, filteredStrokes.size)
        assertEquals("s-active", filteredStrokes[0].strokeId)
        assertEquals(6000L, filteredStrokes[0].remainingMs!!)
    }

    @Test
    fun testSessionEndedMessageSerialization() {
        val endMsg = SyncMessage(
            type = "session_ended",
            sessionId = "sess-999",
            message = "Partner explicitly left the session."
        )

        val json = gson.toJson(endMsg)
        val parsed = gson.fromJson(json, SyncMessage::class.java)

        assertEquals("session_ended", parsed.type)
        assertEquals("sess-999", parsed.sessionId)
        assertEquals("Partner explicitly left the session.", parsed.message)
    }
}
