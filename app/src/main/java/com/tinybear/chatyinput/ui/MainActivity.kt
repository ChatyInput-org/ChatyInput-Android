package com.tinybear.chatyinput.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.LocaleHelper

// 主 Activity：Voice / History / Dictionary / Settings 四 tab 导航
class MainActivity : ComponentActivity() {

    // 在 Activity 创建前应用语言设置
    override fun attachBaseContext(newBase: Context) {
        val config = AppConfig(newBase)
        val localizedContext = LocaleHelper.applyLocale(newBase, config.language)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AppConfig(applicationContext)

        setContent {
            ChatyInputTheme {
                MainScreen(
                    config = config,
                    onLanguageChanged = {
                        // 语言切换后 recreate activity 使新 locale 生效
                        recreate()
                    }
                )
            }
        }
    }
}

// 底部导航栏 tab 定义
private enum class Tab {
    VOICE, HISTORY, DICTIONARY, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(config: AppConfig, onLanguageChanged: () -> Unit) {
    var selectedTab by remember { mutableStateOf(Tab.VOICE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.VOICE,
                    onClick = { selectedTab = Tab.VOICE },
                    icon = { Text("\uD83C\uDFA4") },
                    label = { Text(stringResource(R.string.tab_voice)) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.HISTORY,
                    onClick = { selectedTab = Tab.HISTORY },
                    icon = { Text("\uD83D\uDCCB") },
                    label = { Text(stringResource(R.string.tab_history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.DICTIONARY,
                    onClick = { selectedTab = Tab.DICTIONARY },
                    icon = { Text("\uD83D\uDCD6") },
                    label = { Text(stringResource(R.string.tab_dictionary)) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.SETTINGS,
                    onClick = { selectedTab = Tab.SETTINGS },
                    icon = {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings))
                    },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                Tab.VOICE -> VoiceScreen(config = config)
                Tab.HISTORY -> HistoryScreen(config = config)
                Tab.DICTIONARY -> DictionaryScreen(config = config)
                Tab.SETTINGS -> SettingsScreen(config = config, onLanguageChanged = onLanguageChanged)
            }
        }
    }
}
