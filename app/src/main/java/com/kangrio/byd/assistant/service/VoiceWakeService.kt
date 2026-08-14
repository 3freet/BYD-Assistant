package com.kangrio.byd.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.kangrio.byd.assistant.R
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceWakeService : Service() {
    private var detector: SnowboyDetector? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        isStarted = true
        createNotificationChannel()

        scope.launch {
            startHotwordDetection()
        }
    }

    suspend fun startHotwordDetection() = withContext(Dispatchers.IO) {
        detector = SnowboyDetector(
            context = this@VoiceWakeService,
            modelFile = "snowboy/Hey_Rio.pmdl",
            sensitivity = 0.4f,
            audioGain = 1.0f,
        ) {
            onHeyRioDetected()
        }

        detector?.start()
    }

    fun stopHotwordDetection() {
        detector?.stop()
        detector = null
    }

    private fun onHeyRioDetected() {
        scope.launch(Dispatchers.Main) {
            Utils.startVoiceAssistant(this@VoiceWakeService)
        }
    }

    override fun onDestroy() {
        isStarted = false
        stopHotwordDetection()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val handler = Handler(Looper.getMainLooper())

        val manager = getSystemService(
            NotificationManager::class.java
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        handler.post(object : Runnable {
            override fun run() {
                try {
                    startForeground(NOTIFICATION_ID, createNotification())
                } catch (e: Throwable) {
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText("Listening for Hey Rio")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIFICATION_ID = 1001
        private var isStarted = false

        fun startService(context: Context) {
            if (isStarted || !Utils.setupCompleted(context)) return

            val intent = Intent(context, VoiceWakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}