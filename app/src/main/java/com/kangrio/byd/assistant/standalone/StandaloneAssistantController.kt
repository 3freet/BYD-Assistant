package com.kangrio.byd.assistant.standalone

import android.content.Context
import android.util.Log
import com.kangrio.byd.assistant.llm.LlmProviders
import com.kangrio.byd.assistant.llm.LlmResult
import com.kangrio.byd.assistant.standalone.stt.AndroidSpeechRecognizerEngine
import com.kangrio.byd.assistant.standalone.stt.SttResult
import com.kangrio.byd.assistant.standalone.tts.AndroidTextToSpeechEngine
import com.kangrio.byd.assistant.standalone.tts.TtsResult
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.SecureCredentials
import com.kangrio.byd.assistant.vehicle.LoggingVehicleController
import com.kangrio.byd.assistant.vehicle.ReflectionVehicleController
import com.kangrio.byd.assistant.vehicle.VehicleCommandRouter
import com.kangrio.byd.assistant.vehicle.VehicleConfirmationPhrases
import com.kangrio.byd.assistant.vehicle.VehicleController
import com.kangrio.byd.assistant.vehicle.VehicleSafety

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

    private fun resolveVehicleController(context: Context): VehicleController =
        vehicleControllerOverride
            ?: if (Preferences.vehicleControlEnabled) ReflectionVehicleController(context.applicationContext)
            else LoggingVehicleController

    suspend fun runSession(context: Context, onStatus: (String) -> Unit) {
        val languageTag = Preferences.assistantLanguage.bcp47.ifEmpty { null }

        onStatus("Listening…")
        val sttResult = AndroidSpeechRecognizerEngine(context).transcribe(languageTag)
        val userText = when (sttResult) {
            is SttResult.Success -> sttResult.text
            is SttResult.Failure -> {
                Log.w(TAG, "STT failed: ${sttResult.reason}")
                onStatus("Didn't catch that.")
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
                onStatus("Couldn't reach ${provider.displayName}.")
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
}
