# Open JARVIS Android

A native, autonomous, hybrid AI agent for Android smartphones inspired by [OpenJarvis](https://github.com/open-jarvis/OpenJarvis), built with Kotlin, Jetpack Compose, Hilt, Room, Gemini REST API, and LiteRT-LM (`com.google.ai.edge.litertlm`).

## Download APK

[![Download Debug APK](https://img.shields.io/badge/Download-Debug_APK-0284C7?style=for-the-badge&logo=android&logoColor=white)](https://github.com/reyyanking110/open-jarvis/actions/runs/45148566182)

1. Open the linked successful Android CI run.
2. Find the `Artifacts` section.
3. Download `open-jarvis-debug-apk`.
4. Extract the ZIP and install `app-debug.apk`.

> **Note:** GitHub Actions build artifacts are temporary and may expire based on repository retention settings.

## Architecture

- **Intelligence Layer:** `ModelCatalog` covering Gemini Cloud (`gemini-2.5-flash`, `gemini-2.5-pro`) and on-device LiteRT-LM models (`Gemma 3 1B INT4`, `Qwen 2.5 1.5B Q8`, `Gemma 3n E2B INT4`).
- **Hybrid Inference Engine:** `GeminiCloudEngine` (OkHttp SSE streaming + native function calling) + `LiteRtLmEngine` (on-device LiteRT-LM) + `HybridInferenceRouter` (online/offline, privacy mode, RAM/thermal/battery-aware routing + feedback-driven adaptive scoring).
- **Autonomous Agent:** `AutonomousAgentEngine` executing a bounded Plan -> Tool Call -> Observation -> Verification -> Recovery loop with explicit user confirmation gates for sensitive actions (`call_phone`, `compose_message`, `write_workspace_file`, `save_memory`) and Room task persistence.
- **Android Tool System:** `DeviceCommandExecutor` & `ToolRegistry` supporting app launching, dialer/SMS/email intents, camera flashlight torch, audio stream volume, alarms & timers, live battery/RAM/storage/thermal telemetry, notifications, Android settings panels, Storage Access Framework (SAF) file operations, and FTS4 memory tools.
- **Voice & Background:** `SpeechRecognizerManager`, `TextToSpeechManager`, `AgentForegroundService` (`specialUse`), and `ModelDownloadWorker` (`WorkManager` + `dataSync`).
- **Persistent Memory & Feedback Learning:** Room SQLite + FTS4 (`MemoryRepository`), conversation history (`ConversationRepository`), and trace/feedback statistics (`LearningRepository`).

## Gemini API Configuration

Never commit real API keys. Choose either option:

1. **Runtime (Recommended):** Open the app -> **Settings** -> enter your Gemini API key. It is encrypted with AES-256-GCM via `AndroidKeyStore` (`SecretVault`).
2. **Local Build (Optional):** Add to your git-ignored `local.properties`:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```

## Local Model Setup (Offline Mode)

Offline inference requires an actual `.litertlm` model file on-device (the app never simulates offline replies):
1. Open the **Models** tab in the app.
2. Tap **Download** (via `WorkManager`) on a supported model (e.g., `Gemma 3 1B IT (INT4)`), **or** tap **Import File** to select a `.litertlm` / `.task` file already on your device via Storage Access Framework.
3. Select your preferred accelerator (`GPU`, `CPU`, or `NPU`).

## Build & Test

```bash
./gradlew clean testDebugUnitTest assembleDebug
```
