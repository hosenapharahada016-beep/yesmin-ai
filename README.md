# Yesmin AI

Native Android app (Kotlin + Jetpack Compose): Gemini chat, live voice calls, phone commands, coding mode, memory, tools.
Package: `com.yesmin.ai` · minSdk 26 · targetSdk 34 · ABIs: arm64-v8a, armeabi-v7a

> **Honest status:** the source was written carefully and cross-checked, but it has **not been compiled or run** by the
> author (no Android SDK was available while writing). Expect to fix a few small compile errors on the first build.
> Paste any error into Yesmin's Coding mode or back to Claude and it can be fixed quickly.

## Versions (chosen to work together)
| Part | Version |
|---|---|
| JDK | 17 |
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 (Compose compiler 1.5.14, KSP 1.9.24-1.0.20) |
| compileSdk / targetSdk | 34 |

## Two ways to build

### Option A: GitHub Actions (easiest, most reliable, no phone tooling)
1. Create a free GitHub account and a new private repository.
2. Upload the whole `yesmin-ai` folder contents (the `.github/workflows/build.yml` file must be included).
3. Open the repository's **Actions** tab, choose **Build debug APK**, press **Run workflow**.
4. When it finishes, download `yesmin-ai-debug-apk` and install the APK. (Never put your API key in the repository.)

### Option B: Build on the phone in Termux
Building Android apps inside Termux is possible but fiddly, because Google's SDK tools are made for x86 computers.
It needs roughly 4 GB of free storage and a lot of patience. Use Termux from **F-Droid**, not the Play Store.

**1. Install packages**
```bash
pkg update && pkg upgrade -y
pkg install -y openjdk-17 gradle unzip wget git aapt2 apksigner
termux-setup-storage
```

**2. Extract the project** (assuming `yesmin-ai.zip` is in your phone's Downloads)
```bash
cd ~
unzip ~/storage/downloads/yesmin-ai.zip -d ~
cd ~/yesmin-ai
```

**3. Android SDK and Java**
You need `platforms/android-34` and `build-tools/34.0.0` in an ARM-compatible SDK folder.
Install an ARM Android SDK for Termux (for example from the community "android-sdk-tools" / "androidide-tools"
projects on GitHub; check their README for the current install command), then set:
```bash
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/android-sdk        # wherever your SDK folder is
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

**4. Generate the Gradle wrapper jar** (the jar is a binary file, so it is created on your phone)
```bash
gradle wrapper --gradle-version 8.7
chmod +x gradlew
```

**5. Build the debug APK** (use Termux's own aapt2, because the one Gradle downloads won't run on ARM)
```bash
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2 --no-daemon
```
The first build downloads dependencies and can take 10+ minutes. If it runs out of memory, close other apps.

**6. Find the APK**
```bash
ls app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/YesminAI.apk
```

**7. Install**
Open **Downloads** in your file manager, tap `YesminAI.apk`, allow "install unknown apps" for that file manager when asked.

## First run
1. Get a Gemini API key from Google AI Studio.
2. In the app: **Settings → API key → Save → Test connection → Load models**.
3. Check the **Live voice model** field: pick one from "Pick a Live-capable model". Live access depends on your key and plan.
4. Try **AI Chat**, then **Call Yesmin**.

## Troubleshooting
| Problem | Fix |
|---|---|
| `SDK location not found` | Create `local.properties` with `sdk.dir=...` (step 3) |
| `aapt2` errors / "Syntax error: word unexpected" | Use the `-Pandroid.aapt2FromMavenOverride=...` flag |
| `gradle-wrapper.jar is missing` | Run step 4 |
| Out of memory | Lower `-Xmx` in `gradle.properties`, close apps, keep `--no-daemon` |
| `Unsupported class file major version` | Wrong JDK; use 17 |
| Call fails right away (HTTP 400/403/404) | Wrong Live model name or no Live access: use Load models |
| Replies say model not found | Pick another chat model in Settings |
| Voice input button does nothing | Install/enable Google speech services |

## Security notes
- The API key is encrypted with the Android Keystore (`SecureKeyStore`) and never written to Room, logs or source.
- A key inside any installed app can be recovered by someone with full control of the phone. Use a key with a quota
  limit, and never distribute the APK with a key. For a public app, use a server that hands out short-lived Live API tokens.
- Live voice sends your microphone audio to Google's Gemini API.

## Feature status
**Implemented (untested on device):** chat with memory prompt, Hindi/Hinglish/English persona, spoken replies (Android TTS),
speech input (system dialog), live voice call (WebSocket, barge-in, mute, subtitles, timer, foreground service),
memory CRUD with approval prompts, intent-based automation (apps, YouTube search, alarms, timers, calls/SMS drafts, maps,
calendar drafts), optional accessibility control, notification reading, coding mode (copy, save-to-file),
tools (web search via Gemini Google Search tool, translate, study/write, daily plan, photo understanding, ML Kit OCR,
PDF/text summary, voice notes), 10 screens, dark/light themes.

**Not implemented:** screen capture (MediaProjection), floating overlay shortcut, custom wake word,
editing existing project files, in-app scheduled reminders (alarms and events go through the clock/calendar apps).

**Limits worth knowing:** YouTube opens search results (you tap a video); Android confirms an intent was *launched*,
not that you finished the task; OCR is Latin script only.
