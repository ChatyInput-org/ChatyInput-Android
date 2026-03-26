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
import com.tinybear.chatyinput.model.VoiceIntent
import com.tinybear.chatyinput.service.AudioCaptureService
import com.tinybear.chatyinput.service.RecordingPipeline
import com.tinybear.chatyinput.service.VadService
import kotlinx.coroutines.launch

// 语音录制界面，对应 Apple 版 VoiceTabView
@Composable
fun VoiceScreen(config: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pipeline 和 VadService（composable 生命周期内唯一实例）
    val pipeline = remember {
        RecordingPipeline(config, context)
    }
    val vadService = remember {
        VadService(context, (config.silenceThreshold * 1000).toLong())
    }

    // 状态
    var buffer by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) } // 编辑模式标记
    var lastSegment by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    // Pipeline 队列状态
    val queueStatus by pipeline.queueStatus.collectAsState()

    // VAD 状态
    val vadListening by vadService.isListening.collectAsState()
    val vadSpeaking by vadService.isSpeaking.collectAsState()

    // 录制模式
    val recordingMode = config.recordingMode

    // 本地化的错误文字（Compose 内获取，传入非 Compose lambda）
    val strNoAudio = stringResource(R.string.error_no_audio)
    val strMicPermission = stringResource(R.string.error_mic_permission)
    val strRecordingFailed = stringResource(R.string.error_recording_failed)

    // 服务
    val audioService = remember { AudioCaptureService() }

    // 设置 Pipeline 回调
    LaunchedEffect(Unit) {
        pipeline.onBufferUpdated = { result, newBuffer ->
            buffer = newBuffer
            when (result.intent) {
                VoiceIntent.CONTENT -> {
                    lastAction = "Content: ${result.explanation ?: "appended"}"
                }
                VoiceIntent.EDIT -> {
                    lastAction = "Edit: ${result.explanation ?: "edited"}"
                }
                VoiceIntent.SEND -> {
                    lastAction = "Send: ready to send"
                }
                VoiceIntent.UNDO -> {
                    lastAction = "Undo: ${result.explanation ?: "reverted to previous version"}"
                }
            }
        }
        pipeline.onError = { msg ->
            errorMessage = msg
        }
        pipeline.onSegmentTranscribed = { transcript ->
            lastSegment = transcript
        }
    }

    // 同步 buffer 编辑到 pipeline（用户手动编辑文本缓冲区时）
    LaunchedEffect(buffer) {
        pipeline.buffer = buffer
    }

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

    // VAD 模式：手动控制，不再自动启动
    // 跟踪 VAD 编辑模式（true=编辑模式, false=语音模式）
    var vadEditMode by remember { mutableStateOf(false) }

    // VAD 启动辅助函数
    fun startVad(isEdit: Boolean) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        vadService.silenceThresholdMs = (config.silenceThreshold * 1000).toLong()
        vadEditMode = isEdit
        vadService.start { audioData ->
            pipeline.submit(audioData, isEdit = isEdit)
        }
    }

    fun stopVad() {
        vadService.stop()
    }

    // DisposableEffect：清理 pipeline 和 vadService
    DisposableEffect(Unit) {
        onDispose {
            vadService.destroy()
            pipeline.destroy()
        }
    }

    // 录音完成后通过 Pipeline 提交（PTT/Toggle 模式）
    fun processRecording(editMode: Boolean = false) {
        scope.launch {
            errorMessage = null
            try {
                val audioData = audioService.stopRecording()
                if (audioData == null || audioData.isEmpty()) {
                    errorMessage = strNoAudio
                    return@launch
                }
                pipeline.submit(audioData, isEdit = editMode)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error"
            } finally {
                isEditMode = false // 处理完毕重置编辑模式
            }
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

        // 队列进度指示器（非空闲时显示）
        if (!queueStatus.isIdle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.queue_progress, queueStatus.currentIndex, queueStatus.total),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 根据录制模式显示不同 UI
        when (recordingMode) {
            "vad" -> {
                // VAD 模式：语音/编辑 按钮（手动开始/停止 VAD）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 语音按钮（3/4 宽度）
                    val isVoiceActive = vadListening && !vadEditMode
                    Button(
                        onClick = {
                            if (isVoiceActive) {
                                stopVad()
                            } else {
                                if (vadListening) stopVad() // 先停止编辑模式
                                startVad(isEdit = false)
                            }
                        },
                        modifier = Modifier.weight(3f).fillMaxHeight(),
                        colors = if (isVoiceActive) {
                            ButtonDefaults.buttonColors(containerColor = Color.Red)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        if (isVoiceActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (vadSpeaking) stringResource(R.string.vad_speaking)
                                else stringResource(R.string.vad_listening)
                            )
                        } else {
                            Text(stringResource(R.string.btn_record))
                        }
                    }

                    // 编辑按钮（1/4 宽度）
                    val isEditActive = vadListening && vadEditMode
                    Button(
                        onClick = {
                            if (isEditActive) {
                                stopVad()
                            } else {
                                if (vadListening) stopVad() // 先停止语音模式
                                startVad(isEdit = true)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        enabled = buffer.isNotEmpty() || isEditActive,
                        contentPadding = PaddingValues(4.dp),
                        colors = if (isEditActive) {
                            ButtonDefaults.buttonColors(containerColor = Color.Red)
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    ) {
                        if (isEditActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("\u270F\uFE0F", fontSize = 18.sp)
                        }
                    }
                }
            }

            else -> {
                // PTT / Toggle 模式：显示录音按钮
                val holdMode = recordingMode == "ptt"
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
                    // 主录音按钮（3/4 宽度）— 不再因 isProcessing 而禁用
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
                                                isEditMode = false
                                                startRec()
                                                tryAwaitRelease()
                                                if (isRecording) stopRec()
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        colors = if (isRecording && !isEditMode) {
                            ButtonDefaults.buttonColors(containerColor = Color.Red)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(buttonText)
                    }

                    // 编辑录音按钮（1/4 宽度，铅笔图标）— 不再因 isProcessing 而禁用
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
                                                if (buffer.isNotEmpty()) {
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
                        enabled = buffer.isNotEmpty() && !(isRecording && !isEditMode),
                        contentPadding = PaddingValues(4.dp),
                        colors = if (isRecording && isEditMode) {
                            ButtonDefaults.buttonColors(containerColor = Color.Red)
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    ) {
                        Text("\u270F\uFE0F", fontSize = 18.sp)
                    }
                }
            }
        }

        // Clear + Copy 按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    pipeline.clearBuffer()
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
