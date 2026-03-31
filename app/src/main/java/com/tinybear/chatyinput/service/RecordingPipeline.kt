package com.tinybear.chatyinput.service

import android.content.Context
import android.util.Log
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.model.HistoryEntry
import com.tinybear.chatyinput.model.ProcessingResult
import com.tinybear.chatyinput.model.ToolSideEffect
import com.tinybear.chatyinput.model.VoiceIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class QueueStatus(
    val total: Int = 0,
    val currentIndex: Int = 0,
    val isIdle: Boolean = true
)

class RecordingPipeline(
    private val config: AppConfig,
    private val context: Context,
    private val modeResolver: ModeResolver? = null,
    var locationProvider: LocationProvider? = null
) {
    companion object {
        private const val TAG = "RecordingPipeline"
        private const val MAX_UNDO_SIZE = 30
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val llmQueue = Channel<PendingSegment>(Channel.UNLIMITED)
    private val submitCount = AtomicInteger(0)
    private val processedCount = AtomicInteger(0)

    private val _queueStatus = MutableStateFlow(QueueStatus())
    val queueStatus: StateFlow<QueueStatus> = _queueStatus.asStateFlow()

    // Callbacks — set by UI
    var onBufferUpdated: ((result: ProcessingResult, newBuffer: String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSegmentTranscribed: ((transcript: String) -> Unit)? = null
    var onUndo: ((previousBuffer: String) -> Unit)? = null
    var onModeChanged: ((modeName: String) -> Unit)? = null

    // LLM 建议的 Mode（会话级，clearBuffer 时重置）
    var llmSuggestedModeId: String? = null

    // Undo stack — 保存缓冲区历史，最多 30 条
    private val undoStack = ArrayDeque<String>()

    // Current buffer — managed by pipeline
    var buffer: String = ""
    var currentGroupId: String = UUID.randomUUID().toString()

    // 当前前台应用包名（由 IME 设置）
    var currentAppPackage: String? = null

    private data class PendingSegment(
        val transcript: String,
        val isEdit: Boolean,
        val audioData: ByteArray,
        val index: Int,
        val appPackage: String? = null
    )

    init {
        startLLMConsumer()
    }

    /**
     * Submit a recorded audio segment. STT runs immediately in parallel.
     * LLM processing is queued.
     */
    fun submit(audioData: ByteArray, isEdit: Boolean) {
        val index = submitCount.incrementAndGet()
        updateStatus()

        scope.launch(Dispatchers.IO) {
            try {
                // STT runs in parallel (no buffer dependency)
                val sttProvider = config.makeSTTProvider()
                val transcript = sttProvider.transcribe(audioData, "m4a")

                // Skip empty transcripts (no speech detected by Whisper)
                if (transcript.isBlank()) {
                    Log.i(TAG, "Segment $index: empty transcript, skipping LLM")
                    processedCount.incrementAndGet()
                    updateStatus()
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onSegmentTranscribed?.invoke(transcript)
                }

                // Queue for LLM processing (needs buffer context)
                llmQueue.send(PendingSegment(transcript, isEdit, audioData, index, currentAppPackage))
            } catch (e: Exception) {
                Log.e(TAG, "STT failed for segment $index: ${e.message}")
                processedCount.incrementAndGet()
                updateStatus()
                withContext(Dispatchers.Main) {
                    onError?.invoke("STT failed: ${e.message}")
                }
            }
        }
    }

    private fun startLLMConsumer() {
        scope.launch(Dispatchers.IO) {
            for (segment in llmQueue) {
                try {
                    val llmProvider = config.makeLLMProvider()
                    val basePrompt = if (segment.isEdit) config.editSystemPrompt else config.systemPrompt

                    // Mode 解析（含 LLM 建议和 force 判断）
                    val resolved = modeResolver?.resolveMode(segment.appPackage, llmSuggestedModeId)
                        ?: ResolvedMode(null, ModeSource.DEFAULT)
                    val mode = resolved.mode
                    // 位置信息（仅在 locationModeEnabled 时获取）
                    val location = if (config.locationModeEnabled) locationProvider?.getCachedLocation() else null

                    // GPS 语言切换开启时，弱化 prompt 中的语言限制
                    val effectiveBasePrompt = if (config.locationModeEnabled && config.locationLanguageEnabled && location != null) {
                        basePrompt
                            .replace("请严格使用简体中文，不要使用繁体中文", "默认使用简体中文，但可根据位置和上下文切换语言")
                            .replace("請嚴格使用繁體中文，不要使用簡體中文", "預設使用繁體中文，但可根據位置和上下文切換語言")
                            .replace(Regex("The user's primary language is (\\w+)\\."), "The user's default language is $1, but may switch based on location and context.")
                    } else basePrompt

                    var finalPrompt = modeResolver?.buildFinalPrompt(effectiveBasePrompt, mode, segment.isEdit) ?: effectiveBasePrompt
                    val mergedWords = modeResolver?.mergeCustomWords(config.customWords, mode) ?: config.customWords

                    // Smart/Strict Edit 模式
                    if (config.smartEditMode) {
                        finalPrompt += "\n\n" + config.smartEditPrompt
                    } else {
                        finalPrompt += "\n\n" + config.strictEditPrompt
                    }

                    // 基于位置的语言切换指令（两个开关都开时追加到 prompt 末尾）
                    if (config.locationModeEnabled && config.locationLanguageEnabled && location != null) {
                        finalPrompt += "\n\n" + com.tinybear.chatyinput.config.ModeSelectionPrompts.getLanguageSwitchSuffix(
                            config.resolvedLanguage, location.latitude, location.longitude
                        )
                    }

                    // 生成 Mode 上下文（附加到 user message，让 LLM 判断是否切换）
                    val modeContext = modeResolver?.buildModeContext(
                        currentMode = mode,
                        appPackage = segment.appPackage,
                        isForced = resolved.isForced,
                        language = config.resolvedLanguage,
                        location = location,
                        locationProvider = locationProvider
                    ) ?: ""

                    // 创建 Tool 系统
                    val toolRegistry = ToolRegistry.createDefault()
                    val modeManagerForTools = ModeManager(context)
                    val toolExecutor = ToolExecutor(modeManagerForTools)

                    val processor = VoiceIntentProcessor(
                        llmProvider = llmProvider,
                        systemPrompt = finalPrompt,
                        customWords = mergedWords,
                        toolRegistry = toolRegistry,
                        toolExecutor = toolExecutor,
                        maxToolRounds = config.maxToolRounds
                    )

                    val toolResult = processor.process(segment.transcript, buffer, modeContext)
                    val result = toolResult.processingResult

                    // 处理 tool 副作用
                    for (effect in toolResult.sideEffects) {
                        when (effect) {
                            is ToolSideEffect.ModeSwitched -> {
                                if (!resolved.isForced) {
                                    llmSuggestedModeId = effect.modeId
                                    Log.i(TAG, "Tool switch_mode: ${effect.modeName}")
                                    withContext(Dispatchers.Main) {
                                        val name = "${effect.modeEmoji ?: ""} ${effect.modeName}".trim()
                                        onModeChanged?.invoke(name)
                                    }
                                }
                            }
                        }
                    }

                    // 兼容旧 suggested_mode JSON 字段（fallback）
                    if (toolResult.sideEffects.none { it is ToolSideEffect.ModeSwitched } &&
                        !result.suggestedMode.isNullOrBlank() && !resolved.isForced) {
                        val modeManager2 = ModeManager(context)
                        val suggested = modeManager2.getMode(result.suggestedMode)
                        if (suggested != null) {
                            llmSuggestedModeId = result.suggestedMode
                            Log.i(TAG, "LLM suggested_mode fallback: ${suggested.name}")
                            withContext(Dispatchers.Main) {
                                val name = "${suggested.iconEmoji ?: ""} ${suggested.name}".trim()
                                onModeChanged?.invoke(name)
                            }
                        }
                    }

                    // Update buffer based on intent
                    val newBuffer = when (result.intent) {
                        VoiceIntent.CONTENT -> {
                            // 保存当前缓冲区到撤销栈
                            pushUndo(buffer)
                            if (buffer.isEmpty()) result.resultText
                            else buffer + result.resultText
                        }
                        VoiceIntent.EDIT -> {
                            // 保存当前缓冲区到撤销栈
                            pushUndo(buffer)
                            result.resultText
                        }
                        VoiceIntent.SEND -> buffer // keep buffer, UI handles send
                        VoiceIntent.UNDO -> {
                            // 从撤销栈恢复上一个版本
                            val previous = popUndo()
                            if (previous != null) {
                                previous
                            } else {
                                buffer // 栈为空，保持当前缓冲区不变
                            }
                        }
                    }
                    buffer = newBuffer

                    // Save history
                    val audioFileName = "voice_${System.currentTimeMillis()}.m4a"
                    val entry = HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        transcript = segment.transcript,
                        intent = result.intent,
                        resultText = result.resultText,
                        explanation = result.explanation,
                        audioFileName = audioFileName,
                        groupId = currentGroupId
                    )
                    HistoryManager.saveEntry(context, entry, segment.audioData)

                    if (result.intent == VoiceIntent.SEND) {
                        HistoryManager.endGroup(
                            context, currentGroupId,
                            com.tinybear.chatyinput.model.GroupEndType.SENT, buffer
                        )
                        currentGroupId = UUID.randomUUID().toString()
                    }

                    processedCount.incrementAndGet()
                    updateStatus()

                    withContext(Dispatchers.Main) {
                        onBufferUpdated?.invoke(result, newBuffer)
                        if (result.intent == VoiceIntent.UNDO) {
                            onUndo?.invoke(newBuffer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM failed for segment ${segment.index}: ${e.message}")
                    processedCount.incrementAndGet()
                    updateStatus()
                    withContext(Dispatchers.Main) {
                        onError?.invoke("LLM failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun updateStatus() {
        val total = submitCount.get()
        val processed = processedCount.get()
        _queueStatus.value = QueueStatus(
            total = total,
            currentIndex = processed,
            isIdle = total == processed
        )
    }

    fun clearBuffer() {
        if (buffer.isNotEmpty()) {
            HistoryManager.endGroup(
                context, currentGroupId,
                com.tinybear.chatyinput.model.GroupEndType.CLEARED, buffer
            )
            currentGroupId = UUID.randomUUID().toString()
        }
        buffer = ""
        undoStack.clear()
        llmSuggestedModeId = null
    }

    // 将当前缓冲区压入撤销栈
    private fun pushUndo(value: String) {
        undoStack.addLast(value)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeFirst()
        }
    }

    // 从撤销栈弹出上一个版本，栈空时返回 null
    private fun popUndo(): String? {
        return if (undoStack.isNotEmpty()) undoStack.removeLast() else null
    }

    fun destroy() {
        scope.cancel()
        llmQueue.close()
    }
}
