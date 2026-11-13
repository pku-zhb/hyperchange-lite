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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuhuibin.hyperchange.lite.*
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
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
    themeMode: Int, language: String,
    onThemeMode: (Int) -> Unit, onLanguage: (String) -> Unit,
) {
    val mode = when (themeMode) { 1 -> ColorSchemeMode.Light; 2 -> ColorSchemeMode.Dark; else -> ColorSchemeMode.System }
    val theme = remember(mode) { ThemeController(mode) }
    val night = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val pageStates = rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf("home") }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastEditKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editSequence by rememberSaveable { mutableIntStateOf(0) }
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    val values = remember(revision) { repository.preferences.all }
    val feature = Feature.entries.firstOrNull { it.name == page }
    fun writeSetting(spec: LiteSetting, value: Any) {
        onWrite(spec, value)
        lastEditKey = spec.key
        editSequence++
    }
    LaunchedEffect(page) {
        when (page) {
            "GLASS", "glass-presets" -> page = Feature.MATERIAL.name
            "CONTROL" -> page = Feature.CORNERS.name
            "ASSISTANT" -> page = Feature.SCOPES.name
            "config", "presets" -> page = "settings"
            "preview" -> page = "home"
        }
    }
    LaunchedEffect(editKey) {
        if (editKey?.let { it in DeferredSettings.hiddenKeys } == true) editKey = null
    }
    val back = { page = "home" }
    BackHandler(page != "home" && editKey == null && !resetDialog) { back() }
    MiuixTheme(controller = theme) {
        Scaffold(topBar = {
            SmallTopAppBar(
                title = uiText(feature?.title ?: when (page) { "settings" -> "设置"; "about" -> "关于"; else -> "主页" }),
                navigationIcon = {
                    if (feature != null) IconButton(onClick = back) {
                        Icon(MiuixIcons.Basic.ArrowRight, uiText("返回"), Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                    }
                },
            )
        }, bottomBar = {
            if (feature == null) RootNavigation(page) { page = it }
        }) { padding ->
            val body = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            when {
                feature != null -> pageStates.SaveableStateProvider(feature.name) {
                    FeaturePage(feature, repository, revision, values, ::writeSetting, { editKey = it.key },
                        lastEditKey, editSequence,
                        onApplyGlassPreset, onRestoreGlassPreset, wallpaperUri, wallpaperStyle, night, body)
                }
                page == "settings" -> SettingsPage(themeMode, language, onThemeMode, onLanguage,
                    onExport, onImport, { resetDialog = true }, body)
                page == "about" -> AboutPage(body)
                else -> Column(body.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(insideMargin = compactPreferenceMargin, title = uiText(when {
                            repository.syncFailed -> "同步失败"
                            repository.connected -> "已连接"
                            else -> "未连接"
                        }))
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Feature.entries.forEach { target ->
                            ArrowPreference(uiText(target.title), insideMargin = compactPreferenceMargin, onClick = { page = target.name })
                        }
                    }
                }
            }
        }
        editKey?.let { name ->
            LiteCatalog.entries.firstOrNull { it.key == name && settingVisible(it) }?.let { spec ->
                NumberDialog(spec, number(values, spec), { editKey = null }) { writeSetting(spec, it); editKey = null }
            }
        }
        WindowDialog(show = resetDialog, title = uiText("恢复全部默认设置？"), onDismissRequest = { resetDialog = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(uiText("取消"), { resetDialog = false }, Modifier.weight(1f), minHeight = 44.dp)
                TextButton(uiText("恢复默认"), { resetDialog = false; onReset() }, Modifier.weight(1f), minHeight = 44.dp)
            }
        }
    }
}

