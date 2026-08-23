# Assistant

**Assistant** (`com.kangrio.byd.assistant`) is an Android utility and voice interaction bridge tailored for BYD vehicle infotainment units (DiLink) and custom Android head units / AOSP devices.

Launcher shortcuts directly to your preferred AI voice assistant (Google Assistant, ChatGPT, Perplexity, Claude, etc.), provides **always-listening offline hotword / wake-word detection** ("Hey Rio", "Alexa", "Snowboy", or custom trained models), and includes a modern onboarding wizard to configure system-level voice interaction services with ease.

---

## Key Features

- **One-Tap Voice Launcher**:
  - Launching the app via home launcher icon, steering wheel button, or widget immediately invokes your selected voice assistant without opening configuration screens once set up.
  - Custom launch profiles:
    - **Google App / Google Assistant**: Executes `android.intent.action.VOICE_COMMAND`.
    - **ChatGPT**: Direct intent target to ChatGPT's voice assistant mode (`com.openai.voice.assistant.AssistantActivity`).
    - **Other AI / Voice Interaction Services**: Dispatches standard `Intent.ACTION_ASSIST`.
  - Optional audible start chime ("ding") confirmation sound upon activation.

- **Offline Hotword & Wake-Word Detection (`VoiceWakeService`)**:
  - Continuous, low-latency wake-word detection powered by embedded Snowboy engine.
  - Ships with bundled offline models: **Hey Rio** (`hey_rio.pmdl`), **Alexa** (`alexa.pmdl`), and **Snowboy** (`snowboy.pmdl`).
  - Configurable microphone input gain and detection sensitivity sliders.
  - Runs efficiently as a foreground service with a persistent notification and quick-access settings.

- **In-App Custom Wake-Word Trainer**:
  - Built-in training studio powered by Snowboy Seasalt API.
  - Record voice samples directly on the head unit / device (minimum 3 samples).
  - Train custom `.pmdl` wake-word models on the fly.

- **Guided Jetpack Compose Onboarding Wizard (`PermissionOnboardingActivity`)**:
  - Interactive status check and setup workflow for all required capabilities:
    1. **Microphone Access**: Grants audio capture for hotword detection.
    2. **Display Over Other Apps**: Allows launching the assistant UI seamlessly over active apps (e.g. navigation / media).
    3. **Auto-Start & Battery Management**: Native handling for BYD DiLink (`BYD_APPSTARTMANAGEMENT`) and standard Android battery optimization bypass.
    4. **Write Secure Settings**: Automatic on-device grant via local ADB socket (`dadb`) or manual ADB command copy.
    5. **Target AI Assistant Selection**: Scans installed `VoiceInteractionService` providers and allows 1-tap switching.
    6. **Notification Permission**: Enables background status indication (Android 13+).

- **Boot Persistence**:
  - `BootReceiver` captures `BOOT_COMPLETED` to re-apply `Settings.Secure` assistant keys and initialize the voice wake service on startup.

---

## Architecture & Flow

```
                             +---------------------------+
                             |   User Launches App       |
                             |   or Wake Word Detected   |
                             +-------------+-------------+
                                           |
                                           v
                             +---------------------------+
                             |       StartActivity       |
                             +-------------+-------------+
                                           |
                               Is Setup Fully Completed?
                      (Secure Settings, Audio, Assistant App,
                         Auto-Start, Overlay Permissions)
                                    /             \
                                  YES              NO
                                  /                 \
                                 v                   v
             +-----------------------+   +--------------------------------+
             | Trigger Voice Command |   | PermissionOnboardingActivity   |
             |  - Google Assistant   |   |   (Setup Wizard)    |
             |  - ChatGPT Voice      |   +--------------------------------+
             |  - Custom Assist App  |
             +-----------------------+
```

---

## Secure Settings

To designate the default voice interaction provider system-wide, the app configures the following `Settings.Secure` keys:

| Key | Description / Example Value |
| :--- | :--- |
| `assistant` | Flattened component name (e.g., `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService`) |
| `voice_interaction_service` | Flattened component name (e.g., `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService`) |

---

## Setup & Permissions

### Granting `WRITE_SECURE_SETTINGS`

Modifying system assistant keys requires the `android.permission.WRITE_SECURE_SETTINGS` permission.

