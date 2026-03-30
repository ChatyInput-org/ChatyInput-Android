package com.tinybear.chatyinput.service

import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.AppLanguage
import com.tinybear.chatyinput.config.ModeSelectionPrompts
import com.tinybear.chatyinput.model.LocationData
import com.tinybear.chatyinput.model.Mode

enum class ModeSource { MANUAL, LLM_SUGGESTION, APP_MAPPING, DEFAULT }

data class ResolvedMode(
    val mode: Mode?,
    val source: ModeSource,
    val isForced: Boolean = false
)

// Mode 解析器
class ModeResolver(
    private val config: AppConfig,
    private val modeManager: ModeManager
) {
    /**
     * 解析当前应使用的 Mode。
     *
     * 优先级：
     * 1. IME 手动锁定（activeModeId）→ 用户显式选择，最高优先级，LLM 不能覆盖
     * 2. App 绑定 forced=true → LLM 不能覆盖
     * 3. LLM 建议（如有）→ 覆盖非 forced app 绑定
     * 4. App 绑定 forced=false
     * 5. Default / Auto（返回 null，LLM 自动判断）
     */
    fun resolveMode(appPackage: String?, llmSuggestedModeId: String? = null): ResolvedMode {
        // 1. IME 手动锁定 → 最高优先级
        val manualId = config.activeModeId
        if (!manualId.isNullOrBlank()) {
            val mode = modeManager.getMode(manualId)
            if (mode != null) return ResolvedMode(mode, ModeSource.MANUAL, isForced = true)
        }

        // 2. App 绑定 forced=true → LLM 不能覆盖
        if (config.autoModeEnabled && !appPackage.isNullOrBlank()) {
            val mapping = modeManager.getAppMappings()[appPackage]
            if (mapping != null && mapping.forced) {
                val mode = modeManager.getMode(mapping.modeId)
                if (mode != null) return ResolvedMode(mode, ModeSource.APP_MAPPING, isForced = true)
            }
        }

        // 3. LLM 建议
        if (!llmSuggestedModeId.isNullOrBlank()) {
            val suggested = modeManager.getMode(llmSuggestedModeId)
            if (suggested != null) return ResolvedMode(suggested, ModeSource.LLM_SUGGESTION)
        }

        // 4. App 绑定 forced=false
        if (config.autoModeEnabled && !appPackage.isNullOrBlank()) {
            val mapping = modeManager.getAppMappings()[appPackage]
            if (mapping != null) {
                val mode = modeManager.getMode(mapping.modeId)
                if (mode != null) return ResolvedMode(mode, ModeSource.APP_MAPPING)
            }
        }

        // 5. Default
        return ResolvedMode(null, ModeSource.DEFAULT)
    }

    fun buildFinalPrompt(basePrompt: String, mode: Mode?, isEdit: Boolean): String {
        if (mode == null) return basePrompt
        val suffix = if (isEdit) mode.editPromptSuffix else mode.promptSuffix
        return if (suffix.isNotBlank()) "$basePrompt\n\n$suffix" else basePrompt
    }

    fun mergeCustomWords(globalWords: List<String>, mode: Mode?): List<String> {
        if (mode == null) return globalWords
        return (globalWords + mode.customWords).distinct()
    }

    /**
     * 生成 Mode 上下文：所有 Mode 的 triggerCondition + 当前状态，让 LLM 判断
     */
    fun buildModeContext(
        currentMode: Mode?,
        appPackage: String?,
        isForced: Boolean,
        language: AppLanguage,
        location: LocationData? = null,
        locationProvider: LocationProvider? = null
    ): String {
        val modes = modeManager.getAllModes()
        if (modes.isEmpty()) return ""

        if (isForced) {
            val currentName = currentMode?.name ?: "Default"
            val appName = appPackage ?: "unknown"
            return ModeSelectionPrompts.getLockedContext(language, currentName, appName)
        }

        // 构建每个 Mode 的描述（含 triggerCondition + 位置距离）
        val modeDescriptions = modes.joinToString("\n") { mode ->
            val condition = if (mode.triggerCondition.isNotBlank()) " — ${mode.triggerCondition}" else ""
            val locationInfo = if (location != null && locationProvider != null && mode.locationTriggers.isNotEmpty()) {
                val nearest = mode.locationTriggers.minByOrNull { trigger ->
                    locationProvider.distanceTo(location.latitude, location.longitude, trigger.latitude, trigger.longitude)
                }
                if (nearest != null) {
                    val dist = locationProvider.distanceTo(location.latitude, location.longitude, nearest.latitude, nearest.longitude)
                    if (dist <= nearest.radiusMeters) {
                        " [NEARBY: \"${nearest.name}\", ${dist.toInt()}m]"
                    } else {
                        val distKm = String.format("%.1f", dist / 1000.0)
                        " [\"${nearest.name}\", ${distKm}km away]"
                    }
                } else ""
            } else ""
            "- ${mode.id}: ${mode.name}$condition$locationInfo"
        }

        val currentName = currentMode?.name ?: "Default"
        val appName = appPackage ?: "unknown"

        val baseContext = ModeSelectionPrompts.getContextWithConditions(
            language, modeDescriptions, currentName, appName
        )

        // 附加位置信息
        val locationContext = if (location != null) {
            "\nCurrent location: lat=${String.format("%.4f", location.latitude)}, lon=${String.format("%.4f", location.longitude)}"
        } else ""

        return baseContext + locationContext
    }
}
