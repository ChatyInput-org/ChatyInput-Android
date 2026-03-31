package com.tinybear.chatyinput.model

import kotlinx.serialization.json.JsonObject

// Tool 定义（发给 LLM）
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

// 聊天消息（provider 无关的统一格式）
sealed class ChatMessage {
    data class System(val content: String) : ChatMessage()
    data class User(val content: String) : ChatMessage()
    data class Assistant(val content: String?, val toolCalls: List<ToolCall> = emptyList()) : ChatMessage()
    data class ToolResult(val toolCallId: String, val content: String) : ChatMessage()
}

// LLM 返回的 tool 调用
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

// LLM 响应（文本完成 或 tool 调用）
sealed class LLMResponse {
    data class Text(val content: String) : LLMResponse()
    data class ToolUse(val textContent: String?, val toolCalls: List<ToolCall>) : LLMResponse()
}

// Tool 执行副作用
sealed class ToolSideEffect {
    data class ModeSwitched(val modeId: String, val modeName: String, val modeEmoji: String?) : ToolSideEffect()
}

// 带副作用的处理结果
data class ToolAwareProcessingResult(
    val processingResult: ProcessingResult,
    val sideEffects: List<ToolSideEffect> = emptyList()
)
