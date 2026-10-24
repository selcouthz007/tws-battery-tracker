# tws-battery-tracker
# 🎧 TWS Battery Tracker

A lightweight, background-aware Android utility application designed to automatically track, log, and analyze True Wireless Stereo (TWS) earbud sessions from connect to disconnect.

---

## ⚡ Key Features

- **Automated Session Logging:** Automatically detects Bluetooth connection and disconnection events (`ACL_CONNECTED` / `ACL_DISCONNECTED`).
- **Live Battery Tracking:** Monitors real-time battery drain percentage and live connection states.
- **Drain Rate Analytics:** Calculates session duration and average hourly drain rate (`% / hour`) to evaluate battery performance and health.
- **Local Persistence:** Stores complete historical logs locally using an optimized SQLite database (`SessionDbHelper`).
- **Clean Dashboard UI:** Card-based UI showing active connection status, battery levels, and scrollable session history.
- **Built-in Simulator:** Includes a one-tap test logging button to preview analytics without physical devices.

---

## 🛠️ Tech Stack & Architecture

- **Platform:** Android (Min SDK: 26, Target SDK: 34)
- **Language:** Kotlin
- **Database:** SQLite (Native Helper)
- **CI/CD:** GitHub Actions (Automated cloud builds for APK generation)
- **Permissions:** Android 12+ runtime Bluetooth support (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`)

---

## 📂 Project Structure

```text
tws-battery-tracker/
├── .github/workflows/
│   └── build.yml               # Automated CI pipeline for debug APKs
├── app/
│   ├── build.gradle.kts        # Application build & dependencies configuration
│   └── src/main/
│       ├── AndroidManifest.xml # Permissions & BroadcastReceiver declarations
│       ├── java/com/tws/tracker/
│       │   ├── BluetoothReceiver.kt  # Handles ACL connection & battery events
│       │   ├── MainActivity.kt       # Live dashboard and log viewer
│       │   └── SessionDbHelper.kt    # SQLite storage for session data
│       └── res/
│           ├── drawable/ic_tws_icon.xml # App icon vector
│           └── layout/activity_main.xml # Dashboard layout
├── build.gradle.kts            # Root Gradle configuration
├── gradle.properties           # JVM & AndroidX settings
└── settings.gradle.kts         # Repository and project settings