@Composable
private fun FeaturePage(
    feature: Feature, repository: SettingsRepository, revision: Int, values: Map<String, *>,
    onWrite: (LiteSetting, Any) -> Unit, onEdit: (LiteSetting) -> Unit,
    lastEditKey: String?, editSequence: Int,
    onApply: (GlassPreset) -> Unit, onUndo: () -> Unit,
    wallpaperUri: String?, wallpaperStyle: Int, night: Boolean, modifier: Modifier,
) {
    val categories = feature.categories
    val projects = if (categories.isNotEmpty()) categories.map { it.name to it.title }
        else feature.visibleSettings.map { it.group }.distinct().map { it to it }
    var project by rememberSaveable(feature.name) { mutableStateOf(projects.first().first) }
    val index = projects.indexOfFirst { it.first == project }.coerceAtLeast(0)
    val selectedProject = projects[index].first
    val category = categories.getOrNull(index)
    val specs = (category?.settings ?: feature.visibleSettings.filter { it.group == selectedProject }).filter(::settingVisible)
    val fallback = specs.firstOrNull() ?: LiteCatalog.entries.first { it.key == "glass_refraction_blur" }
    var focusedKey by rememberSaveable(feature.name) { mutableStateOf(fallback.key) }
    val focused = specs.firstOrNull { it.key == focusedKey } ?: fallback
    val editBaseline = remember(feature.name, selectedProject) { editSequence }
    val editedKey = lastEditKey?.takeIf { editSequence != editBaseline && specs.any { spec -> spec.key == it } }
    val target = previewTargetForSetting(focused, values, editedKey)
    // Each project opens at its first setting, without carrying an old list's scroll or focus.
    val list = key(selectedProject) { rememberLazyListState() }
    LaunchedEffect(list, selectedProject) {
        snapshotFlow {
            list.isScrollInProgress to list.layoutInfo.visibleItemsInfo
                .firstOrNull { (it.key as? String)?.startsWith("setting:") == true }?.key
        }.distinctUntilChanged().collect { (scrolling, key) ->
            if (scrolling && key is String) {
                val visibleKey = key.removePrefix("setting:")
                if (specs.any { it.key == visibleKey }) focusedKey = visibleKey
            }
        }
    }
    fun write(spec: LiteSetting, value: Any) { focusedKey = spec.key; onWrite(spec, value) }
    fun edit(spec: LiteSetting) { focusedKey = spec.key; onEdit(spec) }
    BoxWithConstraints(modifier) {
        val previewHeight = (maxHeight.value * .28f).coerceIn(64f, 180f).toInt()
        Column(Modifier.fillMaxSize()) {
            PreviewPanel(repository, revision, target, night, wallpaperUri, wallpaperStyle,
                previewHeight,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            if (projects.size > 1) ProjectTabs(projects.map { uiText(it.second) }, index, { selected ->
                if (selected != index) {
                    project = projects[selected].first
                    val first = categories.getOrNull(selected)?.settings?.firstOrNull(::settingVisible)
                        ?: feature.visibleSettings.firstOrNull { it.group == project }
                    focusedKey = first?.key ?: "glass_refraction_blur"
                }
            }, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (category == MaterialCategory.PRESETS) item("presets") {
                    GlassPresetControls(values, repository.hasGlassPresetUndo, onApply, onUndo)
                } else {
                    specs.forEach { spec ->
                        item("setting:${spec.key}") {
                            Card(Modifier.fillMaxWidth()) {
                                val reason = settingDisabledReason(spec, values)
                                if (spec.isToggle) SwitchPreference(
                                    title = settingDisplayTitle(spec, uiText(spec.title), uiText("（注意兼容）")),
                                    insideMargin = compactPreferenceMargin,
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

@Composable
private fun ProjectTabs(titles: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
    val labels = titles.map { measurer.measure(it, style = style, maxLines = 1).size }
    BoxWithConstraints(modifier) {
        val tabWidth = (with(density) { labels.maxOf { it.width }.toDp() } + 24.dp).coerceAtMost(maxWidth)
        TabRow(
            tabs = titles,
            selectedTabIndex = selected,
            onTabSelected = onSelected,
            minWidth = tabWidth,
            maxWidth = maxOf(tabWidth, TabRowDefaults.TabRowMaxWidth),
            height = maxOf(44.dp, with(density) { labels.maxOf { it.height }.toDp() } + 16.dp),
        )
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
    val title = uiText(spec.title)
    val inputLabel = uiText("输入数值")
    val decreaseLabel = uiText("减小")
    val increaseLabel = uiText("增大")
    val disabledLabel = uiText("请先启用对应功能")
    fun display(value: Float) = format(value) + if (spec.valueUnit.isBlank()) "" else " ${spec.valueUnit}"
    fun change(value: Float) {
        dragging = false; draft = spec.validate(value.coerceIn(spec.min, spec.max)) as Float
        onWrite(spec, draft)
    }
    LaunchedEffect(value, enabled) {
        if (!enabled) dragging = false
        if (!dragging) draft = value
    }
    BasicComponent(title = settingDisplayTitle(spec, title, uiText("（注意兼容）")), enabled = enabled,
        insideMargin = PaddingValues(start = 16.dp, end = 8.dp), endActions = {
        TextButton(display(draft), onEdit, enabled = enabled, minWidth = 44.dp, minHeight = 44.dp,
            insideMargin = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.semantics { contentDescription = "$title, ${display(draft)}, $inputLabel" })
    })
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton("−", { change(draft - spec.step) }, enabled = enabled && draft > spec.min,
            minWidth = 44.dp, minHeight = 44.dp, insideMargin = PaddingValues(0.dp),
            modifier = Modifier.semantics { contentDescription = "$decreaseLabel $title" })
        Slider(draft.coerceIn(range.start, range.endInclusive), enabled = enabled && !outside,
            onValueChange = {
                if (enabled && !outside) {
                    dragging = true; draft = spec.validate(it.coerceIn(range.start, range.endInclusive)) as Float
                    val now = SystemClock.uptimeMillis()
                    if (now - lastWrite >= 100L) { lastWrite = now; onWrite(spec, draft) }
                }
            }, onValueChangeFinished = {
                if (dragging && enabled) { dragging = false; onWrite(spec, draft) }
            }, valueRange = range, modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics {
                contentDescription = if (reason == null) title else "$title, $disabledLabel"
            })
        TextButton("+", { change(draft + spec.step) }, enabled = enabled && draft < spec.max,
            minWidth = 44.dp, minHeight = 44.dp, insideMargin = PaddingValues(0.dp),
            modifier = Modifier.semantics { contentDescription = "$increaseLabel $title" })
    }
    if (outside) TextButton(uiText("移到常用范围"), { change(draft.coerceIn(range.start, range.endInclusive)) },
        enabled = enabled, modifier = Modifier.fillMaxWidth(), minHeight = 44.dp)
}

@Composable
private fun NumberDialog(spec: LiteSetting, value: Float, dismiss: () -> Unit, save: (Float) -> Unit) {
    var text by rememberSaveable(spec.key) { mutableStateOf(format(value)) }
    var error by remember { mutableStateOf(false) }
    WindowDialog(show = true, title = uiText(spec.title),
        summary = "${format(spec.min)} ～ ${format(spec.max)} ${spec.valueUnit}", onDismissRequest = dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(text, { text = it; error = false }, label = uiText("数值"), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (spec.min < 0f) KeyboardType.Text else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (error) Text(uiText("请输入范围内的数值"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(uiText("取消"), dismiss, Modifier.weight(1f), minHeight = 44.dp)
                TextButton(uiText("保存"), {
                    val value = text.toFloatOrNull()
                    if (value == null || !value.isFinite() || value !in spec.min..spec.max) error = true
                    else save(spec.validate(value) as Float)
                }, Modifier.weight(1f), minHeight = 44.dp)
            }
            TextButton(uiText("恢复默认"), { save((spec.defaultValue as Number).toFloat()) }, Modifier.fillMaxWidth(), minHeight = 44.dp)
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
                view.focusSetting(target.sampleFocusKey)
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

internal val compactPreferenceMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
