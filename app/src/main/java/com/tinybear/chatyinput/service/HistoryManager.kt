package com.tinybear.chatyinput.service

import android.content.Context
import android.util.Log
import com.tinybear.chatyinput.model.GroupEndType
import com.tinybear.chatyinput.model.HistoryEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// 历史记录分组
data class HistoryGroup(
    val groupId: String,
    val entries: List<HistoryEntry>,
    val startTime: Long,
    val endTime: Long,
    val endType: GroupEndType,
    val finalText: String
)

// 历史记录管理器：JSON 文件存储 + 音频文件管理
object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val HISTORY_FILE = "history.json"
    private const val AUDIO_DIR = "history_audio"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    // 获取音频存储目录
    private fun audioDir(context: Context): File {
        val dir = File(context.filesDir, AUDIO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 获取历史 JSON 文件
    private fun historyFile(context: Context): File {
        return File(context.filesDir, HISTORY_FILE)
    }

    // 保存一条历史记录（含可选音频数据）
    fun saveEntry(context: Context, entry: HistoryEntry, audioData: ByteArray? = null) {
        try {
            // 保存音频文件
            if (audioData != null && entry.audioFileName != null) {
                val audioFile = File(audioDir(context), entry.audioFileName)
                audioFile.writeBytes(audioData)
                Log.i(TAG, "Audio saved: ${entry.audioFileName} (${audioData.size} bytes)")
            }

            // 读取现有记录，追加新记录
            val entries = loadEntriesInternal(context).toMutableList()
            entries.add(0, entry) // 最新的在前面

            // 写入 JSON
            val jsonStr = json.encodeToString(entries)
            historyFile(context).writeText(jsonStr)
            Log.i(TAG, "History saved: ${entries.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history entry", e)
        }
    }

    // 加载所有记录（自动执行清理）
    fun loadEntries(context: Context, retentionDays: Int): List<HistoryEntry> {
        cleanup(context, retentionDays)
        return loadEntriesInternal(context)
    }

    // 内部加载（不触发清理）
    private fun loadEntriesInternal(context: Context): List<HistoryEntry> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) emptyList()
            else json.decodeFromString<List<HistoryEntry>>(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history", e)
            emptyList()
        }
    }

    // 清理过期记录及对应音频
    fun cleanup(context: Context, retentionDays: Int) {
        try {
            val entries = loadEntriesInternal(context)
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
            val (keep, remove) = entries.partition { it.timestamp >= cutoff }

            // 删除过期音频
            for (entry in remove) {
                entry.audioFileName?.let { fileName ->
                    val audioFile = File(audioDir(context), fileName)
                    if (audioFile.exists()) {
                        audioFile.delete()
                        Log.i(TAG, "Deleted expired audio: $fileName")
                    }
                }
            }

            if (remove.isNotEmpty()) {
                val jsonStr = json.encodeToString(keep)
                historyFile(context).writeText(jsonStr)
                Log.i(TAG, "Cleanup: removed ${remove.size} expired entries, kept ${keep.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    // 删除单条记录及音频
    fun deleteEntry(context: Context, entryId: String) {
        try {
            val entries = loadEntriesInternal(context)
            val target = entries.find { it.id == entryId }

            // 删除音频文件
            target?.audioFileName?.let { fileName ->
                val audioFile = File(audioDir(context), fileName)
                if (audioFile.exists()) audioFile.delete()
            }

            // 更新 JSON
            val filtered = entries.filter { it.id != entryId }
            val jsonStr = json.encodeToString(filtered)
            historyFile(context).writeText(jsonStr)
            Log.i(TAG, "Deleted entry: $entryId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete entry", e)
        }
    }

    // 标记一个 group 结束（sent 或 cleared），记录最终文本
    fun endGroup(context: Context, groupId: String, endType: GroupEndType, finalText: String) {
        try {
            val entries = loadEntriesInternal(context).toMutableList()
            val updated = entries.map { entry ->
                if (entry.groupId == groupId && entry.groupEndType == GroupEndType.ACTIVE) {
                    entry.copy(groupEndType = endType, finalText = finalText)
                } else entry
            }
            historyFile(context).writeText(json.encodeToString(updated))
            Log.i(TAG, "Group $groupId ended: $endType, finalText=${finalText.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end group", e)
        }
    }

    // 按 groupId 分组加载（返回 groupId → entries 的 map，按时间倒序）
    fun loadGrouped(context: Context, retentionDays: Int): List<HistoryGroup> {
        val entries = loadEntries(context, retentionDays)
        return entries
            .groupBy { it.groupId.ifEmpty { it.id } }  // 没有 groupId 的单独成组
            .map { (groupId, items) ->
                val sorted = items.sortedBy { it.timestamp }
                val lastEntry = sorted.last()
                HistoryGroup(
                    groupId = groupId,
                    entries = sorted,
                    startTime = sorted.first().timestamp,
                    endTime = lastEntry.timestamp,
                    endType = lastEntry.groupEndType,
                    finalText = lastEntry.finalText ?: sorted
                        .filter { it.intent != com.tinybear.chatyinput.model.VoiceIntent.SEND }
                        .joinToString("\n") { it.resultText }
                )
            }
            .sortedByDescending { it.startTime }
    }

    // 获取音频文件（用于播放）
    fun getAudioFile(context: Context, fileName: String): File? {
        val file = File(audioDir(context), fileName)
        return if (file.exists()) file else null
    }

    // 清空所有历史记录和音频
    fun clearAll(context: Context) {
        try {
            // 删除所有音频文件
            val dir = audioDir(context)
            dir.listFiles()?.forEach { it.delete() }

            // 清空 JSON
            historyFile(context).writeText("[]")
            Log.i(TAG, "All history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear history", e)
        }
    }
}
