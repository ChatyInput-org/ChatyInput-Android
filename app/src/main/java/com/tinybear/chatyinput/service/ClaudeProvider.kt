package com.tinybear.chatyinput.service

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

                if (!response.isSuccessful) {
                    throw LLMError.ApiError("${response.code}: $responseBody")
                }

                val json = Json.parseToJsonElement(responseBody).jsonObject
                json["content"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content
                    ?: throw LLMError.InvalidResponse()
            } catch (e: LLMError) {
                throw e
            } catch (e: Exception) {
                throw LLMError.NetworkError(e)
            }
        }
}
