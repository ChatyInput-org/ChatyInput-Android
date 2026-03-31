package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.model.ChatMessage
import com.tinybear.chatyinput.model.LLMResponse
import com.tinybear.chatyinput.model.ToolDefinition

// LLM 服务接口
interface LLMProvider {
    // 单轮文本完成（向后兼容）
    suspend fun complete(systemPrompt: String, userMessage: String): String

    // 多轮 tool use（默认不支持，子类按需覆盖）
    suspend fun completeWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse = throw UnsupportedOperationException("Tool use not supported by this provider")

    // 是否支持 tool use
    val supportsToolUse: Boolean get() = false
}

// LLM 错误
sealed class LLMError(message: String) : Exception(message) {
    class InvalidResponse : LLMError("Invalid API response")
    class ApiError(val detail: String) : LLMError("API error: $detail")
    class NetworkError(cause: Throwable) : LLMError("Network error: ${cause.message}")
}
