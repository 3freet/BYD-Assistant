package com.kangrio.byd.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.kangrio.byd.assistant.util.Utils

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isGranted = Utils.isGranted(this, Manifest.permission.WRITE_SECURE_SETTINGS)
        val isEnableVoiceAssistant = Utils.isEnableVoiceAssistant(this)
        val isGoogleAppInstalled = Utils.isGoogleAppInstalled(this)
        if (isGranted && isEnableVoiceAssistant && isGoogleAppInstalled) {
            try {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                intent.setPackage("com.google.android.googlequicksearchbox")
                startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(this, "${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finishAffinity()
    }
}