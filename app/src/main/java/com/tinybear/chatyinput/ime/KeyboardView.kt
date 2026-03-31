package com.tinybear.chatyinput.ime

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinybear.chatyinput.R

@Composable
fun KeyboardView(
    buffer: String,
    lastAction: String = "",
    isRecording: Boolean,
    isProcessing: Boolean,
    isEditMode: Boolean = false,
    errorMessage: String?,
    holdToRecord: Boolean = false,
    queueStatusText: String = "",
    isVadMode: Boolean = false,
    isVadListening: Boolean = false,
    isVadSpeaking: Boolean = false,
    isVadEditMode: Boolean = false,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onToggleRecording: () -> Unit,
    onStartEditRecording: () -> Unit = {},
    onStopEditRecording: () -> Unit = {},
    onToggleEditRecording: () -> Unit = {},
    onToggleVadVoice: () -> Unit = {},
    onToggleVadEdit: () -> Unit = {},
    onInsert: () -> Unit,
    onClear: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onDeleteTarget: () -> Unit = {},
    onEnter: () -> Unit = {},
    onBufferChange: (String) -> Unit = {},
    bottomPaddingDp: Int = 0,
    currentModeName: String = "",
    availableModes: List<Pair<String?, String>> = emptyList(),
    onModeSelected: (String?) -> Unit = {},
    isSmartEdit: Boolean = false,
    onToggleSmartEdit: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonHeight = 80 // 按钮区固定高度
    val totalHeight = if (expanded) (260 + bottomPaddingDp) else (160 + bottomPaddingDp)

    // 用 TextFieldValue 跟踪光标位置和选区
    var textFieldValue by remember { mutableStateOf(TextFieldValue(buffer)) }
    // 外部 buffer 变化时同步（录音处理后 buffer 会被更新）
    LaunchedEffect(buffer) {
        if (buffer != textFieldValue.text) {
            textFieldValue = TextFieldValue(buffer, TextRange(buffer.length))
        }
    }

    // 获取本地化文字
    val placeholderText = stringResource(R.string.ime_placeholder)
    val recText = stringResource(R.string.ime_rec)
    val stopText = stringResource(R.string.ime_stop)
    val sendText = stringResource(R.string.ime_send)
    val editText = stringResource(R.string.ime_edit)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight.dp)
            .animateContentSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp)
            .padding(top = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 上半区：信息区（弹性高度，不挤压按钮区）
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
        // 错误信息
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // 文本预览（弹性填充剩余空间）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onBufferChange(it.text)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                decorationBox = { innerTextField ->
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            placeholderText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            )
        }

        // AI 处理说明 + 队列状态（同一行）
        if (lastAction.isNotEmpty() || queueStatusText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lastAction.isNotEmpty()) {
                    Text(
                        text = "\u2192 $lastAction",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (queueStatusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = queueStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        } // end 信息区 Column

        // 主操作区：左列 | Mode | PTT | Edit | 右列（固定高度）
        Row(
            modifier = Modifier.fillMaxWidth().height(buttonHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 左列：展开 + Clear + 切换键盘
            Column(
                modifier = Modifier.width(48.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FilledTonalButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(if (expanded) "\u25BE" else "\u25B4", fontSize = 14.sp) }

                FilledTonalButton(
                    onClick = onClear,
                    enabled = buffer.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("\u2715", fontSize = 14.sp) }

                FilledTonalButton(
                    onClick = onSwitchKeyboard,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("\uD83C\uDF10", fontSize = 14.sp) }
            }

            // Mode 选择器状态（共用）
            var showModeMenu by remember { mutableStateOf(false) }

            // 内联 Mode 选择面板（替代 DropdownMenu，避免 IME 弹跳）
            if (showModeMenu) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auto 选项
                    Surface(
                        onClick = { onModeSelected(null); showModeMenu = false },
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentModeName.isEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.mode_auto), fontSize = 13.sp,
                                color = if (currentModeName.isEmpty()) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 各 Mode 选项
                    availableModes.forEach { (id, displayName) ->
                        Surface(
                            onClick = { onModeSelected(id); showModeMenu = false },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                Text("\uD83D\uDD12 $displayName", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
            // 正常按钮区
            else if (isVadMode) {
                // VAD 模式：Mode(1/4) + 语音按钮(2/4) + 编辑按钮(1/4)
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Mode + Smart/Strict 列 (1/4)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Mode 按钮（上半）
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = { tryAwaitRelease(); showModeMenu = true })
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    if (currentModeName.isEmpty()) stringResource(R.string.mode_auto)
                                    else currentModeName.take(2).trim(),
                                    fontSize = 12.sp,
                                    color = Color.White, maxLines = 1
                            )
                        }
                    }

                        // Smart/Strict 按钮（下半）
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = { tryAwaitRelease(); onToggleSmartEdit() })
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSmartEdit) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    if (isSmartEdit) "\u26A1" else "\uD83D\uDD12",
                                    fontSize = 14.sp,
                                    color = if (isSmartEdit) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } // end Mode + Smart/Strict Column

                    // 语音 VAD 按钮 (2/4)
                    val isVoiceActive = isVadListening && !isVadEditMode
                    val voiceColor = if (isVoiceActive) Color.Red
                                     else MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = { tryAwaitRelease(); onToggleVadVoice() })
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = voiceColor
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isVoiceActive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White, strokeWidth = 2.dp
                                    )
                                    Text(
                                        if (isVadSpeaking) stringResource(R.string.vad_speaking)
                                        else stringResource(R.string.vad_listening),
                                        fontSize = 14.sp, color = Color.White
                                    )
                                } else {
                                    Text("\uD83C\uDFA4 $recText", fontSize = 18.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // 编辑 VAD 按钮 (1/4)
                    val isEditActive = isVadListening && isVadEditMode
                    val editVadColor = if (isEditActive) Color.Red
                                       else MaterialTheme.colorScheme.secondary
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(buffer) {
                                if (buffer.isEmpty() && !isEditActive) return@pointerInput
                                detectTapGestures(onPress = { tryAwaitRelease(); onToggleVadEdit() })
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (buffer.isEmpty() && !isEditActive) editVadColor.copy(alpha = 0.3f)
                                else editVadColor
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isEditActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                            } else {
                                Text("\u270F\uFE0F", fontSize = 18.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // PTT / Toggle 模式：Mode(1/4) + PTT(2/4) + Edit(1/4)
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Mode + Smart/Strict 列 (1/4)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Mode 按钮（上半）
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = { tryAwaitRelease(); showModeMenu = true })
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    if (currentModeName.isEmpty()) stringResource(R.string.mode_auto)
                                    else currentModeName.take(2).trim(),
                                    fontSize = 12.sp,
                                    color = Color.White, maxLines = 1
                            )
                        }
                    }

                        // Smart/Strict 按钮（下半）
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = { tryAwaitRelease(); onToggleSmartEdit() })
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSmartEdit) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    if (isSmartEdit) "\u26A1" else "\uD83D\uDD12",
                                    fontSize = 14.sp,
                                    color = if (isSmartEdit) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } // end Mode + Smart/Strict Column

                    // PTT 大按钮 (2/4)
                    val pttColor = if (isRecording && !isEditMode) Color.Red
                                   else MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .pointerInput(holdToRecord) {
                                detectTapGestures(
                                    onPress = {
                                        if (holdToRecord) {
                                            onStartRecording()
                                            tryAwaitRelease()
                                            onStopRecording()
                                        } else {
                                            tryAwaitRelease()
                                            onToggleRecording()
                                        }
                                    }
                                )
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = pttColor
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (isRecording && !isEditMode) "\u25A0 $stopText" else "\uD83C\uDFA4 $recText",
                                fontSize = 18.sp, color = Color.White
                            )
                        }
                    }

                    // 编辑按钮 (1/4)
                    val editColor = if (isRecording && isEditMode) Color.Red
                                    else MaterialTheme.colorScheme.secondary
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(holdToRecord, buffer) {
                                if (buffer.isEmpty()) return@pointerInput
                                detectTapGestures(
                                    onPress = {
                                        if (holdToRecord) {
                                            onStartEditRecording()
                                            tryAwaitRelease()
                                            onStopEditRecording()
                                        } else {
                                            tryAwaitRelease()
                                            onToggleEditRecording()
                                        }
                                    }
                                )
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (buffer.isEmpty()) editColor.copy(alpha = 0.3f) else editColor
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (isRecording && isEditMode) "\u25A0" else "\u270F\uFE0F",
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 右列：删除 + Insert + 回车
            Column(
                modifier = Modifier.width(48.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 删除（支持长按连续删除）
                // 缓冲区有字：按光标位置删除；缓冲区空：删目标输入框
                RepeatableButton(
                    onClick = {
                        val text = textFieldValue.text
                        val sel = textFieldValue.selection
                        if (text.isEmpty()) {
                            // 缓冲区空：删目标输入框
                            onDeleteTarget()
                        } else if (sel.length > 0) {
                            // 有选区：删除选中内容
                            val newText = text.removeRange(sel.min, sel.max)
                            textFieldValue = TextFieldValue(newText, TextRange(sel.min))
                            onBufferChange(newText)
                        } else if (sel.start > 0) {
                            // 无选区：删光标前一个字符
                            val newText = text.removeRange(sel.start - 1, sel.start)
                            textFieldValue = TextFieldValue(newText, TextRange(sel.start - 1))
                            onBufferChange(newText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { Text("\u232B", fontSize = 16.sp) }

                // 回车/发送：缓冲区有字时 Insert，无字时回车
                Button(
                    onClick = { if (buffer.isNotEmpty()) onInsert() else onEnter() },
                    enabled = !isRecording && !isProcessing,
                    modifier = Modifier.fillMaxWidth().weight(2f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = if (buffer.isNotEmpty()) ButtonDefaults.buttonColors()
                             else ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(
                        if (buffer.isNotEmpty()) sendText else "\u21B5",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// 支持长按连续触发的按钮
@Composable
private fun RepeatableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    // 监听按压/释放状态
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // 按下时触发首次点击 + 长按连续
    LaunchedEffect(pressed) {
        if (pressed) {
            onClick()
            delay(400)
            while (pressed) {
                onClick()
                delay(50)
            }
        }
    }

    FilledTonalButton(
        onClick = { /* 由 interactionSource 处理 */ },
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
        content = content
    )
}
