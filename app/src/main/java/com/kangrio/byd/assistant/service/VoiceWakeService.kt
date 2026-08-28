package com.kangrio.byd.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kangrio.byd.assistant.R
import com.kangrio.byd.assistant.activity.SettingsActivity
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.standalone.StandaloneAssistantController
import com.kangrio.byd.assistant.util.OperationMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class VoiceWakeService : Service() {
    private var detector: OpenWakeWordDetector? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    lateinit var toast: Toast

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var floatingButtonView: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d("VoiceWakeService", "Screen turned ON, checking for OTA update...")
                OtaUpdater.checkUpdateInBackground(context, force = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isStarted = true
        createNotificationChannel()

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        OtaUpdater.checkUpdateInBackground(this, force = false)

        scope.launch {
            startHotwordDetection()
        }

        updateFloatingButtonVisibility()
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

            SET_AUDIO_SOURCE -> {
                val source = intent.extras?.getInt("source") ?: return START_STICKY
                setAudioSource(source)
            }

            START_STANDALONE_SESSION -> {
                scope.launch(Dispatchers.IO) {
                    detector?.pause()
                    runStandaloneSession()
                    detector?.resume()
                }
            }

            REFRESH_FLOATING_BUTTON -> {
                updateFloatingButtonVisibility()
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
        scope.launch {
            Preferences.hotwordSensitivity = sensitivity
            restartHotwordDetection()
        }
    }

    fun setAudioSource(source: Int) {
        scope.launch {
            Preferences.micAudioSource = source
            restartHotwordDetection()
        }
    }

    fun setModel(modelName: String) {
        scope.launch {
            Preferences.hotwordModelName = modelName
            restartHotwordDetection()
        }
    }

    fun startHotwordDetection() {
        if (!Utils.isGranted(this, android.Manifest.permission.RECORD_AUDIO)) {
            showToast("""Please grant the microphone permission to start hotword detection.""")
            return
        }
        if (!Utils.setupCompleted(this@VoiceWakeService) || !Preferences.startHotword) return

        val modelName = Preferences.hotwordModelName
        val audioSource = Preferences.micAudioSource
        detector?.stop()
        detector?.destroy()
        detector = OpenWakeWordDetector(
            context = this@VoiceWakeService,
            audioSource = audioSource,
            modelName = modelName,
            sensitivity = Preferences.hotwordSensitivity,
            onDetected = { onWakeWordDetected() },
            onError = { error ->
                isWakeWordStarted = false
                Log.e("VoiceWakeService", "Hotword detection stopped unexpectedly", error)
                showToast("Hotword detection stopped unexpectedly")
            },
        )

        isWakeWordStarted = detector?.start() == true
        if (isWakeWordStarted) {
            showToast("""Hotword Detection Started""")
        } else {
            showToast("""Hotword Detection Failed""")
        }
    }

    fun stopHotwordDetection() {
        detector?.stop()
        detector?.destroy()
        detector = null
        isWakeWordStarted = false
        showToast("""Hotword Detection Stopped""")
    }

    suspend fun restartHotwordDetection() {
        stopHotwordDetection()
        delay(1000.milliseconds)
        startHotwordDetection()
    }

    private fun onWakeWordDetected() {
        scope.launch(Dispatchers.IO) {
            detector?.pause()

            if (Preferences.operationMode == OperationMode.STANDALONE_AI) {
                // Held for the actual session duration (STT+LLM+TTS), not a fixed debounce,
                // to avoid the detector's own AudioRecord contending for the mic mid-session.
                runStandaloneSession()
            } else {
                withContext(Dispatchers.Main) {
                    Utils.startVoiceAssistant(this@VoiceWakeService)
                }
                delay(DEBOUNCE_DELAY_MS.milliseconds)
            }

            detector?.resume()
        }
    }

    private suspend fun runStandaloneSession() {
        StandaloneAssistantController.runSession(this@VoiceWakeService) { status -> showToast(status) }
    }

    override fun onDestroy() {
        isStarted = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        stopHotwordDetection() // also destroys the detector's own scope, see stopHotwordDetection()
        removeFloatingButton()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /** Adds or removes the floating overlay button to match current settings/permission state.
     * This is the primary way to trigger voice mode now that opening the app just shows its UI —
     * see [com.kangrio.byd.assistant.StartActivity]. Safe to call repeatedly/idempotently. */
    private fun updateFloatingButtonVisibility() {
        val shouldShow = Preferences.showFloatingButton &&
            Settings.canDrawOverlays(this) &&
            Utils.setupCompleted(this)

        if (shouldShow && floatingButtonView == null) {
            addFloatingButton()
        } else if (!shouldShow && floatingButtonView != null) {
            removeFloatingButton()
        }
    }

    private fun addFloatingButton() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val view = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            elevation = 8f * resources.displayMetrics.density
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // clampToScreen guards a saved position from a since-changed screen size/orientation,
            // not just future drags — otherwise a stale off-screen position leaves the button
            // permanently untappable with no in-app recovery.
            x = clampToScreen(Preferences.floatingButtonX, size, resources.displayMetrics.widthPixels)
            y = clampToScreen(Preferences.floatingButtonY, size, resources.displayMetrics.heightPixels)
        }

        attachDragToTrigger(view, params, size)

        try {
            windowManager.addView(view, params)
            floatingButtonView = view
            floatingButtonParams = params
        } catch (e: Throwable) {
            Log.e("VoiceWakeService", "Failed to add floating button", e)
        }
    }

    private fun removeFloatingButton() {
        floatingButtonView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        floatingButtonView = null
        floatingButtonParams = null
    }

    /** Keeps a coordinate within [0, screenSize - viewSize] so the button can never be dragged (or
     * restored from a saved position on a since-resized/rotated screen) fully off-screen with no
     * way back short of toggling it off and on again. */
    private fun clampToScreen(coordinate: Int, viewSize: Int, screenSize: Int): Int =
        coordinate.coerceIn(0, (screenSize - viewSize).coerceAtLeast(0))

    /** Distinguishes a drag (reposition, persisted for next time) from a tap (starts voice mode)
     * by movement distance and press duration — the same approach used by every "chat head"-style
     * floating overlay button. */
    private fun attachDragToTrigger(view: View, params: WindowManager.LayoutParams, viewSize: Int) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTimeMs = 0L
        var moved = false
        val dragTolerancePx = CLICK_DRAG_TOLERANCE_DP * resources.displayMetrics.density

        fun persistPosition() {
            Preferences.floatingButtonX = params.x
            Preferences.floatingButtonY = params.y
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTimeMs = System.currentTimeMillis()
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > dragTolerancePx || abs(dy) > dragTolerancePx) moved = true
                    params.x = clampToScreen(initialX + dx.toInt(), viewSize, resources.displayMetrics.widthPixels)
                    params.y = clampToScreen(initialY + dy.toInt(), viewSize, resources.displayMetrics.heightPixels)
                    try {
                        windowManager.updateViewLayout(v, params)
                    } catch (_: Throwable) {}
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved && System.currentTimeMillis() - downTimeMs < CLICK_MAX_DURATION_MS) {
                        Utils.startVoiceAssistant(this)
                    } else {
                        persistPosition()
                    }
                    true
                }

                // The system can steal the touch stream mid-gesture (e.g. another window taking
                // focus) — without this, a position already applied via updateViewLayout() above
                // would never get persisted, leaving the visible and saved positions out of sync.
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) persistPosition()
                    true
                }

                else -> false
            }
        }
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
            var attempts = 0
            override fun run() {
                try {
                    startForegroundSafely()
                } catch (e: Throwable) {
                    attempts++
                    Log.e("VoiceWakeService", "Error starting foreground (attempt $attempts)", e)
                    if (attempts < MAX_START_FOREGROUND_RETRIES) {
                        handler.postDelayed(this, 1000)
                    } else {
                        Log.e("VoiceWakeService", "Giving up on startForeground after $attempts attempts")
                    }
                }
            }
        })
    }

    /**
     * The manifest declares `foregroundServiceType="microphone"`, but RECORD_AUDIO isn't required
     * to reach this service in every [com.kangrio.byd.assistant.util.OperationMode] (see
     * [com.kangrio.byd.assistant.util.Utils.setupCompleted]). On API 29+, `startForeground()`
     * throws `SecurityException` for a microphone-typed start without that permission, so the
     * type actually requested here is chosen at call time instead of always using the manifest's.
     */
    private fun startForegroundSafely() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Utils.isGranted(this, android.Manifest.permission.RECORD_AUDIO)) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        private const val DEBOUNCE_DELAY_MS = 5_000L
        private var isStarted = false
        var isWakeWordStarted = false
            private set

        const val START_HOTWORD_DETECTION =
            "com.kangrio.byd.assistant.action.START_HOTWORD_DETECTION"
        const val STOP_HOTWORD_DETECTION = "com.kangrio.byd.assistant.action.STOP_HOTWORD_DETECTION"
        const val SET_MODEL = "com.kangrio.byd.assistant.action.SET_MODEL"
        const val SET_SENSITIVITY = "com.kangrio.byd.assistant.action.SET_SENSITIVITY"
        const val SET_AUDIO_SOURCE = "com.kangrio.byd.assistant.action.SET_AUDIO_SOURCE"
        const val START_STANDALONE_SESSION = "com.kangrio.byd.assistant.action.START_STANDALONE_SESSION"
        const val REFRESH_FLOATING_BUTTON = "com.kangrio.byd.assistant.action.REFRESH_FLOATING_BUTTON"
        private const val CLICK_DRAG_TOLERANCE_DP = 8
        private const val CLICK_MAX_DURATION_MS = 250L
        private const val MAX_START_FOREGROUND_RETRIES = 10

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

        fun setAudioSource(context: Context, source: Int) {
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = SET_AUDIO_SOURCE
                putExtra("source", source)
            }
            context.startService(intent)
        }

        /** Call after [Preferences.showFloatingButton] changes, or after the overlay permission
         * is granted/revoked, to add/remove the floating button without restarting the service. */
        fun refreshFloatingButton(context: Context) {
            val intent = Intent(context, VoiceWakeService::class.java).apply {
                action = REFRESH_FLOATING_BUTTON
            }
            context.startService(intent)
        }
    }
}