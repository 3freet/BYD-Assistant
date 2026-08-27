package com.kangrio.byd.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiProvider : LlmProvider {
    private const val TAG = "GeminiProvider"

    override val id = "gemini"
    override val displayName = "Google Gemini"
    override val defaultModel = "gemini-2.5-flash"
    override val availableModels = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeminiApi::class.java)

    override suspend fun generateReply(
        apiKey: String,
        model: String,
        userText: String,
        replyLanguage: String?
    ): LlmResult = withContext(Dispatchers.IO) {
        try {
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(userText)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemInstructionFor(replyLanguage))))
            )
            val response = api.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

            if (text.isNullOrBlank()) LlmResult.Failure(LlmError.EMPTY_RESPONSE)
            else LlmResult.Success(text)
        } catch (e: HttpException) {
            Log.e(TAG, "generateReply failed: HTTP ${e.code()}", e)
            val error = when (e.code()) {
                400, 401, 403 -> LlmError.INVALID_API_KEY
                429 -> LlmError.RATE_LIMITED
                else -> LlmError.PROVIDER_ERROR
            }
            LlmResult.Failure(error)
        } catch (e: IOException) {
            Log.e(TAG, "generateReply network error", e)
            LlmResult.Failure(LlmError.NETWORK_ERROR)
        } catch (e: Throwable) {
            Log.e(TAG, "generateReply unexpected error", e)
            LlmResult.Failure(LlmError.PROVIDER_ERROR)
        }
    }

    private fun systemInstructionFor(replyLanguage: String?): String {
        val base = "You are a concise voice assistant embedded in a car. Keep replies short and spoken-friendly."
        val languageName = when (replyLanguage) {
            "ar" -> "Arabic"
            "en" -> "English"
            else -> null
        }
        return if (languageName != null) "$base Always respond in $languageName." else base
    }
}
