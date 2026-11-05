package com.example.floatdraw.server

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionManagerTest {

    private val gson = Gson()

    @Test
    fun testCreateSession() {
        val manager = SessionManager()
        val response = manager.createSession()
        assertNotNull(response.sessionId)
        assertNotNull(response.pairingCode)
        assertEquals(6, response.pairingCode.length)
        assertNotNull(response.deviceId)

        val session = manager.getSession(response.sessionId)
        assertNotNull(session)
        assertEquals(1, session?.authorizedDevices?.size)
    }

    @Test
    fun testJoinSessionSuccess() {
        val manager = SessionManager()
        val createRes = manager.createSession()

        val joinRes = manager.joinSession(createRes.pairingCode, null)
        assertNotNull(joinRes.sessionId)
        assertEquals(createRes.sessionId, joinRes.sessionId)
        assertNotEquals(createRes.deviceId, joinRes.deviceId)

        val session = manager.getSession(createRes.sessionId)
        assertEquals(2, session?.authorizedDevices?.size)
    }

    @Test(expected = IllegalStateException::class)
    fun testRejectThirdDeviceInSession() {
        val manager = SessionManager()
        val createRes = manager.createSession()
        manager.joinSession(createRes.pairingCode, "device-2")
        manager.joinSession(createRes.pairingCode, "device-3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testJoinWithInvalidCode() {
        val manager = SessionManager()
        manager.joinSession("INVALID", "device-1")
    }

    @Test
    fun testExplicitLeaveTerminatesSession() = runBlocking {
        val manager = SessionManager()
        val createRes = manager.createSession()
        val sId = createRes.sessionId
        val devA = createRes.deviceId

        val session = manager.getSession(sId)!!
        assertFalse(session.isTerminated)

        // Device A explicitly leaves
        manager.processMessage(sId, devA, gson.toJson(SocketMessage(type = "leave_session")))

        assertTrue(session.isTerminated)

        // Attempting to join terminated session should throw IllegalArgumentException
        try {
            manager.joinSession(createRes.pairingCode, "devB")
            fail("Expected exception for joining terminated session")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testSimultaneousAndDuplicateLeaveRequests() = runBlocking {
        val manager = SessionManager()
        val createRes = manager.createSession()
        val sId = createRes.sessionId
        val devA = createRes.deviceId
        val joinRes = manager.joinSession(createRes.pairingCode, "devB")
        val devB = joinRes.deviceId

        val session = manager.getSession(sId)!!
        assertFalse(session.isTerminated)

        // Device A sends leave
        manager.processMessage(sId, devA, gson.toJson(SocketMessage(type = "leave_session")))
        assertTrue(session.isTerminated)

        // Device B sends leave at the same time (duplicate/simultaneous) -> handled safely & idempotently
        manager.processMessage(sId, devB, gson.toJson(SocketMessage(type = "leave_session")))
        assertTrue(session.isTerminated)
    }

    @Test
    fun testStrokeExpiryAssignment() = runBlocking {
        val manager = SessionManager()
        val createRes = manager.createSession()
        val sId = createRes.sessionId
        val devA = createRes.deviceId

        manager.processMessage(sId, devA, gson.toJson(SocketMessage(
            type = "stroke_start", strokeId = "s1", x = 0.1f, y = 0.1f
        )))
        manager.processMessage(sId, devA, gson.toJson(SocketMessage(
            type = "stroke_end", strokeId = "s1", x = 0.2f, y = 0.2f
        )))

        val session = manager.getSession(sId)!!
        assertEquals(1, session.strokes.size)
        assertTrue(session.strokes[0].expiresAt > System.currentTimeMillis())
    }
}
