// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import org.json.JSONObject

/** App appearance is local to the settings App, never published into hooked processes. */
data class AppUiSettings(val themeMode: Int = 0, val language: String = "system")

object AppUiPreferences {
    private const val GROUP = "app_ui"
    private val languages = setOf("system", "zh-CN", "en")

    /** A recreated Activity observes imports/reset jobs started by its predecessor. */
    fun observe(context: Context, changed: () -> Unit): () -> Unit {
        val preferences = context.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "theme_mode" || key == "language") changed()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun load(context: Context): AppUiSettings {
        val prefs = context.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
        val theme = runCatching { prefs.getInt("theme_mode", 0) }.getOrDefault(0).takeIf { it in 0..2 } ?: 0
        val saved = runCatching { prefs.getString("language", "system") }.getOrNull().takeIf { it in languages } ?: "system"
        val selected = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
        val language = when {
            selected == null -> saved
            selected.isEmpty -> "system"
            selected[0].language == "zh" -> "zh-CN"
            selected[0].language == "en" -> "en"
            else -> "system"
        }
        return AppUiSettings(theme, language)
    }

    fun saveTheme(context: Context, mode: Int) {
        require(mode in 0..2)
        context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).edit().putInt("theme_mode", mode).apply()
    }

    fun saveLanguage(context: Context, language: String) {
        require(language in languages)
        val locales = LocaleList.forLanguageTags(if (language == "system") "" else language)
        val manager = requireNotNull(context.getSystemService(LocaleManager::class.java))
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
        context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).edit().putString("language", language).apply()
    }

    fun restore(context: Context, settings: AppUiSettings) {
        saveTheme(context, settings.themeMode)
        saveLanguage(context, settings.language)
    }
}

/** Optional root metadata preserves the existing module preset format and old imports. */
internal object AppUiSettingsCodec {
    fun encode(settings: AppUiSettings): JSONObject = JSONObject()
        .put("version", 1).put("themeMode", settings.themeMode).put("language", settings.language)

    fun fromBackup(text: String): AppUiSettings? {
        val root = JSONObject(text)
        if (!root.has("appUi")) return null
        val value = root.get("appUi")
        require(value is JSONObject) { "Invalid App appearance settings" }
        require(value.optInt("version", -1) == 1) { "Unsupported App appearance version" }
        val rawTheme = value.get("themeMode")
        require(rawTheme is Number && rawTheme.toDouble().isFinite() &&
            rawTheme.toDouble() in 0.0..2.0 && rawTheme.toDouble() == rawTheme.toInt().toDouble()) { "Invalid theme" }
        val language = value.get("language")
        require(language is String && language in setOf("system", "zh-CN", "en")) { "Invalid language" }
        return AppUiSettings(rawTheme.toInt(), language)
    }
}
