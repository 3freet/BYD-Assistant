package com.kangrio.byd.assistant.service

import android.content.Context
import android.util.Log
import com.kangrio.byd.assistant.util.WakeWordModelManager
import com.suxsem.androidwakeword.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class OpenWakeWordDetector(
    private val context: Context,
    private val audioSource: Int = android.media.MediaRecorder.AudioSource.DEFAULT,
    private val modelName: String = "hey_billy",
    private val sensitivity: Float = 0.5f,
    private val onDetected: () -> Unit
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeWordEngine: WakeWordDetector? = null
    private val modelsPath = "openwakeword/models"

    /**
     * Starts the wake word engine.
     * @return true if the engine was successfully started, false otherwise.
     */

    init {
        loadModels()
    }

    fun loadModels() {
        WakeWordModelManager.ensureDefaultModels(context)
    }

    private fun classifierFile(name: String) = File("${context.filesDir}/$modelsPath/$name.onnx")

    fun start(): Boolean {
        var resolvedModelFile: String? = null
        var resolvedTemplate: FloatArray? = null

        val requestedFile = classifierFile(modelName)
        if (requestedFile.exists()) {
            resolvedModelFile = requestedFile.path
        } else {
            val template = WakeWordModelManager.loadCustomTemplate(context, modelName)
            if (template != null) {
                resolvedTemplate = template
            } else {
                log("No model found for \"$modelName\", trying to load default model")
                val defaultFile = classifierFile("hey_billy")
                if (!defaultFile.exists()) {
                    log("No default models found in assets")
                    return false
                }
                resolvedModelFile = defaultFile.path
            }
        }

        log("Starting wake word engine")
        val engine = WakeWordDetector(
            context = context,
            audioSource = audioSource,
            modelFile = resolvedModelFile,
            templateEmbedding = resolvedTemplate,
            verifierFile = null,
            minScore = sensitivity,
            minVerifierScore = 0.1f, // 0 = skip
            onDetected = { score ->
                Log.d("WakeWord", "Detected! score=$score")
                onDetected()
            }
        )
        wakeWordEngine = engine

        scope.launch {
            engine.startDetection()
        }
        return true
    }

    fun stop() {
        try {
            wakeWordEngine?.pauseDetection()
        } catch (_: Exception) {
        }
        try {
            wakeWordEngine?.releaseResources()
        } catch (_: Exception) {
        }
        wakeWordEngine = null
        log("Wake word engine stopped")
    }

    fun pause() {
        wakeWordEngine?.pauseDetection()
    }

    fun resume() {
        scope.launch {
            wakeWordEngine?.startDetection()
        }
    }

    fun log(msg: String) {
        Log.d("OpenWakeWordDetector", msg)
    }
}