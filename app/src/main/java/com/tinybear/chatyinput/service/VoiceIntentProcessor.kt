package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.model.ProcessingResult
import kotlinx.serialization.json.Json

// 核心：语音意图分类 + 文本处理
class VoiceIntentProcessor(
    private val llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val customWords: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """你是一个语音输入助手。用户通过语音逐段输入文字。

每段语音转文字后发给你，判断意图并处理：

1. **content** — 普通内容输入。纠正错别字和语法，result_text 只返回纠正后的**新内容**（不要包含缓冲区已有的文字）。如果用户提供了常用词列表，遇到发音相似的词请优先使用常用词。
2. **edit** — 编辑命令（如"把X改成Y"、"删掉上一句"）。根据命令修改当前缓冲区，result_text 返回修改后的**完整缓冲区全文**。你要很确定用户是真实需要修改他输入的文字才进行修改,需要根据上下文推理.
3. **send** — 发送命令（如"发送"、"确认"、"OK"、"send"）。result_text 留空。你要很确定用户是真实的要发送这段文字了才使用这个命令.
4. **undo** — 撤销命令（如"undo"、"撤销"、"改回去"、"rollback"、"回退"、"还原"）。result_text 留空。将缓冲区恢复到上一次修改之前的状态。

严格只返回 JSON，不要返回任何其他文字，不要用 markdown 代码块包裹：
{"intent": "content", "result_text": "纠正后的新内容", "explanation": "说明"}"""
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun process(newSegment: String, currentBuffer: String): ProcessingResult {
        val userMessage = buildString {
            append("当前缓冲区内容：")
            if (currentBuffer.isEmpty()) append("（空）") else append(currentBuffer)
            append("\n\n新语音片段：")
            append(newSegment)
            if (customWords.isNotEmpty()) {
                append("\n\n用户常用词（遇到发音相似的词请优先使用这些）：")
                append(customWords.joinToString("、"))
            }
        }

        val response = llmProvider.complete(systemPrompt, userMessage)
        return parseResponse(response)
    }

    private fun parseResponse(response: String): ProcessingResult {
        // 尝试直接解析
        try {
            return json.decodeFromString<ProcessingResult>(response.trim())
        } catch (_: Exception) {}

        // 提取 JSON 子串（处理 LLM 返回额外文字的情况）
        val jsonStr = extractJSON(response)
        if (jsonStr != null) {
            try {
                return json.decodeFromString<ProcessingResult>(jsonStr)
            } catch (_: Exception) {}
        }

        // Fallback：字符串搜索
        return fallbackParse(response)
    }

    private fun extractJSON(text: String): String? {
        // 去掉 markdown 代码块
        val cleaned = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1)
        }
        return null
    }

    private fun fallbackParse(text: String): ProcessingResult {
        // 从文本中搜索 intent 和 result_text
        val intentMatch = Regex(""""intent"\s*:\s*"(\w+)"""").find(text)
        val resultMatch = Regex(""""result_text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)
        val explainMatch = Regex(""""explanation"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)

        val intentStr = intentMatch?.groupValues?.get(1)
            ?: throw ProcessingError.InvalidJSON(text)
        val resultText = resultMatch?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?: ""

        val intent = when (intentStr) {
            "content" -> com.tinybear.chatyinput.model.VoiceIntent.CONTENT
            "edit" -> com.tinybear.chatyinput.model.VoiceIntent.EDIT
            "send" -> com.tinybear.chatyinput.model.VoiceIntent.SEND
            "undo" -> com.tinybear.chatyinput.model.VoiceIntent.UNDO
            else -> throw ProcessingError.InvalidJSON(text)
        }

        return ProcessingResult(
            intent = intent,
            resultText = resultText,
            explanation = explainMatch?.groupValues?.get(1)
        )
    }
}

sealed class ProcessingError(message: String) : Exception(message) {
    class InvalidJSON(val raw: String) : ProcessingError("Failed to parse LLM response: ${raw.take(100)}")
}
