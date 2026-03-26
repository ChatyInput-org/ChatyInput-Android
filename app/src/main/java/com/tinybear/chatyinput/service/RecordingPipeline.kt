package com.tinybear.chatyinput.service

import android.content.Context
import android.util.Log
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.model.HistoryEntry
import com.tinybear.chatyinput.model.ProcessingResult
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
    private val context: Context
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

    // Undo stack — 保存缓冲区历史，最多 30 条
    private val undoStack = ArrayDeque<String>()

    // Current buffer — managed by pipeline
    var buffer: String = ""
    var currentGroupId: String = UUID.randomUUID().toString()

    private data class PendingSegment(
        val transcript: String,
        val isEdit: Boolean,
        val audioData: ByteArray,
        val index: Int
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

                withContext(Dispatchers.Main) {
                    onSegmentTranscribed?.invoke(transcript)
                }

                // Queue for LLM processing (needs buffer context)
                llmQueue.send(PendingSegment(transcript, isEdit, audioData, index))
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
                    val prompt = if (segment.isEdit) config.editSystemPrompt else config.systemPrompt
                    val processor = VoiceIntentProcessor(
                        llmProvider = llmProvider,
                        systemPrompt = prompt,
                        customWords = config.customWords
                    )

                    val result = processor.process(segment.transcript, buffer)

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
