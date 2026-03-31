package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.model.ToolCall
import com.tinybear.chatyinput.model.ToolSideEffect
import com.tinybear.chatyinput.service.tools.SwitchModeTool
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Tool 执行结果
data class ToolExecutionResult(
    val content: String,
    val sideEffects: List<ToolSideEffect> = emptyList()
)

// Tool 执行器：分发 tool 调用到具体实现
class ToolExecutor(
    private val modeManager: ModeManager
) {
    fun execute(toolCall: ToolCall): ToolExecutionResult {
        return when (toolCall.name) {
            SwitchModeTool.NAME -> executeSwitchMode(toolCall)
            else -> ToolExecutionResult("Unknown tool: ${toolCall.name}")
        }
    }

    private fun executeSwitchMode(toolCall: ToolCall): ToolExecutionResult {
        val modeId = toolCall.arguments["mode_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolExecutionResult("Error: mode_id is required")
        val mode = modeManager.getMode(modeId)
            ?: return ToolExecutionResult("Error: mode '$modeId' not found. Available modes: ${modeManager.getAllModes().joinToString { it.id }}")
        return ToolExecutionResult(
            content = "Mode switched to ${mode.name}",
            sideEffects = listOf(ToolSideEffect.ModeSwitched(modeId, mode.name, mode.iconEmoji))
        )
    }
}
