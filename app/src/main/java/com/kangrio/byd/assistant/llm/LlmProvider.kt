package com.kangrio.byd.assistant.llm

interface LlmProvider {
    val id: String
    val displayName: String
    val defaultModel: String
    val availableModels: List<String>

    /** [replyLanguage] is a BCP-47 tag like "ar"/"en", or null/blank to let the model infer it. */
    suspend fun generateReply(apiKey: String, model: String, userText: String, replyLanguage: String?): LlmResult
}

sealed interface LlmResult {
    data class Success(val text: String) : LlmResult
    data class Failure(val reason: LlmError) : LlmResult
}

enum class LlmError { INVALID_API_KEY, RATE_LIMITED, NETWORK_ERROR, PROVIDER_ERROR, EMPTY_RESPONSE }

object LlmProviders {
    val all: List<LlmProvider> = listOf(GeminiProvider)
    fun byId(id: String): LlmProvider? = all.find { it.id == id }
}
