package com.kangrio.byd.assistant.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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

object Utils {
    fun isGranted(context: Context, permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun enableVoiceAssistant(context: Context) {
        val componentName =
            "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
        putSecureSetting(context, "assistant", null)
        putSecureSetting(context, "voice_interaction_service", null)

        Thread.sleep(100)

        putSecureSetting(context, "assistant", componentName)
        putSecureSetting(context, "voice_interaction_service", componentName)
    }

    fun isEnableVoiceAssistant(context: Context): Boolean {
        val componentName =
            "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant")
        val voiceInteractionService =
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service")

        return assistant == componentName
                && voiceInteractionService == componentName
    }

    fun setupCompleted(context: Context): Boolean {
        val isGranted = isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
        val isEnableVoiceAssistant = isEnableVoiceAssistant(context)
        val isGoogleAppInstalled = isGoogleAppInstalled(context)
        val isAutoStart = isGrantedAutoStart(context)
        return isGranted && isEnableVoiceAssistant && isGoogleAppInstalled && isAutoStart
    }

    fun startVoiceAssistant(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND)
            intent.setPackage("com.google.android.googlequicksearchbox")
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun putSecureSetting(context: Context, key: String, value: String?) {
        Settings.Secure.putString(context.contentResolver, key, value)
    }

    suspend fun adbRequestPermission(context: Context, permission: String) =
        withContext(Dispatchers.IO) {
            setupUserHome(context)
            withTimeoutOrNull(10_000L.milliseconds) {
                val dAdb = Dadb.discover(connectTimeout = 5000, socketTimeout = 5000) ?: run {
                    Log.e("PermissionUtil", "No device found")
                    return@withTimeoutOrNull null
                }
                while (isActive) {
                    try {
                        dAdb.shell("pm grant ${context.packageName} $permission")
                        return@withTimeoutOrNull null
                    } catch (e: Throwable) {
                        delay(100L.milliseconds)
                    }
                }
            }
        }

    private fun setupUserHome(context: Context) {
        val userHome = System.getProperty("user.home") ?: ""
        if (userHome.isEmpty()) {
            val userDir = context.filesDir.absolutePath
            System.setProperty("user.home", userDir)
        }
    }

    fun isGoogleAppInstalled(context: Context): Boolean {
        val packageName = "com.google.android.googlequicksearchbox"
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openGoogleAppInStore(context: Context) {
        val packageName = "com.google.android.googlequicksearchbox"
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Throwable) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun isDilink(): Boolean {
        return Build.FINGERPRINT.contains("dilink", true)
    }

    // use last update time instead
    fun isGrantedAutoStart(context: Context): Boolean {
        if (!isDilink()) return true

        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val currentUpdateTime = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val lastUpdateTime = prefs.getLong("last_update_time", 0)
        return lastUpdateTime > currentUpdateTime
    }

    fun openAutoStartSettings(context: Context) = runCatching {
        val intent = Intent("android.intent.action.BYD_APPSTARTMANAGEMENT")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun markAutoStartTime(context: Context) {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(
                "last_update_time",
                System.currentTimeMillis()
            )
        }
    }
}