package com.kangrio.byd.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kangrio.byd.assistant.util.Utils

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isGranted = Utils.isGranted(this, Manifest.permission.WRITE_SECURE_SETTINGS)
        val isEnableVoiceAssistant = Utils.isEnableVoiceAssistant(this)
        val isGoogleAppInstalled = Utils.isGoogleAppInstalled(this)
        if (isGranted && isEnableVoiceAssistant && isGoogleAppInstalled) {
            Utils.startVoiceAssistant(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finishAffinity()
    }
}