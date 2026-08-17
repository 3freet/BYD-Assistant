package com.kangrio.byd.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kangrio.byd.assistant.R
import com.kangrio.byd.assistant.StartActivity
import com.kangrio.byd.assistant.activity.SettingsActivity
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceWakeService : Service() {
    private var detector: SnowboyDetector? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    lateinit var toast: Toast

    override fun onCreate() {
        super.onCreate()
        isStarted = true
        createNotificationChannel()

        scope.launch {
            startHotwordDetection()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when(action) {
             START_HOTWORD_DETECTION -> {
                scope.launch {
                    startHotwordDetection()
                }
            }

            STOP_HOTWORD_DETECTION -> {
                stopHotwordDetection()
            }

            SET_MODEL -> {
                val filePath = intent.extras?.getString("pmdlFile") ?: return START_STICKY
                setModel(filePath)
            }

            SET_SENSITIVITY -> {
                val sensitivity = intent.extras?.getFloat("sensitivity") ?: return START_STICKY
                setSensitivity(sensitivity)
            }

            SET_GAIN -> {
                val gain = intent.extras?.getFloat("gain") ?: return START_STICKY
                setGain(gain)
            }
        }

        return START_STICKY
    }

    fun showToast(message: String) {
        handler.post {
            if (::toast.isInitialized) {
                toast.cancel()
            }
            toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    fun setSensitivity(sensitivity: Float) {
        Preferences.hotwordSensitivity = sensitivity
        stopHotwordDetection()
        scope.launch {
            startHotwordDetection()
        }
    }

    fun setGain(gain: Float) {
        Preferences.hotwordGain = gain
        stopHotwordDetection()
        scope.launch {
            startHotwordDetection()
        }
    }

    fun setModel(modelName: String) {
        stopHotwordDetection()
        Preferences.hotwordModelName = modelName
        scope.launch {
            startHotwordDetection()
        }
    }

    fun startHotwordDetection() {
        if (!Utils.setupCompleted(this@VoiceWakeService) || !Preferences.startHotword) return

        val modelName = Preferences.hotwordModelName
        detector?.stop()
        detector = SnowboyDetector(
            context = this@VoiceWakeService,
            modelName = modelName,
            sensitivity = Preferences.hotwordSensitivity,
            audioGain = Preferences.hotwordGain,
        ) {
            onHeyRioDetected()
        }

        detector?.start()
        showToast("""Hotword Detection Started""")
    }

    fun stopHotwordDetection() {
        detector?.stop()
        detector = null
        showToast("""Hotword Detection Stopped""")
    }

    private fun onHeyRioDetected() {
        scope.launch(Dispatchers.Main) {
            startActivity(
                Intent(this@VoiceWakeService, StartActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
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
        val manager = getSystemService(
            NotificationManager::class.java
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Voice Assistant",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        handler.post(object : Runnable {
            override fun run() {
                try {
                    startForeground(NOTIFICATION_ID, createNotification())
                } catch (e: Throwable) {
                    Log.e("VoiceWakeService", "Error creating notification", e)
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText("Click to open settings")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIFICATION_ID = 1001
        private var isStarted = false

        const val START_HOTWORD_DETECTION =
            "com.kangrio.byd.assistant.action.START_HOTWORD_DETECTION"
        const val STOP_HOTWORD_DETECTION = "com.kangrio.byd.assistant.action.STOP_HOTWORD_DETECTION"
        const val SET_MODEL = "com.kangrio.byd.assistant.action.SET_MODEL"
        const val SET_SENSITIVITY = "com.kangrio.byd.assistant.action.SET_SENSITIVITY"
        const val SET_GAIN = "com.kangrio.byd.assistant.action.SET_GAIN"

        fun startService(context: Context) {
            if (isStarted || !Utils.setupCompleted(context)) return

            val intent = Intent(context, VoiceWakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setState(context: Context, state: Boolean) {
            Preferences.startHotword = state
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = if (state) START_HOTWORD_DETECTION else STOP_HOTWORD_DETECTION
            }
            context.startService(intent)
        }

        fun setModel(context: Context, pmdlFilePath: String) {
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = SET_MODEL
                putExtra("pmdlFile", pmdlFilePath)
            }
            context.startService(intent)
        }

        fun setSensitivity(context: Context, sensitivity: Float) {
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = SET_SENSITIVITY
                putExtra("sensitivity", sensitivity)
            }
            context.startService(intent)
        }

        fun setGain(context: Context, gain: Float) {
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = SET_GAIN
                putExtra("gain", gain)
            }
            context.startService(intent)
        }
    }
}