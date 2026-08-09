package com.kangrio.byd.assistant.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kangrio.byd.assistant.util.PermissionUtil

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PermissionUtil.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) return
        PermissionUtil.enableVoiceAssistant(context)
    }
}