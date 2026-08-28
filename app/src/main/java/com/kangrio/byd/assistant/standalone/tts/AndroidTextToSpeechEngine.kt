package com.kangrio.byd.assistant.standalone.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val UTTERANCE_ID = "standalone_reply"

// Watchdog, not the expected path: normally onDone/onError from the engine itself resolves this.
// Exists for vendor/OEM TTS implementations (a real risk on non-Google automotive Android builds)
// that never fire either callback. Generous since a reply can run long, but must still be bounded.
private const val SPEAK_TIMEOUT_MS = 30_000L

class AndroidTextToSpeechEngine(private val context: Context) : TtsEngine {

    override suspend fun speak(text: String, languageTag: String?): TtsResult =
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) { awaitSpeak(text, languageTag) } ?: TtsResult.Failure(TtsError.TIMEOUT)

    private suspend fun awaitSpeak(text: String, languageTag: String?): TtsResult = suspendCancellableCoroutine { continuation ->
        var tts: TextToSpeech? = null

        fun finish(result: TtsResult) {
            if (continuation.isActive) continuation.resume(result)
            tts?.shutdown()
        }

        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                finish(TtsResult.Failure(TtsError.INIT_FAILED))
                return@TextToSpeech
            }

            val locale = if (!languageTag.isNullOrBlank()) Locale.forLanguageTag(languageTag) else Locale.getDefault()
            val languageResult = engine.setLanguage(locale)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                finish(TtsResult.Failure(TtsError.LANGUAGE_NOT_SUPPORTED))
                return@TextToSpeech
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = finish(TtsResult.Success)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(TtsResult.Failure(TtsError.UNKNOWN))
            })

            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }

        continuation.invokeOnCancellation {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
