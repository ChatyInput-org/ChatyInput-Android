package com.tinybear.chatyinput.ui

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.model.GroupEndType
import com.tinybear.chatyinput.model.VoiceIntent
import com.tinybear.chatyinput.service.HistoryGroup
import com.tinybear.chatyinput.service.HistoryManager

// 历史记录界面：按 group 分组展示
@Composable
fun HistoryScreen(config: AppConfig) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<HistoryGroup>>(emptyList()) }
    var expandedGroupId by remember { mutableStateOf<String?>(null) }
    var currentPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        groups = HistoryManager.loadGrouped(context, config.historyRetentionDays)
    }

    DisposableEffect(Unit) {
        onDispose { currentPlayer?.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.history_subtitle, config.historyRetentionDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.groupId }) { group ->
                    val isExpanded = expandedGroupId == group.groupId

                    GroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        playingId = playingId,
                        onClick = {
                            expandedGroupId = if (isExpanded) null else group.groupId
                        },
                        onPlayAudio = { fileName, entryId ->
                            currentPlayer?.release()
                            currentPlayer = null
                            playingId = null
                            val audioFile = HistoryManager.getAudioFile(context, fileName)
                            if (audioFile != null) {
                                try {
                                    val player = MediaPlayer().apply {
                                        setDataSource(audioFile.absolutePath)
                                        prepare()
                                        start()
                                        setOnCompletionListener {
                                            playingId = null
                                            it.release()
                                            currentPlayer = null
                                        }
                                    }
                                    currentPlayer = player
                                    playingId = entryId
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}

// Group 卡片
@Composable
private fun GroupCard(
    group: HistoryGroup,
    isExpanded: Boolean,
    playingId: String?,
    onClick: () -> Unit,
    onPlayAudio: (fileName: String, entryId: String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 顶部：时间 + group 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatRelativeTimeLocalized(group.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 条目数量
                    Text(
                        "${group.entries.size}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 结束类型标签
                    GroupEndBadge(group.endType)
                }
            }

            // 最终文本（group 的输出结果）
            if (group.finalText.isNotEmpty()) {
                Text(
                    group.finalText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 展开：显示每条语音输入详情
            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    // 逐条展示
                    group.entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 意图标签
                            IntentBadge(entry.intent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                // 原始语音
                                Text(
                                    entry.transcript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // 处理结果
                                if (entry.resultText.isNotEmpty() && entry.resultText != entry.transcript) {
                                    Text(
                                        "→ ${entry.resultText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // 说明
                                if (!entry.explanation.isNullOrBlank()) {
                                    Text(
                                        entry.explanation,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // 播放按钮
                            if (entry.audioFileName != null) {
                                IconButton(
                                    onClick = { onPlayAudio(entry.audioFileName, entry.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (playingId == entry.id) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Group 结束类型标签
@Composable
private fun GroupEndBadge(endType: GroupEndType) {
    val (text, color) = when (endType) {
        GroupEndType.SENT -> "Sent" to MaterialTheme.colorScheme.primary
        GroupEndType.CLEARED -> "Cleared" to MaterialTheme.colorScheme.error
        GroupEndType.ACTIVE -> "Active" to MaterialTheme.colorScheme.tertiary
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall, color = color
        )
    }
}

// 意图标签
@Composable
private fun IntentBadge(intent: VoiceIntent) {
    val (text, color) = when (intent) {
        VoiceIntent.CONTENT -> stringResource(R.string.intent_content) to MaterialTheme.colorScheme.primary
        VoiceIntent.EDIT -> stringResource(R.string.intent_edit) to MaterialTheme.colorScheme.tertiary
        VoiceIntent.SEND -> stringResource(R.string.intent_send) to MaterialTheme.colorScheme.secondary
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall, color = color
        )
    }
}

// 本地化相对时间
@Composable
private fun formatRelativeTimeLocalized(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
        days == 1L -> stringResource(R.string.time_yesterday)
        days < 7 -> stringResource(R.string.time_days_ago, days.toInt())
        else -> java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
