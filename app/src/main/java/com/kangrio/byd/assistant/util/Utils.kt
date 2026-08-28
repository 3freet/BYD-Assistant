package com.kangrio.byd.assistant.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.SoundPool
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log
import android.widget.Toast
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit
import androidx.core.net.toUri
import com.kangrio.byd.assistant.Constant
import com.kangrio.byd.assistant.R
import com.kangrio.byd.assistant.VoiceAssistantTriggerActivity
import com.kangrio.byd.assistant.data.AssistantApp
import com.kangrio.byd.assistant.service.VoiceWakeService

object Utils {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .build()

    private var dingSound: Int = -1

    /**
     * Check if a permission is granted.
     */
    fun isGranted(context: Context, permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Set default voice assistant app to be used. Suspends (rather than blocking the calling
     * thread, which used to be the main thread at every call site — a UI click handler or
     * `BroadcastReceiver.onReceive()`) for the settle delay Android needs between clearing and
     * re-setting these secure settings.
     */
    suspend fun enableVoiceAssistant(context: Context, componentName: String? = null) {
        if (componentName.isNullOrEmpty()) return

        withContext(Dispatchers.IO) {
            putSecureSetting(context, "assistant", null)
            putSecureSetting(context, "voice_interaction_service", null)
            delay(100)
            putSecureSetting(context, "assistant", componentName)
            putSecureSetting(context, "voice_interaction_service", componentName)
        }
    }

    /**
     * List all installed voice assistant apps with support for VoiceInteractionService.
     */
    fun listAssistantPackages(context: Context): List<AssistantApp> {
        val pm = context.packageManager

        return pm.queryIntentServices(
            Intent(VoiceInteractionService.SERVICE_INTERFACE),
            0
        ).mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null

            AssistantApp(
                name = serviceInfo.loadLabel(pm).toString(),
                packageName = serviceInfo.packageName,
                className = serviceInfo.name,
            )
        }
    }

    /**
     * Check if a package has support for VoiceInteractionService.
     */
    fun isSupportAssistService(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = Intent(VoiceInteractionService.SERVICE_INTERFACE)
        intent.setPackage(packageName)
        return pm.queryIntentServices(intent, 0).isNotEmpty()
    }

