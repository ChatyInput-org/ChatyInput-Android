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
import com.tinybear.chatyinput.model.HistoryEntry
import com.tinybear.chatyinput.model.VoiceIntent
import com.tinybear.chatyinput.service.AudioCaptureService
import com.tinybear.chatyinput.service.HistoryManager
import com.tinybear.chatyinput.service.VoiceIntentProcessor
import kotlinx.coroutines.*
import java.util.UUID

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

    // 本地化 Context（用于获取字符串资源）
    private lateinit var localizedContext: Context

    // 当前 group ID
    private var currentGroupId = UUID.randomUUID().toString()

    // 键盘 UI 状态
    private var buffer = mutableStateOf("")
    private var isRecording = mutableStateOf(false)
    private var isProcessing = mutableStateOf(false)
    private var isEditMode = mutableStateOf(false) // 编辑模式标记
    private var errorMessage = mutableStateOf<String?>(null)
    private var lastAction = mutableStateOf("")

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
    }

    override fun onCreateInputView(): View {
        // 每次创建键盘视图时刷新本地化（用户可能在主 app 切换了语言）
        localizedContext = LocaleHelper.getLocalizedContext(this)

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
            val processingValue by isProcessing
            val editModeValue by isEditMode
            val errorValue by errorMessage
            val actionValue by lastAction
            val holdMode = AppConfig(applicationContext).holdToRecord

            KeyboardView(
                buffer = bufferValue,
                lastAction = actionValue,
                isRecording = recordingValue,
                isProcessing = processingValue,
                isEditMode = editModeValue,
                errorMessage = errorValue,
                holdToRecord = holdMode,
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() },
                onToggleRecording = { toggleRecording() },
                onStartEditRecording = { startEditRecording() },
                onStopEditRecording = { stopEditRecording() },
                onToggleEditRecording = { toggleEditRecording() },
                onInsert = { insertText() },
                onClear = { clearBuffer() },
                onSwitchKeyboard = { switchKeyboard() },
                onDeleteTarget = { deleteTargetText() },
                onEnter = { commitTextToEditor("\n") },
                onBufferChange = { buffer.value = it },
                bottomPaddingDp = navBarDp
            )
            } // end ChatyInputTheme
        }
        return composeView
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

    // 开始录音（长按模式 + 切换模式共用）
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

    // 停止录音并处理（长按模式 + 切换模式共用）
    private fun stopRecording() {
        if (!isRecording.value) return
        isRecording.value = false
        processRecording()
    }

    private fun processRecording() {
        // 在协程启动前保存当前编辑模式状态
        val editMode = isEditMode.value
        scope.launch {
            isProcessing.value = true
            errorMessage.value = null
            try {
                val audioData = audioService.stopRecording()
                if (audioData == null || audioData.isEmpty()) {
                    errorMessage.value = localizedContext.getString(R.string.ime_no_audio)
                    isProcessing.value = false
                    return@launch
                }

                val config = AppConfig(applicationContext)
                if (!config.isValid) {
                    errorMessage.value = localizedContext.getString(R.string.ime_configure_api)
                    isProcessing.value = false
                    return@launch
                }

                val sttProvider = config.makeSTTProvider()
                val transcript = sttProvider.transcribe(audioData, "m4a")

                // 编辑模式使用 editSystemPrompt
                val llmProvider = config.makeLLMProvider()
                val prompt = if (editMode) config.editSystemPrompt else config.systemPrompt
                val processor = VoiceIntentProcessor(llmProvider, prompt, config.customWords)
                val result = processor.process(transcript, buffer.value)

                when (result.intent) {
                    VoiceIntent.CONTENT -> {
                        buffer.value = if (buffer.value.isEmpty()) result.resultText
                        else buffer.value + "\n" + result.resultText
                        lastAction.value = result.explanation ?: "Added text"
                    }
                    VoiceIntent.EDIT -> {
                        buffer.value = result.resultText
                        lastAction.value = result.explanation ?: "Edited text"
                    }
                    VoiceIntent.SEND -> {
                        commitTextToEditor(buffer.value)
                        buffer.value = ""
                        lastAction.value = "Text sent"
                    }
                }

                // 保存历史记录
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
                HistoryManager.saveEntry(applicationContext, entry, audioData)

                // send 意图：结束当前 group
                if (result.intent == VoiceIntent.SEND) {
                    HistoryManager.endGroup(applicationContext, currentGroupId, com.tinybear.chatyinput.model.GroupEndType.SENT, buffer.value)
                    currentGroupId = UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Processing error"
            } finally {
                isProcessing.value = false
                isEditMode.value = false // 处理完毕重置编辑模式
            }
        }
    }

    private fun insertText() {
        val text = buffer.value
        if (text.isNotEmpty()) {
            HistoryManager.endGroup(applicationContext, currentGroupId, com.tinybear.chatyinput.model.GroupEndType.SENT, text)
            currentGroupId = UUID.randomUUID().toString()
        }
        commitTextToEditor(text)
        buffer.value = ""
    }

    private fun commitTextToEditor(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun clearBuffer() {
        if (buffer.value.isNotEmpty()) {
            HistoryManager.endGroup(applicationContext, currentGroupId, com.tinybear.chatyinput.model.GroupEndType.CLEARED, buffer.value)
            currentGroupId = UUID.randomUUID().toString()
        }
        buffer.value = ""
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        scope.cancel()
        super.onDestroy()
    }
}
