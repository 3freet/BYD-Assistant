package com.kangrio.byd.assistant.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        OtaUpdater.checkUpdateInBackground(context, force = false)
        Utils.enableVoiceAssistant(context, Preferences.assistantPackageComponent)
        VoiceWakeService.startService(context)
    }
}