# 🦀 ClawAPK

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-10+-green?logo=android)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**ClawAPK** is the official Android companion app for [OpenClaw](https://github.com/openclaw/openclaw) — an AI agent gateway. Interact with your AI assistant via voice or text, and receive spoken responses, push notifications, and scheduled alerts directly on your phone.

---

## 🚀 Features

- 💬 **Voice & Text Chat** — Seamless communication with OpenClaw via a modern Material 3 chat UI.
- 🔊 **Text-to-Speech (TTS)** — Responses read aloud using [Piper](https://github.com/rhasspy/piper) (Polish) or [Kokoro](https://github.com/remsky/kokoro-fastapi) (English).
- 🎙️ **Speech-to-Text (STT)** — Built-in Android speech recognition for natural voice input.
- ⏰ **Cron Event Listener** — Persistent WebSocket connection for real-time reactions to OpenClaw events:
  - 🔔 **Push Notifications**
  - 🗣️ **Spoken announcements**
  - 📳 **Custom vibration patterns**
- 🌍 **Localization** — Full support for Polish and English, auto-adapting to your device settings.
- 🔐 **Flexible Auth** — Supports tokens, passwords, device pairing, or no-auth modes.
- ⚙️ **Configurable Server** — Connect to any OpenClaw instance (LAN or Cloud).

---

## 🏗️ Architecture

The project follows the **Hexagonal (Ports & Adapters)** architecture, ensuring a clean separation of concerns and testability.

### Architecture Overview

```mermaid
graph TD
    subgraph "App Layer (:app)"
        A[Composition Root] --> B[DI / Koin]
        A --> C[Navigation]
    end

    subgraph "Feature Layer (:feature)"
        D[Chat UI / Settings]
        E[Foreground Service / Notifications]
    end

    subgraph "Domain Layer (:core:domain)"
        F[Models]
        G[Ports / Interfaces]
        H[Use Cases]
    end

    subgraph "Library & Data Layer"
        I[:libs:websocket]
        J[:libs:tts]
        K[:libs:stt]
        L[:core:data]
    end

    A -.-> D
    A -.-> E
    D --> F
    E --> F
    I -- Adapts --> G
    J -- Adapts --> G
    K -- Adapts --> G
    L -- Adapts --> G
```

### Module Dependencies

- **`:core:domain`** & **`:core:common`**: Pure Kotlin (no Android dependencies).
- **Adapters**: `:libs:websocket`, `:libs:tts`, `:libs:stt`, `:core:data`.
- **Features**: `:feature:chat`, `:feature:notifications`.
- **App**: Orchestrates everything, wiring adapters to ports via Koin.

---

## 🛠️ Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.0 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Networking** | OkHttp 4 (WebSocket) |
| **Serialization** | kotlinx.serialization |
| **Dependency Injection** | Koin 4 |
| **Audio Engine** | Media3 ExoPlayer |
| **Persistence** | DataStore Preferences |
| **Min SDK** | 29 (Android 10) |

---

## 🏁 Getting Started

### Prerequisites

- ✅ Android Studio Ladybug or newer
- ✅ JDK 17+
- ✅ Running [OpenClaw](https://github.com/openclaw/openclaw) instance

### Build & Install

```bash
git clone https://github.com/your-org/clawapk.git
cd clawapk
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### ⚙️ Configuration

1. Launch **ClawAPK**.
2. Tap the **Settings** ⚙️ icon.
3. Enter your OpenClaw server (e.g., `wss://claw.example.com`).
4. Select **Auth Mode**: `None`, `Token`, `Password`, `Device Pairing`, or `Cloudflare Access`.
5. Choose **TTS Engine** (Polish/Piper or English/Kokoro).
6. Tap **Save and Connect**.

### 🔒 Cloudflare Access Authentication

If your OpenClaw instance is protected by [Cloudflare Zero Trust Access](https://developers.cloudflare.com/cloudflare-one/), ClawAPK supports native authentication through an embedded WebView login flow:

1. In **Settings**, select **Cloudflare Access** as the auth mode.
2. Enter your server address (e.g., `wss://claw.ks-infra.dev`).
3. Tap **"Log in via Cloudflare"** — a WebView opens with Cloudflare's login page.
4. Authenticate using your email (or any identity provider configured in Cloudflare).
5. Once authenticated, the app captures the `CF_Authorization` cookie and returns to Settings showing **"Logged in via Cloudflare"**.
6. Tap **Save and Connect** — the cookie is sent with the WebSocket handshake, and Cloudflare forwards the connection to OpenClaw.

**How it works under the hood:**

```
Phone → wss://claw.example.com
     → Cloudflare CDN (verifies CF_Authorization cookie)
     → Cloudflare Tunnel (encrypted)
     → localhost:18789 (OpenClaw gateway, trusted-proxy mode)
```

> [!NOTE]
> Cloudflare Access sessions expire based on your configured **Session Duration** (default: 24 hours). When the session expires, the app will show an "Auth expired (Cloudflare 403)" error — simply go to Settings and tap "Log in via Cloudflare" again to re-authenticate.

> [!TIP]
> To avoid frequent re-logins, increase the session duration in **Cloudflare Dashboard → Zero Trust → Access → Applications → Your App → Session Duration** (up to 30 days).

---

## 🔌 OpenClaw Integration

### Cron Events (JSON Payload)

Configure cron jobs on the OpenClaw side to trigger device actions:

```json
{
  "jobId": "morning-reminder",
  "jobName": "Morning Briefing",
  "message": "Good morning! You have 3 meetings today.",
  "actions": ["notify", "speak", "vibrate"]
}
```

| Action | Result |
| :--- | :--- |
| `notify` | 🔔 Show a push notification |
| `speak` | 🗣️ Read message via TTS |
| `vibrate` | 📳 Trigger vibration |
| `sound` | 🔊 Play notification sound |

---

## 📂 Project Structure

- 🧩 **`app/`** — DI, navigation, and entry point.
- 🏛️ **`core/domain/`** — Business logic, ports, and models.
- 💾 **`core/data/`** — Android-specific data adapters (DataStore, Vibration).
- 🔌 **`libs/`** — External integrations (WebSocket, TTS, STT).
- 📱 **`feature/`** — UI modules (Chat, Notifications).

---

## 🛡️ Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | WebSocket connection to OpenClaw |
| `RECORD_AUDIO` | Speech-to-text input |
| `VIBRATE` | Cron event vibration alerts |
| `POST_NOTIFICATIONS` | Push notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Persistent cron event listener |

## License

This project is licensed under the MIT License.
