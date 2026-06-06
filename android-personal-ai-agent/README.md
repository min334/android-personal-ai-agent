# Personal AI Agent — Android App

A production-ready Android application for **MinThitSarAung** that acts as a
personal AI assistant activated by tapping anywhere on the screen **10
consecutive times**.

---

## Features

| Feature | Details |
|---------|---------|
| **Global tap detection** | Accessibility Service detects 10 taps ≤500ms apart, across all apps |
| **Gemini AI integration** | Uses `gemini-1.5-flash` via Retrofit with retry & error handling |
| **Voice input** | Android `SpeechRecognizer` — English + Burmese |
| **Text-to-speech** | Android `TextToSpeech` — auto-detects Burmese script |
| **Floating overlay** | Draggable, shows Listening / Thinking / Speaking state |
| **Encrypted API key storage** | `EncryptedSharedPreferences` + Android Keystore |
| **MVVM + Hilt** | Clean architecture with StateFlow, coroutines, DI |
| **Material Design 3** | Full MD3 theming, dark mode ready |

---

## Project Structure

```
app/src/main/java/com/minthitsaraung/personalaiagent/
│
├── PersonalAIAgentApp.kt          # Application class — Hilt + notification channel
│
├── ui/activity/
│   ├── MainActivity.kt            # Permission setup, status display, settings nav
│   └── SettingsActivity.kt        # Tap count, interval, TTS rate/pitch, user name
│
├── viewmodel/
│   └── MainViewModel.kt           # Single ViewModel for both activities
│
├── data/
│   ├── model/
│   │   ├── GeminiModels.kt        # Request/response data classes + AiResult sealed class
│   │   └── UserPreferences.kt     # User settings data class
│   ├── remote/
│   │   └── GeminiApiService.kt    # Retrofit interface for Gemini REST API
│   └── local/
│       ├── PreferencesDataSource.kt   # DataStore (non-sensitive prefs)
│       └── SecureStorageManager.kt    # EncryptedSharedPreferences (API key)
│
├── repository/
│   └── AiRepository.kt            # Single source of truth for AI + prefs data
│
├── ai/
│   └── GeminiService.kt           # API calls, system prompt, retry logic
│
├── accessibility/
│   └── TapDetectionAccessibilityService.kt  # Global 10-tap detection
│
├── overlay/
│   └── FloatingOverlayService.kt  # Floating UI + voice + AI orchestration
│
├── speech/
│   └── SpeechRecognitionManager.kt # SpeechRecognizer wrapper with Flow events
│
├── tts/
│   └── TextToSpeechManager.kt     # TTS wrapper — Burmese detection, rate/pitch
│
├── service/
│   └── BootReceiver.kt            # BOOT_COMPLETED broadcast receiver
│
├── di/
│   └── AppModule.kt               # Hilt module: OkHttp, Retrofit, Gemini, Context
│
└── utils/
    ├── Constants.kt               # App-wide constants
    └── NotificationHelper.kt      # Notification builder helper
```

---

## Quick Start

### 1. Prerequisites

- **Android Studio Hedgehog** (2023.1.1) or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK** with API level 35 installed
- A free **Gemini API key** from [Google AI Studio](https://aistudio.google.com/app/apikey)

### 2. Open the project

```bash
# In Android Studio: File → Open → select the android-personal-ai-agent/ folder
```

### 3. Add your API key

Copy `local.properties.template` to `local.properties` and fill in your values:

```properties
sdk.dir=/path/to/your/Android/sdk
GEMINI_API_KEY=your_key_here
```

> ⚠️ `local.properties` is in `.gitignore` — it will never be committed to version control.

Alternatively, enter the key at runtime in the app's **Settings screen** — it is
stored in Android Keystore-backed encrypted storage.

### 4. Build and run

Click ▶ **Run** in Android Studio, or:

```bash
./gradlew assembleDebug
```

---

## First-Launch Setup

The app's **Setup Checklist** guides you through four steps:

1. **Enable Accessibility Service**  
   Tap the button → find *"Personal AI Agent — Tap Detection"* in the list → enable it.

2. **Enable Overlay Permission**  
   Tap the button → find the app → toggle *"Allow display over other apps"*.

3. **Grant Microphone Permission**  
   Tap the button → allow in the system dialog.

4. **Add Gemini API Key**  
   Enter your key in the text field → tap **Save API Key**.

Once all four are green, the banner *"🎉 All set! Tap anywhere 10 times to activate."* appears.

---

## How to Use

1. From **any screen** (including inside other apps), tap anywhere **10 times quickly**.
2. Feel a double vibration — the floating overlay appears.
3. **Speak** your question or command.
4. The overlay shows "Thinking…" while Gemini processes.
5. The response is read aloud by the TTS engine.
6. The overlay dismisses itself 2 seconds after speech ends, or tap **×** to close.

---

## API Key Security — Three Approaches

### Approach 1: `local.properties` (development)
The key is injected at build time via `BuildConfig.GEMINI_API_KEY`.  
**Pro:** Never in source code. **Con:** Baked into the APK binary (extractable by decompiling).

### Approach 2: EncryptedSharedPreferences (runtime — used by this app)
The user enters the key in the Settings screen. It is stored using Android Keystore-backed
AES-256-GCM encryption. Even on a rooted device it is very difficult to extract.  
**Pro:** Not in APK at all. **Con:** If the user loses their key, they must re-enter it.

### Approach 3: Remote key server (production recommendation)
Your backend authenticates the user and serves the Gemini API key securely.
The key never touches the APK. Requires a backend — not implemented here but
strongly recommended for publicly distributed apps.

---

## Architecture

```
UI (Activity/View)
    ↕ observes StateFlow
ViewModel (AndroidViewModel)
    ↕ calls suspend functions
Repository (AiRepository)
    ↕                    ↕
GeminiService      PreferencesDataSource / SecureStorageManager
    ↕
GeminiApiService (Retrofit)
    ↕
Google Gemini REST API
```

Services (AccessibilityService, FloatingOverlayService) are singletons that
broadcast events to the ViewModel via LocalBroadcastManager.

---

## Configurable Settings

| Setting | Default | Range |
|---------|---------|-------|
| Activation tap count | 10 taps | 3 – 20 |
| Max tap interval | 500 ms | 200 – 2000 ms |
| TTS speech rate | 1.0× | 0.25 – 3.0× |
| TTS pitch | 1.0 | 0.5 – 2.0 |
| User name | MinThitSarAung | Any text |

---

## Minimum Requirements

- Android 8.0 (API 26) or higher
- Internet connection (for Gemini API calls)
- A TTS engine installed (Google Text-to-Speech is pre-installed on most devices)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Tap detection not working | Check that the Accessibility Service is enabled in system settings |
| Overlay not appearing | Grant "Display over other apps" permission for the app |
| "No speech recognized" | Speak clearly; check microphone permission is granted |
| API errors (401/403) | Check your Gemini API key is correct and has not expired |
| "No internet" error | Check Wi-Fi/mobile data; the Gemini API requires internet |
| TTS not speaking | Install/update Google Text-to-Speech from the Play Store |
| Service killed in background | Disable battery optimisation for the app (button in main screen) |
