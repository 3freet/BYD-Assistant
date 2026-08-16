package com.kangrio.byd.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.kangrio.byd.assistant.util.Utils
import androidx.core.net.toUri
import com.kangrio.byd.assistant.service.VoiceWakeService

class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceWakeService.startService(this)
    }

    override fun onResume() {
        super.onResume()
        if (setupPermissions()) {
            continueStartup()
        }
    }

    private fun setupPermissions(): Boolean {
        if (!Utils.isGranted(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_RECORD_AUDIO
            )
            return false
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            return false
        }

        return true
    }

    private fun continueStartup() {
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

        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Require Record Audio Permission", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 1
    }
}