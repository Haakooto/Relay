package com.relay.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Per-app language through the system API (API 33+), so the choice is also visible and editable
 * in system settings. Setting it recreates the activity. [SYSTEM] clears the override.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    NORWEGIAN("nb");

    companion object {
        fun current(context: Context): AppLanguage {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) return SYSTEM
            val language = locales[0].language
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }

        fun apply(context: Context, language: AppLanguage) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
    }
}
