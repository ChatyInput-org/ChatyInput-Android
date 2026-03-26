package com.tinybear.chatyinput.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceIntent {
    @SerialName("content") CONTENT,
    @SerialName("edit") EDIT,
    @SerialName("send") SEND,
    @SerialName("undo") UNDO
}
