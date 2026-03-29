package com.tinybear.chatyinput.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// App-Mode 映射（含强制锁定标志）
@Serializable
data class AppModeMapping(
    @SerialName("mode_id") val modeId: String,
    val forced: Boolean = false
)

@Serializable
data class Mode(
    val id: String,
    val name: String,
    @SerialName("icon_emoji") val iconEmoji: String? = null,
    @SerialName("prompt_suffix") val promptSuffix: String = "",
    @SerialName("edit_prompt_suffix") val editPromptSuffix: String = "",
    @SerialName("trigger_condition") val triggerCondition: String = "",
    @SerialName("custom_words") val customWords: List<String> = emptyList(),
    val language: String? = null,
    @SerialName("is_built_in") val isBuiltIn: Boolean = false,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)
