package com.tinybear.chatyinput.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.LLMProviderType
import com.tinybear.chatyinput.config.STTProviderType
import com.tinybear.chatyinput.config.AppLanguage
import com.tinybear.chatyinput.config.LocalizedPrompts
import com.tinybear.chatyinput.service.HistoryManager

// 设置界面：STT/LLM 配置，对应 Apple 版 SettingsTabView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: AppConfig, onLanguageChanged: () -> Unit = {}) {
    val context = LocalContext.current

    // 历史记录保留天数
    var historyRetentionDays by remember { mutableStateOf(config.historyRetentionDays) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCleared by remember { mutableStateOf(false) }

    // STT 状态
    var sttProvider by remember { mutableStateOf(config.sttProviderType) }
    var sttAPIKey by remember { mutableStateOf(config.sttAPIKey) }
    var sttModel by remember { mutableStateOf(config.sttModel) }
    var sttBaseURL by remember { mutableStateOf(config.sttBaseURL) }

    // LLM 状态
    var llmProvider by remember { mutableStateOf(config.llmProviderType) }
    var openAIAPIKey by remember { mutableStateOf(config.openAIAPIKey) }
    var openAIModel by remember { mutableStateOf(config.openAIModel) }
    var claudeAPIKey by remember { mutableStateOf(config.claudeAPIKey) }
    var claudeModel by remember { mutableStateOf(config.claudeModel) }
    var compatibleBaseURL by remember { mutableStateOf(config.compatibleBaseURL) }
    var compatibleAPIKey by remember { mutableStateOf(config.compatibleAPIKey) }
    var compatibleModel by remember { mutableStateOf(config.compatibleModel) }

    // 语言
    var language by remember { mutableStateOf(config.language) }
    // 记录初始语言，保存时比较是否切换了
    val initialLanguage = remember { config.language }

    // 录音模式
    var recordingMode by remember { mutableStateOf(config.recordingMode) }
    val modeOptions = listOf("ptt" to R.string.settings_mode_ptt, "toggle" to R.string.settings_mode_toggle, "vad" to R.string.settings_mode_vad)

    // VAD 静音阈值
    var silenceThreshold by remember { mutableStateOf(config.silenceThreshold) }

    // Tool use 最大轮次
    var maxToolRounds by remember { mutableStateOf(config.maxToolRounds.toFloat()) }

    // System Prompt（根据语言动态获取默认值）
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }

    // Edit Prompt（编辑指令专用）
    var editSystemPrompt by remember { mutableStateOf(config.editSystemPrompt) }

    // Smart/Strict Edit Prompt
    var smartEditPrompt by remember { mutableStateOf(config.smartEditPrompt) }
    var strictEditPrompt by remember { mutableStateOf(config.strictEditPrompt) }

    // 保存状态提示
    var showSaved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // === STT 配置 ===
        item {
            Text(
                stringResource(R.string.settings_stt_provider),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                STTProviderType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = sttProvider == type,
                        onClick = { sttProvider = type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = STTProviderType.entries.size
                        )
                    ) {
                        Text(type.label)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = sttAPIKey,
                onValueChange = { sttAPIKey = it },
                label = { Text("STT API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = sttModel,
                onValueChange = { sttModel = it },
                label = { Text("STT Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // 仅 Compatible 模式显示 Base URL
        if (sttProvider == STTProviderType.OPENAI_COMPATIBLE) {
            item {
                OutlinedTextField(
                    value = sttBaseURL,
                    onValueChange = { sttBaseURL = it },
                    label = { Text("STT Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // === LLM 配置 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_llm_provider),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LLMProviderType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = llmProvider == type,
                        onClick = { llmProvider = type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = LLMProviderType.entries.size
                        )
                    ) {
                        Text(type.label)
                    }
                }
            }
        }

        // 根据 LLM provider 类型显示对应字段
        when (llmProvider) {
            LLMProviderType.OPENAI -> {
                item {
                    OutlinedTextField(
                        value = openAIAPIKey,
                        onValueChange = { openAIAPIKey = it },
                        label = { Text("OpenAI API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = openAIModel,
                        onValueChange = { openAIModel = it },
                        label = { Text("OpenAI Model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            LLMProviderType.CLAUDE -> {
                item {
                    OutlinedTextField(
                        value = claudeAPIKey,
                        onValueChange = { claudeAPIKey = it },
                        label = { Text("Claude API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = claudeModel,
                        onValueChange = { claudeModel = it },
                        label = { Text("Claude Model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            LLMProviderType.OPENAI_COMPATIBLE -> {
                item {
                    OutlinedTextField(
                        value = compatibleBaseURL,
                        onValueChange = { compatibleBaseURL = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = compatibleAPIKey,
                        onValueChange = { compatibleAPIKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = compatibleModel,
                        onValueChange = { compatibleModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // === 语言设置 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        }

        item {
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = if (language == AppLanguage.AUTO) {
                "Auto (${AppLanguage.detectSystem().nativeLabel})"
            } else {
                language.nativeLabel
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (lang == AppLanguage.AUTO) "Auto (${AppLanguage.detectSystem().nativeLabel})"
                                    else "${lang.nativeLabel} - ${lang.label}"
                                )
                            },
                            onClick = {
                                language = lang
                                expanded = false
                                // 切换语言时重置 prompt 为新语言默认值
                                systemPrompt = LocalizedPrompts.getDefault(lang)
                                editSystemPrompt = LocalizedPrompts.getEditDefault(lang)
                            }
                        )
                    }
                }
            }
        }

        // === 录音模式 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_recording),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Text(stringResource(R.string.settings_recording_mode), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modeOptions.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = recordingMode == mode,
                        onClick = { recordingMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modeOptions.size
                        )
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when (recordingMode) {
                    "ptt" -> stringResource(R.string.settings_hold_desc)
                    "toggle" -> stringResource(R.string.settings_tap_desc)
                    else -> stringResource(R.string.vad_listening)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // VAD 静音阈值滑块（仅 Hands-free 模式显示）
        if (recordingMode == "vad") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_silence_threshold), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "%.1fs".format(silenceThreshold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = silenceThreshold,
                    onValueChange = { silenceThreshold = (Math.round(it * 10) / 10f) },
                    valueRange = 0.5f..3.0f,
                    steps = 24,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // === Tool Use 配置 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_tool_use),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_max_tool_rounds), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${maxToolRounds.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.settings_max_tool_rounds_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = maxToolRounds,
                onValueChange = { maxToolRounds = Math.round(it).toFloat() },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // === History 配置 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_history),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            // 保留天数选择
            val retentionOptions = listOf(1, 3, 7, 14, 30)
            Column {
                Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    retentionOptions.forEachIndexed { index, days ->
                        SegmentedButton(
                            selected = historyRetentionDays == days,
                            onClick = { historyRetentionDays = days },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = retentionOptions.size
                            )
                        ) {
                            Text("${days}d")
                        }
                    }
                }
            }
        }

        item {
            // 清空历史按钮
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.settings_clear_all))
            }
        }

        // 清空确认提示
        if (showCleared) {
            item {
                Text(
                    stringResource(R.string.settings_history_cleared),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // === System Prompt ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_system_prompt),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text(stringResource(R.string.settings_system_prompt)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )
        }

        item {
            TextButton(onClick = {
                systemPrompt = LocalizedPrompts.getDefault(language)
            }) {
                Text(stringResource(R.string.settings_reset_default))
            }
        }

        // === Edit Command Prompt ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_edit_prompt),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_edit_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = editSystemPrompt,
                onValueChange = { editSystemPrompt = it },
                label = { Text(stringResource(R.string.settings_edit_prompt)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )
        }

        item {
            TextButton(onClick = {
                editSystemPrompt = LocalizedPrompts.getEditDefault(language)
            }) {
                Text(stringResource(R.string.settings_reset_default))
            }
        }

        // === Smart Edit Prompt ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_smart_edit_prompt),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_smart_edit_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = smartEditPrompt,
                onValueChange = { smartEditPrompt = it },
                label = { Text(stringResource(R.string.settings_smart_edit_prompt)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )
        }

        item {
            TextButton(onClick = {
                config.resetSmartEditPrompt()
                smartEditPrompt = config.smartEditPrompt
            }) {
                Text(stringResource(R.string.settings_reset_default))
            }
        }

        // === Strict Edit Prompt ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_strict_edit_prompt),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_strict_edit_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = strictEditPrompt,
                onValueChange = { strictEditPrompt = it },
                label = { Text(stringResource(R.string.settings_strict_edit_prompt)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 8
            )
        }

        item {
            TextButton(onClick = {
                config.resetStrictEditPrompt()
                strictEditPrompt = config.strictEditPrompt
            }) {
                Text(stringResource(R.string.settings_reset_default))
            }
        }

        // === Save 按钮 ===
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    // 写入所有配置
                    config.sttProviderType = sttProvider
                    config.sttAPIKey = sttAPIKey
                    config.sttModel = sttModel
                    config.sttBaseURL = sttBaseURL
                    config.llmProviderType = llmProvider
                    config.openAIAPIKey = openAIAPIKey
                    config.openAIModel = openAIModel
                    config.claudeAPIKey = claudeAPIKey
                    config.claudeModel = claudeModel
                    config.compatibleBaseURL = compatibleBaseURL
                    config.compatibleAPIKey = compatibleAPIKey
                    config.compatibleModel = compatibleModel
                    config.systemPrompt = systemPrompt
                    config.editSystemPrompt = editSystemPrompt
                    config.smartEditPrompt = smartEditPrompt
                    config.strictEditPrompt = strictEditPrompt
                    config.language = language
                    config.recordingMode = recordingMode
                    config.silenceThreshold = silenceThreshold
                    config.maxToolRounds = maxToolRounds.toInt()
                    config.historyRetentionDays = historyRetentionDays
                    showSaved = true

                    // 语言发生变化时 recreate activity 使新 locale 生效
                    if (language != initialLanguage) {
                        onLanguageChanged()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }

        // 保存成功提示
        if (showSaved) {
            item {
                Text(
                    stringResource(R.string.settings_saved),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // === About ===
        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "ChatyInput v0.1.1",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "chatyinput.com",
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatyinput.com")))
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Built by Thinkroid",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 清空历史确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        HistoryManager.clearAll(context)
                        showClearConfirm = false
                        showCleared = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
