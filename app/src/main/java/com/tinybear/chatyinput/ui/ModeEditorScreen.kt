package com.tinybear.chatyinput.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.tinybear.chatyinput.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.AppLanguage
import com.tinybear.chatyinput.model.AppModeMapping
import com.tinybear.chatyinput.model.Mode
import com.tinybear.chatyinput.service.ModeManager

// Mode 编辑界面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeEditorScreen(
    mode: Mode,
    config: AppConfig,
    context: Context,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val modeManager = remember { ModeManager(context) }
    val isNewMode = mode.id.isBlank()

    var name by remember { mutableStateOf(mode.name) }
    var emoji by remember { mutableStateOf(mode.iconEmoji ?: "") }
    var triggerCondition by remember { mutableStateOf(mode.triggerCondition) }
    var promptSuffix by remember { mutableStateOf(mode.promptSuffix) }
    var editPromptSuffix by remember { mutableStateOf(mode.editPromptSuffix) }
    var customWords by remember { mutableStateOf(mode.customWords) }
    var newWord by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(mode.language) }
    // 关联 App 列表（含 forced 标志）
    var associatedApps by remember {
        mutableStateOf(
            modeManager.getMappedAppsForMode(mode.id).map { pkg ->
                val mapping = modeManager.getAppMapping(pkg)
                Triple(pkg, mapping?.forced ?: false, mapping?.modeId ?: mode.id)
            }
        )
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    // 语言选项：null = Use Global Setting, 其余为 AppLanguage values
    val languageOptions = listOf<Pair<String?, String>>(
        null to stringResource(R.string.mode_language_global)
    ) + AppLanguage.entries.map { it.code to "${it.nativeLabel} (${it.label})" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewMode) stringResource(R.string.mode_new) else stringResource(R.string.mode_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Name 字段
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.mode_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Emoji 字段
            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it },
                label = { Text(stringResource(R.string.mode_emoji)) },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )

            // Trigger Condition（AI 根据此条件判断是否切换到该 Mode）
            OutlinedTextField(
                value = triggerCondition,
                onValueChange = { triggerCondition = it },
                label = { Text(stringResource(R.string.mode_trigger_condition)) },
                placeholder = { Text(stringResource(R.string.mode_trigger_condition_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Prompt Suffix
            OutlinedTextField(
                value = promptSuffix,
                onValueChange = { promptSuffix = it },
                label = { Text(stringResource(R.string.mode_prompt_suffix)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            // Edit Prompt Suffix
            OutlinedTextField(
                value = editPromptSuffix,
                onValueChange = { editPromptSuffix = it },
                label = { Text(stringResource(R.string.mode_edit_prompt_suffix)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Custom Words 区域
            Text(
                stringResource(R.string.mode_custom_words),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.mode_words_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 添加新词
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text(stringResource(R.string.mode_new_word)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                FilledIconButton(
                    onClick = {
                        val trimmed = newWord.trim()
                        if (trimmed.isNotEmpty() && trimmed !in customWords) {
                            customWords = customWords + trimmed
                            newWord = ""
                        }
                    },
                    enabled = newWord.trim().isNotEmpty()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add word")
                }
            }

            // 词汇列表
            if (customWords.isNotEmpty()) {
                Text(
                    stringResource(R.string.mode_words_count, customWords.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    customWords.forEachIndexed { index, word ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word, style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = {
                                        customWords = customWords.toMutableList().also {
                                            it.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Language Override 下拉选择
            Text(
                stringResource(R.string.mode_language_override),
                style = MaterialTheme.typography.titleSmall
            )
            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it }
            ) {
                val displayText = languageOptions.find { it.first == selectedLanguage }?.second
                    ?: "Use Global Setting"
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.mode_language)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    languageOptions.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedLanguage = code
                                languageExpanded = false
                            }
                        )
                    }
                }
            }

            // Associated Apps 区域
            Text(
                stringResource(R.string.mode_associated_apps),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.mode_associated_apps_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 添加 App 按钮
            OutlinedButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mode_add_app))
            }

            // App 列表（含锁定开关）
            if (associatedApps.isNotEmpty()) {
                Text(
                    stringResource(R.string.mode_app_count, associatedApps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pm = context.packageManager
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    associatedApps.forEachIndexed { index, (packageName, forced, _) ->
                        val appLabel = remember(packageName) {
                            try {
                                val info = pm.getApplicationInfo(packageName, 0)
                                pm.getApplicationLabel(info).toString()
                            } catch (_: Exception) { packageName }
                        }
                        val appIcon = remember(packageName) {
                            try { pm.getApplicationIcon(packageName) } catch (_: Exception) { null }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 应用图标
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon.toBitmap(48, 48).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel, style = MaterialTheme.typography.bodyMedium)
                                    Text(packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    if (forced) {
                                        Text(
                                            stringResource(R.string.mode_forced),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                // 锁定开关
                                Switch(
                                    checked = forced,
                                    onCheckedChange = { newForced ->
                                        associatedApps = associatedApps.toMutableList().also {
                                            it[index] = Triple(packageName, newForced, it[index].third)
                                        }
                                    },
                                    modifier = Modifier.height(32.dp)
                                )
                                // 删除按钮
                                IconButton(
                                    onClick = {
                                        associatedApps = associatedApps.toMutableList().also {
                                            it.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove app",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.mode_no_apps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Save 按钮
            Button(
                onClick = {
                    val updatedMode = mode.copy(
                        name = name.trim(),
                        iconEmoji = emoji.trim().ifEmpty { null },
                        triggerCondition = triggerCondition.trim(),
                        promptSuffix = promptSuffix,
                        editPromptSuffix = editPromptSuffix,
                        customWords = customWords,
                        language = selectedLanguage
                    )

                    if (isNewMode) {
                        val created = modeManager.createMode(updatedMode)
                        associatedApps.forEach { (pkg, forced, _) ->
                            modeManager.setAppMapping(pkg, created.id, forced)
                        }
                    } else {
                        modeManager.updateMode(updatedMode)
                        val oldMappings = modeManager.getMappedAppsForMode(mode.id)
                        oldMappings.forEach { pkg -> modeManager.removeAppMapping(pkg) }
                        associatedApps.forEach { (pkg, forced, _) ->
                            modeManager.setAppMapping(pkg, mode.id, forced)
                        }
                    }
                    onSave()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.btn_save))
            }

            // Reset to Default 按钮（仅内置 Mode）
            if (mode.isBuiltIn) {
                OutlinedButton(
                    onClick = {
                        modeManager.resetBuiltInMode(mode.id)
                        val resetMode = modeManager.getMode(mode.id)
                        if (resetMode != null) {
                            name = resetMode.name
                            emoji = resetMode.iconEmoji ?: ""
                            triggerCondition = resetMode.triggerCondition
                            promptSuffix = resetMode.promptSuffix
                            editPromptSuffix = resetMode.editPromptSuffix
                            customWords = resetMode.customWords
                            selectedLanguage = resetMode.language
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.mode_reset_default))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 已安装应用选择对话框
    if (showAppPicker) {
        AppPickerDialog(
            context = context,
            excludePackages = associatedApps.map { it.first }.toSet(),
            onAppSelected = { pkg ->
                associatedApps = associatedApps + Triple(pkg, false, mode.id)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

// 已安装应用选择对话框
@Composable
private fun AppPickerDialog(
    context: Context,
    excludePackages: Set<String>,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pm = context.packageManager
    // 获取有 launcher intent 的应用（用户可见的应用）
    val apps = remember {
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in excludePackages }
            .map { pkg ->
                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
                val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                val icon = appInfo?.let { pm.getApplicationIcon(it) }
                Triple(pkg, label, icon)
            }
            .sortedBy { it.second.lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.second.contains(searchQuery, ignoreCase = true) ||
            it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_add_app)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.mode_search_app)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 应用列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredApps) { (pkg, label, icon) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(pkg) },
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 应用图标
                                if (icon != null) {
                                    Image(
                                        bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(pkg, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
