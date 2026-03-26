package com.tinybear.chatyinput.service

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 音频录制服务（MediaRecorder → m4a）
class AudioCaptureService {
    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null

    companion object {
        private const val TAG = "AudioCapture"
    }

    fun startRecording(cacheDir: File) {
        val file = File(cacheDir, "chatyinput_${System.currentTimeMillis()}.m4a")
        tempFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(/* context not needed for basic use */)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioSamplingRate(16000)
        mr.setAudioEncodingBitRate(32000)
        mr.setAudioChannels(1)
        mr.setOutputFile(file.absolutePath)

        mr.prepare()
        mr.start()
        recorder = mr
        Log.i(TAG, "Recording started: ${file.name}")
    }

    suspend fun stopRecording(): ByteArray? = withContext(Dispatchers.IO) {
        val mr = recorder ?: return@withContext null
        try {
            mr.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop failed: ${e.message}")
        }
        mr.release()
        recorder = null

        val file = tempFile ?: return@withContext null
        tempFile = null

        if (!file.exists()) return@withContext null
        val data = file.readBytes()
        file.delete()
        Log.i(TAG, "Recording stopped: ${data.size} bytes")
        data
    }

    fun isRecording(): Boolean = recorder != null

    /**
     * Convert raw PCM (16kHz, 16-bit, mono) to m4a ByteArray using MediaCodec.
     */
    suspend fun convertPcmToM4a(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("vad_", ".m4a")
        try {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 32000)
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            var trackIndex = -1
            var muxerStarted = false
            var inputOffset = 0
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                // Feed input
                if (inputOffset < pcmData.size) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val remaining = pcmData.size - inputOffset
                        val size = minOf(remaining, inputBuffer.capacity())
                        inputBuffer.put(pcmData, inputOffset, size)
                        inputOffset += size
                        val flags = if (inputOffset >= pcmData.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(inputIndex, 0, size, 0, flags)
                    }
                }

                // Drain output
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        if (muxerStarted && bufferInfo.size > 0) {
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
                muxer.release()
            }

            tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }
}
