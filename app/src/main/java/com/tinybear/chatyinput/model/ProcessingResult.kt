package com.tinybear.chatyinput.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessingResult(
    val intent: VoiceIntent,
    @SerialName("result_text") val resultText: String,
    val explanation: String? = null
)
