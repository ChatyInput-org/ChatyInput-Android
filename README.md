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
- Smart mode: AI proactively edits, calculates, fills in information, and optimizes formatting
- Strict mode: AI waits for explicit commands before modifying text
- Smart/Strict toggle directly on the keyboard
- All prompts (System, Edit, Smart, Strict) fully customizable in Settings
- Parallel recording: start the next segment while the previous one processes

**Smart Modes**
- AI auto-selects mode based on app, GPS location, and input content
- 4 built-in templates: Business Email, Casual Chat, Technical Docs, Meeting Notes
- Custom trigger conditions per mode ("use when in email apps")
- App binding with installed app picker + optional force lock
- GPS location triggers with "Use Current Location" and NEARBY detection
- Dynamic language switching based on GPS (opt-in, system language unchanged)
- IME mode button: Auto (AI decides) or lock a specific mode

**Multi-turn Tool Use**
- LLM function calling: OpenAI function calling + Claude tool use
- `switch_mode` tool for mid-conversation mode switching
- Extensible tool registry for adding more tools
- Configurable max rounds (1-5) in Settings
- Graceful fallback for providers without tool support

**Voice Activity Detection**
- Silero VAD v4 via ONNX Runtime — local, no cloud
- 3 recording modes: PTT (hold) / Toggle (tap) / Hands-free (VAD)
- Adjustable silence threshold (0.5s - 3.0s)
- Flush on stop: manually stopping VAD submits remaining speech (no lost audio)
- 5-layer Whisper anti-hallucination: VAD threshold, speech ratio, min duration + RMS, verbose_json params, no_speech_prob filter

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
┌──────────────────────────────────────────────────────────┐
│                       Android IME                         │
│  ┌────────┐  ┌────────┐  ┌──────┐  ┌──────┐  ┌───────┐ │
│  │  Mode  │  │Smart/  │  │ Rec  │  │ Edit │  │ Send/ │ │
│  │(Auto/  │  │Strict  │  │(PTT/ │  │      │  │Delete │ │
│  │ Lock)  │  │Toggle  │  │VAD)  │  │      │  │       │ │
│  └───┬────┘  └───┬────┘  └──┬───┘  └──┬───┘  └───────┘ │
│      │           │          │         │                   │
│      ▼           ▼          ▼         ▼                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │              RecordingPipeline                      │  │
│  │                                                     │  │
│  │  Audio ──→ [VAD filter] ──→ [STT parallel]         │  │
│  │                                    ↓                │  │
│  │            [Smart/Strict prompt] + [Mode suffix]    │  │
│  │            + [Location context] + [Mode context]    │  │
│  │                        ↓                            │  │
│  │              [LLM queue + Tool Use loop]            │  │
│  │                        ↓                            │  │
│  │              Intent + Result Text + Side Effects    │  │
│  └────────────────────────────────────────────────────┘  │
│      │              │                │                    │
│      ▼              ▼                ▼                    │
│  ModeResolver   VoiceIntent     ToolExecutor              │
│  (manual lock   Processor       (switch_mode)             │
│   > app forced  (multi-turn                               │
│   > LLM suggest  max 1-5                                  │
│   > app mapping  rounds)                                  │
│   > default)                                              │
└──────────────────────────────────────────────────────────┘
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
│   ├── ChatyInputIME.kt        # IME service (lifecycle, pipeline, VAD, pending buffer)
│   └── KeyboardView.kt         # Compose keyboard UI (mode/smart buttons, inline panels)
│
├── model/                      # Data models
│   ├── Mode.kt                 # Mode + LocationTrigger + AppModeMapping
│   ├── ToolModels.kt           # ChatMessage, ToolCall, LLMResponse, ToolSideEffect
│   ├── ProcessingResult.kt     # LLM response (intent, text, suggestedMode)
│   ├── VoiceIntent.kt          # content | edit | send | undo
│   └── HistoryEntry.kt         # Voice input history record
│
├── service/                    # Core services
│   ├── RecordingPipeline.kt    # STT parallel + LLM queue + Smart/Strict + GPS language
│   ├── VoiceIntentProcessor.kt # Multi-turn tool use loop (max rounds configurable)
│   ├── VadService.kt           # Silero VAD v4 (ONNX Runtime) + flush on stop
│   ├── ModeResolver.kt         # Mode priority: manual > app-forced > LLM > app > default
│   ├── ModeManager.kt          # Mode CRUD + app mappings (with format migration)
│   ├── LocationProvider.kt     # FusedLocationProvider + caching + Haversine distance
│   ├── ToolRegistry.kt         # Extensible tool definitions
│   ├── ToolExecutor.kt         # Tool dispatch + side effects (ModeSwitched)
│   ├── LLMProvider.kt          # Interface: complete() + completeWithTools()
│   ├── OpenAIProvider.kt       # OpenAI chat + function calling
│   ├── ClaudeProvider.kt       # Claude messages + tool use
│   ├── STTProvider.kt          # STT interface
│   ├── WhisperAPIProvider.kt   # Whisper API + 5-layer anti-hallucination
│   ├── AudioCaptureService.kt  # MediaRecorder + PCM→M4A conversion
│   ├── HistoryManager.kt       # JSON history persistence
│   └── tools/
│       └── SwitchModeTool.kt   # switch_mode tool definition
│
└── ui/                         # Jetpack Compose screens
    ├── MainActivity.kt         # 5-tab navigation (Voice/History/Dictionary/Modes/Settings)
    ├── VoiceScreen.kt          # Test area + Load to Buffer + Copy + Clear
    ├── HistoryScreen.kt        # Voice input history with audio playback
    ├── DictionaryScreen.kt     # Custom words management
    ├── ModeListScreen.kt       # Modes tab (auto-switch, location, language toggles)
    ├── ModeEditorScreen.kt     # Mode editor (triggers, apps picker, locations, force lock)
    ├── SettingsScreen.kt       # STT/LLM config, prompts, tool rounds, history
    └── Theme.kt                # Material3 theme
```

---

## How It Works

1. **Speak** — Hold PTT, tap toggle, or use hands-free VAD
2. **Process** — STT transcribes in parallel, LLM classifies intent via tool-use loop
3. **AI Decides** — Smart mode: auto-edits, calculates, fills info. Strict mode: waits for commands
4. **Mode Context** — AI sees current app + GPS location + trigger conditions, may switch mode
5. **Send** — Say "send" and text goes directly into the active app

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
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
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

The ChatyInput name, logo, and branding assets are proprietary to Thinkroid Technology Ltd. and may not be used without permission.

## Links

- **Website:** [chatyinput.com](https://chatyinput.com)
- **GitHub:** [ChatyInput-org](https://github.com/ChatyInput-org)
- **Contact:** [thinkroid.com/contact](https://www.thinkroid.com/contact/)
- **Built by:** [Thinkroid](https://thinkroid.com) in Vancouver
