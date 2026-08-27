package com.kangrio.byd.assistant.vehicle.intent

/**
 * A recognizable phrasing of a [com.kangrio.byd.assistant.vehicle.VehicleCommand]. One matcher
 * algorithm ([KeywordVehicleIntentMatcher]) works for every language — only the phrase/template
 * data here varies per language, keyed by BCP-47 language ("en"/"ar").
 */
sealed interface VehicleIntentSpec {
    val commandId: String
    val phrasesByLanguage: Map<String, List<String>>
    val confirmationTemplateByLanguage: Map<String, String>

    /** A fixed parameter value, e.g. "open the driver window" -> value = 1 (open). */
    data class FixedValue(
        override val commandId: String,
        val value: Int,
        override val phrasesByLanguage: Map<String, List<String>>,
        override val confirmationTemplateByLanguage: Map<String, String>,
    ) : VehicleIntentSpec

    /** A numeric parameter extracted from the utterance, e.g. "set the AC to 22 degrees". */
    data class NumericSlot(
        override val commandId: String,
        override val phrasesByLanguage: Map<String, List<String>>,
        override val confirmationTemplateByLanguage: Map<String, String>, // "%d" placeholder for the resolved value
    ) : VehicleIntentSpec
}
