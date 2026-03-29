package com.tinybear.chatyinput.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tinybear.chatyinput.R
import com.tinybear.chatyinput.config.AppConfig
import com.tinybear.chatyinput.config.LocaleHelper
import com.tinybear.chatyinput.model.Mode

// 主 Activity：Voice / History / Dictionary / Modes / Settings 五 tab 导航
class MainActivity : ComponentActivity() {

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
                    onLanguageChanged = { recreate() },
                    onExit = { finish() }
                )
            }
        }
    }
}

private enum class Tab {
    VOICE, HISTORY, DICTIONARY, MODES, SETTINGS
}

private sealed class ModeNav {
    data object List : ModeNav()
    data class Editor(val mode: Mode) : ModeNav()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(config: AppConfig, onLanguageChanged: () -> Unit, onExit: () -> Unit) {
    var selectedTab by remember { mutableStateOf(Tab.VOICE) }
    var modeNav by remember { mutableStateOf<ModeNav>(ModeNav.List) }
    val context = LocalContext.current

    // 返回键：二级页面返回上一级，主界面双击退出
    var lastBackTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        // 二级页面（Mode Editor）→ 返回列表
        if (selectedTab == Tab.MODES && modeNav is ModeNav.Editor) {
            modeNav = ModeNav.List
            return@BackHandler
        }
        // 主界面 → 双击退出
        val now = System.currentTimeMillis()
        if (now - lastBackTime < 2000) {
            onExit()
        } else {
            lastBackTime = now
            Toast.makeText(context, context.getString(R.string.back_to_exit), Toast.LENGTH_SHORT).show()
        }
    }

    // Mode Editor（覆盖整个屏幕）
    if (selectedTab == Tab.MODES && modeNav is ModeNav.Editor) {
        val editorMode = (modeNav as ModeNav.Editor).mode
        ModeEditorScreen(
            mode = editorMode,
            config = config,
            context = context,
            onSave = { modeNav = ModeNav.List },
            onBack = { modeNav = ModeNav.List }
        )
        return
    }

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
                    selected = selectedTab == Tab.MODES,
                    onClick = {
                        selectedTab = Tab.MODES
                        modeNav = ModeNav.List
                    },
                    icon = { Text("\uD83C\uDFAF") },
                    label = { Text(stringResource(R.string.tab_modes)) }
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
                Tab.MODES -> ModeListScreen(
                    config = config,
                    context = context,
                    onEditMode = { mode -> modeNav = ModeNav.Editor(mode) },
                    onCreateMode = {
                        modeNav = ModeNav.Editor(
                            Mode(id = "", name = "", promptSuffix = "", editPromptSuffix = "")
                        )
                    }
                )
                Tab.SETTINGS -> SettingsScreen(config = config, onLanguageChanged = onLanguageChanged)
            }
        }
    }
}
