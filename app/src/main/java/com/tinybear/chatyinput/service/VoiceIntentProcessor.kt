package com.tinybear.chatyinput.service

import android.util.Log
import com.tinybear.chatyinput.model.*
import kotlinx.serialization.json.Json

// 核心：语音意图分类 + 文本处理 + 多轮 Tool Use
class VoiceIntentProcessor(
    private val llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val customWords: List<String> = emptyList(),
    private val toolRegistry: ToolRegistry? = null,
    private val toolExecutor: ToolExecutor? = null,
    private val maxToolRounds: Int = 3
) {
    companion object {
        private const val TAG = "VoiceIntentProcessor"
        const val DEFAULT_SYSTEM_PROMPT = """你是一个语音文字助手。用户通过语音输入和处理文字。

每段语音转文字后发给你，判断意图并处理：

1. **content** — 普通内容输入。纠正错别字和语法，result_text 只返回纠正后的**新内容**（不要包含缓冲区已有的文字）。如果用户提供了常用词列表，遇到发音相似的词请优先使用常用词。
2. **edit** — 编辑命令（如"把X改成Y"、"删掉上一句"）。根据命令修改当前缓冲区，result_text 返回修改后的**完整缓冲区全文**。你要很确定用户是真实需要修改他输入的文字才进行修改,需要根据上下文推理.
3. **send** — 发送命令（如"发送"、"确认"、"OK"、"send"）。result_text 留空。你要很确定用户是真实的要发送这段文字了才使用这个命令.
4. **undo** — 撤销命令（如"undo"、"撤销"、"改回去"、"rollback"、"回退"、"还原"）。result_text 留空。将缓冲区恢复到上一次修改之前的状态。

严格只返回 JSON，不要返回任何其他文字，不要用 markdown 代码块包裹：
{"intent": "content", "result_text": "纠正后的新内容", "explanation": "说明"}"""
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun process(
        newSegment: String,
        currentBuffer: String,
        modeContext: String = ""
    ): ToolAwareProcessingResult {
        val userMessage = buildUserMessage(newSegment, currentBuffer, modeContext)

        // 快速路径：无 tools 或 provider 不支持
        if (toolRegistry == null || toolExecutor == null || !llmProvider.supportsToolUse) {
            val response = llmProvider.complete(systemPrompt, userMessage)
            return ToolAwareProcessingResult(parseResponse(response))
        }

        // Tool use 路径：多轮循环
        return processWithTools(userMessage)
    }

    private suspend fun processWithTools(userMessage: String): ToolAwareProcessingResult {
        val messages = mutableListOf<ChatMessage>(
            ChatMessage.System(systemPrompt),
            ChatMessage.User(userMessage)
        )
        val tools = toolRegistry!!.getAll()
        val allSideEffects = mutableListOf<ToolSideEffect>()

        for (iteration in 0 until maxToolRounds) {
            Log.d(TAG, "Tool use iteration ${iteration + 1}/$maxToolRounds")
            val response = llmProvider.completeWithTools(messages, tools)

            when (response) {
                is LLMResponse.Text -> {
                    Log.d(TAG, "Final text response received")
                    return ToolAwareProcessingResult(parseResponse(response.content), allSideEffects)
                }
                is LLMResponse.ToolUse -> {
                    Log.d(TAG, "Tool calls: ${response.toolCalls.map { it.name }}")
                    // 加入 assistant 消息
                    messages.add(ChatMessage.Assistant(
                        content = response.textContent,
                        toolCalls = response.toolCalls
                    ))
                    // 执行每个 tool call
                    for (toolCall in response.toolCalls) {
                        val result = toolExecutor!!.execute(toolCall)
                        allSideEffects.addAll(result.sideEffects)
                        messages.add(ChatMessage.ToolResult(
                            toolCallId = toolCall.id,
                            content = result.content
                        ))
                        Log.d(TAG, "Tool ${toolCall.name} result: ${result.content}")
                    }
                }
            }
        }

        // 超过最大轮次 → fallback 单轮不带 tools
        Log.w(TAG, "Max tool rounds ($maxToolRounds) exceeded, falling back to single-turn")
        val response = llmProvider.complete(systemPrompt, messages
            .filterIsInstance<ChatMessage.User>().first().content)
        return ToolAwareProcessingResult(parseResponse(response), allSideEffects)
    }

    private fun buildUserMessage(newSegment: String, currentBuffer: String, modeContext: String): String {
        return buildString {
            append("当前缓冲区内容：")
            if (currentBuffer.isEmpty()) append("（空）") else append(currentBuffer)
            append("\n\n新语音片段：")
            append(newSegment)
            if (customWords.isNotEmpty()) {
                append("\n\n用户常用词（遇到发音相似的词请优先使用这些）：")
                append(customWords.joinToString("、"))
            }
            if (modeContext.isNotBlank()) {
                append("\n\n")
                append(modeContext)
            }
        }
    }

    private fun parseResponse(response: String): ProcessingResult {
        try {
            return json.decodeFromString<ProcessingResult>(response.trim())
        } catch (_: Exception) {}

        val jsonStr = extractJSON(response)
        if (jsonStr != null) {
            try {
                return json.decodeFromString<ProcessingResult>(jsonStr)
            } catch (_: Exception) {}
        }

        return fallbackParse(response)
    }

    private fun extractJSON(text: String): String? {
        val cleaned = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1)
        return null
    }

    private fun fallbackParse(text: String): ProcessingResult {
        val intentMatch = Regex(""""intent"\s*:\s*"(\w+)"""").find(text)
        val resultMatch = Regex(""""result_text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)
        val explainMatch = Regex(""""explanation"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)
        val modeMatch = Regex(""""suggested_mode"\s*:\s*"([^"]+)"""").find(text)

        val intentStr = intentMatch?.groupValues?.get(1)
            ?: throw ProcessingError.InvalidJSON(text)
        val resultText = resultMatch?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?: ""

        val intent = when (intentStr) {
            "content" -> VoiceIntent.CONTENT
            "edit" -> VoiceIntent.EDIT
            "send" -> VoiceIntent.SEND
            "undo" -> VoiceIntent.UNDO
            else -> throw ProcessingError.InvalidJSON(text)
        }

        return ProcessingResult(
            intent = intent,
            resultText = resultText,
            explanation = explainMatch?.groupValues?.get(1),
            suggestedMode = modeMatch?.groupValues?.get(1)
        )
    }
}

sealed class ProcessingError(message: String) : Exception(message) {
    class InvalidJSON(val raw: String) : ProcessingError("Failed to parse LLM response: ${raw.take(100)}")
}
