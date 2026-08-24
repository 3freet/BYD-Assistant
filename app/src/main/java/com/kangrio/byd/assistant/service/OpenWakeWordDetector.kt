package com.kangrio.byd.assistant.service

import android.content.Context
import android.util.Log
import com.suxsem.androidwakeword.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class OpenWakeWordDetector(
    private val context: Context,
    private val modelName: String = "hey_billy",
    private val sensitivity: Float = 0.5f,
    private val onDetected: () -> Unit
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeWordEngine: WakeWordDetector? = null
    private val modelsPath = "openwakeword/models"
    private val modelFile = "${context.filesDir}/$modelsPath/$modelName.onnx"

    /**
     * Starts the wake word engine.
     * @return true if the engine was successfully started, false otherwise.
     */

    init {
        loadModels()
    }

    fun loadModels() {
        context.assets.list(modelsPath)?.forEach { name ->
            if (!name.endsWith(".onnx")) return@forEach

            val file = File(context.filesDir, "$modelsPath/$name").apply {
                parentFile?.mkdirs()
            }
            if (!file.exists()) {
                context.assets.open("$modelsPath/$name").use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun start(): Boolean {
        if (!File(modelFile).exists()) {
            log("No models found in assets")
            return false
        }

        log("Starting wake word engine")
        val engine = WakeWordDetector(
            context = context,
            modelFile = modelFile,
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