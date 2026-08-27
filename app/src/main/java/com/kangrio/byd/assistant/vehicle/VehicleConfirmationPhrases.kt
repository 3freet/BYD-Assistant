package com.kangrio.byd.assistant.vehicle

/** Bilingual spoken confirmations for a dispatched vehicle command. */
object VehicleConfirmationPhrases {

    fun confirmationFor(matched: MatchedVehicleCommand, result: VehicleDispatchResult, languageTag: String?): String {
        val lang = if (languageTag == "ar") "ar" else "en"
        return when (result) {
            is VehicleDispatchResult.Success -> successPhrase(matched, lang)
            is VehicleDispatchResult.Blocked -> blockedPhrase(lang)
            is VehicleDispatchResult.Failure -> failurePhrase(lang)
        }
    }

    private fun successPhrase(matched: MatchedVehicleCommand, lang: String): String {
        val template = matched.confirmationTemplateByLanguage[lang] ?: return genericSuccess(lang)
        return if (template.contains("%d")) template.format(matched.value) else template
    }

    private fun genericSuccess(lang: String) = if (lang == "ar") "تم." else "Done."
    private fun blockedPhrase(lang: String) = if (lang == "ar") "لا يمكنني تنفيذ هذا الأمر." else "I can't do that."
    private fun failurePhrase(lang: String) =
        if (lang == "ar") "حدث خطأ أثناء تنفيذ الأمر." else "Something went wrong with that command."
}
