package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Anthropic Messages API
class ClaudeProvider(
    private val apiKey: String,
    private val model: String = "claude-haiku-4-5-20251001"
) : LLMProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val supportsToolUse: Boolean get() = true

    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", 1024)
                put("system", systemPrompt)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw LLMError.InvalidResponse()
                if (!response.isSuccessful) throw LLMError.ApiError("${response.code}: $responseBody")

                val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
                jsonObj["content"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content
                    ?: throw LLMError.InvalidResponse()
            } catch (e: LLMError) { throw e }
            catch (e: Exception) { throw LLMError.NetworkError(e) }
        }

    override suspend fun completeWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse = withContext(Dispatchers.IO) {
        // 提取 system message
        val systemContent = messages.filterIsInstance<ChatMessage.System>().firstOrNull()?.content ?: ""
        val nonSystemMessages = messages.filter { it !is ChatMessage.System }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("system", systemContent)
            putJsonArray("messages") {
                nonSystemMessages.forEach { msg ->
                    when (msg) {
                        is ChatMessage.User -> addJsonObject {
                            put("role", "user")
                            put("content", msg.content)
                        }
                        is ChatMessage.Assistant -> addJsonObject {
                            put("role", "assistant")
                            putJsonArray("content") {
                                if (!msg.content.isNullOrEmpty()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    }
                                }
                                msg.toolCalls.forEach { tc ->
                                    addJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.id)
                                        put("name", tc.name)
                                        put("input", tc.arguments)
                                    }
                                }
                            }
                        }
                        is ChatMessage.ToolResult -> addJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId)
                                    put("content", msg.content)
                                }
                            }
                        }
                        is ChatMessage.System -> { /* already extracted */ }
                    }
                }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        }
                    }
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw LLMError.InvalidResponse()
            if (!response.isSuccessful) throw LLMError.ApiError("${response.code}: $responseBody")

            val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
            val stopReason = jsonObj["stop_reason"]?.jsonPrimitive?.contentOrNull
            val contentArray = jsonObj["content"]?.jsonArray ?: throw LLMError.InvalidResponse()

            if (stopReason == "tool_use") {
                var textContent: String? = null
                val toolCalls = mutableListOf<ToolCall>()
                contentArray.forEach { block ->
                    val obj = block.jsonObject
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "text" -> textContent = obj["text"]?.jsonPrimitive?.content
                        "tool_use" -> toolCalls.add(ToolCall(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            arguments = obj["input"]?.jsonObject ?: buildJsonObject {}
                        ))
                    }
                }
                LLMResponse.ToolUse(textContent, toolCalls)
            } else {
                val text = contentArray.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw LLMError.InvalidResponse()
                LLMResponse.Text(text)
            }
        } catch (e: LLMError) { throw e }
        catch (e: Exception) { throw LLMError.NetworkError(e) }
    }
}
