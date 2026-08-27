package com.kangrio.byd.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kangrio.byd.assistant.util.Utils
import com.kangrio.byd.assistant.activity.PermissionOnboardingActivity
import com.kangrio.byd.assistant.activity.SettingsActivity
import com.kangrio.byd.assistant.service.VoiceWakeService

/**
 * The launcher activity — a theme-less redirector (see `Theme.Start`), not a screen of its own.
 * Tapping the app icon opens the real app UI ([SettingsActivity]); voice mode is triggered
 * separately, via the floating overlay button, the notification, or wake-word detection — never
 * by opening the app itself.
 */
class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceWakeService.startService(this)
    }

    override fun onResume() {
        super.onResume()
        if (Utils.setupCompleted(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            startActivity(Intent(this, PermissionOnboardingActivity::class.java))
        }
        finish()
    }
}