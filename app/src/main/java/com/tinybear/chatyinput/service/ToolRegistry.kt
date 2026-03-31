package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.model.ToolDefinition
import com.tinybear.chatyinput.service.tools.SwitchModeTool

// Tool 注册表
class ToolRegistry {
    private val tools = mutableMapOf<String, ToolDefinition>()

    fun register(tool: ToolDefinition) { tools[tool.name] = tool }
    fun getAll(): List<ToolDefinition> = tools.values.toList()
    fun get(name: String): ToolDefinition? = tools[name]

    companion object {
        fun createDefault(): ToolRegistry {
            val registry = ToolRegistry()
            registry.register(SwitchModeTool.definition)
            return registry
        }
    }
}
