package com.tinybear.chatyinput.service

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

                if (!response.isSuccessful) {
                    throw LLMError.ApiError("${response.code}: $responseBody")
                }

                val json = Json.parseToJsonElement(responseBody).jsonObject
                json["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?: throw LLMError.InvalidResponse()
            } catch (e: LLMError) {
                throw e
            } catch (e: Exception) {
                throw LLMError.NetworkError(e)
            }
        }
}
