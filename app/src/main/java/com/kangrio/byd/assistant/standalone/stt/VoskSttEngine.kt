package com.kangrio.byd.assistant.standalone.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import kotlin.coroutines.resume

/**
 * Fully offline speech recognition via the same public Vosk Android library i99dash bundles —
 * see [VoskModelManager] for how the model directory this is constructed with gets provisioned.
 * Only ever constructed once a model is confirmed present ([VoskModelManager.isProvisioned]); if
 * loading it still fails for some other reason, that's reported as [SttError.NOT_AVAILABLE] so
 * [FallbackSttEngine]'s contract (only that one reason triggers a fallback) stays meaningful even
 * though this engine has no further fallback of its own.
 */
class VoskSttEngine(private val modelDir: String) : SttEngine {

    override suspend fun transcribe(languageTag: String?): SttResult =
        withTimeoutOrNull(LISTEN_TIMEOUT_MS) { awaitTranscription() } ?: SttResult.Failure(SttError.TIMEOUT)

    private suspend fun awaitTranscription(): SttResult = withContext(Dispatchers.IO) {
        val model = try {
            Model(modelDir)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Vosk model at $modelDir", e)
            return@withContext SttResult.Failure(SttError.NOT_AVAILABLE)
        }

        try {
            recognizeOnce(model)
        } finally {
            model.close()
        }
    }

    private suspend fun recognizeOnce(model: Model): SttResult = suspendCancellableCoroutine { continuation ->
        val recognizer = Recognizer(model, SAMPLE_RATE)
        val speechService = try {
            SpeechService(recognizer, SAMPLE_RATE)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start Vosk SpeechService", e)
            recognizer.close()
            if (continuation.isActive) continuation.resume(SttResult.Failure(SttError.UNKNOWN))
            return@suspendCancellableCoroutine
        }

        // Cleanup runs before resume(), not after — the outer withContext's `finally { model.close() }`
        // only runs once this suspend function actually returns, which can't happen before resume()
        // is called, so this ordering is what guarantees the recognizer/service never outlive the model.
        fun finish(result: SttResult) {
            speechService.stop()
            speechService.shutdown()
            recognizer.close()
            if (continuation.isActive) continuation.resume(result)
        }

        speechService.startListening(object : RecognitionListener {
            override fun onResult(hypothesis: String) = finish(textResultFrom(hypothesis))
            override fun onFinalResult(hypothesis: String) = finish(textResultFrom(hypothesis))
            override fun onPartialResult(hypothesis: String) {}

            override fun onError(exception: Exception) {
                Log.e(TAG, "Vosk recognition error", exception)
                finish(SttResult.Failure(SttError.UNKNOWN))
            }

            override fun onTimeout() = finish(SttResult.Failure(SttError.NO_SPEECH_DETECTED))
        })

        continuation.invokeOnCancellation {
            speechService.stop()
            speechService.shutdown()
            recognizer.close()
        }
    }

    /** Vosk's result JSON is `{"text": "..."}` (or empty when nothing was recognized). */
    private fun textResultFrom(hypothesisJson: String): SttResult {
        val text = try {
            JSONObject(hypothesisJson).optString("text").trim()
        } catch (e: Throwable) {
            Log.w(TAG, "Unparseable Vosk result: $hypothesisJson", e)
            ""
        }
        return if (text.isBlank()) SttResult.Failure(SttError.NO_SPEECH_DETECTED) else SttResult.Success(text)
    }

    companion object {
        private const val TAG = "VoskSttEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val LISTEN_TIMEOUT_MS = 15_000L
    }
}
