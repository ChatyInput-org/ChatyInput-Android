# ChatyInput for Android

> Voice input, reimagined. Speak naturally — AI handles the rest.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://github.com/ChatyInput-org/ChatyInput-Android)
[![Website](https://img.shields.io/badge/Web-chatyinput.com-orange.svg)](https://chatyinput.com)

ChatyInput is an open-source voice input keyboard that uses AI to understand what you mean — not just what you say. Dictate content, edit text, calculate totals, switch languages, and send messages, all by voice. The AI classifies every voice segment as an intent and acts accordingly.

---

## Key Features

**Voice Intelligence**
- 4 intents: content / edit / send / undo — AI classifies automatically
- Smart mode: AI proactively edits, calculates, and fills in information
- Strict mode: AI waits for explicit commands before modifying text
- Parallel recording: start the next segment while the previous one processes

**Smart Modes**
- AI auto-selects mode based on app, location, and content
- 4 built-in templates: Business Email, Casual Chat, Technical Docs, Meeting Notes
- Custom trigger conditions per mode ("use when in email apps")
- App binding with optional force lock
- GPS location triggers with NEARBY detection

**Multi-turn Tool Use**
- LLM function calling (OpenAI + Claude)
- `switch_mode` tool for mid-conversation mode switching
- Extensible tool registry for future tools
- Configurable max rounds (1-5)

**Bring Your Own Provider**
- OpenAI (GPT-4o, GPT-4o-mini)
- Anthropic Claude (Sonnet, Haiku)
- Any OpenAI-compatible endpoint (Ollama, vLLM, LM Studio)
- STT: OpenAI Whisper or any Whisper-compatible API

**Privacy**
- No account, no telemetry, no data collection
- API keys encrypted locally (EncryptedSharedPreferences)
- Audio stored on-device only
- GPS location opt-in, never uploaded

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                   Android IME                    │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌─────────────┐ │
│  │ Mode │  │ Rec  │  │ Edit │  │ Send/Delete │ │
│  └──┬───┘  └──┬───┘  └──┬───┘  └─────────────┘ │
│     │         │         │                        │
│     ▼         ▼         ▼                        │
│  ┌─────────────────────────────────────────────┐ │
│  │           RecordingPipeline                  │ │
│  │  Audio → [STT parallel] → [LLM queue]       │ │
│  │           ↓                    ↓             │ │
│  │       Transcript     Intent + Result Text    │ │
│  └─────────────────────────────────────────────┘ │
│     │              │                │            │
│     ▼              ▼                ▼            │
│  ModeResolver   VoiceIntent    ToolExecutor      │
│  (app+GPS+AI)   Processor      (switch_mode)     │
│                 (multi-turn)                      │
└─────────────────────────────────────────────────┘
```

### Project Structure

```
app/src/main/java/com/tinybear/chatyinput/
├── config/                     # Configuration & prompts
│   ├── AppConfig.kt            # All settings (encrypted storage)
│   ├── LanguageConfig.kt       # 10-language system prompts
│   ├── LocaleHelper.kt         # Runtime locale switching
│   └── ModePrompts.kt          # Mode templates + Smart/Strict/Location prompts
│
├── ime/                        # Input Method Editor
│   ├── ChatyInputIME.kt        # IME service (lifecycle, pipeline, VAD)
│   └── KeyboardView.kt         # Compose keyboard UI
│
├── model/                      # Data models
│   ├── Mode.kt                 # Mode + LocationTrigger + AppModeMapping
│   ├── ToolModels.kt           # ChatMessage, ToolCall, LLMResponse
│   ├── ProcessingResult.kt     # LLM response (intent, text, suggestedMode)
│   ├── VoiceIntent.kt          # content | edit | send | undo
│   └── HistoryEntry.kt         # Voice input history record
│
├── service/                    # Core services
│   ├── RecordingPipeline.kt    # STT parallel + LLM queue orchestration
│   ├── VoiceIntentProcessor.kt # Multi-turn tool use loop
│   ├── VadService.kt           # Silero VAD v4 (ONNX Runtime)
│   ├── ModeResolver.kt         # Mode priority resolution + context building
│   ├── ModeManager.kt          # Mode CRUD + app mappings
│   ├── LocationProvider.kt     # FusedLocationProvider + Haversine distance
│   ├── ToolRegistry.kt         # Extensible tool definitions
│   ├── ToolExecutor.kt         # Tool dispatch + side effects
│   ├── LLMProvider.kt          # Interface (complete + completeWithTools)
│   ├── OpenAIProvider.kt       # OpenAI + function calling
│   ├── ClaudeProvider.kt       # Claude + tool use
│   ├── STTProvider.kt          # STT interface
│   ├── WhisperAPIProvider.kt   # Whisper API (+ anti-hallucination)
│   ├── AudioCaptureService.kt  # MediaRecorder + PCM→M4A conversion
│   ├── HistoryManager.kt       # JSON history persistence
│   └── tools/
│       └── SwitchModeTool.kt   # switch_mode tool definition
│
└── ui/                         # Jetpack Compose screens
    ├── MainActivity.kt         # 5-tab navigation + back handling
    ├── VoiceScreen.kt          # Test area with Load to Buffer
    ├── HistoryScreen.kt        # Voice input history
    ├── DictionaryScreen.kt     # Custom words management
    ├── ModeListScreen.kt       # Modes tab (toggles, selection, location)
    ├── ModeEditorScreen.kt     # Mode editor (triggers, apps, locations)
    ├── SettingsScreen.kt       # All configuration
    └── Theme.kt                # Material3 theme
```

---

## Supported Languages

**UI & prompts:** English, French, Spanish, Chinese (Simplified & Traditional), Hindi, Arabic, Portuguese, Japanese, Korean

**Speech recognition:** 99 languages via Whisper

---

## Download

Latest APK: [GitHub Releases](https://github.com/ChatyInput-org/ChatyInput-Android/releases)

No Play Store required. Enable "Install from unknown sources" in device settings.

## Quick Start

1. Install the APK
2. Open ChatyInput > **Settings** > configure STT and LLM API keys
3. Android **Settings > System > Languages & Input > On-screen keyboard** > enable ChatyInput
4. Switch to ChatyInput keyboard in any app
5. Tap mic and speak

## Building from Source

```bash
git clone https://github.com/ChatyInput-org/ChatyInput-Android.git
cd ChatyInput-Android
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

| Component | Technology |
|-----------|-----------|
| UI | Kotlin / Jetpack Compose |
| Networking | OkHttp 4.12 |
| Serialization | kotlinx.serialization 1.7 |
| Voice Detection | ONNX Runtime 1.17 + Silero VAD v4 |
| Location | Google Play Services Location 21.1 |
| Security | EncryptedSharedPreferences |
| Async | Kotlin Coroutines 1.9 |
| Target | Android 8.0+ (API 26) / SDK 35 |

## Roadmap

See [chatyinput.com/#roadmap](https://chatyinput.com/#roadmap)

## License

[GPL-3.0](LICENSE)

## Links

- **Website:** [chatyinput.com](https://chatyinput.com)
- **GitHub:** [ChatyInput-org](https://github.com/ChatyInput-org)
- **Built by:** [Thinkroid](https://thinkroid.com) in Vancouver
