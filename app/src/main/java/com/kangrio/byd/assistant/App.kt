package com.kangrio.byd.assistant

import android.app.Application
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.util.Preferences

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        OtaUpdater.createNotificationChannel(this)
        OtaUpdater.checkUpdateInBackground(this, force = false)
        VoiceWakeService.startService(this)
    }
}