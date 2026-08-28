package com.kangrio.byd.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        OtaUpdater.checkUpdateInBackground(context, force = false)
        VoiceWakeService.startService(context)

        // enableVoiceAssistant is now suspend (no more blocking the main thread here) — goAsync()
        // keeps the receiver (and process) alive for the short delay it needs to settle.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Utils.enableVoiceAssistant(context, Preferences.assistantPackageComponent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
