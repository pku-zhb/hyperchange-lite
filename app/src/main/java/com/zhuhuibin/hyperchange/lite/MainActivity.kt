// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhuhuibin.hyperchange.lite.ui.LiteApp
import com.zhuhuibin.hyperchange.lite.ui.localizedText
import org.json.JSONObject
import java.util.Locale

/** The Activity owns document pickers and serial IO; screens never restart system scopes. */
class MainActivity : ComponentActivity() {
    private lateinit var repository: SettingsRepository
    private val io by lazy { (application as LiteApplication).settingsExecutor }
    private var revision by mutableIntStateOf(0)
    private var appUi by mutableStateOf(AppUiSettings())
    private var wallpaperUri by mutableStateOf<String?>(null)
    private var wallpaperStyle by mutableIntStateOf(0)
    private var pendingExport = ""

    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        val snapshot = pendingExport
        io.execute {
            val result = runCatching {
                require(snapshot.isNotEmpty()) { "请重新导出配置" }
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(snapshot) }
                    ?: error("无法写入文件")
            }
            result.onFailure { android.util.Log.w("HyperChangeLite", "Export failed", it) }
            report(if (result.isSuccess) "配置已导出" else "导出失败，请重试。")
        }
    }
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        io.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(262_145) }
                    ?: error("无法读取文件")
                require(bytes.size <= 262_144) { "配置文件过大" }
                val text = bytes.toString(Charsets.UTF_8)
                // Validate App metadata before importing any module setting. Older
                // backups omit it and keep the current App appearance.
                val appearance = AppUiSettingsCodec.fromBackup(text)
                try {
                    repository.importJson(text).also { appearance?.let(::restoreAppAppearance) }
                } catch (error: SettingsSyncException) {
                    // A sync error occurs after the local settings were accepted.
                    appearance?.let(::restoreAppAppearance)
                    throw error
                }
            }
            result.fold({ report("已导入 %d 项设置", it) }, { error ->
                if (error is SettingsSyncException) reportSettingsFailure("导入", error)
                else {
                    android.util.Log.w("HyperChangeLite", "Configuration import failed", error)
                    report("导入失败，请检查配置文件和本机存储后重试。")
                }
            })
        }
    }
    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        wallpaperUri = uri.toString()
        getSharedPreferences("preview_ui", MODE_PRIVATE).edit().putString("wallpaper_uri", wallpaperUri).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = SettingsRepository(this)
        appUi = AppUiPreferences.load(this)
        pendingExport = savedInstanceState?.getString("pending_export").orEmpty()
        val preview = getSharedPreferences("preview_ui", MODE_PRIVATE)
        wallpaperUri = preview.getString("wallpaper_uri", null)
        wallpaperStyle = preview.getInt("wallpaper_style", 0)
        repository.onChange = { runOnUiThread { if (!isDestroyed) revision++ } }
        setContent {
            LiteApp(
                repository = repository, revision = revision,
                themeMode = appUi.themeMode, language = appUi.language,
                onThemeMode = { mode ->
                    AppUiPreferences.saveTheme(this, mode)
                    appUi = appUi.copy(themeMode = mode)
                },
                onLanguage = { language ->
                    runCatching { AppUiPreferences.saveLanguage(this, language) }.onSuccess {
                        appUi = appUi.copy(language = language)
                    }.onFailure {
                        android.util.Log.w("HyperChangeLite", "App language change failed", it)
                        report("语言切换失败，请重试。")
                    }
                },
                onWrite = { spec, value -> io.execute {
                    runCatching { repository.set(spec, value) }
                        .onFailure { reportSettingsFailure("保存", it) }
                } },
                onExport = {
                    val appearance = appUi
                    io.execute {
                        val result = runCatching {
                            JSONObject(repository.exportJson()).put("appUi", AppUiSettingsCodec.encode(appearance)).toString(2)
                        }
                        runOnUiThread {
                            if (!isDestroyed) result.onSuccess {
                                pendingExport = it
                                exporter.launch("hc-lite-preset.json")
                            }.onFailure {
                                android.util.Log.w("HyperChangeLite", "Export preparation failed", it)
                                report("导出失败，请重试。")
                            }
                        }
                    }
                },
                onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onReset = { io.execute {
                    runCatching { repository.reset() }.fold(
                        { restoreAppAppearance(AppUiSettings()); report("已恢复默认配置") },
                        { error ->
                            if (error is SettingsSyncException) restoreAppAppearance(AppUiSettings())
                            reportSettingsFailure("恢复", error)
                        })
                } },
                onApplyGlassPreset = { preset -> io.execute {
                    runCatching { repository.applyGlassPreset(preset) }.fold(
                        { report("已应用 %s；卡片材质调节已开启", localizedText(this, preset.title)) },
                        { reportSettingsFailure("应用材质预设", it) })
                } },
                onRestoreGlassPreset = { io.execute {
                    runCatching { repository.restoreBeforeGlassPresets() }.fold(
                        { report("已还原试用前的材质参数") },
                        { reportSettingsFailure("还原", it) })
                } },
                wallpaperUri = wallpaperUri, wallpaperStyle = wallpaperStyle,
                onChooseImage = { backgroundPicker.launch(arrayOf("image/*")) },
                onWallpaperStyle = { style ->
                    wallpaperUri = null; wallpaperStyle = style
                    preview.edit().remove("wallpaper_uri").putInt("wallpaper_style", style).apply()
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_export", pendingExport)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::repository.isInitialized) repository.close()
        super.onDestroy()
    }

    private fun restoreAppAppearance(settings: AppUiSettings) {
        // Import/reset belongs to the application, even if folding or a locale
        // change has already destroyed the Activity that launched the operation.
        runCatching { AppUiPreferences.restore(applicationContext, settings) }.onSuccess {
            runOnUiThread { if (!isDestroyed) appUi = settings }
        }.onFailure {
            android.util.Log.w("HyperChangeLite", "App appearance restore failed", it)
            report("界面设置恢复失败，请重试。")
        }
    }

    private fun report(message: String, vararg args: Any) = runOnUiThread {
        if (!isDestroyed) {
            val template = localizedText(this, message)
            val text = if (args.isEmpty()) template else String.format(resources.configuration.locales[0] ?: Locale.getDefault(), template, *args)
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun reportSettingsFailure(action: String, error: Throwable) {
        android.util.Log.w("HyperChangeLite", "Settings action failed", error)
        if (error is SettingsSyncException) report("已保存在本机，暂未同步到模块。重新连接后会重试。")
        else report("%s失败，请重试。", localizedText(this, action))
    }
}
