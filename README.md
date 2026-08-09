# Assistant

Assistant (`com.kangrio.byd.assistant`) is an Android utility designed for BYD vehicle infotainment units and custom Android head units.

It configures Google Assistant (`com.google.android.googlequicksearchbox`) as the default voice interaction service on the device and provides a direct launcher shortcut for voice commands.

---

## Features

- **One-Tap Voice Launcher**: Once set up, launching the app immediately invokes Google Voice Assistant (`android.intent.action.VOICE_COMMAND`) instead of displaying the configuration screen.
- **On-Device ADB Granting**: Uses `dadb` to request and grant `WRITE_SECURE_SETTINGS` locally over ADB without needing a computer.
- **Boot Persistence**: A background broadcast receiver re-applies voice assistant settings whenever the device reboots.
- **Jetpack Compose UI**: Built with Jetpack Compose and Material 3 for the initial setup interface.
- **Manual ADB Support**: Includes fallback instructions for granting permissions manually via standard ADB commands.

---

## Architecture and Flow

```
                        +---------------------------+
                        |   User Launches App       |
                        +-------------+-------------+
                                      |
                                      v
                        +---------------------------+
                        |      StartActivity        |
                        +-------------+-------------+
                                      |
                       Is WRITE_SECURE_SETTINGS Granted
                       & Voice Assistant Enabled?
                               /             \
                             YES              NO
                             /                 \
                            v                   v
        +-----------------------+   +-----------------------+
        | Trigger Voice Command |   |   Open MainActivity   |
        | (Google Assistant)    |   |   (Setup & Grant UI)  |
        +-----------------------+   +-----------------------+
```

1. **StartActivity**: Main entry point for the launcher icon.
   - Checks whether `WRITE_SECURE_SETTINGS` is granted and if Google Assistant is active.
   - If configured, it launches Google Assistant (`com.google.android.googlequicksearchbox`) directly and exits.
   - If not configured, it forwards the user to `MainActivity`.
2. **MainActivity**: Configuration dashboard.
   - Allows users to grant permissions locally via ADB.
   - Enables Google Voice Assistant service settings.
   - Provides a test button to trigger a voice command.
3. **BootReceiver**: Catches `Intent.ACTION_BOOT_COMPLETED` events to ensure secure settings persist across device reboots.

---

## Secure Settings

The application modifies the following `Settings.Secure` system keys to register Google Assistant:

| Key | Value |
| :--- | :--- |
| `assistant` | `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService` |
| `voice_interaction_service` | `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService` |

---

## Requirements and Setup

### Prerequisites
- Android 7.0 (API level 24) or higher.
- Google App (`com.google.android.googlequicksearchbox`) installed on the target device.

### Setup
1. Install **Google App**
2. Install **Gemini**
3. Install **Assistant** this app
4. Allow **Auto-start** for Google, Gemini, and Assistant
5. Allow **all required permissions for all apps**
6. Open Google App and sign in to your Google account
7. Open Gemini and allow the requested permissions
8. Open Assistant and press **Next** until **Test** appears


### Granting Secure Settings Permission

Modifying assistant settings requires `android.permission.WRITE_SECURE_SETTINGS`.

#### Method 1: In-App Automatic Grant
1. Open the application (`MainActivity`).
2. Tap **Permission: Next**. The app will discover local ADB ports via `dadb` and grant the permission automatically.

#### Method 2: Manual ADB Command
If automatic ADB discovery fails or is unsupported on the head unit, connect via PC or Wireless ADB and run:

```bash
adb shell pm grant com.kangrio.byd.assistant android.permission.WRITE_SECURE_SETTINGS
```

After executing the command, return to the app and tap **Enable voice assistant**.

---

## Repository Structure

```
Assistant/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/com/kangrio/byd/assistant/
│               ├── MainActivity.kt          # Configuration UI (Compose)
│               ├── SplashActivity.kt        # Entry point / Launcher handler
│               ├── receiver/
│               │   └── BootReceiver.kt      # Re-applies settings after boot
│               ├── ui/theme/                # Material 3 theme styling
│               └── util/
│                   └── PermissionUtil.kt    # ADB helper and secure settings functions
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building from Source

### Requirements
- Android Studio Ladybug or JDK 11+
- Gradle 8.x

### Build Commands

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Build outputs are saved to `app/build/outputs/apk/<variant>/` using the following naming convention:
`Assistant-<versionName>(<versionCode>)-<variant>-<timestamp>.apk`

---

## Permissions

- `android.permission.WRITE_SECURE_SETTINGS`: Required to switch system voice interaction services.
- `android.permission.RECEIVE_BOOT_COMPLETED`: Used to restore settings on boot.
- `android.permission.INTERNET`: Used by `dadb` to communicate with the local ADB daemon.
- `android.permission.POST_NOTIFICATIONS`: For system notifications.

---

## License

Distributed under the MIT License.
