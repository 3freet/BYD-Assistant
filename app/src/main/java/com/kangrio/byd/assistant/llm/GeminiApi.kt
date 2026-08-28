package com.kangrio.byd.assistant.llm

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

/** [maxOutputTokens] caps reply length server-side — the system prompt already asks for a short,
 * spoken-friendly reply, but nothing enforced it: an unusually long reply directly extends TTS
 * playback time, which is a real distraction risk while driving, not just a latency/cost concern. */
data class GeminiGenerationConfig(
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null,
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>,
    @SerializedName("role") val role: String? = null
)

data class GeminiPart(@SerializedName("text") val text: String)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(@SerializedName("content") val content: GeminiContent?)
