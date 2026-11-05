package com.example.floatdraw.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.time.Duration

val sessionManager = SessionManager()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("Starting FloatDraw Relay Server on port $port...")
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
    install(CORS) {
        anyHost()
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(45)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respond(mapOf("status" to "ok", "service" to "FloatDraw Synchronization Relay Server"))
        }

        post("/api/session/create") {
            try {
                val res = sessionManager.createSession()
                call.respond(res)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to create session"))
            }
        }

        post("/api/session/join") {
            try {
                val req = call.receive<JoinSessionRequest>()
                val code = req.pairingCode.trim()
                if (code.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pairing code is required"))
                    return@post
                }
                val res = sessionManager.joinSession(code, req.deviceId)
                call.respond(res)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid pairing code"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(e.message ?: "Session full"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to join session: ${e.message}"))
            }
        }

        webSocket("/ws/draw") {
            val sessionIdParam = call.request.queryParameters["sessionId"]
            val deviceIdParam = call.request.queryParameters["deviceId"]

            if (sessionIdParam.isNullOrEmpty() || deviceIdParam.isNullOrEmpty()) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing sessionId or deviceId"))
                return@webSocket
            }

            val sessionId = sessionIdParam
            val deviceId = deviceIdParam

            val registered = sessionManager.registerClient(sessionId, deviceId, this)
            if (!registered) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized or session full"))
                return@webSocket
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        sessionManager.processMessage(sessionId, deviceId, text)
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error for session $sessionId device $deviceId: ${e.message}")
            } finally {
                sessionManager.unregisterClient(sessionId, deviceId)
            }
        }
    }
}
