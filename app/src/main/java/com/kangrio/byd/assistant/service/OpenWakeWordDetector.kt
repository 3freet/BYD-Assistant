package com.kangrio.byd.assistant.service

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenWakeWordDetector(
    private val context: Context,
    private val modelName: String = "hey_billy",
    private val sensitivity: Float = 0.5f,
    private val onDetected: () -> Unit
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeWordEngine: WakeWordEngine? = null
    private val modelsPath = "openwakeword/models"
    private val modelFile = "$modelsPath/$modelName.onnx"

    /**
     * Starts the wake word engine.
     * @return true if the engine was successfully started, false otherwise.
     */
    fun start(): Boolean {
        if (context.assets.list(modelsPath)?.firstOrNull { it.removeSuffix(".onnx") == modelName } == null) {
            log("No models found in assets")
            return false
        }

        val models = listOf(
            WakeWordModel(
                name = modelName,
                modelPath = modelFile,
                threshold = sensitivity
            )
        )

        val engine = WakeWordEngine(
            context = context,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = 5000L
        )
        wakeWordEngine = engine

        scope.launch {
            engine.detections.collect { detection ->
                log("Wake word: \"${detection.model.name}\" (score=${String.format("%.3f", detection.score)})")
                onDetected()
            }
        }

        engine.start()
        log("Wake word engine started (${models.first().modelPath}, threshold=${models.first().threshold})")
        return true
    }

    fun stop() {
        try {
            wakeWordEngine?.stop()
        } catch (_: Exception) {
        }
        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }
        wakeWordEngine = null
        log("Wake word engine stopped")
    }

    fun pause() {
        wakeWordEngine?.stop()
    }

    fun resume() {
        wakeWordEngine?.start()
    }

    fun log(msg: String) {
        Log.d("OpenWakeWordDetector", msg)
    }
}