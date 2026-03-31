package com.tinybear.chatyinput.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig

// 首页：测试输入区域，用户可切换到 ChatyInput 键盘测试 prompt 和 mode
@Composable
fun VoiceScreen(config: AppConfig) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.home_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 按钮行（顶部，不被键盘挡住）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Load to Buffer — 把文本内容写入 IME buffer
            OutlinedButton(
                onClick = {
                    AppConfig.pendingBuffer = text
                    text = ""
                    Toast.makeText(context, context.getString(R.string.home_loaded_to_buffer), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                enabled = text.isNotEmpty()
            ) {
                Text(stringResource(R.string.home_load_to_buffer))
            }
            // Copy
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ChatyInput", text))
                },
                modifier = Modifier.weight(1f),
                enabled = text.isNotEmpty()
            ) {
                Text(stringResource(R.string.btn_copy))
            }
            // Clear
            OutlinedButton(
                onClick = { text = "" },
                modifier = Modifier.weight(1f),
                enabled = text.isNotEmpty()
            ) {
                Text(stringResource(R.string.btn_clear))
            }
        }

        // 测试输入区
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.home_text_area_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxLines = 30
        )
    }
}
