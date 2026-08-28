package com.kangrio.byd.assistant.standalone.stt

import android.util.Log

/**
 * Tries [primary]; if (and only if) it fails specifically with [SttError.NOT_AVAILABLE] — no
 * `RecognitionService` registered on-device, the expected case on a GMS-less build — tries
 * [fallback] when one was supplied. Any other outcome from [primary] (success, or a different
 * failure reason like no-speech-detected or a permission problem) is returned untouched: an
 * offline engine can't do anything about those, so there's no reason to run it twice.
 */
class FallbackSttEngine(
    private val primary: SttEngine,
    private val fallback: SttEngine?,
) : SttEngine {

    override suspend fun transcribe(languageTag: String?): SttResult {
        val primaryResult = primary.transcribe(languageTag)
        if (fallback == null) return primaryResult
        if (primaryResult !is SttResult.Failure || primaryResult.reason != SttError.NOT_AVAILABLE) {
            return primaryResult
        }

        Log.i(TAG, "Primary STT unavailable, trying offline fallback")
        return fallback.transcribe(languageTag)
    }

    companion object {
        private const val TAG = "FallbackSttEngine"
    }
}
