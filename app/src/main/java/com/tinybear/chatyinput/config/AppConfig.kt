package com.tinybear.chatyinput.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tinybear.chatyinput.service.*

// LLM provider 类型
enum class LLMProviderType(val label: String) {
    OPENAI("OpenAI"),
    CLAUDE("Claude"),
    OPENAI_COMPATIBLE("OpenAI Compatible")
}

// STT provider 类型
enum class STTProviderType(val label: String) {
    OPENAI("OpenAI Whisper"),
    OPENAI_COMPATIBLE("Whisper Compatible")
}

// 应用配置（EncryptedSharedPreferences 存储 API keys）
class AppConfig(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "chatyinput_config", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback：普通 SharedPreferences
        context.getSharedPreferences("chatyinput_config", Context.MODE_PRIVATE)
    }

    // STT
    var sttProviderType: STTProviderType
        get() = STTProviderType.entries.find { it.name == prefs.getString("stt_provider", "OPENAI") } ?: STTProviderType.OPENAI
        set(v) = prefs.edit().putString("stt_provider", v.name).apply()
    var sttAPIKey: String
        get() = prefs.getString("stt_api_key", "") ?: ""
        set(v) = prefs.edit().putString("stt_api_key", v).apply()
    var sttModel: String
        get() = prefs.getString("stt_model", "whisper-1") ?: "whisper-1"
        set(v) = prefs.edit().putString("stt_model", v).apply()
    var sttBaseURL: String
        get() = prefs.getString("stt_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(v) = prefs.edit().putString("stt_base_url", v).apply()

    // LLM
    var llmProviderType: LLMProviderType
        get() = LLMProviderType.entries.find { it.name == prefs.getString("llm_provider", "OPENAI") } ?: LLMProviderType.OPENAI
        set(v) = prefs.edit().putString("llm_provider", v.name).apply()
    var openAIAPIKey: String
        get() = prefs.getString("openai_api_key", "") ?: ""
        set(v) = prefs.edit().putString("openai_api_key", v).apply()
    var openAIModel: String
        get() = prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) = prefs.edit().putString("openai_model", v).apply()
    var claudeAPIKey: String
        get() = prefs.getString("claude_api_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_api_key", v).apply()
    var claudeModel: String
        get() = prefs.getString("claude_model", "claude-haiku-4-5-20251001") ?: "claude-haiku-4-5-20251001"
        set(v) = prefs.edit().putString("claude_model", v).apply()
    var compatibleBaseURL: String
        get() = prefs.getString("compatible_base_url", "http://localhost:11434/v1") ?: "http://localhost:11434/v1"
        set(v) = prefs.edit().putString("compatible_base_url", v).apply()
    var compatibleAPIKey: String
        get() = prefs.getString("compatible_api_key", "") ?: ""
        set(v) = prefs.edit().putString("compatible_api_key", v).apply()
    var compatibleModel: String
        get() = prefs.getString("compatible_model", "") ?: ""
        set(v) = prefs.edit().putString("compatible_model", v).apply()

    // 语言设置（auto = 跟随系统）
    var language: AppLanguage
        get() = AppLanguage.fromCode(prefs.getString("language", "auto") ?: "auto")
        set(v) = prefs.edit().putString("language", v.code).apply()

    // 获取实际生效的语言（auto 时解析系统语言）
    val resolvedLanguage: AppLanguage
        get() = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language

    // Legacy: derived from recordingMode for backward compatibility
    var holdToRecord: Boolean
        get() = recordingMode == "ptt"
        set(v) { if (v) recordingMode = "ptt" else recordingMode = "toggle" }

    // Recording mode: "ptt", "toggle", "vad"
    var recordingMode: String
        get() = prefs.getString("recording_mode", "ptt") ?: "ptt"
        set(v) = prefs.edit().putString("recording_mode", v).apply()

    // VAD silence threshold in seconds (0.5 - 3.0)
    var silenceThreshold: Float
        get() = prefs.getFloat("silence_threshold", 1.5f)
        set(v) = prefs.edit().putFloat("silence_threshold", v).apply()

    // 常用词列表（逗号分隔存储）
    var customWords: List<String>
        get() {
            val raw = prefs.getString("custom_words", "") ?: ""
            return if (raw.isEmpty()) emptyList() else raw.split(",")
        }
        set(v) = prefs.edit().putString("custom_words", v.joinToString(",")).apply()

    // 历史记录保留天数（默认 3 天）
    var historyRetentionDays: Int
        get() = prefs.getInt("history_retention_days", 3)
        set(v) = prefs.edit().putInt("history_retention_days", v).apply()

    // System Prompt（默认值根据语言设置变化）
    var systemPrompt: String
        get() = prefs.getString("system_prompt", null) ?: LocalizedPrompts.getDefault(language)
        set(v) = prefs.edit().putString("system_prompt", v).apply()

    // Edit Prompt（编辑指令专用）
    var editSystemPrompt: String
        get() = prefs.getString("edit_system_prompt", null) ?: LocalizedPrompts.getEditDefault(language)
        set(v) = prefs.edit().putString("edit_system_prompt", v).apply()

    // 重置 prompt 为当前语言的默认值
    fun resetPromptToDefault() {
        prefs.edit().remove("system_prompt").remove("edit_system_prompt").apply()
    }

    // Whisper 语言代码（根据 app 语言设置）
    private val whisperLanguage: String get() = when (resolvedLanguage) {
        AppLanguage.ZH_CN, AppLanguage.ZH_TW -> "zh"
        AppLanguage.EN -> "en"
        AppLanguage.JA -> "ja"
        AppLanguage.KO -> "ko"
        AppLanguage.AUTO -> "zh"
    }

    // 工厂方法
    fun makeSTTProvider(): STTProvider = when (sttProviderType) {
        STTProviderType.OPENAI -> WhisperAPIProvider(apiKey = sttAPIKey, language = whisperLanguage, model = sttModel)
        STTProviderType.OPENAI_COMPATIBLE -> WhisperAPIProvider(apiKey = sttAPIKey, language = whisperLanguage, model = sttModel, baseURL = sttBaseURL)
    }

    fun makeLLMProvider(): LLMProvider = when (llmProviderType) {
        LLMProviderType.OPENAI -> OpenAIProvider(apiKey = openAIAPIKey, model = openAIModel)
        LLMProviderType.CLAUDE -> ClaudeProvider(apiKey = claudeAPIKey, model = claudeModel)
        LLMProviderType.OPENAI_COMPATIBLE -> OpenAIProvider(apiKey = compatibleAPIKey, model = compatibleModel, baseURL = compatibleBaseURL)
    }

    val isValid: Boolean get() {
        val sttOk = when (sttProviderType) {
            STTProviderType.OPENAI -> sttAPIKey.isNotEmpty()
            STTProviderType.OPENAI_COMPATIBLE -> sttBaseURL.isNotEmpty() && sttModel.isNotEmpty()
        }
        val llmOk = when (llmProviderType) {
            LLMProviderType.OPENAI -> openAIAPIKey.isNotEmpty()
            LLMProviderType.CLAUDE -> claudeAPIKey.isNotEmpty()
            LLMProviderType.OPENAI_COMPATIBLE -> compatibleBaseURL.isNotEmpty() && compatibleModel.isNotEmpty()
        }
        return sttOk && llmOk
    }
}
