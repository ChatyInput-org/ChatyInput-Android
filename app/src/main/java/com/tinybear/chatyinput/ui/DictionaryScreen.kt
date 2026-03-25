package com.tinybear.chatyinput.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig

// 常用词管理界面
// 用户添加常用词，AI 遇到发音相似的词会优先使用这些
@Composable
fun DictionaryScreen(config: AppConfig) {
    var words by remember { mutableStateOf(config.customWords) }
    var newWord by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.dict_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.dict_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 添加新词
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                label = { Text(stringResource(R.string.dict_new_word)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilledIconButton(
                onClick = {
                    val trimmed = newWord.trim()
                    if (trimmed.isNotEmpty() && trimmed !in words) {
                        words = words + trimmed
                        config.customWords = words
                        newWord = ""
                    }
                },
                enabled = newWord.trim().isNotEmpty()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dict_new_word))
            }
        }

        // 词汇列表
        if (words.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.dict_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                stringResource(R.string.dict_word_count, words.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(words) { index, word ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(word, style = MaterialTheme.typography.bodyLarge)
                            IconButton(
                                onClick = {
                                    words = words.toMutableList().also { it.removeAt(index) }
                                    config.customWords = words
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.btn_delete),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
