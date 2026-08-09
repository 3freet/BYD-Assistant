package com.kangrio.byd.assistant.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kangrio.byd.assistant.util.Utils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) return
        Utils.enableVoiceAssistant(context)
    }
}