package com.kangrio.byd.assistant.vehicle.intent

/**
 * Bilingual voice phrasings for the commands most explicitly requested (windows, AC/climate),
 * plus trunk/volume since they're simple and already in the verified hex table. Other registry
 * commands (sunroof percent, sunshade, screen rotation, ambient light, seat heating/ventilation)
 * are addressable but intentionally have no voice phrasing yet — add a spec here the same way
 * as any entry below, no other file needs to change.
 *
 * **Arabic phrases are a first draft, not reviewed by a native speaker** — expect to need real
 * refinement once tested against actual Arabic STT transcripts.
 */
object VehicleIntentSpecs {

    val all: List<VehicleIntentSpec> = listOf(
        // ── A/C power ────────────────────────────────────────────────────────────
        VehicleIntentSpec.FixedValue(
            commandId = "ac.power", value = 1,
            phrasesByLanguage = mapOf(
                "en" to listOf("turn on the ac", "turn on the air conditioning", "turn the ac on", "ac on", "switch on the ac"),
                "ar" to listOf("شغل المكيف", "شغل التكييف", "افتح المكيف"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Turning on the A/C.",
                "ar" to "تم تشغيل المكيف.",
            ),
        ),
        VehicleIntentSpec.FixedValue(
            commandId = "ac.power", value = 0,
            phrasesByLanguage = mapOf(
                "en" to listOf("turn off the ac", "turn off the air conditioning", "turn the ac off", "ac off", "switch off the ac"),
                "ar" to listOf("اطفئ المكيف", "أطفئ المكيف", "اغلق المكيف", "أغلق المكيف"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Turning off the A/C.",
                "ar" to "تم إطفاء المكيف.",
            ),
        ),

        // ── A/C temperature / fan (numeric) ─────────────────────────────────────
        VehicleIntentSpec.NumericSlot(
            commandId = "ac.temperature",
            phrasesByLanguage = mapOf(
                "en" to listOf("set the temperature to", "set the ac temperature to", "set ac to", "change the temperature to"),
                "ar" to listOf("اضبط درجة الحرارة على", "غير درجة الحرارة الى", "خلي الحرارة على"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Setting the temperature to %d degrees.",
                "ar" to "تم ضبط درجة الحرارة على %d درجة.",
            ),
        ),
        VehicleIntentSpec.NumericSlot(
            commandId = "ac.fan",
            phrasesByLanguage = mapOf(
                "en" to listOf("set the fan speed to", "set fan level to", "change fan speed to", "set the ac fan to"),
                "ar" to listOf("اضبط سرعة المروحة على", "غير سرعة المروحة الى"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Setting the fan speed to %d.",
                "ar" to "تم ضبط سرعة المروحة على %d.",
            ),
        ),

        // ── Windows ──────────────────────────────────────────────────────────────
        *windowIntents(
            commandId = "window.driver",
            enNoun = "the driver window", arNoun = "نافذة السائق",
        ),
        *windowIntents(
            commandId = "window.passenger",
            enNoun = "the passenger window", arNoun = "نافذة الراكب",
        ),
        *windowIntents(
            commandId = "window.rearLeft",
            enNoun = "the rear left window", arNoun = "النافذة الخلفية اليسرى",
            enAliasNoun = "the back left window",
        ),
        *windowIntents(
            commandId = "window.rearRight",
            enNoun = "the rear right window", arNoun = "النافذة الخلفية اليمنى",
            enAliasNoun = "the back right window",
        ),

        // ── Sunroof ──────────────────────────────────────────────────────────────
        VehicleIntentSpec.FixedValue(
            commandId = "sunroof", value = 252,
            phrasesByLanguage = mapOf(
                "en" to listOf("open the sunroof", "open sunroof"),
                "ar" to listOf("افتح فتحة السقف", "افتح السقف"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Opening the sunroof.",
                "ar" to "جارِ فتح فتحة السقف.",
            ),
        ),
        VehicleIntentSpec.FixedValue(
            commandId = "sunroof", value = 0,
            phrasesByLanguage = mapOf(
                "en" to listOf("close the sunroof", "close sunroof"),
                "ar" to listOf("اغلق فتحة السقف", "اغلق السقف", "أغلق السقف"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Closing the sunroof.",
                "ar" to "جارِ إغلاق فتحة السقف.",
            ),
        ),

        // ── Trunk ────────────────────────────────────────────────────────────────
        VehicleIntentSpec.FixedValue(
            commandId = "trunk", value = 1,
            phrasesByLanguage = mapOf(
                "en" to listOf("open the trunk", "open trunk", "pop the trunk"),
                "ar" to listOf("افتح الصندوق", "افتح صندوق السيارة"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Opening the trunk.",
                "ar" to "جارِ فتح الصندوق.",
            ),
        ),
        VehicleIntentSpec.FixedValue(
            commandId = "trunk", value = 3,
            phrasesByLanguage = mapOf(
                "en" to listOf("close the trunk", "close trunk"),
                "ar" to listOf("اغلق الصندوق", "أغلق صندوق السيارة"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Closing the trunk.",
                "ar" to "جارِ إغلاق الصندوق.",
            ),
        ),

        // ── Volume (numeric) ─────────────────────────────────────────────────────
        VehicleIntentSpec.NumericSlot(
            commandId = "audio.volume",
            phrasesByLanguage = mapOf(
                "en" to listOf("set the volume to", "set volume to", "change the volume to"),
                "ar" to listOf("اضبط الصوت على", "اضبط مستوى الصوت على", "غير الصوت الى"),
            ),
            confirmationTemplateByLanguage = mapOf(
                "en" to "Setting the volume to %d.",
                "ar" to "تم ضبط مستوى الصوت على %d.",
            ),
        ),
    )

    /** Open/close/stop [VehicleIntentSpec.FixedValue] triples sharing one noun phrase per language. */
    private fun windowIntents(
        commandId: String,
        enNoun: String,
        arNoun: String,
        enAliasNoun: String? = null,
    ): Array<VehicleIntentSpec> {
        val enOpenVerbs = listOfNotNull("open $enNoun", "roll down $enNoun", enAliasNoun?.let { "open $it" })
        val enCloseVerbs = listOfNotNull("close $enNoun", "roll up $enNoun", enAliasNoun?.let { "close $it" })
        val enStopVerbs = listOfNotNull("stop $enNoun")

        return arrayOf(
            VehicleIntentSpec.FixedValue(
                commandId = commandId, value = 1,
                phrasesByLanguage = mapOf("en" to enOpenVerbs, "ar" to listOf("افتح $arNoun")),
                confirmationTemplateByLanguage = mapOf("en" to "Opening $enNoun.", "ar" to "جارِ فتح $arNoun."),
            ),
            VehicleIntentSpec.FixedValue(
                commandId = commandId, value = 2,
                phrasesByLanguage = mapOf("en" to enCloseVerbs, "ar" to listOf("اغلق $arNoun", "أغلق $arNoun")),
                confirmationTemplateByLanguage = mapOf("en" to "Closing $enNoun.", "ar" to "جارِ إغلاق $arNoun."),
            ),
            VehicleIntentSpec.FixedValue(
                commandId = commandId, value = 3,
                phrasesByLanguage = mapOf("en" to enStopVerbs, "ar" to listOf("أوقف $arNoun", "وقف $arNoun")),
                confirmationTemplateByLanguage = mapOf("en" to "Stopping $enNoun.", "ar" to "تم إيقاف $arNoun."),
            ),
        )
    }
}
