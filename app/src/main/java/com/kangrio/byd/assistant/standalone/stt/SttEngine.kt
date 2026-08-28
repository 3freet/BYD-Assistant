package com.kangrio.byd.assistant.standalone.stt

interface SttEngine {
    /** [languageTag] is a BCP-47 tag like "ar-SA", or null to use the device default. */
    suspend fun transcribe(languageTag: String?): SttResult
}

sealed interface SttResult {
    data class Success(val text: String) : SttResult
    data class Failure(val reason: SttError) : SttResult
}

enum class SttError { NOT_AVAILABLE, NO_SPEECH_DETECTED, NETWORK_ERROR, PERMISSION_DENIED, TIMEOUT, UNKNOWN }
