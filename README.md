# FloatDraw 🎨

**Draw together. Anywhere. In real time.**

FloatDraw is a collaborative Android drawing application that allows two users to draw simultaneously over any application using a floating drawing overlay.

<p align="center">
  <a href="https://github.com/Viswajith060606/FloatDraw/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-FF6847?style=for-the-badge&logo=android&logoColor=white" alt="Download Latest APK">
  </a>
  <a href="https://github.com/Viswajith060606/FloatDraw/releases">
    <img src="https://img.shields.io/badge/GitHub-Releases-blue?style=for-the-badge&logo=github" alt="GitHub Releases">
  </a>
</p>

## 📥 Download FloatDraw

**[Download the Latest FloatDraw APK](https://github.com/Viswajith060606/FloatDraw/releases/latest)**

1. Open the latest release.
2. Download the `.apk` file from the Assets section.
3. Install it on your Android device.
4. Grant the required permissions.
5. Connect another device and start drawing together!

## ✨ Features

### 🖊️ Floating Drawing Overlay
- Draw over other Android applications.
- Draggable floating pencil icon.
- Drawing Mode and Touch Mode.
- Drag-to-dismiss floating icon.
- Restore the icon through the notification.

### 🎨 Drawing Tools
- Pen
- Pencil
- Highlighter
- Pixel Eraser
- Adjustable brush thickness and opacity.
- Built-in colour palette.
- Custom RGB colour selection.
- Shared canvas clearing.

### 🤝 Real-Time Collaboration
- Connect two Android devices over the internet.
- Create and join sessions using pairing codes.
- QR code generation and scanning.
- Real-time bidirectional drawing synchronization.
- Shared canvas across both devices.

### ⏱️ Automatic Stroke Expiry
- Completed strokes disappear automatically after 10 seconds.
- Independent expiration for each stroke.
- Server-authoritative expiry timestamps.
- Expired strokes excluded from canvas snapshots.

### 🔄 Session Management
- Automatic session termination when either participant explicitly leaves.
- Automatic WebSocket reconnection after temporary network interruptions.
- Partner connection status.
- Device display-name synchronization.
- Canvas recovery through server snapshots.

### 📱 Connection-Aware Controls
- Drawing controls remain hidden while waiting for a partner.
- Drawing tools become available when both devices are connected.
- Collaborative controls are disabled when the session is disconnected or terminated.

## 🏗️ Architecture

FloatDraw consists of two main components.

**Android Application — FloatDrawV1**
- Kotlin
- Native Android Views
- Android Canvas API
- Android WindowManager
- OkHttp WebSockets
- Android Foreground Service

**Backend — FloatDrawServer**
- Kotlin
- Ktor
- REST APIs
- WebSockets
- Session and stroke management
- Hosted on Render

## 🛠️ Technology Stack

| Component | Technology |
|---|---|
| Android Development | Kotlin |
| UI | Native Android Views |
| Drawing Engine | Android Canvas |
| Backend | Kotlin + Ktor |
| Communication | REST APIs and WebSockets |
| HTTP Client | OkHttp |
| Hosting | Render |
| IDE | Android Studio |

## 🚀 Getting Started

### Prerequisites
- Android Studio
- JDK 17
- Android SDK
- Internet connection
- Two Android devices for collaborative testing

### Installation

Download the latest APK from:

**https://github.com/Viswajith060606/FloatDraw/releases/latest**

### Build from Source

Clone the repository:

```bash
git clone https://github.com/Viswajith060606/FloatDraw.git
```

Open the Android project in Android Studio, allow Gradle synchronization, configure the backend URL if necessary, and build the application.

## 🔄 How It Works

1. User A creates a collaborative session.
2. User B joins using a QR code or pairing code.
3. Both devices establish a WebSocket connection.
4. Drawing controls become available.
5. Both users draw over their currently open applications.
6. Strokes synchronize in real time.
7. Completed strokes disappear after 10 seconds.
8. Either participant can clear the shared canvas.
9. When either participant explicitly leaves, the session ends for both.

## 🧪 Testing

Android application:

```powershell
.\gradlew.bat test assembleDebug lint
```

Standalone backend:

```powershell
.\gradlew.bat test
```

Physical two-device testing is recommended for validating real-time collaboration and overlay behaviour.

## 📌 Important Notes

- Requires an active internet connection.
- Supports two participants per session.
- Requires Android overlay permission.
- Both devices should use compatible application versions.
- The backend must be available for collaborative synchronization.

## 🗺️ Development Roadmap

- [x] Floating drawing overlay
- [x] Multiple drawing tools
- [x] Two-device pairing
- [x] QR code pairing
- [x] Real-time drawing synchronization
- [x] Automatic 10-second stroke expiry
- [x] Shared canvas clearing
- [x] Automatic session termination
- [x] WebSocket reconnection and snapshot recovery
- [x] Drag-to-dismiss floating icon
- [x] Notification-based icon restoration
- [x] Connection-aware drawing controls
- [ ] Complete physical-device regression testing


---

<p align="center">
  <b>FloatDraw — One canvas. Two devices. Endless possibilities.</b>
</p>

<p align="center">
  <a href="https://github.com/Viswajith060606/FloatDraw">GitHub Repository</a> ·
  <a href="https://github.com/Viswajith060606/FloatDraw/releases">Releases</a> ·
  <a href="https://github.com/Viswajith060606/FloatDraw/issues">Report an Issue</a>
</p>
