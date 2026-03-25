package com.tinybear.chatyinput.service

// LLM 服务接口
interface LLMProvider {
    suspend fun complete(systemPrompt: String, userMessage: String): String
}

// LLM 错误
sealed class LLMError(message: String) : Exception(message) {
    class InvalidResponse : LLMError("Invalid API response")
    class ApiError(val detail: String) : LLMError("API error: $detail")
    class NetworkError(cause: Throwable) : LLMError("Network error: ${cause.message}")
}
