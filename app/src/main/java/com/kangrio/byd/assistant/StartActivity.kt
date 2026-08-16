package com.kangrio.byd.assistant

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.kangrio.byd.assistant.util.Utils
import com.kangrio.byd.assistant.activity.PermissionOnboardingActivity
import com.kangrio.byd.assistant.service.VoiceWakeService

class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceWakeService.startService(this)
    }

    override fun onResume() {
        super.onResume()
        if (Utils.setupCompleted(this)) {
            continueStartup()
        } else {
            startActivity(Intent(this, PermissionOnboardingActivity::class.java))
            finish()
        }
    }

    private fun continueStartup() {
        if (Utils.setupCompleted(this)) {
            Utils.startVoiceAssistant(this)
        }
        finishAffinity()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != REQUEST_CODE_RECORD_AUDIO) {
            return
        }

        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Require Record Audio Permission", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 1
    }
}