    /**
     * Get the currently selected voice assistant app.
     */
    fun getCurrentAssistantApp(context: Context): AssistantApp {
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant") ?: return AssistantApp()

        val component = ComponentName.unflattenFromString(assistant) ?: return AssistantApp()
        return try {
            val appInfo = context.packageManager.getPackageInfo(component.packageName, 0)
            val appLabel = appInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()
            AssistantApp(
                name = appLabel ?: "",
                packageName = component.packageName,
                className = component.className,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            // The "assistant" secure setting can outlive the app it points at (uninstalled since).
            Log.w("Utils", "Configured assistant package ${component.packageName} not found", e)
            AssistantApp()
        }
    }

    /**
     * Check if voice assistant is enabled.
     */
    fun isEnabledVoiceAssistant(context: Context): Boolean {
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant")
        val voiceInteractionService =
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service")

        return !assistant.isNullOrEmpty() && !voiceInteractionService.isNullOrEmpty()
    }

    /**
     * Check if all required permissions are granted.
     */
    fun setupCompleted(context: Context): Boolean {
        val isAutoStart = isGrantedAutoStart(context)
        return when (Preferences.operationMode) {
            OperationMode.STANDALONE_AI ->
                isGranted(context, Manifest.permission.RECORD_AUDIO) && SecureCredentials.hasAnyKeyConfigured() && isAutoStart

            OperationMode.EXTERNAL_APP, OperationMode.UNSET -> {
                val isGrantedWriteSecureSettings = !isDilink() || isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
                val isEnableVoiceAssistant = isEnabledVoiceAssistant(context)
                val isAssistantAppsInstalled = listAssistantPackages(context).isNotEmpty()
                isGrantedWriteSecureSettings && isEnableVoiceAssistant && isAssistantAppsInstalled && isAutoStart
            }
        }
    }

    /**
     * Play a ding sound when Voice Assistant app is launched.
     */
    private fun playDing(context: Context) {
        if (!Preferences.playDingOnStart) return

        if (dingSound == -1) {
            dingSound = soundPool.load(context, R.raw.ding, 1)
            soundPool.setOnLoadCompleteListener { pool, i, i1 ->
                soundPool.play(dingSound, 1f, 1f, 0, 0, 1f)
            }
        } else {
            soundPool.play(dingSound, 1f, 1f, 0, 0, 1f)
        }
    }

    /**
     * Launch the currently selected voice assistant app.
     */
    fun startVoiceAssistant(context: Context) {
        try {
            // Redirect through a bridge Activity when called from a non-Activity context (the
            // floating button, wake-word detection) — ACTION_ASSIST needs an Activity context.
            if (context !is Activity) {
                context.startActivity(
                    Intent(context, VoiceAssistantTriggerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                return
            }

            if (Preferences.operationMode == OperationMode.STANDALONE_AI) {
                context.startService(Intent(context, VoiceWakeService::class.java).apply {
                    action = VoiceWakeService.START_STANDALONE_SESSION
                })
                return
            }

            val assistantApp = getCurrentAssistantApp(context)
            val packageName = assistantApp.packageName
            val intent = Intent(Intent.ACTION_ASSIST).apply {
                if (packageName.isNotEmpty()) {
                    setPackage(packageName)
                }
            }

            when (packageName) {
                Constant.GOOGLE_APP_PACKAGE -> {
                    intent.action = Intent.ACTION_VOICE_COMMAND
                }

                Constant.CHATGPT_APP_PACKAGE -> {
                    intent.component = ComponentName(packageName, Constant.CHATGPT_APP_ASSISTANT_CLASS_NAME)
                    intent.putExtra("isAssistant", true)
                }
            }

            context.startActivity(intent)
            playDing(context)
        } catch (e: Throwable) {
            Log.e("Utils", "startVoiceAssistant", e)
        }
    }

    /**
     * Get the Notification Listener component name for a given package name.
     */
    fun getNotificationListenerComponentName(context: Context, packageName: String): ComponentName? {
        val intent = Intent("android.service.notification.NotificationListenerService").apply {
            setPackage(packageName)
        }
        val resolveInfos = context.packageManager.queryIntentServices(intent, 0)
        if (resolveInfos.isEmpty()) return null
        val serviceInfo = resolveInfos[0].serviceInfo
        return ComponentName(serviceInfo.packageName, serviceInfo.name)
    }

    /**
     * Check if the Notification Listener service is enabled for a given package name.
     */
    fun isNotificationListenerEnabled(context: Context, packageName: String): Boolean {
        val notificationListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val components = notificationListeners?.split(":") ?: emptyList()
        return components.any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    /**
     * Grant the Notification Listener service permission for a given package name via ADB.
     */
    suspend fun grantNotificationListener(context: Context, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) return@withContext false

            val componentName =
                getNotificationListenerComponentName(context, packageName)?.flattenToString()
                    ?: return@withContext false

            adbShell(context, "cmd notification allow_listener $componentName")
            return@withContext isNotificationListenerEnabled(context, packageName)
        }

    /**
     * Put a secure setting.
     */
    private fun putSecureSetting(context: Context, key: String, value: String?) {
        if (!isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) return

        Settings.Secure.putString(context.contentResolver, key, value)
    }

    /**
     * Run a shell command via ADB. [timeoutMs] defaults to a generous 20s for callers with an
     * explicit "connecting…" UI the user is actively watching (e.g. onboarding's permission
     * grant); pass a much shorter value for a retry attempted synchronously mid-flow, where a
     * dead ADB connection would otherwise silently stall the caller for the full 20s.
     */
    suspend fun adbShell(context: Context, cmd: String, timeoutMs: Long = 20_000L) =
        withContext(Dispatchers.IO) {
            setupUserHome(context)
            val result = withTimeoutOrNull(timeoutMs.milliseconds) {
                while (isActive) {
                    try {
                        var dAdb = Dadb.discover(connectTimeout = 2000, socketTimeout = 5000)
                        dAdb?.shell("echo 'init'") ?: throw Throwable("fail to connect adb")
                        dAdb.close()

                        dAdb = Dadb.discover(connectTimeout = 2000, socketTimeout = 5000)
                        dAdb?.shell(cmd)
                        dAdb?.close()
                        return@withTimeoutOrNull true
                    } catch (e: Throwable) {
                        delay(100L.milliseconds)
                    }
                }
                false
            }

            if (result != true) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ADB connection failed. Please check ADB and try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    /**
     * Request a permission via ADB.
     */
    suspend fun adbRequestPermission(context: Context, permission: String, timeoutMs: Long = 20_000L) =
        withContext(Dispatchers.IO) {
            setupUserHome(context)
            adbShell(context, "pm grant ${context.packageName} $permission", timeoutMs)
        }

    /**
     * setup user home when it is empty required by DAdb library.
     */
    private fun setupUserHome(context: Context) {
        val userHome = System.getProperty("user.home") ?: ""
        if (userHome.isEmpty()) {
            val userDir = context.filesDir.absolutePath
            System.setProperty("user.home", userDir)
        }
    }

    /**
     * Open the Google Play Store to install the app.
     */
    fun openStore(context: Context) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "market://".toUri()
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Throwable) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com".toUri()
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Check if the device is a Dilink device.
     */
    fun isDilink(): Boolean {
        return listOf(
            Build.BRAND,
            Build.FINGERPRINT,
            Build.MODEL,
            Build.PRODUCT
        ).any { it.contains("dilink", ignoreCase = true) }
    }

    /**
     * Check if the app is granted auto-start permission.
     * for dilink device, use last update time instead
     */
    fun isGrantedAutoStart(context: Context): Boolean {
        if (!isDilink()) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return power.isIgnoringBatteryOptimizations(context.packageName)
        }

        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val currentUpdateTime = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val lastUpdateTime = prefs.getLong("last_update_time", 0)
        return lastUpdateTime > currentUpdateTime
    }

    /**
     * Open the auto-start settings for the device.
     * for none dilink device, use ignore battery optimization instead
     */
    @SuppressLint("BatteryLife")
    fun openAutoStartSettings(context: Context) = runCatching {
        if (!isDilink()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
            return@runCatching
        }

        val intent = Intent("android.intent.action.BYD_APPSTARTMANAGEMENT")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Mark the last auto-start update time.
     */
    fun markAutoStartTime(context: Context) {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(
                "last_update_time",
                System.currentTimeMillis()
            )
        }
    }

    /**
     * Format byte count into human-readable size string.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }
}