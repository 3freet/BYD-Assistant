package com.kangrio.byd.assistant

import android.app.Application
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.util.CrashLogger
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.SecureCredentials

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        Preferences.init(this)
        SecureCredentials.init(this)
        OtaUpdater.createNotificationChannel(this)
        OtaUpdater.checkUpdateInBackground(this, force = false)
        VoiceWakeService.startService(this)
    }
}