package com.kangrio.byd.assistant

import android.app.Application
import com.kangrio.byd.assistant.service.VoiceWakeService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        VoiceWakeService.startService(this)
    }
}