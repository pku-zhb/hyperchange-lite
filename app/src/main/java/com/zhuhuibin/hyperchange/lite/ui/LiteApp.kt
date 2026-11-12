// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsRepository
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground
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
    repository: SettingsRepository,
    revision: Int,
    onWrite: (LiteSetting, Any) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    wallpaperUri: String?,
    wallpaperStyle: Int,
    onChooseImage: () -> Unit,
    onWallpaperStyle: (Int) -> Unit,
) {
    val theme = remember { ThemeController(ColorSchemeMode.System) }
    val pageStates = rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf("home") }
    var previewReturn by rememberSaveable { mutableStateOf("home") }
    var presetsReturn by rememberSaveable { mutableStateOf("home") }
    var sceneIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusGroup by rememberSaveable { mutableStateOf("") }
    var night by rememberSaveable { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    var backgroundDialog by rememberSaveable { mutableStateOf(false) }
    val feature = Feature.entries.firstOrNull { it.name == page }
    val values = remember(revision) { repository.preferences.all }
    val connection = when { repository.syncFailed -> 2; repository.connected -> 1; else -> 0 }
    val navigateBack = { page = when (page) { "preview" -> previewReturn; "presets" -> presetsReturn; else -> "home" } }
    val openPresets = { presetsReturn = page; page = "presets" }
    BackHandler(page != "home" && editKey == null && !resetDialog && !backgroundDialog) { navigateBack() }

    fun selectFeature(target: Feature) {
        page = target.name
        focusGroup = when (target) {
            Feature.VOLUME -> "音量交互"
            Feature.CONTROL -> "控制中心"
            Feature.ASSISTANT -> "负一屏组件"
            else -> "通知"
        }
        sceneIndex = when (target) {
            Feature.CONTROL -> GlassPlayground.Scene.CONTROL.ordinal
            Feature.VOLUME -> GlassPlayground.Scene.VOLUME.ordinal
            Feature.ASSISTANT -> GlassPlayground.Scene.WIDGET.ordinal
            else -> GlassPlayground.Scene.NOTIFICATION.ordinal
        }
    }
    MiuixTheme(controller = theme) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = feature?.title ?: when (page) {
                        "presets" -> "配置管理"
                        "preview" -> "场景预览"
                        else -> "HyperChange Lite"
                    },
                    navigationIcon = {
                        if (page != "home") IconButton(onClick = navigateBack) {
                            Icon(MiuixIcons.Basic.ArrowRight, "返回", Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                        }
                    },
                    actions = {
                        if (page != "presets") TextButton(
                            text = "配置", onClick = openPresets,
                            minWidth = 0.dp, minHeight = 40.dp,
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        )
                    },
                )
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                val wide = maxWidth >= 760.dp
                val previewContent: @Composable () -> Unit = {
                    PreviewPanel(repository, revision, sceneIndex, { sceneIndex = it }, focusGroup,
                        night, { night = !night }, replay, { replay++ }, wallpaperUri, wallpaperStyle,
                        { backgroundDialog = true }, if (wide || page == "preview") 300 else 230)
                }
                when {
                    page == "home" -> Home(connection, wide, ::selectFeature, {
                        previewReturn = "home"; page = "preview"
                    }, openPresets)
                    page == "presets" -> Presets(onExport, onImport, { resetDialog = true })
                    page == "preview" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        previewContent()
                        Hint("预览只在 App 内运行。系统效果仍由对应功能开关控制。")
                    }
                    feature != null -> pageStates.SaveableStateProvider(feature.name) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 0.dp)) {
                            FeatureSettings(feature, values, onWrite, { editKey = it.key },
                                Modifier.weight(if (wide) .55f else 1f),
                                if (wide) null else {
                                    {
                                        previewContent()
                                        TextButton(text = "展开预览页", onClick = { previewReturn = page; page = "preview" }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }
                                },
                            ) { group ->
                                focusGroup = group
                                sceneIndex = sceneForGroup(group).ordinal
                            }
                            if (wide) Column(Modifier.weight(.45f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                                previewContent()
                            }
                        }
                    }
                }
            }
        }
        editKey?.let { key ->
            val spec = com.zhuhuibin.hyperchange.lite.LiteCatalog.entries.firstOrNull { it.key == key }
            if (spec != null) NumberDialog(spec, number(values, spec), { editKey = null }) { value ->
                onWrite(spec, value); editKey = null
            }
        }
        WindowDialog(show = resetDialog, title = "恢复默认配置？", summary = "功能开关和参数将恢复初始值，预览背景保留。", onDismissRequest = { resetDialog = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", { resetDialog = false }, Modifier.weight(1f))
                TextButton("恢复", { resetDialog = false; onReset() }, Modifier.weight(1f))
            }
        }
        WindowDialog(show = backgroundDialog, title = "预览背景", onDismissRequest = { backgroundDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("海岸", "暮色", "晨光").forEachIndexed { index, title ->
                    TextButton(title, { onWallpaperStyle(index); backgroundDialog = false }, Modifier.fillMaxWidth())
                }
                TextButton("选择图片", { backgroundDialog = false; onChooseImage() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Home(connection: Int, wide: Boolean, onFeature: (Feature) -> Unit, onPreview: () -> Unit, onPresets: () -> Unit) {
    val destinations: @Composable () -> Unit = {
        SmallTitle("功能")
        Card(Modifier.fillMaxWidth()) {
            Feature.entries.forEach { feature -> ArrowPreference(title = feature.title, summary = feature.summary, onClick = { onFeature(feature) }) }
        }
    }
    val status: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(title = when {
                connection == 2 -> "设置保存在本地"
                connection == 1 -> "模块已连接"
                else -> "可以先预览和调参"
            }, summary = when {
                connection == 2 -> "模块同步失败。重新连接后会继续同步。"
                connection == 1 -> "修改会自动保存并同步。系统效果取决于对应作用域是否已加载。"
                else -> "当前未连接模块服务。你的参数会保留，连接后同步。"
            })
        }
    }
    val utilities: @Composable () -> Unit = {
        SmallTitle("预览与配置")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference("场景预览", summary = "通知、媒体、音量与系统面板", onClick = onPreview)
            ArrowPreference("配置管理", summary = "导入、导出与恢复默认", onClick = onPresets)
        }
        Hint("按功能逐项开启。已有参数会保留，不需要重新设置。")
    }
    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) { status(); destinations() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) { utilities() }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        status(); destinations(); Spacer(Modifier.height(16.dp)); utilities(); Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureSettings(
    feature: Feature, values: Map<String, *>, onWrite: (LiteSetting, Any) -> Unit,
    onEdit: (LiteSetting) -> Unit, modifier: Modifier,
    preview: (@Composable () -> Unit)?, onFocus: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    val sections = feature.settings.filter { query.isBlank() || it.title.contains(query.trim(), true) || it.group.contains(query.trim(), true) || it.summary.contains(query.trim(), true) }.groupBy { it.group }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("search") { TextField(value = query, onValueChange = { query = it }, label = "搜索${feature.title}设置", singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (preview != null) item("preview") {
            Card(Modifier.fillMaxWidth()) { ArrowPreference(if (previewOpen) "收起预览" else "查看对应预览", summary = if (previewOpen) null else "调参时可随时展开", onClick = { previewOpen = !previewOpen }) }
            if (previewOpen) Box(Modifier.padding(top = 12.dp)) { preview() }
        }
        if (sections.isEmpty()) item("empty") { Hint("没有找到相关设置，换一个关键词试试。") }
        sections.forEach { (group, specs) -> item(group) {
            SettingSection(group, specs, values, query.isNotBlank(), onWrite, onEdit, {
                onFocus(group); previewOpen = true
            })
        } }
    }
}

@Composable
private fun SettingSection(group: String, specs: List<LiteSetting>, values: Map<String, *>, searching: Boolean,
                           onWrite: (LiteSetting, Any) -> Unit, onEdit: (LiteSetting) -> Unit, onPreview: () -> Unit) {
    var expanded by rememberSaveable(group) { mutableStateOf(false) }
    val primary = remember(specs) {
        if (specs.size <= 8) specs else specs.filter { it.isToggle }.take(3) + specs.filterNot { it.isToggle }.take(3)
    }
    val extra = specs.filterNot { it in primary }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallTitle(group) }
            TextButton("预览", onPreview, minWidth = 0.dp, minHeight = 36.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
        }
        Card(Modifier.fillMaxWidth()) {
            (if (expanded || searching) specs else primary).forEach { spec ->
                key(spec.key) {
                    if (spec.isToggle) SwitchPreference(title = spec.title, summary = spec.summary.ifBlank { null },
                        checked = (values[spec.key] as? Boolean) ?: spec.defaultValue as Boolean,
                        onCheckedChange = { onWrite(spec, it) })
                    else NumberSetting(spec, number(values, spec), onWrite, { onEdit(spec) })
                }
            }
            if (extra.isNotEmpty() && !searching) ArrowPreference(
                title = if (expanded) "收起细调参数" else "更多参数（${extra.size}）",
                summary = if (expanded) null else "展开全部调节项", onClick = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun NumberSetting(spec: LiteSetting, value: Float, onWrite: (LiteSetting, Any) -> Unit, onEdit: () -> Unit) {
    var draft by remember(spec.key) { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var lastWrite by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) { if (!dragging) draft = value }
    BasicComponent(title = spec.title, summary = spec.summary.ifBlank { null }, endActions = {
        TextButton(format(draft), onEdit, minWidth = 52.dp, minHeight = 40.dp,
            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp))
    })
    Slider(value = draft, onValueChange = {
        dragging = true; draft = spec.validate(it.coerceIn(spec.min, spec.max)) as Float
        val now = SystemClock.uptimeMillis()
        if (now - lastWrite >= 100L) { lastWrite = now; onWrite(spec, draft) }
    }, onValueChangeFinished = { dragging = false; onWrite(spec, draft) }, valueRange = spec.min..spec.max,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
}

@Composable
private fun NumberDialog(spec: LiteSetting, value: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by rememberSaveable(spec.key) { mutableStateOf(format(value)) }
    var error by remember { mutableStateOf<String?>(null) }
    WindowDialog(show = true, title = spec.title, summary = "范围 ${format(spec.min)} — ${format(spec.max)}", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value = text, onValueChange = { text = it; error = null }, label = "数值", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (spec.min < 0f) KeyboardType.Text else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            error?.let { Hint(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", onDismiss, Modifier.weight(1f))
                TextButton("保存", {
                    val candidate = text.toFloatOrNull()
                    if (candidate == null || !candidate.isFinite() || candidate !in spec.min..spec.max) error = "请输入范围内的数值"
                    else onSave(spec.validate(candidate) as Float)
                }, Modifier.weight(1f))
            }
            TextButton("使用默认值 ${format((spec.defaultValue as Number).toFloat())}", { onSave((spec.defaultValue as Number).toFloat()) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Presets(onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        SmallTitle("保存与迁移")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference("导出当前配置", summary = "保存全部功能开关与参数", onClick = onExport)
            ArrowPreference("导入配置", summary = "读取 HyperChange Lite 的 JSON 配置文件", onClick = onImport)
        }
        Spacer(Modifier.height(16.dp))
        SmallTitle("重新开始")
        Card(Modifier.fillMaxWidth()) { ArrowPreference("恢复默认配置", summary = "恢复前会再次确认；预览背景保留", onClick = onReset) }
        Hint("界面升级不会清除旧参数。只导入已通过格式与数值校验的配置，不会自动重启系统界面。")
    }
}

@Composable
private fun PreviewPanel(repository: SettingsRepository, revision: Int, scene: Int, onScene: (Int) -> Unit,
                         focus: String, night: Boolean, onNight: () -> Unit, replay: Int, onReplay: () -> Unit,
                         wallpaperUri: String?, wallpaperStyle: Int, onBackground: () -> Unit, height: Int) {
    // Store applied values separately from Compose state: AndroidView.update must not
    // rebuild the scene on unrelated recompositions or reuse a permanently released view.
    class Applied(initialReplay: Int) {
        var night: Boolean? = null
        var focus: String? = null
        var scene = -1
        var image: String? = null
        var style = -1
        var revision = -1
        var replay = initialReplay
    }
    val applied = remember { Applied(replay) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            WindowDropdownPreference(title = "预览场景", items = GlassPlayground.Scene.entries.map { it.title },
                selectedIndex = scene, onSelectedIndexChange = onScene)
        }
        AndroidView(factory = { context -> GlassPlayground(context, repository.preferences) },
            modifier = Modifier.fillMaxWidth().height(height.dp),
            onReset = null,
            onRelease = { it.release() },
            update = { view ->
                if (applied.focus != focus) { view.setFocusGroup(focus); applied.focus = focus; applied.scene = -1 }
                if (applied.scene != scene) { view.setScene(GlassPlayground.Scene.entries[scene]); applied.scene = scene }
                if (applied.night != night) { view.setNight(night); applied.night = night }
                if (applied.image != wallpaperUri || applied.style != wallpaperStyle) {
                    if (wallpaperUri != null) view.loadWallpaper(Uri.parse(wallpaperUri)) else view.setWallpaperStyle(wallpaperStyle)
                    applied.image = wallpaperUri; applied.style = wallpaperStyle
                }
                if (applied.revision != revision) { view.refresh(); applied.revision = revision }
                if (applied.replay != replay) { view.replay(); applied.replay = replay }
            })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(if (night) "夜间" else "日间", onNight, Modifier.weight(1f), minWidth = 0.dp)
            TextButton("背景", onBackground, Modifier.weight(1f), minWidth = 0.dp)
            TextButton("重播", onReplay, Modifier.weight(1f), minWidth = 0.dp)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

private fun number(values: Map<String, *>, spec: LiteSetting): Float =
    (values[spec.key] as? Number)?.toFloat() ?: (spec.defaultValue as Number).toFloat()
private fun format(value: Float) = if (value % 1f == 0f) value.roundToInt().toString()
    else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
private fun sceneForGroup(group: String) = when {
    group.contains("悬浮") -> GlassPlayground.Scene.HEADS_UP
    group.contains("音量") -> GlassPlayground.Scene.VOLUME
    group.contains("控制") -> GlassPlayground.Scene.CONTROL
    group.contains("负一") -> GlassPlayground.Scene.WIDGET
    else -> GlassPlayground.Scene.NOTIFICATION
}