#### Method 1: Automatic In-App Grant (Local ADB)
1. Open the app onboarding screen.
2. Under **Write Secure Settings**, tap **Grant via Local ADB**.
3. The app connects to the local ADB daemon via `dadb` and grants the permission automatically without needing a computer.

#### Method 2: Manual ADB Command
If local ADB discovery is unavailable, execute the following command via PC or Wireless ADB:

```bash
adb shell pm grant com.kangrio.byd.assistant android.permission.WRITE_SECURE_SETTINGS
```

---

## Project Structure

```
Assistant/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── snowboy/              # Pretrained models (.pmdl) & resources
│           │       ├── common.res
│           │       └── models/
│           │           ├── alexa.pmdl
│           │           ├── hey_rio.pmdl
│           │           └── snowboy.pmdl
│           ├── java/com/kangrio/byd/assistant/
│           │   ├── App.kt                # Application entry point
│           │   ├── Constant.kt           # Shared constants & preference keys
│           │   ├── StartActivity.kt      # Fast launcher & voice intent dispatcher
│           │   ├── activity/
│           │   │   ├── PermissionOnboardingActivity.kt # Guided onboarding host
│           │   │   └── SettingsActivity.kt             # Hotword settings & voice trainer
│           │   ├── data/
│           │   │   └── AssistantApp.kt   # Assistant metadata model
│           │   ├── receiver/
│           │   │   └── BootReceiver.kt   # Restores settings & service on boot
│           │   ├── service/
│           │   │   ├── SnowboyDetector.kt# Audio recorder & Snowboy JNI bridge
│           │   │   └── VoiceWakeService.kt # Foreground service for wake word
│           │   ├── ui/
│           │   │   ├── composable/       # Shared UI components & app icons
│           │   │   ├── onboarding/       # Onboarding wizard screens & permission cards
│           │   │   └── theme/            # Material 3 theme & typography
│           │   └── util/
│           │       ├── Preferences.kt    # SharedPreferences wrapper
│           │       ├── SnowboyTrainClient.kt # API client for custom model training
│           │       └── Utils.kt          # System helpers, dadb runner & intent utilities
│           └── res/
│               └── raw/
│                   └── ding.mp3          # Activation chime
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building from Source

### Requirements
- Android Studio
- Android SDK (API 37 compile target, `minSdk` 24)
- Supported ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64`

### Build Commands

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease

# Build Beta Variant
./gradlew assembleBeta
```

Artifacts will be generated in `app/build/outputs/apk/<variant>/` following the naming format:
`Assistant-<versionName>(<versionCode>)-<variant>-<timestamp>.apk`

---

## Permissions Overview

| Permission                                             | Purpose                                                                        |
|:-------------------------------------------------------|:-------------------------------------------------------------------------------|
| `RECORD_AUDIO`                                         | Required for wake-word listening and voice sample recording.                   |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keeps the hotword detector running reliably in the background.                 |
| `SYSTEM_ALERT_WINDOW`                                  | Enables launching assistant interfaces over active apps.                       |
| `WRITE_SECURE_SETTINGS`                                | Configures system default assistant and voice interaction services.            |
| `RECEIVE_BOOT_COMPLETED`                               | Automatically restarts hotword service and secures settings on device boot.    |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`                 | Prevents Android OS from killing background services in standby.               |
| `POST_NOTIFICATIONS`                                   | Displays the foreground service notification on Android 13+.                   |
| `INTERNET`                                             | Used for local ADB loopback socket communication and wake-word model training. |

---

## Credits

This project builds upon the following open-source libraries and projects:

| Project                                                  | Author / Org | Description                                                                                                                                                                      |
|:---------------------------------------------------------|:-------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Snowboy](https://github.com/Kitt-AI/snowboy)            | Kitt-AI      | Always-on offline hotword detection engine. Powers the `VoiceWakeService` with low-latency wake-word recognition using bundled `.pmdl` models.                                   |
| [dadb](https://github.com/mobile-dev-inc/dadb)           | mobile.dev   | A Kotlin library for communicating with Android devices over ADB directly from the device itself. Used here to automatically grant `WRITE_SECURE_SETTINGS` without needing a PC. |
| [audx-android](https://github.com/rizukirr/audx-android) | rizukirr     | Android audio processing library providing real-time noise suppression and voice activity detection (VAD) to improve wake-word accuracy in noisy environments.                   |


---

## License

Distributed under the [MIT License](LICENSE).
