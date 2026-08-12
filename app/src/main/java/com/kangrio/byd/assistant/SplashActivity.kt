package com.kangrio.byd.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kangrio.byd.assistant.util.Utils

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Utils.setupCompleted(this)) {
            Utils.startVoiceAssistant(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finishAffinity()
    }
}