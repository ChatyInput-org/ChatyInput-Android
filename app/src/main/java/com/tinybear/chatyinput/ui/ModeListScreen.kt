package com.tinybear.chatyinput.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.model.Mode
import com.tinybear.chatyinput.service.ModeManager

// Mode 列表界面（主 App 的 Modes Tab）
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModeListScreen(
    config: AppConfig,
    context: Context,
    onEditMode: (Mode) -> Unit,
    onCreateMode: () -> Unit
) {
    val modeManager = remember { ModeManager(context) }
    var modes by remember { mutableStateOf(modeManager.getAllModes()) }
    var activeModeId by remember { mutableStateOf(config.activeModeId) }
    var autoModeEnabled by remember { mutableStateOf(config.autoModeEnabled) }
    var locationModeEnabled by remember { mutableStateOf(config.locationModeEnabled) }
    var locationLanguageEnabled by remember { mutableStateOf(config.locationLanguageEnabled) }
    var showDeleteDialog by remember { mutableStateOf<Mode?>(null) }

    // 位置权限请求
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationModeEnabled = true
            config.locationModeEnabled = true
        } else {
            locationModeEnabled = false
            config.locationModeEnabled = false
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateMode
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.mode_create))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.tab_modes),
                style = MaterialTheme.typography.titleMedium
            )

            // Auto-switch 开关
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.mode_auto_switch),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = autoModeEnabled,
                            onCheckedChange = {
                                autoModeEnabled = it
                                config.autoModeEnabled = it
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.mode_auto_switch_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location Context 开关
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.mode_location_context),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = locationModeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        locationModeEnabled = true
                                        config.locationModeEnabled = true
                                    } else {
                                        locationPermissionLauncher.launch(
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    }
                                } else {
                                    locationModeEnabled = false
                                    config.locationModeEnabled = false
                                }
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.mode_location_context_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location Language 开关（仅在 locationModeEnabled 时显示）
            if (locationModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.mode_location_language),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = locationLanguageEnabled,
                                onCheckedChange = {
                                    locationLanguageEnabled = it
                                    config.locationLanguageEnabled = it
                                }
                            )
                        }
                        Text(
                            stringResource(R.string.mode_location_language_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 当前活跃 Mode 指示
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.mode_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val activeMode = activeModeId?.let { id -> modes.find { it.id == id } }
                    Text(
                        if (activeMode != null) {
                            "${activeMode.iconEmoji ?: ""} ${activeMode.name}".trim()
                        } else {
                            stringResource(R.string.mode_default)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Mode 列表
            if (modes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.mode_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(modes) { _, mode ->
                        val isActive = mode.id == activeModeId
                        val appCount = modeManager.getMappedAppsForMode(mode.id).size

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    // 点击 = 选择/激活 Mode
                                    onClick = {
                                        if (isActive) {
                                            config.activeModeId = null
                                            activeModeId = null
                                        } else {
                                            config.activeModeId = mode.id
                                            activeModeId = mode.id
                                        }
                                    },
                                    // 长按 = 删除（仅非内置）
                                    onLongClick = {
                                        if (!mode.isBuiltIn) {
                                            showDeleteDialog = mode
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isActive) CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else CardDefaults.cardColors()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Emoji 或默认图标
                                Text(
                                    mode.iconEmoji ?: "\uD83D\uDCCB",
                                    fontSize = 28.sp
                                )

                                // Mode 信息
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            mode.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (isActive) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (mode.isBuiltIn) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    stringResource(R.string.mode_built_in),
                                                    modifier = Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 2.dp
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.mode_app_count, appCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 齿轮按钮 = 进入编辑
                                IconButton(
                                    onClick = { onEditMode(mode) }
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.mode_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { modeToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.mode_delete_title)) },
            text = { Text(stringResource(R.string.mode_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        modeManager.deleteMode(modeToDelete.id)
                        if (activeModeId == modeToDelete.id) {
                            config.activeModeId = null
                            activeModeId = null
                        }
                        modes = modeManager.getAllModes()
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
