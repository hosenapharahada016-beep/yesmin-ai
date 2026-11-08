# Yesmin AI — Architecture

Package: `com.yesmin.ai` · Kotlin · Jetpack Compose (Material 3) · Room · OkHttp · Coroutines

## Verified API facts (from Google docs, checked while planning)
- Text/chat: Gemini Developer API `generateContent` (REST, `x-goog-api-key` header).
- Live voice: WebSocket `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=KEY`
- Audio in: raw PCM16, 16 kHz, mono, little-endian (`audio/pcm;rate=16000`). Audio out: PCM16 24 kHz mono.
- Live model names change often (docs currently show e.g. `gemini-3.8-live`).
  Yesmin therefore **lists models from your key** (`models.list`, filtered by `generateContent` / `bidiGenerateContent`) and lets you pick. Nothing is hardcoded as the only option; defaults are editable text.
- Google recommends ephemeral tokens over raw API keys for production. This app uses your own key stored on-device (see Security).

## Security
- Key stored with `EncryptedSharedPreferences`-style protection via Android Keystore (AES-GCM wrapper in `SecureKeyStore`). Never in source, never in logs, never in Room.
- Limitation: a key on the device is only as safe as the device. Anyone with root or a debugger can extract it. For a public app, use a backend that issues ephemeral tokens.

## Voice modes
1. **Live mode** (primary): `LiveSession` streams mic PCM over WebSocket, plays returned PCM, handles interruption.
2. **Fallback** (no Live access): Android `SpeechRecognizer` -> Gemini text -> Android `TextToSpeech`.

## Folder tree
```
yesmin-ai/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (values, drawable, mipmap-anydpi-v26, xml)
        └── java/com/yesmin/ai/
            ├── MainActivity.kt, YesminApp.kt
            ├── data/   (SecureKeyStore, SettingsStore, db/ Room: MemoryEntity, MemoryDao, AppDb)
            ├── ai/     (GeminiClient, ModelCatalog, Persona, LiveSession, FallbackVoice)
            ├── voice/  (MicCapture, PcmPlayer, CallService)
            ├── automation/ (Actions, CommandRouter, YesminAccessibilityService, YesminNotificationListener)
            ├── tools/  (OcrTool, DocTool, ImageTool)
            └── ui/     (theme/, nav, screens: Home, Chat, Call, Coding, Automation,
                          Memory, Tools, Settings, Permissions, About; components: Orb, Avatar)
```

## Feature status (updated as parts land)
| Feature | Status |
|---|---|
| Gemini chat, settings, test connection, model list | planned Part 2 |
| Live voice call + fallback | planned Part 3 |
| Memory (Room) | planned Part 2 |
| Automation (intents, accessibility, notifications) | planned Part 4 |
| Coding mode | planned Part 5 |
| Tools: OCR, image, PDF/doc, translation, web search (Gemini Google Search grounding) | planned Part 5 |
| Screen capture (MediaProjection) | NOT in v1 — documented as future work |
| Floating overlay, custom wake word | NOT in v1 — documented as future work |
