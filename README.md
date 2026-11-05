# FloatDraw 🎨

**Draw together. Anywhere. In real time.**

FloatDraw is a collaborative Android drawing application that allows two users to draw simultaneously over any application using a floating drawing overlay.

## 📥 Download APK

Want to try FloatDraw? Download the latest Android APK from the GitHub Releases page.

<p align="center">
  <a href="https://github.com/YOUR_USERNAME/YOUR_REPOSITORY/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-FF6847?style=for-the-badge&logo=android&logoColor=white" alt="Download Latest APK">
  </a>
</p>

1. Open the [Latest Releases](https://github.com/YOUR_USERNAME/YOUR_REPOSITORY/releases/latest) page.
2. Download the latest `.apk` file under **Assets**.
3. Open the downloaded APK on your Android device.
4. Allow installation from your browser or file manager if Android requests it.
5. Install FloatDraw and grant the required permissions.

**Requirements:** Compatible Android device and an internet connection.

> Only download APK files published under the official GitHub Releases of this repository.

---

## ✨ Features

- Floating drawing overlay over other Android applications.
- Pen, Pencil, Highlighter and Pixel Eraser.
- Real-time collaborative drawing between two devices.
- QR code and pairing-code connections.
- Automatic 10-second stroke expiration.
- Shared canvas clearing.
- Automatic session termination when a participant leaves.
- Floating icon dismissal and notification-based restoration.
- Automatic WebSocket reconnection.

## 🛠️ Technology Stack

- Kotlin and Native Android Views
- Android Canvas API
- Ktor Backend
- OkHttp and WebSockets
- Render Hosting

## 🧪 Testing

Android build:

```powershell
.\gradlew.bat test assembleDebug lint
```

Standalone backend tests:

```powershell
.\gradlew.bat test
```

## 📄 License

Add your preferred open-source license before distributing the project.

---

**FloatDraw — One canvas. Two devices. Endless possibilities.**
