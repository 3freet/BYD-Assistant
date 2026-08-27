package com.kangrio.byd.assistant.vehicle.intent

import com.kangrio.byd.assistant.vehicle.VehicleCommandRegistry
import com.kangrio.byd.assistant.vehicle.VehicleParameter

private val NUMBER = Regex("\\d+")

/**
 * Deterministic, offline, bilingual keyword matcher. One algorithm for every language: phrases
 * are matched as an order-independent token set (tolerates STT filler words and word-order
 * variance), and only the phrase/template data in [VehicleIntentSpec] varies per language.
 */
object KeywordVehicleIntentMatcher {

    /**
     * Returns the resolved parameter value if [spec] matches [normalizedText] for any of
     * [languages], or null if it doesn't match at all.
     */
    fun tryMatch(spec: VehicleIntentSpec, normalizedText: String, languages: List<String>): Int? {
        val textTokens = normalizedText.split(' ').filterNot { it.isEmpty() }.toSet()

        val matchedPhrase = languages
            .flatMap { spec.phrasesByLanguage[it].orEmpty() }
            .firstOrNull { phrase -> phraseTokensContainedIn(phrase, textTokens) }
            ?: return null

        return when (spec) {
            is VehicleIntentSpec.FixedValue -> spec.value
            is VehicleIntentSpec.NumericSlot -> resolveNumericValue(spec, normalizedText)
        }
    }

    private fun phraseTokensContainedIn(phrase: String, textTokens: Set<String>): Boolean {
        val phraseTokens = TextNormalization.normalize(phrase).split(' ').filterNot { it.isEmpty() }
        if (phraseTokens.isEmpty()) return false
        return phraseTokens.all { it in textTokens }
    }

    private fun resolveNumericValue(spec: VehicleIntentSpec.NumericSlot, normalizedText: String): Int? {
        val rawValue = NUMBER.find(normalizedText)?.value?.toIntOrNull() ?: return null
        val range = (VehicleCommandRegistry.byId(spec.commandId)?.parameter as? VehicleParameter.Range) ?: return rawValue
        return rawValue.coerceIn(range.min, range.max)
    }
}
