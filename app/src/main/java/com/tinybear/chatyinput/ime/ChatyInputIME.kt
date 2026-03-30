package com.tinybear.chatyinput.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.LocaleHelper
import com.tinybear.chatyinput.model.VoiceIntent
import com.tinybear.chatyinput.service.AudioCaptureService
import com.tinybear.chatyinput.service.LocationProvider
import com.tinybear.chatyinput.service.ModeManager
import com.tinybear.chatyinput.service.ModeResolver
import com.tinybear.chatyinput.service.RecordingPipeline
import com.tinybear.chatyinput.service.VadService
import kotlinx.coroutines.*

// IME 输入法服务
// 实现 LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner 以支持 Compose
class ChatyInputIME : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val audioService = AudioCaptureService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Pipeline 和 VadService（Service 生命周期管理）
    private var pipeline: RecordingPipeline? = null
    private var vadService: VadService? = null

    // 本地化 Context（用于获取字符串资源）
    private lateinit var localizedContext: Context

    // 键盘 UI 状态
    private var buffer = mutableStateOf("")
    private var isRecording = mutableStateOf(false)
    private var isEditMode = mutableStateOf(false) // 编辑模式标记
    private var errorMessage = mutableStateOf<String?>(null)
    private var lastAction = mutableStateOf("")
    // Pipeline 队列状态文字（在 IME 缓冲区旁显示）
    private var queueStatusText = mutableStateOf("")
    // VAD 模式状态
    private var isVadMode = mutableStateOf(false)
    private var isVadListening = mutableStateOf(false)
    private var isVadSpeaking = mutableStateOf(false)
    private var isVadEditMode = mutableStateOf(false) // VAD 编辑模式标记
    // 当前 Mode 名称（用于 IME 指示器）
    private var currentModeName = mutableStateOf("")
    // 可用 Mode 列表（供 IME 下拉选择器）
    private var availableModes = mutableStateOf<List<Pair<String?, String>>>(emptyList())

    // 收集 Pipeline/VAD StateFlow 的协程
    private var collectorJob: Job? = null

    // 在 IME 创建前应用语言设置
    override fun attachBaseContext(newBase: Context) {
        val config = AppConfig(newBase)
        val ctx = LocaleHelper.applyLocale(newBase, config.language)
        super.attachBaseContext(ctx)
    }

    override fun onCreate() {
        super.onCreate()
        // 缓存本地化 Context，供非 Compose 代码使用
        localizedContext = LocaleHelper.getLocalizedContext(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // 创建 Pipeline 和 VadService（Service 生命周期）
        val config = AppConfig(applicationContext)
        val modeManager = ModeManager(applicationContext)
        val modeResolver = ModeResolver(config, modeManager)
        val locationProvider = if (config.locationModeEnabled) LocationProvider(applicationContext) else null
        locationProvider?.refreshLocation()
        pipeline = RecordingPipeline(config, applicationContext, modeResolver, locationProvider).also { p ->
            p.onBufferUpdated = { result, newBuffer ->
                buffer.value = newBuffer
                when (result.intent) {
                    VoiceIntent.CONTENT -> {
                        lastAction.value = result.explanation ?: "Added text"
                    }
                    VoiceIntent.EDIT -> {
                        lastAction.value = result.explanation ?: "Edited text"
                    }
                    VoiceIntent.SEND -> {
                        // SEND 意图：通过 currentInputConnection 注入文字
                        commitTextToEditor(newBuffer)
                        p.buffer = ""
                        buffer.value = ""
                        lastAction.value = "Text sent"
                    }
                    VoiceIntent.UNDO -> {
                        lastAction.value = result.explanation ?: "Reverted to previous version"
                    }
                }
            }
            p.onError = { msg ->
                errorMessage.value = msg
            }
            p.onSegmentTranscribed = { _ ->
                // IME 中不单独显示转录结果（空间有限）
            }
            p.onModeChanged = { modeName ->
                currentModeName.value = modeName
            }
        }

        vadService = VadService(applicationContext, (config.silenceThreshold * 1000).toLong())

        // 收集 Pipeline 队列状态和 VAD 状态
        collectorJob = scope.launch {
            launch {
                pipeline!!.queueStatus.collect { status ->
                    queueStatusText.value = if (status.isIdle) "" else "${status.currentIndex}/${status.total}"
                }
            }
            launch {
                vadService!!.isListening.collect { listening ->
                    isVadListening.value = listening
                }
            }
            launch {
                vadService!!.isSpeaking.collect { speaking ->
                    isVadSpeaking.value = speaking
                }
            }
        }

        // 检查录制模式
        isVadMode.value = config.recordingMode == "vad"
    }

    override fun onCreateInputView(): View {
        // 每次创建键盘视图时刷新本地化（用户可能在主 app 切换了语言）
        localizedContext = LocaleHelper.getLocalizedContext(this)

        // 刷新录制模式配置
        val config = AppConfig(applicationContext)
        isVadMode.value = config.recordingMode == "vad"

        // 必须在 IME 窗口的 decorView 上设置（Compose 往父级查找，不在 ComposeView 本身）
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
        }

        // 检测是否使用手势导航（底部有手势条需要留空间）
        // 三键/两键导航不需要额外 padding，系统已把 IME 放在导航栏上方
        val density = resources.displayMetrics.density
        val navBarDp = run {
            val resourceId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
            // 0=三键, 1=两键, 2=手势导航
            val navMode = if (resourceId > 0) resources.getInteger(resourceId) else -1
            android.util.Log.i("ChatyInputIME", "navMode=$navMode (2=gesture)")
            if (navMode == 2) {
                // 手势导航：留出手势条空间
                val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                if (resId > 0) (resources.getDimensionPixelSize(resId) / density).toInt()
                else 16
            } else {
                // 三键/两键导航：只留最小间距
                4
            }
        }
        android.util.Log.i("ChatyInputIME", "bottomPadding=${navBarDp}dp")

        val composeView = ComposeView(this)
        composeView.setContent {
            com.tinybear.chatyinput.ui.ChatyInputTheme {
            val bufferValue by buffer
            val recordingValue by isRecording
            val editModeValue by isEditMode
            val errorValue by errorMessage
            val actionValue by lastAction
            val queueText by queueStatusText
            val vadMode by isVadMode
            val vadListening by isVadListening
            val vadSpeaking by isVadSpeaking
            val vadEditMode by isVadEditMode
            val holdMode = config.holdToRecord
            val modeName by currentModeName
            val modesList by availableModes

            KeyboardView(
                buffer = bufferValue,
                lastAction = actionValue,
                isRecording = recordingValue,
                isProcessing = false, // 按钮不再因处理中而禁用
                isEditMode = editModeValue,
                errorMessage = errorValue,
                holdToRecord = holdMode,
                queueStatusText = queueText,
                isVadMode = vadMode,
                isVadListening = vadListening,
                isVadSpeaking = vadSpeaking,
                isVadEditMode = vadEditMode,
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() },
                onToggleRecording = { toggleRecording() },
                onStartEditRecording = { startEditRecording() },
                onStopEditRecording = { stopEditRecording() },
                onToggleEditRecording = { toggleEditRecording() },
                onToggleVadVoice = { toggleVadVoice() },
                onToggleVadEdit = { toggleVadEdit() },
                onInsert = { insertText() },
                onClear = { clearBuffer() },
                onSwitchKeyboard = { switchKeyboard() },
                onDeleteTarget = { deleteTargetText() },
                onEnter = { commitTextToEditor("\n") },
                onBufferChange = { newText ->
                    buffer.value = newText
                    pipeline?.buffer = newText
                },
                bottomPaddingDp = navBarDp,
                currentModeName = modeName,
                availableModes = modesList,
                onModeSelected = { modeId -> onModeSelected(modeId) }
            )
            } // end ChatyInputTheme
        }
        return composeView
    }

    // 键盘显示时：刷新 VAD 模式配置，捕获前台应用包名（不再自动开始监听）
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val config = AppConfig(applicationContext)
        isVadMode.value = config.recordingMode == "vad"
        // 捕获前台应用包名，用于 Mode 自动切换
        val pkg = info?.packageName
        pipeline?.currentAppPackage = pkg
        // 更新 Mode 指示器和可用 Mode 列表
        updateModeState(config, pkg)
    }

    // 更新 Mode 相关 UI 状态
    private fun updateModeState(config: AppConfig? = null, pkg: String? = null) {
        val cfg = config ?: AppConfig(applicationContext)
        val modeManager = ModeManager(applicationContext)
        val resolver = ModeResolver(cfg, modeManager)
        val resolved = resolver.resolveMode(pkg ?: pipeline?.currentAppPackage, pipeline?.llmSuggestedModeId)
        currentModeName.value = if (resolved.mode != null) {
            "${resolved.mode.iconEmoji ?: ""} ${resolved.mode.name}".trim()
        } else ""
        // 刷新可用 Mode 列表
        availableModes.value = modeManager.getAllModes().map { mode ->
            mode.id to "${mode.iconEmoji ?: ""} ${mode.name}".trim()
        }
    }

    // IME 内手动切换 Mode
    private fun onModeSelected(modeId: String?) {
        val config = AppConfig(applicationContext)
        config.activeModeId = modeId
        // 清除 LLM 建议（手动选择优先）
        pipeline?.llmSuggestedModeId = null
        updateModeState(config)
    }

    // 键盘隐藏时：停止 VAD 监听
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        vadService?.stop()
    }

    // 启动 VAD 监听（手动触发，指定是否编辑模式）
    private fun startVadListening(isEdit: Boolean) {
        val hasPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            errorMessage.value = localizedContext.getString(R.string.ime_mic_permission)
            return
        }

        val config = AppConfig(applicationContext)
        isVadEditMode.value = isEdit
        vadService?.let { vad ->
            vad.silenceThresholdMs = (config.silenceThreshold * 1000).toLong()
            vad.start { audioData ->
                pipeline?.submit(audioData, isEdit = isEdit)
            }
        }
    }

    // 停止 VAD 监听
    private fun stopVadListening() {
        vadService?.stop()
    }

    // 语音 VAD 按钮切换
    private fun toggleVadVoice() {
        val listening = isVadListening.value
        val editMode = isVadEditMode.value
        if (listening && !editMode) {
            // 语音模式正在监听 → 停止
            stopVadListening()
        } else {
            if (listening) stopVadListening() // 先停止编辑模式
            startVadListening(isEdit = false)
        }
    }

    // 编辑 VAD 按钮切换
    private fun toggleVadEdit() {
        val listening = isVadListening.value
        val editMode = isVadEditMode.value
        if (listening && editMode) {
            // 编辑模式正在监听 → 停止
            stopVadListening()
        } else {
            if (listening) stopVadListening() // 先停止语音模式
            startVadListening(isEdit = true)
        }
    }

    // 点击切换模式
    private fun toggleRecording() {
        android.util.Log.i("ChatyInputIME", "toggleRecording: isRecording=${isRecording.value}")
        if (isRecording.value) stopRecording() else {
            isEditMode.value = false
            startRecording()
        }
    }

    // 编辑模式：点击切换
    private fun toggleEditRecording() {
        android.util.Log.i("ChatyInputIME", "toggleEditRecording: isRecording=${isRecording.value}, isEditMode=${isEditMode.value}")
        if (isRecording.value && isEditMode.value) {
            stopRecording()
        } else if (!isRecording.value) {
            isEditMode.value = true
            startRecording()
        }
    }

    // 编辑模式：长按开始
    private fun startEditRecording() {
        isEditMode.value = true
        startRecording()
    }

    // 编辑模式：长按结束
    private fun stopEditRecording() {
        if (isRecording.value && isEditMode.value) {
            stopRecording()
        }
    }

    // 开始录音（长按模式 + 切换模式共用，PTT/Toggle 模式）
    private fun startRecording() {
        android.util.Log.i("ChatyInputIME", "startRecording called")
        errorMessage.value = null

        // 检查麦克风权限
        val hasPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        android.util.Log.i("ChatyInputIME", "RECORD_AUDIO permission: $hasPermission")
        if (!hasPermission) {
            errorMessage.value = localizedContext.getString(R.string.ime_mic_permission)
            return
        }

        try {
            audioService.startRecording(cacheDir)
            isRecording.value = true
            android.util.Log.i("ChatyInputIME", "Recording started")
        } catch (e: Exception) {
            android.util.Log.e("ChatyInputIME", "Recording failed", e)
            errorMessage.value = localizedContext.getString(R.string.ime_recording_failed, e.message ?: "")
        }
    }

    // 停止录音并通过 Pipeline 提交（PTT/Toggle 模式）
    private fun stopRecording() {
        if (!isRecording.value) return
        isRecording.value = false
        processRecording()
    }

    private fun processRecording() {
        // 在协程启动前保存当前编辑模式状态
        val editMode = isEditMode.value
        scope.launch {
            errorMessage.value = null
            try {
                val audioData = audioService.stopRecording()
                if (audioData == null || audioData.isEmpty()) {
                    errorMessage.value = localizedContext.getString(R.string.ime_no_audio)
                    return@launch
                }

                val config = AppConfig(applicationContext)
                if (!config.isValid) {
                    errorMessage.value = localizedContext.getString(R.string.ime_configure_api)
                    return@launch
                }

                // 通过 Pipeline 提交（STT 并行 + LLM 排队）
                pipeline?.submit(audioData, isEdit = editMode)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Processing error"
            } finally {
                isEditMode.value = false // 处理完毕重置编辑模式
            }
        }
    }

    private fun insertText() {
        val text = buffer.value
        if (text.isNotEmpty()) {
            pipeline?.let { p ->
                // 记录 SENT 历史并重置 group
                com.tinybear.chatyinput.service.HistoryManager.endGroup(
                    applicationContext, p.currentGroupId,
                    com.tinybear.chatyinput.model.GroupEndType.SENT, text
                )
                p.currentGroupId = java.util.UUID.randomUUID().toString()
                p.buffer = ""
            }
        }
        commitTextToEditor(text)
        buffer.value = ""
    }

    private fun commitTextToEditor(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun clearBuffer() {
        pipeline?.clearBuffer()
        buffer.value = ""
        pipeline?.buffer = ""
        errorMessage.value = null
    }

    // 删除目标输入框的字（缓冲区空时由 KeyboardView 调用）
    private fun deleteTargetText() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
        }
    }

    private fun switchKeyboard() {
        switchToNextInputMethod(false)
    }

    override fun onDestroy() {
        // 清理 Pipeline 和 VadService
        collectorJob?.cancel()
        vadService?.destroy()
        pipeline?.destroy()
        vadService = null
        pipeline = null

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        scope.cancel()
        super.onDestroy()
    }
}
