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

/** The Activity owns document pickers and serial IO; screens never restart system scopes. */
class MainActivity : ComponentActivity() {
    private lateinit var repository: SettingsRepository
    private val io by lazy { (application as LiteApplication).settingsExecutor }
    private var revision by mutableIntStateOf(0)
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
            report(result.fold({ "配置已导出" }, { "导出失败：${it.message}" }))
        }
    }
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        io.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(262_145) }
                    ?: error("无法读取文件")
                require(bytes.size <= 262_144) { "配置文件过大" }
                repository.importJson(bytes.toString(Charsets.UTF_8))
            }
            result.fold({ report("已导入 $it 项设置") }, { error ->
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
        pendingExport = savedInstanceState?.getString("pending_export").orEmpty()
        val preview = getSharedPreferences("preview_ui", MODE_PRIVATE)
        wallpaperUri = preview.getString("wallpaper_uri", null)
        wallpaperStyle = preview.getInt("wallpaper_style", 0)
        repository.onChange = { runOnUiThread { if (!isDestroyed) revision++ } }
        setContent {
            LiteApp(
                repository = repository, revision = revision,
                onWrite = { spec, value -> io.execute {
                    runCatching { repository.set(spec, value) }
                        .onFailure { reportSettingsFailure("保存", it) }
                } },
                onExport = {
                    io.execute {
                        val result = runCatching { repository.exportJson() }
                        runOnUiThread {
                            if (!isDestroyed) result.onSuccess {
                                pendingExport = it
                                exporter.launch("hyperchange-lite-preset.json")
                            }.onFailure { report("导出失败：${it.message}") }
                        }
                    }
                },
                onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onReset = { io.execute {
                    runCatching { repository.reset() }.fold(
                        { report("已恢复默认配置") }, { reportSettingsFailure("恢复", it) })
                } },
                onApplyGlassPreset = { preset -> io.execute {
                    runCatching { repository.applyGlassPreset(preset) }.fold(
                        { report("已应用${preset.title}；系统材质调节已开启，作用范围保留") },
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
        repository.close()
        super.onDestroy()
    }

    private fun report(message: String) = runOnUiThread {
        if (!isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun reportSettingsFailure(action: String, error: Throwable) {
        android.util.Log.w("HyperChangeLite", "$action failed", error)
        report(if (error is SettingsSyncException)
            "已保存在本机，暂未同步到模块。重新连接后会重试。"
        else "$action 失败，请重试。")
    }
}
