package com.kangrio.byd.assistant.vehicle.intent

/**
 * Normalizes an STT transcript before matching it against [VehicleIntentSpec] phrases, so the
 * same matcher works for both English and Arabic without per-language special-casing.
 */
object TextNormalization {

    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670]") // Arabic tashkeel
    private val PUNCTUATION = Regex("[,.!?;:'\"()\\[\\]{}،؛؟\\-]")
    private val WHITESPACE = Regex("\\s+")

    fun normalize(text: String): String {
        var result = text.lowercase()
        result = DIACRITICS.replace(result, "")
        result = normalizeArabicLetters(result)
        result = normalizeDigits(result)
        result = PUNCTUATION.replace(result, " ")
        result = WHITESPACE.replace(result, " ").trim()
        return result
    }

    /** Collapses common alef/ya letter-shape variants so phrasing/spelling variance doesn't break matching. */
    private fun normalizeArabicLetters(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'أ', 'إ', 'آ' -> 'ا'
                    'ى' -> 'ي'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** Eastern Arabic-Indic digits (٠-٩) -> ASCII digits, so numeric-slot extraction works in Arabic. */
    private fun normalizeDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(if (code in 0x0660..0x0669) ('0' + (code - 0x0660)) else c)
        }
        return sb.toString()
    }
}
