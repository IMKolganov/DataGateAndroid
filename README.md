<p align="center">
  <img src="assets/logo.png" width="120" alt="DataGate" />
</p>

<h1 align="center">DataGate</h1>
<p align="center"><strong>Android VPN client — OpenVPN over WebSocket Secure (WSS)</strong></p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%2024%2B-green?logo=android" alt="Android 24+" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/OpenVPN-WSS-blue" alt="OpenVPN over WSS" />
</p>

---

## What is this?

**DataGate** is a native Android app that connects to your VPN backend and establishes an **OpenVPN** tunnel. Traffic is carried over **WebSocket Secure (WSS)** from the device to your server, which then forwards it to the real OpenVPN server. That lets you run OpenVPN behind HTTPS/WSS (e.g. nginx) and avoid direct UDP/TCP to the VPN port.

- **App** gets config and WSS URL from your API, manages auth (e.g. Google Sign-In) and UI.
- **VpnService** runs the OpenVPN core and a local TCP↔WSS bridge inside the VPN process.

## Features

| Feature | Description |
|--------|-------------|
| **OpenVPN over WSS** | Tunnel traffic over WebSocket Secure; no direct VPN port exposure. |
| **Google Sign-In** | Optional OAuth login; token used for API and VPN config. |
| **Server list (Access)** | View VPN servers and status from your backend. |
| **Statistics** | Overview series and traffic stats from your API. |
| **Material 3** | Jetpack Compose UI with Material You. |

## Requirements

- **Android 7.0+ (API 24+)**
- **Android Studio** (or compatible IDE)
- **Kotlin 1.9+**
- **JDK 17+**

## Setup

### 1. Clone and open

```bash
git clone <repo-url>
cd DataGateAndroid
```

### 2. Config (env and keystore)

The project expects optional config files (see `app/build.gradle.kts`):

- **env.properties** — API base URL, Google client ID, etc. (create from `env.example.properties` if present)
- **keystore.properties** — signing config for release builds

Add these files as needed; they are typically in `.gitignore`.

### 3. Build

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run the **app** configuration.

## Project layout

| Path | Description |
|------|-------------|
| **app/** | Main app (Kotlin, Jetpack Compose). |
| **native-openvpn3/** | OpenVPN3 core (submodule or vendored). |
| **assets/** | Logo and images for the repo (e.g. README). |

## License

See the repository license file.
