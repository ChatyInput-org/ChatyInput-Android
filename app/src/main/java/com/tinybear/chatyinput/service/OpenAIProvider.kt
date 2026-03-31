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

// OpenAI + Compatible 端点
class OpenAIProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val baseURL: String = "https://api.openai.com/v1"
) : LLMProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // 标准 OpenAI 端点支持 tool use
    override val supportsToolUse: Boolean get() = baseURL == "https://api.openai.com/v1"

    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("temperature", 0.3)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url("$baseURL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw LLMError.InvalidResponse()
                if (!response.isSuccessful) throw LLMError.ApiError("${response.code}: $responseBody")

                val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
                jsonObj["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?: throw LLMError.InvalidResponse()
            } catch (e: LLMError) { throw e }
            catch (e: Exception) { throw LLMError.NetworkError(e) }
        }

    override suspend fun completeWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.3)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    when (msg) {
                        is ChatMessage.System -> addJsonObject {
                            put("role", "system")
                            put("content", msg.content)
                        }
                        is ChatMessage.User -> addJsonObject {
                            put("role", "user")
                            put("content", msg.content)
                        }
                        is ChatMessage.Assistant -> addJsonObject {
                            put("role", "assistant")
                            if (msg.toolCalls.isNotEmpty()) {
                                put("content", msg.content?.let { JsonPrimitive(it) } ?: JsonNull)
                                putJsonArray("tool_calls") {
                                    msg.toolCalls.forEach { tc ->
                                        addJsonObject {
                                            put("id", tc.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", tc.name)
                                                put("arguments", tc.arguments.toString())
                                            }
                                        }
                                    }
                                }
                            } else {
                                put("content", msg.content ?: "")
                            }
                        }
                        is ChatMessage.ToolResult -> addJsonObject {
                            put("role", "tool")
                            put("tool_call_id", msg.toolCallId)
                            put("content", msg.content)
                        }
                    }
                }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("$baseURL/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw LLMError.InvalidResponse()
            if (!response.isSuccessful) throw LLMError.ApiError("${response.code}: $responseBody")

            val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
            val choice = jsonObj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw LLMError.InvalidResponse()
            val message = choice["message"]?.jsonObject ?: throw LLMError.InvalidResponse()
            val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull

            if (finishReason == "tool_calls") {
                val toolCalls = message["tool_calls"]?.jsonArray?.map { tc ->
                    val tcObj = tc.jsonObject
                    val fn = tcObj["function"]?.jsonObject ?: throw LLMError.InvalidResponse()
                    ToolCall(
                        id = tcObj["id"]?.jsonPrimitive?.content ?: "",
                        name = fn["name"]?.jsonPrimitive?.content ?: "",
                        arguments = json.parseToJsonElement(
                            fn["arguments"]?.jsonPrimitive?.content ?: "{}"
                        ).jsonObject
                    )
                } ?: emptyList()
                val textContent = message["content"]?.jsonPrimitive?.contentOrNull
                LLMResponse.ToolUse(textContent, toolCalls)
            } else {
                val content = message["content"]?.jsonPrimitive?.content ?: throw LLMError.InvalidResponse()
                LLMResponse.Text(content)
            }
        } catch (e: LLMError) { throw e }
        catch (e: Exception) { throw LLMError.NetworkError(e) }
    }
}
