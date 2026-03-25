package com.tinybear.chatyinput.config

import android.content.Context
import java.util.Locale

// 根据 AppConfig 中的语言设置切换 app locale
object LocaleHelper {

    // 应用指定语言到 Context，返回新的本地化 Context
    fun applyLocale(context: Context, language: AppLanguage): Context {
        val locale = when (language) {
            AppLanguage.AUTO -> Locale.getDefault()
            AppLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ZH_TW -> Locale.TRADITIONAL_CHINESE
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.JA -> Locale.JAPANESE
            AppLanguage.KO -> Locale.KOREAN
        }
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    // 从 Context 获取本地化字符串（用于非 Compose 环境，如 IME）
    fun getLocalizedContext(context: Context): Context {
        val appConfig = AppConfig(context)
        return applyLocale(context, appConfig.language)
    }
}
