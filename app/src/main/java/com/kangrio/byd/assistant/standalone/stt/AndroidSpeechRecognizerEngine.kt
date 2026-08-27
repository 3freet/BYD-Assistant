package com.kangrio.byd.assistant.standalone.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidSpeechRecognizerEngine(private val context: Context) : SttEngine {

    override suspend fun transcribe(languageTag: String?): SttResult = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return@withContext SttResult.Failure(SttError.NOT_AVAILABLE)
        }

        suspendCancellableCoroutine<SttResult> { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            fun finish(result: SttResult) {
                if (continuation.isActive) continuation.resume(result)
                recognizer.destroy()
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) finish(SttResult.Failure(SttError.NO_SPEECH_DETECTED))
                    else finish(SttResult.Success(text))
                }

                override fun onError(error: Int) {
                    val reason = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttError.NO_SPEECH_DETECTED
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SttError.NETWORK_ERROR
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttError.PERMISSION_DENIED
                        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SttError.NOT_AVAILABLE
                        else -> SttError.UNKNOWN
                    }
                    finish(SttResult.Failure(reason))
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                normalizeLanguageTag(languageTag)?.let { tag ->
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                }
            }
            recognizer.startListening(recognizerIntent)

            continuation.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    // SpeechRecognizer generally expects a concrete locale rather than a bare language code.
    private fun normalizeLanguageTag(tag: String?): String? = when (tag) {
        null, "" -> null
        "ar" -> "ar-SA"
        "en" -> "en-US"
        else -> tag
    }
}
