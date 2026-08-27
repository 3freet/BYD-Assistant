package com.kangrio.byd.assistant

import android.app.Activity
import android.os.Bundle
import com.kangrio.byd.assistant.util.Utils

/**
 * Theme-less, invisible Activity-context bridge for [Utils.startVoiceAssistant] when it's called
 * from a non-Activity context (the floating button, wake-word detection) — launching the external
 * assistant app via `ACTION_ASSIST` needs an Activity context to do reliably. This is deliberately
 * separate from [StartActivity]: that one is the launcher and just opens the app's UI now: this one
 * exists purely to trigger voice mode and is never shown to the user directly.
 */
class VoiceAssistantTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.startVoiceAssistant(this)
        finish()
    }
}
