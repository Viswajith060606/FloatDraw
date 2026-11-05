# FloatDraw Relay Server (Ktor Backend)

Subproject module providing authenticated 2-device pairing sessions and real-time WebSocket synchronization for the FloatDraw Android app.

## How to Run Locally

### Using Gradle Wrapper
From the root directory of FloatDrawV1:

```powershell
.\gradlew.bat :server:run
```

Or on Linux / macOS:
```bash
./gradlew :server:run
```

By default, the server listens on `http://0.0.0.0:8080`.
The WebSocket endpoint is `ws://<server-ip>:8080/ws/draw`.

### Custom Port
To change the server port, set the `PORT` environment variable:
```powershell
$env:PORT="9090"; .\gradlew.bat :server:run
```

## Production Deployment (HTTPS / WSS)

1. Deploy this `:server` module to any cloud host (e.g. Render, Heroku, Railway, AWS, DigitalOcean, Docker).
2. Configure TLS / SSL via reverse proxy (e.g. Nginx / Caddy / Cloudflare) to terminate HTTPS/WSS.
3. Set the Android app's Server URL to `wss://your-domain.com`.

## REST API Endpoints

- `GET /` -> Returns service health status.
- `POST /api/session/create` -> Generates a new session and 6-character random pairing code.
- `POST /api/session/join` -> Body: `{"pairingCode": "..."}` -> Joins existing session (max 2 devices).

## WebSocket Protocol (`/ws/draw`)

Query parameters: `?sessionId=...&deviceId=...`

Supported JSON message types:
- `stroke_start`: `{ "type": "stroke_start", "sessionId": "...", "deviceId": "...", "strokeId": "...", "color": -65536, "width": 8.0, "x": 0.5, "y": 0.5 }`
- `stroke_update`: `{ "type": "stroke_update", "sessionId": "...", "deviceId": "...", "strokeId": "...", "x": 0.51, "y": 0.52 }`
- `stroke_end`: `{ "type": "stroke_end", "sessionId": "...", "deviceId": "...", "strokeId": "...", "x": 0.53, "y": 0.54 }`
- `undo`: `{ "type": "undo", "sessionId": "...", "deviceId": "...", "strokeId": "..." }`
- `clear`: `{ "type": "clear", "sessionId": "...", "deviceId": "..." }`
- `canvas_state_request`: `{ "type": "canvas_state_request", "sessionId": "...", "deviceId": "..." }`
- `ping`: `{ "type": "ping", "sessionId": "...", "deviceId": "..." }`
