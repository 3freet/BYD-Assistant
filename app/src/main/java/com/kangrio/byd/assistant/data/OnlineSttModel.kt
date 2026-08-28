package com.kangrio.byd.assistant.data

/**
 * A downloadable offline speech-recognition model from the official public Vosk model catalog
 * (https://alphacephei.com/vosk/models) — first-party-authored data, not scraped from any
 * third-party app. [languageTag] is the BCP-47-ish tag this project uses elsewhere ("en"/"ar").
 */
data class OnlineSttModel(
    val name: String,
    val languageTag: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val license: String,
    val description: String,
    val isInstalled: Boolean = false,
)

object OnlineSttModels {
    /** Recommended defaults balance accuracy against a large download on what may be a
     * bandwidth/storage-constrained car head unit — not necessarily the smallest option. */
    val catalog: List<OnlineSttModel> = listOf(
        OnlineSttModel(
            name = "vosk-model-small-en-us-0.15",
            languageTag = "en",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sizeBytes = 40L * 1024 * 1024,
            license = "Apache-2.0",
            description = "Lightweight English model (recommended)",
        ),
        OnlineSttModel(
            name = "vosk-model-en-us-0.22",
            languageTag = "en",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
            sizeBytes = 1_800L * 1024 * 1024,
            license = "Apache-2.0",
            description = "Larger, more accurate US English model",
        ),
        OnlineSttModel(
            name = "vosk-model-ar-mgb2-0.4",
            languageTag = "ar",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
            sizeBytes = 318L * 1024 * 1024,
            license = "Apache-2.0",
            description = "General Arabic model, MGB2 broadcast-news data (recommended)",
        ),
        OnlineSttModel(
            name = "vosk-model-small-ar-tn-0.1-linto",
            languageTag = "ar",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip",
            sizeBytes = 158L * 1024 * 1024,
            license = "Apache-2.0",
            description = "Smaller download, Tunisian Arabic dialect specifically",
        ),
        OnlineSttModel(
            name = "vosk-model-ar-0.22-linto-1.1.0",
            languageTag = "ar",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-0.22-linto-1.1.0.zip",
            sizeBytes = 1_300L * 1024 * 1024,
            license = "AGPL-3.0",
            description = "Most accurate Arabic model — AGPL-licensed, review before using",
        ),
    )

    fun forLanguage(languageTag: String): List<OnlineSttModel> = catalog.filter { it.languageTag == languageTag }
}
