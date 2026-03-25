package com.tinybear.chatyinput.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Group 结束方式
@Serializable
enum class GroupEndType {
    @SerialName("sent") SENT,       // 用户发送了
    @SerialName("cleared") CLEARED, // 用户清空了
    @SerialName("active") ACTIVE    // 还在进行中
}

// 历史记录条目：记录每次语音输入的完整处理结果
@Serializable
data class HistoryEntry(
    val id: String,                    // UUID
    val timestamp: Long,               // System.currentTimeMillis()
    val transcript: String,            // STT 原始识别文字
    val intent: VoiceIntent,           // LLM 判断的意图
    @SerialName("result_text")
    val resultText: String,            // LLM 处理后的文字
    val explanation: String? = null,   // LLM 解释
    @SerialName("audio_file_name")
    val audioFileName: String? = null, // 音频文件名
    @SerialName("group_id")
    val groupId: String = "",          // 同一组输入的 groupId
    @SerialName("group_end_type")
    val groupEndType: GroupEndType = GroupEndType.ACTIVE,  // 这条记录时 group 的结束状态
    @SerialName("final_text")
    val finalText: String? = null      // group 结束时的最终文本
)
