package com.tinybear.chatyinput.service

import android.content.Context
import android.util.Log
import com.tinybear.chatyinput.config.AppLanguage
import com.tinybear.chatyinput.config.ModePrompts
import com.tinybear.chatyinput.model.AppModeMapping
import com.tinybear.chatyinput.model.Mode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

// Mode 管理器：CRUD、JSON 持久化、内置模板
class ModeManager(private val context: Context) {
    companion object {
        private const val TAG = "ModeManager"
        private const val MODES_FILE = "modes.json"
        private const val MAPPINGS_FILE = "app_mode_mappings.json"

        // 内置 Mode ID
        const val MODE_BUSINESS_EMAIL = "built_in_business_email"
        const val MODE_CASUAL_CHAT = "built_in_casual_chat"
        const val MODE_TECHNICAL_DOCS = "built_in_technical_docs"
        const val MODE_MEETING_NOTES = "built_in_meeting_notes"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var modes: MutableList<Mode> = mutableListOf()
    private var appMappings: MutableMap<String, AppModeMapping> = mutableMapOf()

    init {
        load()
    }

    // 加载 modes 和 mappings（含旧格式向后兼容迁移）
    private fun load() {
        try {
            val modesFile = File(context.filesDir, MODES_FILE)
            if (modesFile.exists()) {
                modes = json.decodeFromString<List<Mode>>(modesFile.readText()).toMutableList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load modes: ${e.message}")
            modes = mutableListOf()
        }

        try {
            val mappingsFile = File(context.filesDir, MAPPINGS_FILE)
            if (mappingsFile.exists()) {
                val text = mappingsFile.readText()
                try {
                    // 新格式：Map<String, AppModeMapping>
                    appMappings = json.decodeFromString<Map<String, AppModeMapping>>(text).toMutableMap()
                } catch (_: Exception) {
                    // 旧格式：Map<String, String> → 自动迁移
                    try {
                        val oldMappings = json.decodeFromString<Map<String, String>>(text)
                        appMappings = oldMappings.mapValues { AppModeMapping(modeId = it.value, forced = false) }.toMutableMap()
                        saveMappings() // 保存为新格式
                        Log.i(TAG, "Migrated ${oldMappings.size} app mappings to new format")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to load mappings (both formats): ${e2.message}")
                        appMappings = mutableMapOf()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mappings: ${e.message}")
            appMappings = mutableMapOf()
        }

        ensureBuiltInModes()
    }

    private fun saveModes() {
        try {
            File(context.filesDir, MODES_FILE).writeText(json.encodeToString(modes.toList()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save modes: ${e.message}")
        }
    }

    private fun saveMappings() {
        try {
            File(context.filesDir, MAPPINGS_FILE).writeText(json.encodeToString(appMappings.toMap()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mappings: ${e.message}")
        }
    }

    fun getAllModes(): List<Mode> = modes.toList()

    fun getMode(id: String): Mode? = modes.find { it.id == id }

    fun createMode(mode: Mode): Mode {
        val newMode = mode.copy(
            id = if (mode.id.isBlank()) UUID.randomUUID().toString() else mode.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        modes.add(newMode)
        saveModes()
        return newMode
    }

    fun updateMode(mode: Mode) {
        val index = modes.indexOfFirst { it.id == mode.id }
        if (index >= 0) {
            modes[index] = mode.copy(updatedAt = System.currentTimeMillis())
            saveModes()
        }
    }

    fun deleteMode(id: String): Boolean {
        val mode = modes.find { it.id == id } ?: return false
        if (mode.isBuiltIn) return false
        modes.removeAll { it.id == id }
        appMappings.entries.removeAll { it.value.modeId == id }
        saveModes()
        saveMappings()
        return true
    }

    // App-to-Mode 映射（支持 forced 标志）
    fun setAppMapping(packageName: String, modeId: String, forced: Boolean = false) {
        appMappings[packageName] = AppModeMapping(modeId = modeId, forced = forced)
        saveMappings()
    }

    fun removeAppMapping(packageName: String) {
        appMappings.remove(packageName)
        saveMappings()
    }

    fun getAppMappings(): Map<String, AppModeMapping> = appMappings.toMap()

    fun getMappedAppsForMode(modeId: String): List<String> =
        appMappings.filter { it.value.modeId == modeId }.keys.toList()

    // 获取某 app 的映射详情（含 forced 状态）
    fun getAppMapping(packageName: String): AppModeMapping? = appMappings[packageName]

    fun resetBuiltInMode(id: String) {
        val builtIn = createBuiltInModes().find { it.id == id } ?: return
        val index = modes.indexOfFirst { it.id == id }
        if (index >= 0) {
            modes[index] = builtIn.copy(updatedAt = System.currentTimeMillis())
            saveModes()
        }
    }

    private fun ensureBuiltInModes() {
        val builtInIds = setOf(MODE_BUSINESS_EMAIL, MODE_CASUAL_CHAT, MODE_TECHNICAL_DOCS, MODE_MEETING_NOTES)
        val existingIds = modes.map { it.id }.toSet()
        val missing = builtInIds - existingIds
        if (missing.isNotEmpty()) {
            val builtIns = createBuiltInModes()
            modes.addAll(builtIns.filter { it.id in missing })
            saveModes()
        }
    }

    private fun createBuiltInModes(): List<Mode> = listOf(
        Mode(id = MODE_BUSINESS_EMAIL, name = "Business Email", iconEmoji = "\uD83D\uDCE7",
            promptSuffix = ModePrompts.getBusinessEmail(AppLanguage.EN),
            editPromptSuffix = ModePrompts.getBusinessEmailEdit(AppLanguage.EN),
            triggerCondition = "Use when in email apps (Gmail, Outlook) or when content is a formal email, letter, or professional message",
            isBuiltIn = true),
        Mode(id = MODE_CASUAL_CHAT, name = "Casual Chat", iconEmoji = "\uD83D\uDCAC",
            promptSuffix = ModePrompts.getCasualChat(AppLanguage.EN),
            editPromptSuffix = ModePrompts.getCasualChatEdit(AppLanguage.EN),
            triggerCondition = "Use when in messaging apps (WhatsApp, Telegram, LINE, WeChat) or when content is casual conversation",
            isBuiltIn = true),
        Mode(id = MODE_TECHNICAL_DOCS, name = "Technical Docs", iconEmoji = "\uD83D\uDCBB",
            promptSuffix = ModePrompts.getTechnicalDocs(AppLanguage.EN),
            editPromptSuffix = ModePrompts.getTechnicalDocsEdit(AppLanguage.EN),
            triggerCondition = "Use when in documentation/note apps (Notion, Confluence, Google Docs) or when content contains technical terms, code, or API references",
            isBuiltIn = true),
        Mode(id = MODE_MEETING_NOTES, name = "Meeting Notes", iconEmoji = "\uD83D\uDCDD",
            promptSuffix = ModePrompts.getMeetingNotes(AppLanguage.EN),
            editPromptSuffix = ModePrompts.getMeetingNotesEdit(AppLanguage.EN),
            triggerCondition = "Use when content mentions meetings, action items, attendees, or discussions",
            isBuiltIn = true)
    )
}
