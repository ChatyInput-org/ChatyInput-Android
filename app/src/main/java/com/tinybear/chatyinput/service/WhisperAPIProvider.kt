package com.tinybear.chatyinput.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Whisper + Compatible 端点
class WhisperAPIProvider(
    private val apiKey: String,
    private val language: String = "zh",
    private val model: String = "whisper-1",
    private val baseURL: String = "https://api.openai.com/v1"
) : STTProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(audioData: ByteArray, fileExtension: String): String =
        withContext(Dispatchers.IO) {
            if (audioData.isEmpty()) throw STTError.NoAudioData()

            val mimeType = when (fileExtension) {
                "m4a" -> "audio/m4a"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                else -> "audio/m4a"
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("language", language)
                .addFormDataPart(
                    "file", "audio.$fileExtension",
                    audioData.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseURL/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw STTError.InvalidResponse()

                if (!response.isSuccessful) {
                    throw STTError.ApiError("${response.code}: $responseBody")
                }

                val json = Json.parseToJsonElement(responseBody).jsonObject
                json["text"]?.jsonPrimitive?.content ?: throw STTError.InvalidResponse()
            } catch (e: STTError) {
                throw e
            } catch (e: Exception) {
                throw STTError.ApiError(e.message ?: "Unknown error")
            }
        }
}
