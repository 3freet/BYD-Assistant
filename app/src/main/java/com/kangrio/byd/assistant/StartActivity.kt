package com.kangrio.byd.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import com.kangrio.byd.assistant.util.Utils
import androidx.core.net.toUri
import com.kangrio.byd.assistant.service.VoiceWakeService

class StartActivity : Activity() {

    private var startupCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        if (!startupCompleted) {
            continueStartup()
        }
    }

    private fun continueStartup() {
        if (!Utils.isGranted(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_RECORD_AUDIO
            )
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
            )
            return
        }

        startupCompleted = true

        VoiceWakeService.startService(this)

        if (Utils.setupCompleted(this)) {
            Utils.startVoiceAssistant(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
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

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            continueStartup()
        } else {
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 1
    }
}