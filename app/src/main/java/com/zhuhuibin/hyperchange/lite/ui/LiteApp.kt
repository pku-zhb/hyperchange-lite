// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuhuibin.hyperchange.lite.*
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LiteApp(
    repository: SettingsRepository, revision: Int,
    onWrite: (LiteSetting, Any) -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit,
    onApplyGlassPreset: (GlassPreset) -> Unit, onRestoreGlassPreset: () -> Unit,
    wallpaperUri: String?, wallpaperStyle: Int,
    onChooseImage: () -> Unit, onWallpaperStyle: (Int) -> Unit,
) {
    val theme = remember { ThemeController(ColorSchemeMode.System) }
    val pageStates = rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf("home") }
    var configReturn by rememberSaveable { mutableStateOf("home") }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    val values = remember(revision) { repository.preferences.all }
    val feature = Feature.entries.firstOrNull { it.name == page }
    LaunchedEffect(page) {
        when (page) {
            "GLASS", "CONTROL", "ASSISTANT", "glass-presets" -> page = Feature.MATERIAL.name
            "preview" -> page = "home"
        }
    }
    val back = { page = if (page == "config" || page == "presets") configReturn else "home" }
    BackHandler(page != "home" && editKey == null && !resetDialog) { back() }
    MiuixTheme(controller = theme) {
        Scaffold(topBar = {
            SmallTopAppBar(
                title = feature?.title ?: if (page == "config" || page == "presets") "配置" else "HyperChange Lite",
                navigationIcon = {
                    if (page != "home") IconButton(onClick = back) {
                        Icon(MiuixIcons.Basic.ArrowRight, "返回", Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                    }
                },
                actions = {
                    if (page != "config" && page != "presets") TextButton("配置", {
                        configReturn = page; page = "config"
                    }, minWidth = 0.dp, minHeight = 44.dp)
                },
            )
        }) { padding ->
            val body = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            when {
                feature != null -> pageStates.SaveableStateProvider(feature.name) {
                    FeaturePage(feature, repository, revision, values, onWrite, { editKey = it.key },
                        onApplyGlassPreset, onRestoreGlassPreset, wallpaperUri, wallpaperStyle, body)
                }
                page == "config" || page == "presets" -> Column(body.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference("导出配置", onClick = onExport)
                        ArrowPreference("导入配置", onClick = onImport)
                        ArrowPreference("恢复默认", onClick = { resetDialog = true })
                    }
                }
                else -> Column(body.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = when {
                            repository.syncFailed -> "同步失败"
                            repository.connected -> "已连接"
                            else -> "未连接"
                        })
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Feature.entries.forEach { target -> ArrowPreference(target.title, onClick = { page = target.name }) }
                    }
                }
            }
        }
        editKey?.let { name ->
            LiteCatalog.entries.firstOrNull { it.key == name }?.let { spec ->
                NumberDialog(spec, number(values, spec), { editKey = null }) { onWrite(spec, it); editKey = null }
            }
        }
        WindowDialog(show = resetDialog, title = "恢复全部默认设置？", onDismissRequest = { resetDialog = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", { resetDialog = false }, Modifier.weight(1f))
                TextButton("恢复默认", { resetDialog = false; onReset() }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeaturePage(
    feature: Feature, repository: SettingsRepository, revision: Int, values: Map<String, *>,
    onWrite: (LiteSetting, Any) -> Unit, onEdit: (LiteSetting) -> Unit,
    onApply: (GlassPreset) -> Unit, onUndo: () -> Unit,
    wallpaperUri: String?, wallpaperStyle: Int, modifier: Modifier,
) {
    val projects = if (feature == Feature.MATERIAL) MaterialCategory.entries.map { it.name to it.title }
        else feature.settings.map { it.group }.distinct().map { it to it }
    var project by rememberSaveable(feature.name) { mutableStateOf(projects.first().first) }
    val index = projects.indexOfFirst { it.first == project }.coerceAtLeast(0)
    val category = if (feature == Feature.MATERIAL) MaterialCategory.entries[index] else null
    val specs = category?.settings ?: feature.settings.filter { it.group == projects[index].first }
    val fallback = specs.firstOrNull() ?: LiteCatalog.entries.first { it.key == "glass_refraction_blur" }
    var focusedKey by rememberSaveable(feature.name) { mutableStateOf(fallback.key) }
    val focused = LiteCatalog.entries.firstOrNull { it.key == focusedKey } ?: fallback
    val target = previewTargetForSetting(focused)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(list, project) {
        snapshotFlow {
            list.isScrollInProgress to list.layoutInfo.visibleItemsInfo
                .firstOrNull { (it.key as? String)?.startsWith("setting:") == true }?.key
        }.distinctUntilChanged().collect { (scrolling, key) ->
            if (scrolling && key is String) focusedKey = key.removePrefix("setting:")
        }
    }
    fun write(spec: LiteSetting, value: Any) { focusedKey = spec.key; onWrite(spec, value) }
    fun edit(spec: LiteSetting) { focusedKey = spec.key; onEdit(spec) }
    val night = isSystemInDarkTheme()
    BoxWithConstraints(modifier) {
        val previewHeight = (maxHeight.value * .28f).coerceIn(64f, 180f).toInt()
        Column(Modifier.fillMaxSize()) {
            PreviewPanel(repository, revision, target, night, wallpaperUri, wallpaperStyle,
                previewHeight,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("project") {
                    Card(Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(title = "项目", items = projects.map { it.second }, selectedIndex = index,
                            onSelectedIndexChange = { selected ->
                                project = projects[selected].first
                                scope.launch { list.scrollToItem(0) }
                                val first = if (feature == Feature.MATERIAL) MaterialCategory.entries[selected].settings.firstOrNull()
                                    else feature.settings.firstOrNull { it.group == project }
                                focusedKey = first?.key ?: "glass_refraction_blur"
                            })
                    }
                }
                if (category == MaterialCategory.PRESETS) item("presets") {
                    GlassPresetControls(values, repository.hasGlassPresetUndo, onApply, onUndo)
                } else {
                    specs.groupBy(::settingSectionTitle).forEach { (group, section) ->
                        item("group:$group") { SmallTitle(group) }
                        section.forEach { spec ->
                            item("setting:${spec.key}") {
                                Card(Modifier.fillMaxWidth()) {
                                    val reason = settingDisabledReason(spec, values)
                                    if (spec.isToggle) SwitchPreference(
                                        title = settingDisplayTitle(spec),
                                        checked = values[spec.key] as? Boolean ?: spec.defaultValue as Boolean,
                                        enabled = reason == null,
                                        onCheckedChange = { if (reason == null) write(spec, it) },
                                    ) else NumberSetting(spec, number(values, spec), reason, ::write) { edit(spec) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberSetting(spec: LiteSetting, value: Float, reason: String?, onWrite: (LiteSetting, Any) -> Unit, onEdit: () -> Unit) {
    var draft by remember(spec.key) { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var lastWrite by remember { mutableLongStateOf(0L) }
    val enabled = reason == null
    val range = spec.sliderRange
    val outside = draft < range.start - spec.step * .01f || draft > range.endInclusive + spec.step * .01f
    fun display(value: Float) = format(value) + if (spec.valueUnit.isBlank()) "" else " ${spec.valueUnit}"
    fun change(value: Float) {
        dragging = false; draft = spec.validate(value.coerceIn(spec.min, spec.max)) as Float
        onWrite(spec, draft)
    }
    LaunchedEffect(value, enabled) {
        if (!enabled) dragging = false
        if (!dragging) draft = value
    }
    BasicComponent(title = settingDisplayTitle(spec), endActions = {
        TextButton(display(draft), onEdit, enabled = enabled, minWidth = 0.dp, minHeight = 44.dp,
            modifier = Modifier.semantics { contentDescription = "${spec.title}，${display(draft)}，输入数值" })
    })
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton("−", { change(draft - spec.step) }, enabled = enabled && draft > spec.min,
            minWidth = 44.dp, minHeight = 44.dp,
            modifier = Modifier.semantics { contentDescription = "减小${spec.title}" })
        Slider(draft.coerceIn(range.start, range.endInclusive), enabled = enabled && !outside,
            onValueChange = {
                if (enabled && !outside) {
                    dragging = true; draft = spec.validate(it.coerceIn(range.start, range.endInclusive)) as Float
                    val now = SystemClock.uptimeMillis()
                    if (now - lastWrite >= 100L) { lastWrite = now; onWrite(spec, draft) }
                }
            }, onValueChangeFinished = {
                if (dragging && enabled) { dragging = false; onWrite(spec, draft) }
            }, valueRange = range, modifier = Modifier.weight(1f).semantics {
                contentDescription = reason ?: spec.title
            })
        TextButton("+", { change(draft + spec.step) }, enabled = enabled && draft < spec.max,
            minWidth = 44.dp, minHeight = 44.dp,
            modifier = Modifier.semantics { contentDescription = "增大${spec.title}" })
    }
    if (outside) TextButton("移到常用范围", { change(draft.coerceIn(range.start, range.endInclusive)) },
        enabled = enabled, modifier = Modifier.fillMaxWidth(), minHeight = 44.dp)
}

@Composable
private fun NumberDialog(spec: LiteSetting, value: Float, dismiss: () -> Unit, save: (Float) -> Unit) {
    var text by rememberSaveable(spec.key) { mutableStateOf(format(value)) }
    var error by remember { mutableStateOf(false) }
    WindowDialog(show = true, title = spec.title,
        summary = "${format(spec.min)} ～ ${format(spec.max)} ${spec.valueUnit}", onDismissRequest = dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(text, { text = it; error = false }, label = "数值", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (spec.min < 0f) KeyboardType.Text else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (error) Text("请输入范围内的数值")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", dismiss, Modifier.weight(1f))
                TextButton("保存", {
                    val value = text.toFloatOrNull()
                    if (value == null || !value.isFinite() || value !in spec.min..spec.max) error = true
                    else save(spec.validate(value) as Float)
                }, Modifier.weight(1f))
            }
            TextButton("恢复默认", { save((spec.defaultValue as Number).toFloat()) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PreviewPanel(repository: SettingsRepository, revision: Int, target: SettingPreviewTarget,
                         night: Boolean, wallpaperUri: String?, wallpaperStyle: Int, height: Int, modifier: Modifier) {
    class Applied {
        var target: SettingPreviewTarget? = null
        var revision = -1
        var night: Boolean? = null
        var image: String? = null
        var style = -1
    }
    val applied = remember { Applied() }
    AndroidView(factory = { GlassPlayground(it, repository.preferences) }, modifier = modifier.height(height.dp),
        onReset = null, onRelease = { it.release() }, update = { view ->
            view.setRenderOnly(true)
            view.setMaterialComparison(true)
            val changed = applied.target != target
            if (changed) {
                view.setFocusGroup(target.focusGroup)
                view.setScene(target.scene)
                view.setFocusedSetting(target.focusKey)
                applied.target = target
            }
            if (applied.night != night) { view.setNight(night); applied.night = night }
            if (applied.image != wallpaperUri || applied.style != wallpaperStyle) {
                if (wallpaperUri == null) view.setWallpaperStyle(wallpaperStyle) else view.loadWallpaper(Uri.parse(wallpaperUri))
                applied.image = wallpaperUri; applied.style = wallpaperStyle
            }
            val refreshed = applied.revision != revision
            if (refreshed) { view.refresh(); applied.revision = revision }
            if (changed || refreshed) view.previewFocusedEffect()
        })
}

private fun number(values: Map<String, *>, spec: LiteSetting) =
    (values[spec.key] as? Number)?.toFloat() ?: (spec.defaultValue as Number).toFloat()
private fun format(value: Float) = if (value % 1f == 0f) value.roundToInt().toString()
    else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
