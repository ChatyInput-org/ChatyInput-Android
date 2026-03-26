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
    bottomPaddingDp: Int = 0
) {
    var expanded by remember { mutableStateOf(false) }
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
            .padding(top = 3.dp, bottom = bottomPaddingDp.dp),
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

        // 文本预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp, max = if (expanded) 140.dp else 36.dp)
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

        // AI 处理说明
        if (lastAction.isNotEmpty()) {
            Text(
                text = "\u2192 $lastAction",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }

        // 队列状态
        if (queueStatusText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
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

        // 主操作区：左列 | PTT(大) | 右列
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
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

            // 中间：VAD 模式显示语音/编辑切换按钮，PTT/Toggle 模式显示录音按钮
            if (isVadMode) {
                // VAD 模式：语音按钮(3/4) + 编辑按钮(1/4)，手动开始/停止
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 语音 VAD 按钮 (3/4)
                    val isVoiceActive = isVadListening && !isVadEditMode
                    val voiceColor = if (isVoiceActive) Color.Red
                                     else MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        tryAwaitRelease()
                                        onToggleVadVoice()
                                    }
                                )
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
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        if (isVadSpeaking) stringResource(R.string.vad_speaking)
                                        else stringResource(R.string.vad_listening),
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        "\uD83C\uDFA4 $recText",
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
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
                                detectTapGestures(
                                    onPress = {
                                        tryAwaitRelease()
                                        onToggleVadEdit()
                                    }
                                )
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (buffer.isEmpty() && !isEditActive) editVadColor.copy(alpha = 0.3f)
                                else editVadColor
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isEditActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "\u270F\uFE0F",
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // PTT / Toggle 模式：PTT 大按钮(3/4) + 编辑按钮(1/4)
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // PTT 大按钮 (3/4) — 不再因 isProcessing 而禁用
                    val pttColor = if (isRecording && !isEditMode) Color.Red
                                   else MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .weight(3f)
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
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        }
                    }

                    // 编辑按钮 (1/4) — 不再因 isProcessing 而禁用
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
