package com.tinybear.chatyinput.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.model.HistoryEntry
import com.tinybear.chatyinput.model.VoiceIntent
import com.tinybear.chatyinput.service.AudioCaptureService
import com.tinybear.chatyinput.service.HistoryManager
import com.tinybear.chatyinput.service.VoiceIntentProcessor
import kotlinx.coroutines.launch
import java.util.UUID

// 语音录制界面，对应 Apple 版 VoiceTabView
@Composable
fun VoiceScreen(config: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态
    var buffer by remember { mutableStateOf("") }
    // 当前 group ID（每次 clear/send 后重新生成）
    var currentGroupId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) } // 编辑模式标记
    var lastSegment by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    // 本地化的错误文字（Compose 内获取，传入非 Compose lambda）
    val strNoAudio = stringResource(R.string.error_no_audio)
    val strConfigureApi = stringResource(R.string.error_configure_api)
    val strMicPermission = stringResource(R.string.error_mic_permission)
    val strRecordingFailed = stringResource(R.string.error_recording_failed)

    // 服务
    val audioService = remember { AudioCaptureService() }

    // 运行时权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            errorMessage = strMicPermission
        }
    }

    // 初始时请求权限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 录音完成后的处理流程（editMode: 是否使用编辑 Prompt）
    fun processRecording(editMode: Boolean = false) {
        scope.launch {
            isProcessing = true
            errorMessage = null
            try {
                // 1. 停止录音，获取音频数据
                val audioData = audioService.stopRecording()
                if (audioData == null || audioData.isEmpty()) {
                    errorMessage = strNoAudio
                    isProcessing = false
                    return@launch
                }

                // 2. 检查配置有效性
                if (!config.isValid) {
                    errorMessage = strConfigureApi
                    isProcessing = false
                    return@launch
                }

                // 3. STT 转文字
                val sttProvider = config.makeSTTProvider()
                val transcript = sttProvider.transcribe(audioData, "m4a")
                lastSegment = transcript

                // 4. LLM 意图分类 + 处理（编辑模式使用 editSystemPrompt）
                val llmProvider = config.makeLLMProvider()
                val prompt = if (editMode) config.editSystemPrompt else config.systemPrompt
                val processor = VoiceIntentProcessor(
                    llmProvider = llmProvider,
                    systemPrompt = prompt,
                    customWords = config.customWords
                )
                val result = processor.process(transcript, buffer)

                // 5. 根据意图更新缓冲区
                when (result.intent) {
                    VoiceIntent.CONTENT -> {
                        buffer = if (buffer.isEmpty()) {
                            result.resultText
                        } else {
                            buffer + result.resultText
                        }
                        lastAction = "Content: ${result.explanation ?: "appended"}"
                    }
                    VoiceIntent.EDIT -> {
                        buffer = result.resultText
                        lastAction = "Edit: ${result.explanation ?: "edited"}"
                    }
                    VoiceIntent.SEND -> {
                        lastAction = "Send: ready to send"
                    }
                }

                // 6. 保存历史记录
                val audioFileName = "voice_${System.currentTimeMillis()}.m4a"
                val entry = HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    transcript = transcript,
                    intent = result.intent,
                    resultText = result.resultText,
                    explanation = result.explanation,
                    audioFileName = audioFileName,
                    groupId = currentGroupId
                )
                HistoryManager.saveEntry(context, entry, audioData)

                // send 意图：结束当前 group
                if (result.intent == VoiceIntent.SEND) {
                    HistoryManager.endGroup(context, currentGroupId, com.tinybear.chatyinput.model.GroupEndType.SENT, buffer)
                    currentGroupId = UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error"
            } finally {
                isProcessing = false
                isEditMode = false // 处理完毕重置编辑模式
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 文本缓冲区编辑框
        OutlinedTextField(
            value = buffer,
            onValueChange = { buffer = it },
            label = { Text(stringResource(R.string.text_buffer)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxLines = 20
        )

        // 错误信息
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 最近片段 + 动作
        if (lastSegment.isNotEmpty()) {
            Text(
                text = stringResource(R.string.last_segment, lastSegment),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (lastAction.isNotEmpty()) {
            Text(
                text = stringResource(R.string.last_action, lastAction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 处理中指示器
        if (isProcessing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.processing), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 开始录音的公共逻辑
        fun startRec() {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            errorMessage = null
            try {
                audioService.startRecording(context.cacheDir)
                isRecording = true
            } catch (e: Exception) {
                errorMessage = "$strRecordingFailed: ${e.message}"
            }
        }

        // 停止录音的公共逻辑
        fun stopRec() {
            isRecording = false
            processRecording(editMode = isEditMode)
        }

        // 录音按钮：支持点击切换和长按模式
        val holdMode = config.holdToRecord
        // 按钮文字
        val buttonText = if (isRecording) {
            if (holdMode) stringResource(R.string.recording_release_to_stop)
            else stringResource(R.string.recording_tap_to_stop)
        } else {
            if (holdMode) stringResource(R.string.btn_hold_to_record)
            else stringResource(R.string.btn_record)
        }

        // 录音 + 编辑按钮行（主按钮占 3/4，编辑按钮占 1/4）
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 主录音按钮（3/4 宽度）
            Button(
                onClick = {
                    if (!holdMode) {
                        if (isRecording) stopRec() else {
                            isEditMode = false
                            startRec()
                        }
                    }
                },
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
                    .then(
                        if (holdMode) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        if (!isProcessing) {
                                            isEditMode = false
                                            startRec()
                                            tryAwaitRelease()
                                            if (isRecording) stopRec()
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    ),
                enabled = !isProcessing,
                colors = if (isRecording && !isEditMode) {
                    ButtonDefaults.buttonColors(containerColor = Color.Red)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(buttonText)
            }

            // 编辑录音按钮（1/4 宽度，铅笔图标）
            Button(
                onClick = {
                    if (!holdMode) {
                        if (isRecording && isEditMode) {
                            stopRec()
                        } else if (!isRecording) {
                            isEditMode = true
                            startRec()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (holdMode) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        if (!isProcessing && buffer.isNotEmpty()) {
                                            isEditMode = true
                                            startRec()
                                            tryAwaitRelease()
                                            if (isRecording) stopRec()
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    ),
                enabled = !isProcessing && buffer.isNotEmpty() && !(isRecording && !isEditMode),
                contentPadding = PaddingValues(4.dp),
                colors = if (isRecording && isEditMode) {
                    ButtonDefaults.buttonColors(containerColor = Color.Red)
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {
                Text("✏️", fontSize = 18.sp)
            }
        }

        // Clear + Copy 按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (buffer.isNotEmpty()) {
                        HistoryManager.endGroup(context, currentGroupId, com.tinybear.chatyinput.model.GroupEndType.CLEARED, buffer)
                        currentGroupId = UUID.randomUUID().toString()
                    }
                    buffer = ""
                    lastSegment = ""
                    lastAction = ""
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.btn_clear))
            }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ChatyInput", buffer))
                },
                modifier = Modifier.weight(1f),
                enabled = buffer.isNotEmpty()
            ) {
                Text(stringResource(R.string.btn_copy))
            }
        }
    }
}
