package com.tinybear.chatyinput.service

// 语音转文字接口
interface STTProvider {
    suspend fun transcribe(audioData: ByteArray, fileExtension: String = "m4a"): String
}

sealed class STTError(message: String) : Exception(message) {
    class InvalidResponse : STTError("Invalid STT response")
    class ApiError(val detail: String) : STTError("STT API error: $detail")
    class NoAudioData : STTError("No audio data to transcribe")
}
