package com.kangrio.byd.assistant.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PermissionUtil {
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

    private fun putSecureSetting(context: Context, key: String, value: String?) {
        Settings.Secure.putString(context.contentResolver, key, value)
    }

    suspend fun adbRequestPermission(context: Context, permission: String) =
        withContext(Dispatchers.IO) {
            setupUserHome(context)
            val dAdb = Dadb.discover() ?: run {
                Log.e("PermissionUtil", "No device found")
                return@withContext
            }
            dAdb.shell("pm grant ${context.packageName} $permission")
        }

    private fun setupUserHome(context: Context) {
        val userHome = System.getProperty("user.home") ?: ""
        if (userHome.isEmpty()) {
            val userDir = context.filesDir.absolutePath
            System.setProperty("user.home", userDir)
        }
    }
}