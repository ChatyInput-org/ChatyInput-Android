# ChatyInput for Android

> Voice input, reimagined. Speak naturally — AI handles the rest.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://github.com/ChatyInput-org/ChatyInput-Android)
[![Website](https://img.shields.io/badge/Web-chatyinput.com-orange.svg)](https://chatyinput.com)

## What is ChatyInput?

ChatyInput is an open-source voice input app that goes beyond simple speech-to-text. It uses an LLM to understand your **intent** — whether you're dictating new content, editing existing text, or sending a message. No mode switching, no buttons to toggle. Just speak naturally.

## Features

- **LLM Intent Recognition** — AI classifies every voice segment as content, edit, send, or undo. No manual mode switching.
- **Bring Your Own Provider** — Works with OpenAI, Claude, Ollama, vLLM, or any OpenAI-compatible endpoint. Self-host your own models if you want.
- **Hands-free VAD Mode** — Local voice activity detection powered by Silero VAD. Auto-detects when you start and stop speaking.
- **Parallel Recording** — Record the next segment while the previous one is still processing. STT runs in parallel, LLM processes as a queue.
- **Undo** — Say "undo" to revert the buffer to its previous state. Up to 30 levels of history.
- **Custom Dictionary** — Add names, jargon, and domain terms. AI prioritizes phonetically similar matches from your dictionary.
- **Privacy First** — No account, no telemetry, no data collection. API keys encrypted locally. Audio stored on-device only.
- **Full IME Keyboard** — Works as a system keyboard inside any app. PTT button, edit button, buffer display, and send — all inline.

## How It Works

1. **Speak** — Hold the PTT button and speak naturally in any language
2. **Process** — Your STT model transcribes, then the LLM classifies intent and updates the buffer
3. **Edit** *(optional)* — Hold the edit button to refine: "change 3 PM to 5 PM" or "rewrite as a formal email"
4. **Send** — Say "send" and the text goes directly into the active app

## Supported Languages

ChatyInput's UI and prompts are available in **10 languages**:

English, French, Spanish, Chinese (Simplified), Chinese (Traditional), Hindi, Arabic, Portuguese, Japanese, Korean

Speech recognition supports **99 languages** via Whisper — including German, Italian, Russian, Turkish, Vietnamese, Thai, Indonesian, Dutch, Polish, Ukrainian, and many more. [Full list](https://github.com/openai/whisper#available-models-and-languages).

## Download

Download the latest APK from [GitHub Releases](https://github.com/ChatyInput-org/ChatyInput-Android/releases).

No Play Store required. Enable "Install from unknown sources" in your device settings.

## Quick Start

1. Install the APK
2. Open ChatyInput → **Settings** → configure your STT and LLM API keys
3. Go to Android **Settings → System → Languages & Input → On-screen keyboard** → enable ChatyInput
4. In any app, switch to the ChatyInput keyboard
5. Hold the mic button and speak

## Configuration

### STT Providers
- **OpenAI Whisper** — Best accuracy, especially for multilingual input
- **Whisper Compatible** — Any endpoint that implements the Whisper API (e.g., Groq, local Whisper)

### LLM Providers
- **OpenAI** — GPT-4o, GPT-4o-mini, etc.
- **Anthropic Claude** — Sonnet, Haiku, etc.
- **OpenAI Compatible** — Ollama, vLLM, LM Studio, or any OpenAI-compatible endpoint

### Recording Modes
- **PTT (Push-to-Talk)** — Hold to record, release to stop
- **Toggle** — Tap to start, tap to stop
- **Hands-free (VAD)** — Auto-detects speech, tap to start/stop listening

## Building from Source

```bash
git clone https://github.com/ChatyInput-org/ChatyInput-Android.git
cd ChatyInput-Android
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Tech Stack

- Kotlin / Jetpack Compose
- OkHttp for networking
- EncryptedSharedPreferences for secure config storage
- ONNX Runtime + Silero VAD v4 for voice activity detection
- kotlinx.serialization for JSON parsing

## Roadmap

See the full roadmap at [chatyinput.com/#roadmap](https://chatyinput.com/#roadmap).

## License

[GPL-3.0](LICENSE)

## Links

- **Website:** [chatyinput.com](https://chatyinput.com)
- **GitHub:** [ChatyInput-org](https://github.com/ChatyInput-org)
- **Built by:** [Thinkroid](https://thinkroid.com) in Vancouver
