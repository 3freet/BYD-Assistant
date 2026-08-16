package com.kangrio.byd.assistant.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kangrio.byd.assistant.ui.onboarding.PermissionOnboardingScreen
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Utils

class PermissionOnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                PermissionOnboardingScreen(
                    onOnboardingFinished = {
                        finishOnboarding()
                    }
                )
            }
        }
    }

    private fun finishOnboarding() {
        if (Utils.setupCompleted(this)) {
            Utils.startVoiceAssistant(this)
        }
        finish()
    }
}
