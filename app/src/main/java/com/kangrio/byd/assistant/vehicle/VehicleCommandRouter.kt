package com.kangrio.byd.assistant.vehicle

import com.kangrio.byd.assistant.vehicle.intent.KeywordVehicleIntentMatcher
import com.kangrio.byd.assistant.vehicle.intent.TextNormalization
import com.kangrio.byd.assistant.vehicle.intent.VehicleIntentSpecs

data class MatchedVehicleCommand(
    val command: VehicleCommand,
    val value: Int,
    val confirmationTemplateByLanguage: Map<String, String>,
)

/**
 * Tier 1 of the voice pipeline: a fully local, offline, deterministic first pass. If this
 * returns null, [com.kangrio.byd.assistant.standalone.StandaloneAssistantController] falls
 * through to the existing cloud LLM conversation path unchanged.
 */
object VehicleCommandRouter {

    fun match(userText: String, languageTag: String?): MatchedVehicleCommand? {
        val normalized = TextNormalization.normalize(userText)
        val languages = languageCandidates(languageTag)

        for (spec in VehicleIntentSpecs.all) {
            val value = KeywordVehicleIntentMatcher.tryMatch(spec, normalized, languages) ?: continue
            val command = VehicleCommandRegistry.byId(spec.commandId) ?: continue
            if (command.domain.isBlocked) continue // defense in depth — see VehicleSafety for the hard floor
            return MatchedVehicleCommand(command, value, spec.confirmationTemplateByLanguage)
        }
        return null
    }

    private fun languageCandidates(languageTag: String?): List<String> = when (languageTag) {
        "ar" -> listOf("ar")
        "en" -> listOf("en")
        else -> listOf("en", "ar") // AUTO: try both — cross-script collisions aren't a realistic risk
    }
}
