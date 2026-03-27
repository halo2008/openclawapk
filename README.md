# ClawAPK

Android companion app for [OpenClaw](https://github.com/openclaw/openclaw) — an AI agent gateway. Talk to your AI assistant by voice or text, and receive spoken responses, push notifications, and scheduled alerts directly on your phone.

## Features

- **Voice & Text Chat** — Speak or type messages to OpenClaw. Responses are displayed in a Material 3 chat UI.
- **Text-to-Speech Output** — Agent responses are read aloud via [Piper](https://github.com/rhasspy/piper) (Polish) or [Kokoro](https://github.com/remsky/kokoro-fastapi) (English) TTS engines.
- **Speech-to-Text Input** — Android's built-in speech recognizer converts your voice to text, supporting Polish and English.
- **Cron Event Listener** — A foreground service maintains a persistent WebSocket connection and reacts to OpenClaw cron events with:
  - Push notifications
  - **Spoken announcements** (TTS readout of the event message)
  - Vibration patterns
  - Configurable per-event actions
- **Localization** — Full Polish and English UI, auto-selected based on device language.
- **Flexible Auth** — Supports token, password, device pairing, or no-auth modes.
- **Configurable Server** — Enter any OpenClaw instance URL (domain or LAN IP) in the settings screen.

## Architecture

Hexagonal (Ports & Adapters) architecture with a multi-module Gradle project, following SOLID and Clean Code principles.

```
┌─────────────────────────────────────────────────────────────────┐
│                          :app                                   │
│              Composition Root · DI · Navigation                 │
├──────────────────────┬──────────────────────────────────────────┤
│    :feature:chat     │         :feature:notifications           │
│  Chat UI · Settings  │  Foreground Service · Push Notifications │
├──────────────────────┴──────────────────────────────────────────┤
│                       :core:domain                              │
│          Models · Ports (interfaces) · Use Cases                │
│                    Pure Kotlin — no Android                     │
├──────────────┬──────────────┬──────────────┬────────────────────┤
│ :libs:websocket │  :libs:tts  │  :libs:stt  │    :core:data     │
│ OkHttp WS ↔    │ Piper/Kokoro│ Android STT │ ExoPlayer·DataStore│
│ OpenClaw Proto  │  HTTP API   │  Recognizer │ Vibration·Settings │
└──────────────┴──────────────┴──────────────┴────────────────────┘
              :core:common — Dispatchers · Utilities
```

### Module Dependency Graph

```
:core:domain        ← depends on nothing (pure Kotlin JVM)
:core:common        ← depends on nothing (pure Kotlin JVM)

:libs:websocket     ← :core:domain, :core:common
:libs:tts           ← :core:domain, :core:common
:libs:stt           ← :core:domain, :core:common
:core:data          ← :core:domain, :core:common

:feature:chat       ← :core:domain, :core:common  (never sees adapters)
:feature:notifications ← :core:domain, :core:common

:app                ← all modules (wires adapters to ports via Koin)
```

### Domain Ports

| Port | Responsibility |
|------|---------------|
| `OpenClawGateway` | WebSocket connection, send/receive messages, event stream |
| `TextToSpeechPort` | Synthesize text → audio via Piper or Kokoro |
| `SpeechToTextPort` | Android speech recognizer, recognition state flow |
| `AudioPlayerPort` | Play audio data via ExoPlayer |
| `NotificationPort` | Show push notifications |
| `VibrationPort` | Trigger vibration patterns |
| `SettingsPort` | Persist/retrieve connection config via DataStore |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Networking | OkHttp 4 WebSocket |
| Serialization | kotlinx.serialization |
| DI | Koin 4 |
| Audio | Media3 ExoPlayer |
| Persistence | DataStore Preferences |
| Navigation | Navigation Compose |
| Min SDK | 29 (Android 10) |
| Target SDK | 36 (Android 15) |

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- JDK 11+
- An [OpenClaw](https://github.com/openclaw/openclaw) instance running with gateway enabled

### Build & Install

```bash
git clone https://github.com/your-org/clawapk.git
cd clawapk
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Configuration

1. Launch ClawAPK on your device
2. Tap the **Settings** icon (top-right gear)
3. Enter your OpenClaw server address (e.g. `wss://claw.example.com` or `ws://192.168.1.100:18789`)
4. Select authorization mode:
   - **None** — no authentication
   - **Token** — paste a bearer token
   - **Password** — enter gateway password
   - **Device Pairing** — initiates pairing flow (approve on OpenClaw side)
5. Choose TTS language (Polish via Piper / English via Kokoro)
6. Tap **Save and connect**

## OpenClaw Integration

### WebSocket Protocol

ClawAPK communicates directly with the OpenClaw gateway via WebSocket using JSON frames:

```
Client → Server:  { type: "req",   id: "...", method: "agent", params: { message: "Hello" } }
Server → Client:  { type: "res",   id: "...", ok: true, payload: { ... } }
Server → Client:  { type: "event", event: "agent", payload: { text: "Hi there!" } }
```

### Cron Events

OpenClaw can trigger actions on the device by sending cron events. Configure cron jobs on the OpenClaw side with a payload like:

```json
{
  "jobId": "morning-reminder",
  "jobName": "Morning Briefing",
  "message": "Good morning! You have 3 meetings today.",
  "actions": ["notify", "speak", "vibrate"]
}
```

| Action | Behavior |
|--------|----------|
| `notify` | Shows a push notification |
| `speak` | **Reads the message aloud** via TTS (Piper/Kokoro) |
| `vibrate` | Triggers a 500ms vibration |
| `sound` | Plays the system notification sound |

Actions can be combined freely. If `actions` is omitted, defaults to `["notify", "speak", "vibrate"]`.

## Project Structure

```
clawapk/
├── app/                              # Composition root
│   ├── src/main/kotlin/.../app/
│   │   ├── ClawApplication.kt        # Koin initialization
│   │   ├── di/AppModule.kt           # Dispatchers binding
│   │   └── navigation/AppNavigation.kt
│   └── src/main/java/.../
│       └── MainActivity.kt           # Entry point, permissions
│
├── core/
│   ├── domain/                        # Pure Kotlin module
│   │   ├── model/                     # Message, Session, CronEvent, ...
│   │   ├── port/                      # 7 port interfaces
│   │   └── usecase/                   # 10 use cases
│   ├── common/                        # CoroutineDispatchers, UUID
│   └── data/                          # Android adapters
│       └── adapter/                   # ExoPlayer, Vibration, DataStore
│
├── libs/
│   ├── websocket/                     # OpenClaw WebSocket client
│   │   ├── model/                     # Frame, ConnectParams, HelloOk
│   │   └── adapter/                   # OkHttpOpenClawGateway
│   ├── tts/                           # Text-to-Speech
│   │   └── adapter/                   # Piper, Kokoro, Composite
│   └── stt/                           # Speech-to-Text
│       └── adapter/                   # AndroidSttAdapter
│
└── feature/
    ├── chat/                          # Chat feature
    │   ├── ui/                        # ChatScreen, SettingsScreen
    │   └── viewmodel/                 # ChatViewModel, SettingsViewModel
    └── notifications/                 # Background listener
        ├── adapter/                   # AndroidNotificationAdapter
        └── service/                   # CronListenerService
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | WebSocket connection to OpenClaw |
| `RECORD_AUDIO` | Speech-to-text input |
| `VIBRATE` | Cron event vibration alerts |
| `POST_NOTIFICATIONS` | Push notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Persistent cron event listener |

## License

This project is licensed under the MIT License.
