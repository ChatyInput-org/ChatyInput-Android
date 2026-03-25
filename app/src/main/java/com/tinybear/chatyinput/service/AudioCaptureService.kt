package com.tinybear.chatyinput.service

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
}
