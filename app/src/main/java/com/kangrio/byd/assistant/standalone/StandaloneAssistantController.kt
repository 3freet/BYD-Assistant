package com.kangrio.byd.assistant.standalone

import android.content.Context
import android.util.Log
import com.kangrio.byd.assistant.llm.LlmError
import com.kangrio.byd.assistant.llm.LlmProviders
import com.kangrio.byd.assistant.llm.LlmResult
import com.kangrio.byd.assistant.standalone.stt.AndroidSpeechRecognizerEngine
import com.kangrio.byd.assistant.standalone.stt.FallbackSttEngine
import com.kangrio.byd.assistant.standalone.stt.SttEngine
import com.kangrio.byd.assistant.standalone.stt.SttError
import com.kangrio.byd.assistant.standalone.stt.SttResult
import com.kangrio.byd.assistant.standalone.stt.VoskSttEngine
import com.kangrio.byd.assistant.standalone.tts.AndroidTextToSpeechEngine
import com.kangrio.byd.assistant.standalone.tts.TtsResult
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.SecureCredentials
import com.kangrio.byd.assistant.util.VoskModelManager
import com.kangrio.byd.assistant.vehicle.LoggingVehicleController
import com.kangrio.byd.assistant.vehicle.ReflectionVehicleController
import com.kangrio.byd.assistant.vehicle.VehicleCommandRouter
import com.kangrio.byd.assistant.vehicle.VehicleConfirmationPhrases
import com.kangrio.byd.assistant.vehicle.VehicleController
import com.kangrio.byd.assistant.vehicle.VehicleSafety
import kotlinx.coroutines.sync.Mutex

/**
 * Runs one Listen -> think -> speak turn for standalone mode. Status updates go through
 * [onStatus] rather than a persistent UI — this app has no live screen while driving.
 */
object StandaloneAssistantController {
    private const val TAG = "StandaloneAssistant"

    /** Test-injection seam. When null (the default), the controller is chosen fresh on every
     * dispatch from [Preferences.vehicleControlEnabled] — so flipping the settings toggle takes
     * effect immediately, without an app restart. */
    var vehicleControllerOverride: VehicleController? = null

    // Guards against two triggers overlapping (e.g. the floating button tapped while a wake-word
    // session is already listening/speaking) — both would otherwise contend for the same mic and
    // TTS engine. A later trigger is dropped rather than queued; the driver can just try again.
    private val sessionLock = Mutex()

    private fun resolveVehicleController(context: Context): VehicleController =
        vehicleControllerOverride
            ?: if (Preferences.vehicleControlEnabled) ReflectionVehicleController(context.applicationContext)
            else LoggingVehicleController

    /** [AndroidSpeechRecognizerEngine] needs a `RecognitionService` registered on-device — absent
     * on a GMS-less build, where it always fails with [SttError.NOT_AVAILABLE]. Falls back to a
     * fully offline [VoskSttEngine] only when a model has actually been downloaded via Settings
     * (see [VoskModelManager]) — never attempted otherwise. */
    private fun resolveSttEngine(context: Context, languageTag: String?): SttEngine {
        val voskLanguage = resolveVoskLanguage(context, languageTag)
        val vosk = voskLanguage?.let { VoskSttEngine(VoskModelManager.getModelDir(context, it).absolutePath) }
        return FallbackSttEngine(AndroidSpeechRecognizerEngine(context), vosk)
    }

    /** Vosk needs one committed language per recognizer; AUTO has no true on-device dual-language
     * path, so this just picks whichever single offline model is actually provisioned, preferring
     * English if both are. */
    private fun resolveVoskLanguage(context: Context, languageTag: String?): String? {
        val candidates = when (languageTag) {
            "ar" -> listOf("ar")
            "en" -> listOf("en")
            else -> listOf("en", "ar")
        }
        return candidates.firstOrNull { VoskModelManager.isProvisioned(context, it) }
    }

    suspend fun runSession(context: Context, onStatus: (String) -> Unit) {
        if (!sessionLock.tryLock()) {
            Log.w(TAG, "runSession already in progress — ignoring concurrent trigger")
            return
        }
        try {
            runSessionLocked(context, onStatus)
        } finally {
            sessionLock.unlock()
        }
    }

    private suspend fun runSessionLocked(context: Context, onStatus: (String) -> Unit) {
        val languageTag = Preferences.assistantLanguage.bcp47.ifEmpty { null }

        onStatus("Listening…")
        val sttResult = resolveSttEngine(context, languageTag).transcribe(languageTag)
        val userText = when (sttResult) {
            is SttResult.Success -> sttResult.text
            is SttResult.Failure -> {
                Log.w(TAG, "STT failed: ${sttResult.reason}")
                onStatus(sttStatusMessage(sttResult.reason))
                return
            }
        }

        val matched = VehicleCommandRouter.match(userText, languageTag)
        if (matched != null) {
            onStatus("Vehicle command: ${matched.command.displayName}")
            VehicleSafety.assertDispatchAllowed(matched.command)
            val result = resolveVehicleController(context).dispatch(matched.command, matched.value)
            val spoken = VehicleConfirmationPhrases.confirmationFor(matched, result, languageTag)
            onStatus("Speaking…")
            val ttsResult = AndroidTextToSpeechEngine(context).speak(spoken, languageTag)
            if (ttsResult is TtsResult.Failure) {
                Log.w(TAG, "TTS failed: ${ttsResult.reason}")
                onStatus("Couldn't speak the reply.")
            }
            return
        }

        onStatus("Thinking…")
        val provider = LlmProviders.byId(Preferences.llmProviderId) ?: LlmProviders.all.first()
        val apiKey = SecureCredentials.getApiKey(provider.id)
        if (apiKey.isNullOrBlank()) {
            onStatus("No API key configured for ${provider.displayName}.")
            return
        }
        val model = Preferences.llmModel.ifEmpty { provider.defaultModel }

        val llmResult = provider.generateReply(apiKey, model, userText, languageTag)
        val replyText = when (llmResult) {
            is LlmResult.Success -> llmResult.text
            is LlmResult.Failure -> {
                Log.w(TAG, "LLM failed: ${llmResult.reason}")
                onStatus(llmStatusMessage(llmResult.reason, provider.displayName))
                return
            }
        }

        onStatus("Speaking…")
        val ttsResult = AndroidTextToSpeechEngine(context).speak(replyText, languageTag)
        if (ttsResult is TtsResult.Failure) {
            Log.w(TAG, "TTS failed: ${ttsResult.reason}")
            onStatus("Couldn't speak the reply.")
        }
    }

    private fun sttStatusMessage(reason: SttError): String = when (reason) {
        SttError.NO_SPEECH_DETECTED -> "Didn't catch that."
        SttError.NETWORK_ERROR -> "No network for speech recognition."
        SttError.PERMISSION_DENIED -> "Microphone permission is required."
        SttError.NOT_AVAILABLE, SttError.TIMEOUT, SttError.UNKNOWN -> "Speech recognition isn't available right now."
    }

    private fun llmStatusMessage(reason: LlmError, providerName: String): String = when (reason) {
        LlmError.INVALID_API_KEY -> "Check your $providerName API key in Settings."
        LlmError.RATE_LIMITED -> "$providerName is rate-limiting requests — try again shortly."
        LlmError.NETWORK_ERROR -> "No network connection."
        LlmError.PROVIDER_ERROR, LlmError.EMPTY_RESPONSE -> "Couldn't reach $providerName."
    }
}
