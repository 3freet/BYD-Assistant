package com.kangrio.byd.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.kangrio.byd.assistant.util.PermissionUtil

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isGranted = PermissionUtil.isGranted(this, Manifest.permission.WRITE_SECURE_SETTINGS)
        val isEnableVoiceAssistant = PermissionUtil.isEnableVoiceAssistant(this)
        if (isGranted && isEnableVoiceAssistant) {
            try {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                intent.setPackage("com.google.android.googlequicksearchbox")
                startActivity(intent)
            }catch (e: Throwable) {
                Toast.makeText(this, "${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finishAffinity()
    }
}