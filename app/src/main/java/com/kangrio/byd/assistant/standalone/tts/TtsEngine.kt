package com.kangrio.byd.assistant.standalone.tts

interface TtsEngine {
    /** [languageTag] is a BCP-47 tag like "ar"/"en", or null to use the device default. */
    suspend fun speak(text: String, languageTag: String?): TtsResult
}

sealed interface TtsResult {
    data object Success : TtsResult
    data class Failure(val reason: TtsError) : TtsResult
}

enum class TtsError { LANGUAGE_NOT_SUPPORTED, INIT_FAILED, TIMEOUT, UNKNOWN }
