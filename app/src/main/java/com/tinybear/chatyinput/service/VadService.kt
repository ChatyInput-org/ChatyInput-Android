package com.tinybear.chatyinput.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.FloatBuffer
import java.nio.LongBuffer

// Silero VAD 语音活动检测服务
class VadService(
    private val context: Context,
    var silenceThresholdMs: Long = 1500L
) {
    companion object {
        private const val TAG = "VadService"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 512 // Silero VAD v5 frame size for 16kHz
        private const val SPEECH_THRESHOLD = 0.5f
        // Silero VAD v4 LSTM state dimensions: [2, 1, 64], separate h and c
        private const val STATE_SIZE = 2 * 1 * 64
        private const val AUDIO_GAIN = 10.0f // amplify mic signal
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var audioRecord: AudioRecord? = null
    private var listenJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Silero VAD v4: separate h and c states
    private var h = FloatArray(STATE_SIZE)
    private var c = FloatArray(STATE_SIZE)

    // 语音收集状态（类级别，stop 时可 flush）
    private val pcmCollector = mutableListOf<Short>()
    private var speechFrameCount = 0
    private var totalSegFrames = 0
    private var speechActive = false

    private fun initModel() {
        if (ortSession != null) return
        ortEnv = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("silero_vad.onnx").readBytes()
        ortSession = ortEnv!!.createSession(modelBytes)
        Log.i(TAG, "Silero VAD model loaded, inputs: ${ortSession!!.inputNames}")
    }

    private fun runVad(audioFrame: FloatArray): Float {
        val session = ortSession ?: return 0f
        val env = ortEnv ?: return 0f

        // Apply gain to amplify weak mic signal
        val amplified = FloatArray(audioFrame.size) {
            (audioFrame[it] * AUDIO_GAIN).coerceIn(-1f, 1f)
        }

        // input: float32 [1, chunk_size]
        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(amplified),
            longArrayOf(1, amplified.size.toLong())
        )
        // sr: int64 [1]
        val srTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())),
            longArrayOf(1)
        )
        // h: float32 [2, 1, 64]
        val hTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(h), longArrayOf(2, 1, 64)
        )
        // c: float32 [2, 1, 64]
        val cTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(c), longArrayOf(2, 1, 64)
        )

        val inputs = mapOf("input" to inputTensor, "sr" to srTensor, "h" to hTensor, "c" to cTensor)
        val results = session.run(inputs)

        // output: float32 [1, 1]
        val output = (results[0].value as Array<FloatArray>)[0][0]
        // hn: float32 [2, 1, 64]
        @Suppress("UNCHECKED_CAST")
        val newH = results[1].value as Array<Array<FloatArray>>
        // cn: float32 [2, 1, 64]
        @Suppress("UNCHECKED_CAST")
        val newC = results[2].value as Array<Array<FloatArray>>

        // Update states
        for (i in 0 until 2) for (j in 0 until 64) {
            h[i * 64 + j] = newH[i][0][j]
            c[i * 64 + j] = newC[i][0][j]
        }

        results.close()
        inputTensor.close()
        srTensor.close()
        hTensor.close()
        cTensor.close()

        return output
    }

    private fun resetState() {
        h = FloatArray(STATE_SIZE)
        c = FloatArray(STATE_SIZE)
    }

    fun start(onSegmentReady: (ByteArray) -> Unit) {
        Log.i(TAG, "start() called, isListening=${_isListening.value}")
        if (_isListening.value) {
            Log.w(TAG, "Already listening, ignoring start()")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "No RECORD_AUDIO permission")
            return
        }

        try {
            initModel()
            Log.i(TAG, "Model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init VAD model: ${e.message}", e)
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        Log.i(TAG, "AudioRecord minBufferSize=$minBufSize")
        val bufferSize = maxOf(minBufSize, FRAME_SIZE * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize, state=${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return
            }
            audioRecord?.startRecording()
            Log.i(TAG, "AudioRecord started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}", e)
            return
        }

        _isListening.value = true
        lastOnSegmentReady = onSegmentReady
        // 重置语音收集状态
        pcmCollector.clear()
        speechFrameCount = 0
        totalSegFrames = 0
        speechActive = false

        listenJob = scope.launch {
            Log.i(TAG, "VAD listen loop started")
            val readBuf = ShortArray(FRAME_SIZE)
            val frameBuf = ShortArray(FRAME_SIZE)
            var frameBufPos = 0
            var silenceStart = 0L
            var frameCount = 0

            while (isActive && _isListening.value) {
                val read = audioRecord?.read(readBuf, 0, FRAME_SIZE) ?: -1
                if (read <= 0) continue

                // Accumulate into frameBuf, process when full
                var srcPos = 0
                while (srcPos < read) {
                    val copyLen = minOf(read - srcPos, FRAME_SIZE - frameBufPos)
                    System.arraycopy(readBuf, srcPos, frameBuf, frameBufPos, copyLen)
                    frameBufPos += copyLen
                    srcPos += copyLen

                    if (frameBufPos < FRAME_SIZE) break

                    // Full frame ready — run VAD
                    frameBufPos = 0
                    val floatFrame = FloatArray(FRAME_SIZE) { frameBuf[it] / 32768f }
                    val prob = try {
                        runVad(floatFrame)
                    } catch (e: Exception) {
                        Log.e(TAG, "runVad failed: ${e.message}", e)
                        0f
                    }

                    frameCount++
                    // Calculate RMS volume for diagnostics
                    val rms = Math.sqrt(floatFrame.map { (it * it).toDouble() }.average()).toFloat()
                    if (frameCount % 30 == 0) {
                        val maxSample = floatFrame.maxOrNull() ?: 0f
                        Log.d(TAG, "Frame $frameCount, prob=${"%.4f".format(prob)}, rms=${"%.4f".format(rms)}, max=${"%.4f".format(maxSample)}, speaking=$speechActive")
                    }

                    if (prob >= SPEECH_THRESHOLD) {
                        if (!speechActive) {
                            speechActive = true
                            pcmCollector.clear()
                            speechFrameCount = 0
                            totalSegFrames = 0
                            Log.d(TAG, "Speech started")
                        }
                        silenceStart = 0L
                        _isSpeaking.value = true
                        speechFrameCount++
                        totalSegFrames++
                        for (i in 0 until FRAME_SIZE) pcmCollector.add(frameBuf[i])
                    } else {
                        if (speechActive) {
                            totalSegFrames++
                            // Collect during short pauses
                            for (i in 0 until FRAME_SIZE) pcmCollector.add(frameBuf[i])

                            if (silenceStart == 0L) {
                                silenceStart = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - silenceStart > silenceThresholdMs) {
                                // Silence exceeded threshold — flush segment
                                speechActive = false
                                _isSpeaking.value = false
                                silenceStart = 0L
                                resetState()

                                // Calculate speech ratio and RMS
                                val speechRatio = if (totalSegFrames > 0) speechFrameCount.toFloat() / totalSegFrames else 0f
                                val segmentRms = Math.sqrt(pcmCollector.map { (it.toFloat() / 32768f).toDouble().let { v -> v * v } }.average()).toFloat()
                                val minDuration = pcmCollector.size > SAMPLE_RATE  // at least 1 second

                                if (minDuration && speechRatio > 0.3f && segmentRms > 0.002f) {
                                    Log.i(TAG, "Segment ready: ${pcmCollector.size} samples, rms=${"%.4f".format(segmentRms)}, speechRatio=${"%.2f".format(speechRatio)}, speechFrames=$speechFrameCount/$totalSegFrames")
                                    val pcmBytes = ByteArray(pcmCollector.size * 2)
                                    for (i in pcmCollector.indices) {
                                        pcmBytes[i * 2] = (pcmCollector[i].toInt() and 0xFF).toByte()
                                        pcmBytes[i * 2 + 1] = (pcmCollector[i].toInt() shr 8 and 0xFF).toByte()
                                    }
                                    pcmCollector.clear()

                                    try {
                                        val m4a = AudioCaptureService().convertPcmToM4a(pcmBytes, SAMPLE_RATE)
                                        withContext(Dispatchers.Main) {
                                            onSegmentReady(m4a)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "PCM to m4a conversion failed: ${e.message}")
                                    }
                                } else {
                                    Log.d(TAG, "Segment discarded: duration=${pcmCollector.size/SAMPLE_RATE}s, rms=${"%.4f".format(segmentRms)}, speechRatio=${"%.2f".format(speechRatio)}")
                                    pcmCollector.clear()
                                }
                            }
                        } else {
                            _isSpeaking.value = false
                        }
                    }
                }
            }
        }

        Log.i(TAG, "VAD listening started")
    }

    // 停止时的 segment callback（用于 flush 未处理的语音）
    private var lastOnSegmentReady: ((ByteArray) -> Unit)? = null

    fun stop() {
        _isListening.value = false
        _isSpeaking.value = false
        listenJob?.cancel()
        listenJob = null

        // Flush：如果有未处理的语音数据，提交处理
        if (pcmCollector.isNotEmpty() && speechFrameCount > 0) {
            val speechRatio = if (totalSegFrames > 0) speechFrameCount.toFloat() / totalSegFrames else 0f
            val segmentRms = Math.sqrt(pcmCollector.map { (it.toFloat() / 32768f).toDouble().let { v -> v * v } }.average()).toFloat()
            val minDuration = pcmCollector.size > SAMPLE_RATE

            if (minDuration && speechRatio > 0.3f && segmentRms > 0.002f) {
                Log.i(TAG, "Flushing remaining speech on stop: ${pcmCollector.size} samples")
                val pcmBytes = ByteArray(pcmCollector.size * 2)
                for (i in pcmCollector.indices) {
                    pcmBytes[i * 2] = (pcmCollector[i].toInt() and 0xFF).toByte()
                    pcmBytes[i * 2 + 1] = (pcmCollector[i].toInt() shr 8 and 0xFF).toByte()
                }
                val callback = lastOnSegmentReady
                scope.launch {
                    try {
                        val m4a = AudioCaptureService().convertPcmToM4a(pcmBytes, SAMPLE_RATE)
                        withContext(Dispatchers.Main) {
                            callback?.invoke(m4a)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Flush PCM to m4a failed: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "Flush discarded: too short or low speech ratio")
            }
            pcmCollector.clear()
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        speechFrameCount = 0
        totalSegFrames = 0
        resetState()
        Log.i(TAG, "VAD listening stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
    }
}
