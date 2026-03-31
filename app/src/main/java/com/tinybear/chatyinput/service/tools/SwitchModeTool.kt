package com.tinybear.chatyinput.service.tools

import com.tinybear.chatyinput.model.ToolDefinition
import kotlinx.serialization.json.*

// switch_mode tool：切换 Voice Input Mode
object SwitchModeTool {
    const val NAME = "switch_mode"

    val definition = ToolDefinition(
        name = NAME,
        description = "Switch to a different voice input mode. Use this when the current mode doesn't match the user's context (app, location, or content type).",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("mode_id") {
                    put("type", "string")
                    put("description", "The mode ID to switch to")
                }
            }
            putJsonArray("required") { add("mode_id") }
        }
    )
}
