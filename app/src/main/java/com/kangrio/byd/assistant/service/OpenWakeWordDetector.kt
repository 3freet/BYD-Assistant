package com.kangrio.byd.assistant.service

import android.content.Context
import android.util.Log
import com.kangrio.byd.assistant.util.WakeWordModelManager
import com.suxsem.androidwakeword.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class OpenWakeWordDetector(
    private val context: Context,
    private val audioSource: Int = android.media.MediaRecorder.AudioSource.DEFAULT,
    private val modelName: String = "hey_billy",
    private val sensitivity: Float = 0.5f,
    private val onDetected: () -> Unit,
    private val onError: ((Throwable) -> Unit)? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var wakeWordEngine: WakeWordDetector? = null

    @Volatile
    private var detectionJob: Job? = null

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
        detectionJob = launchDetection(engine, previousJob = null)
        return true
    }

    /** Runs [WakeWordDetector.startDetection] on [scope], reporting a crash via [onError] instead
     * of letting it die silently — a bare `Log.e` here isn't enough since nothing else in the call
     * chain would otherwise notice the detector stopped. Optionally waits for a prior job to
     * actually finish first, so two detection loops can never run concurrently against the same
     * [WakeWordDetector] instance's shared, non-thread-safe state (used by [resume]). */
    private fun launchDetection(engine: WakeWordDetector, previousJob: Job?): Job = scope.launch {
        previousJob?.join()
        if (wakeWordEngine !== engine) return@launch // superseded by a stop()/start() while we waited
        try {
            engine.startDetection()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("OpenWakeWordDetector", "Detection loop crashed", e)
            onError?.invoke(e)
        }
    }

    /** Synchronous by design (matches every existing caller, including non-suspend ones like
     * `Service.onDestroy()`/`onStartCommand()`): signals the detection loop to stop, waits briefly
     * for it to actually exit, then releases native resources — never releases them out from under
     * a loop iteration still in flight. */
    fun stop() {
        val engine = wakeWordEngine
        val job = detectionJob
        wakeWordEngine = null
        detectionJob = null

        engine?.pauseDetection()
        runBlocking {
            withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { job?.join() }
        }
        try {
            engine?.releaseResources()
        } catch (e: Exception) {
            Log.e("OpenWakeWordDetector", "releaseResources failed", e)
        }
        log("Wake word engine stopped")
    }

    fun pause() {
        wakeWordEngine?.pauseDetection()
    }

    fun resume() {
        val engine = wakeWordEngine ?: return
        detectionJob = launchDetection(engine, previousJob = detectionJob)
    }

    /** Tears this instance down for good — call once [stop] has been called and this detector
     * won't be reused, e.g. in `Service.onDestroy()` or right before replacing it with a fresh
     * instance. Not calling this leaks this detector's [scope] (bounded, since [stop] already
     * joins its job, but unbounded if [stop] is never called either). */
    fun destroy() {
        scope.cancel()
    }

    fun log(msg: String) {
        Log.d("OpenWakeWordDetector", msg)
    }

    companion object {
        // stop() can run on the main thread (Service.onStartCommand()), so this stays short —
        // AudioRecord.stop() (called by pauseDetection() just above) unblocks a pending read()
        // near-instantly in practice; this is a safety bound, not the expected wait.
        private const val STOP_JOIN_TIMEOUT_MS = 500L
    }
}
