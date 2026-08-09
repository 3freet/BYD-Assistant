# Assistant

Assistant (`com.kangrio.byd.assistant`) is an Android utility designed for BYD vehicle infotainment units and custom Android head units.

It configures Google Assistant (`com.google.android.googlequicksearchbox`) as the default voice interaction service on the device and provides a direct launcher shortcut for voice commands.

---

## Features

- **One-Tap Voice Launcher**: Once set up, launching the app (`StartActivity`) checks requirements and immediately invokes Google Voice Assistant (`android.intent.action.VOICE_COMMAND`) instead of displaying the configuration screen.
- **Modular 4-Step Setup Wizard**: Jetpack Compose and Material 3 interface guides users through setup step-by-step:
  1. **Google App Check**: Verifies if Google App (`com.google.android.googlequicksearchbox`) is installed, with direct Google Play Store redirection if missing.
  2. **On-Device ADB Granting**: Uses `dadb` to request and grant `WRITE_SECURE_SETTINGS` locally over ADB without needing a computer, complete with retry logic and connection timeouts.
  3. **Voice Assistant Configuration**: Configures required system secure settings keys with one tap.
  4. **Launcher Test**: Includes a direct test button to verify intent execution.
- **Auto-Refresh Lifecycle Observer**: Automatically re-checks permission and package states whenever `MainActivity` resumes (e.g. after installing Google App or altering system settings).
- **Manual ADB Support**: Displays exact `adb` shell commands with a one-click **Copy Command** button as a fallback method.
- **Boot Persistence**: A background broadcast receiver (`BootReceiver`) re-applies voice assistant settings whenever the device reboots.

---

## Architecture and Flow

```
                        +---------------------------+
                        |   User Launches App       |
                        +-------------+-------------+
                                      |
                                      v
                        +---------------------------+
                        |       StartActivity       |
                        +-------------+-------------+
                                      |
             Are WRITE_SECURE_SETTINGS Granted,
             Voice Assistant Enabled, AND Google App Installed?
                               /             \
                             YES              NO
                             /                 \
                            v                   v
        +-----------------------+   +-----------------------+
        | Trigger Voice Command |   |   Open MainActivity   |
        | (Google Assistant)    |   |  (4-Step Setup Guide) |
        +-----------------------+   +-----------------------+
```

1. **StartActivity**: Entry point registered for the launcher icon (`SplashActivity.kt`).
   - Checks whether `WRITE_SECURE_SETTINGS` is granted, Google Assistant service is active, and Google App is installed.
   - If fully configured, it fires `android.intent.action.VOICE_COMMAND` directed at `com.google.android.googlequicksearchbox` and exits immediately.
   - If any requirement is missing, it routes the user to `MainActivity`.
2. **MainActivity**: Step-by-step configuration dashboard (`MainActivity.kt`).
   - Displays modular `StepCard` UI components with completion indicators, descriptions, and active status tracking.
   - Handles auto-refreshing setup states on lifecycle `ON_RESUME`.
3. **BootReceiver**: Catches `Intent.ACTION_BOOT_COMPLETED` events to ensure secure settings persist across device reboots (`receiver/BootReceiver.kt`).

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

### Setup Guide (In-App Wizard)

1. **Step 1: Google App Check**
   - Verify that Google App is installed. If missing, tap **Install Google App** to open Play Store.
2. **Step 2: Grant Secure Settings Permission**
   - Tap **Permission: Next** to automatically grant `WRITE_SECURE_SETTINGS` via local `dadb`.
   - *Fallback*: If auto-grant fails, copy the provided `adb shell pm grant` command and execute it via PC or Wireless ADB.
3. **Step 3: Configure Voice Assistant Service**
   - Tap **Enable Voice Assistant** to set `GsaVoiceInteractionService` in `Settings.Secure`.
4. **Step 4: Test Voice Assistant Launcher**
   - Tap **Test Voice Assistant** to execute a voice command intent.
5. **Auto-Start**:
   - On head units (e.g. BYD DiLink), ensure Auto-start is allowed for Google App, Gemini (if used), and Assistant.

---

## Granting Secure Settings Permission

Modifying assistant settings requires `android.permission.WRITE_SECURE_SETTINGS`.

### Method 1: In-App Automatic Grant (dadb)
1. Open the application (`MainActivity`).
2. Tap **Permission: Next** in Step 2. The app will attempt local ADB connection via `dadb` (with built-in retry mechanism and 10-second timeout) and grant the permission automatically.

### Method 2: Manual ADB Command
If automatic ADB discovery fails or local ADB socket access is disabled on the head unit, execute the following command via PC or Wireless ADB:

```bash
adb shell pm grant com.kangrio.byd.assistant android.permission.WRITE_SECURE_SETTINGS
```

Then return to the app and proceed to **Step 3: Enable Voice Assistant**.

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
│               ├── MainActivity.kt          # 4-Step setup UI & Compose dashboard
│               ├── SplashActivity.kt        # Entry point (StartActivity / launcher handler)
│               ├── receiver/
│               │   └── BootReceiver.kt      # Re-applies settings after boot
│               ├── ui/theme/                # Material 3 theme styling
│               └── util/
│                   └── PermissionUtil.kt    # Dadb helper, store intent, & secure settings
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
