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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.GlassPreset
import com.zhuhuibin.hyperchange.lite.SettingsRepository
import com.zhuhuibin.hyperchange.lite.sliderRange
import com.zhuhuibin.hyperchange.lite.valueUnit
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
    onApplyGlassPreset: (GlassPreset) -> Unit,
    onRestoreGlassPreset: () -> Unit,
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
    var materialScene by rememberSaveable { mutableIntStateOf(GlassPlayground.Scene.CONTROL.ordinal) }
    var materialCategoryName by rememberSaveable { mutableStateOf(MaterialCategory.SCOPES.name) }
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
    LaunchedEffect(page) {
        // Older saved navigation state can outlive an App upgrade. Map those
        // destinations into the editor without changing any stored setting.
        when (page) {
            "GLASS", "glass-presets" -> {
                materialCategoryName = if (page == "glass-presets") MaterialCategory.PRESETS.name else MaterialCategory.SURFACE.name
                page = Feature.MATERIAL.name
            }
            "CONTROL" -> { materialCategoryName = MaterialCategory.CONTROL.name; materialScene = GlassPlayground.Scene.CONTROL.ordinal; page = Feature.MATERIAL.name }
            "ASSISTANT" -> { materialCategoryName = MaterialCategory.ASSISTANT.name; materialScene = GlassPlayground.Scene.WIDGET.ordinal; page = Feature.MATERIAL.name }
        }
    }
    val navigateBack = { page = when (page) { "preview" -> previewReturn; "presets" -> presetsReturn; else -> "home" } }
    val openPresets = { presetsReturn = page; page = "presets" }
    BackHandler(page != "home" && editKey == null && !resetDialog && !backgroundDialog) { navigateBack() }

    fun selectFeature(target: Feature) {
        page = target.name
        focusGroup = when (target) {
            Feature.VOLUME -> "音量交互"
            Feature.MATERIAL -> "材质"
            else -> "通知"
        }
        sceneIndex = when (target) {
            Feature.MATERIAL -> GlassPlayground.Scene.CONTROL.ordinal
            Feature.VOLUME -> GlassPlayground.Scene.VOLUME.ordinal
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
                    page == "home" -> Home(connection, wide, ::selectFeature, openPresets)
                    page == "presets" -> Presets(onExport, onImport, { resetDialog = true })
                    feature == Feature.MATERIAL -> pageStates.SaveableStateProvider(Feature.MATERIAL.name) {
                        val category = MaterialCategory.entries.firstOrNull { it.name == materialCategoryName }
                            ?: MaterialCategory.SCOPES
                        MaterialEditorPage(
                            category, { selected ->
                                materialCategoryName = selected.name
                                materialSceneForCategory(selected)?.let { materialScene = it.ordinal }
                            }, materialScene, { materialScene = it }, values, connection,
                            repository.hasGlassPresetUndo, onApplyGlassPreset, onRestoreGlassPreset,
                            { spec, value ->
                                materialSceneForSetting(spec)?.let { materialScene = it.ordinal }
                                onWrite(spec, value)
                            }, { spec ->
                                materialSceneForSetting(spec)?.let { materialScene = it.ordinal }
                                editKey = spec.key
                            },
                            preview = { height ->
                                PreviewPanel(repository, revision, materialScene, { materialScene = it }, "材质",
                                    night, { night = !night }, replay, { replay++ }, wallpaperUri, wallpaperStyle,
                                    { backgroundDialog = true }, height, materialComparison = true, compact = true)
                            },
                        )
                    }
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
        WindowDialog(show = resetDialog, title = "恢复全部默认设置？", summary = "所有功能开关和参数都会恢复默认值。预览背景保留；此操作不会重启系统界面。", onDismissRequest = { resetDialog = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", { resetDialog = false }, Modifier.weight(1f))
                TextButton("恢复默认", { resetDialog = false; onReset() }, Modifier.weight(1f))
            }
        }
        WindowDialog(show = backgroundDialog, title = "预览背景", onDismissRequest = { backgroundDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("海岸", "暮色", "晨光", "对比测试图").forEachIndexed { index, title ->
                    TextButton(title, { onWallpaperStyle(index); backgroundDialog = false }, Modifier.fillMaxWidth())
                }
                TextButton("选择图片", { backgroundDialog = false; onChooseImage() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Home(connection: Int, wide: Boolean, onFeature: (Feature) -> Unit, onPresets: () -> Unit) {
    val destinations: @Composable () -> Unit = {
        SmallTitle("功能")
        Card(Modifier.fillMaxWidth()) {
            Feature.entries.forEach { feature -> ArrowPreference(title = feature.title, summary = feature.summary, onClick = { onFeature(feature) }) }
        }
    }
    val status: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(title = when {
                connection == 2 -> "设置尚未同步到系统"
                connection == 1 -> "设置同步已连接"
                else -> "尚未连接模块服务"
            }, summary = when {
                connection == 2 -> "修改已保存在本机。连接恢复后会重试同步。"
                connection == 1 -> "修改会自动保存，无需点应用。此状态只表示设置服务已连接。"
                else -> "可以先预览和保存设置。系统效果需在 LSPosed 启用模块，并由你重载对应作用域。"
            })
        }
    }
    val utilities: @Composable () -> Unit = {
        SmallTitle("配置")
        Card(Modifier.fillMaxWidth()) {
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
    val groups = feature.settings.map { it.group }.distinct()
    var selectedGroup by rememberSaveable(feature.name) { mutableStateOf(groups.first()) }
    val searching = query.isNotBlank()
    val sections = feature.settings.filter { spec ->
        if (searching) listOf(spec.title, spec.group, spec.summary).any { it.contains(query.trim(), true) }
        else spec.group == selectedGroup
    }.groupBy(::settingSectionTitle)
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("category") {
            Card(Modifier.fillMaxWidth()) {
                WindowDropdownPreference(
                    title = "调节项目", items = groups, selectedIndex = groups.indexOf(selectedGroup).coerceAtLeast(0),
                    onSelectedIndexChange = { selectedGroup = groups[it]; query = ""; onFocus(selectedGroup) },
                )
            }
        }
        item("search") {
            TextField(value = query, onValueChange = { query = it }, label = "搜索全部${feature.title}设置", singleLine = true, modifier = Modifier.fillMaxWidth())
            if (searching) TextButton("清除搜索", { query = "" }, modifier = Modifier.fillMaxWidth())
            else Hint("点数值可直接输入，使用加减按钮可按最小步长微调。")
        }
        if (feature == Feature.NOTIFICATIONS) item("notification-scope") {
            Hint("这里调节通知动画。锁屏是否使用这些效果，在“材质与圆角 → 启用与范围”中单独选择。")
        }
        if (preview != null) item("preview") {
            Card(Modifier.fillMaxWidth()) { ArrowPreference(if (previewOpen) "收起动画预览" else "查看动画预览", summary = if (previewOpen) null else "仅在 App 内演示已开启的效果", onClick = { previewOpen = !previewOpen; onFocus(selectedGroup) }) }
            if (previewOpen) Box(Modifier.padding(top = 12.dp)) { preview() }
        }
        if (sections.isEmpty()) item("empty") { Hint("没有找到相关设置，换一个关键词试试。") }
        sections.forEach { (group, specs) -> item(group) {
            SettingSection(group, specs, values, searching = searching, onWrite = onWrite, onEdit = onEdit, onPreview = {
                onFocus(specs.first().group); previewOpen = true
            })
        } }
    }
}

@Composable
internal fun SettingSection(group: String, specs: List<LiteSetting>, values: Map<String, *>, searching: Boolean = false,
                           onWrite: (LiteSetting, Any) -> Unit, onEdit: (LiteSetting) -> Unit, onPreview: (() -> Unit)? = null) {
    val collapsible = group.startsWith("悬浮通知：") && group != "悬浮通知：弹出与收起"
    var expanded by rememberSaveable(group) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallTitle(group) }
            if (onPreview != null) TextButton("预览", onPreview, minWidth = 0.dp, minHeight = 44.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp))
        }
        Card(Modifier.fillMaxWidth()) {
            if (collapsible && !searching) ArrowPreference(
                title = if (expanded) "收起参数" else "展开参数",
                summary = if (expanded) null else if (group.endsWith("曲线")) "选择弹簧或自定义曲线，再调整对应参数" else "分别设置翻转与模糊的过渡",
                onClick = { expanded = !expanded },
            )
            (if (!collapsible || expanded || searching) specs else emptyList()).forEach { spec ->
                key(spec.key) {
                    val reason = settingDisabledReason(spec, values)
                    if (spec.isToggle) SwitchPreference(title = spec.title,
                        summary = reason ?: spec.summary.ifBlank { null }, enabled = reason == null,
                        checked = (values[spec.key] as? Boolean) ?: spec.defaultValue as Boolean,
                        onCheckedChange = { if (reason == null) onWrite(spec, it) })
                    else NumberSetting(spec, number(values, spec), reason, onWrite, { onEdit(spec) })
                }
            }
        }
    }
}

@Composable
private fun NumberSetting(spec: LiteSetting, value: Float, disabledReason: String?, onWrite: (LiteSetting, Any) -> Unit, onEdit: () -> Unit) {
    var draft by remember(spec.key) { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var lastWrite by remember { mutableLongStateOf(0L) }
    val enabled = disabledReason == null
    val common = spec.sliderRange
    val outside = draft < common.start - spec.step * .01f || draft > common.endInclusive + spec.step * .01f
    val unit = spec.valueUnit
    fun display(number: Float) = format(number) + if (unit.isBlank()) "" else " $unit"
    fun setValue(candidate: Float) {
        dragging = false
        draft = spec.validate(candidate.coerceIn(spec.min, spec.max)) as Float
        onWrite(spec, draft)
    }
    LaunchedEffect(value, enabled) {
        if (!enabled) dragging = false
        if (!dragging) draft = value
    }
    BasicComponent(title = spec.title, summary = disabledReason ?: spec.summary.ifBlank { null })
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton("−", { setValue(draft - spec.step) }, enabled = enabled && draft > spec.min,
            modifier = Modifier.semantics { contentDescription = "减小${spec.title}，每次 ${display(spec.step)}" },
            minWidth = 44.dp, minHeight = 48.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp))
        TextButton(display(draft), onEdit, enabled = enabled, modifier = Modifier.weight(1f).semantics {
            contentDescription = "${spec.title}，当前 ${display(draft)}，点按输入精确数值"
        }, minWidth = 0.dp, minHeight = 48.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
        TextButton("+", { setValue(draft + spec.step) }, enabled = enabled && draft < spec.max,
            modifier = Modifier.semantics { contentDescription = "增大${spec.title}，每次 ${display(spec.step)}" },
            minWidth = 44.dp, minHeight = 48.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp))
    }
    if (outside) {
        Text("当前值超出常用滑动范围，已保留。可用加减或点数值继续细调。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        TextButton("移到常用范围", { setValue(draft.coerceIn(common.start, common.endInclusive)) },
            enabled = enabled, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), minHeight = 44.dp)
    }
    Slider(value = draft.coerceIn(common.start, common.endInclusive), enabled = enabled && !outside,
        onValueChange = {
            if (enabled && !outside) {
                dragging = true; draft = spec.validate(it.coerceIn(common.start, common.endInclusive)) as Float
                val now = SystemClock.uptimeMillis()
                if (now - lastWrite >= 100L) { lastWrite = now; onWrite(spec, draft) }
            }
        }, onValueChangeFinished = { if (dragging && enabled) { dragging = false; onWrite(spec, draft) } },
        valueRange = common,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(display(common.start), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(display(common.endInclusive), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun NumberDialog(spec: LiteSetting, value: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by rememberSaveable(spec.key) { mutableStateOf(format(value)) }
    var error by remember { mutableStateOf<String?>(null) }
    WindowDialog(show = true, title = spec.title, summary = "可输入 ${format(spec.min)} 至 ${format(spec.max)}${if (spec.valueUnit.isBlank()) "" else " ${spec.valueUnit}"}，最小步长 ${format(spec.step)}", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value = text, onValueChange = { text = it; error = null }, label = "精确数值", singleLine = true,
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
            TextButton("恢复此项默认值 ${format((spec.defaultValue as Number).toFloat())}", { onSave((spec.defaultValue as Number).toFloat()) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Presets(onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        SmallTitle("保存与迁移")
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference("导出当前配置", summary = "保存全部功能开关与参数", onClick = onExport)
            ArrowPreference("导入配置", summary = "从之前导出的 Lite 配置文件恢复设置", onClick = onImport)
        }
        Spacer(Modifier.height(16.dp))
        SmallTitle("重新开始")
        Card(Modifier.fillMaxWidth()) { ArrowPreference("恢复默认配置", summary = "恢复前会再次确认；预览背景保留", onClick = onReset) }
        Hint("导入会覆盖文件中的同名设置，未包含项保留；预览背景不变。")
    }
}

@Composable
private fun PreviewPanel(repository: SettingsRepository, revision: Int, scene: Int, onScene: (Int) -> Unit,
                         focus: String, night: Boolean, onNight: () -> Unit, replay: Int, onReplay: () -> Unit,
                         wallpaperUri: String?, wallpaperStyle: Int, onBackground: () -> Unit, height: Int,
                         materialComparison: Boolean = false, compact: Boolean = false) {
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
    var nativeMaterial by remember { mutableStateOf<Boolean?>(null) }
    var previewOptions by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
        if (!compact) {
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = when (nativeMaterial) {
                    true -> "系统玻璃样例"
                    false -> "当前为示意样例"
                    null -> "正在准备预览"
                }, summary = if (nativeMaterial == false)
                    "设备未提供完整玻璃预览，模糊与折射请以系统面板为准。"
                else "这是 App 内样例，不表示系统面板已启用效果。")
            }
            Card(Modifier.fillMaxWidth()) {
                WindowDropdownPreference(title = "预览场景", items = GlassPlayground.Scene.entries.map { it.title },
                    selectedIndex = scene, onSelectedIndexChange = onScene)
            }
        }
        AndroidView(factory = { context -> GlassPlayground(context, repository.preferences).also { view ->
            view.onMaterialStatusChanged = { nativeMaterial = it }
            nativeMaterial = view.nativeMaterialActive
        } },
            modifier = Modifier.fillMaxWidth().height(height.dp),
            onReset = null,
            onRelease = { it.onMaterialStatusChanged = null; it.release() },
            update = { view ->
                view.setMaterialComparison(materialComparison)
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
        if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (nativeMaterial) {
                    true -> "系统玻璃样例 · App 内预览"
                    false -> "示意样例 · 以系统面板效果为准"
                    null -> "正在准备预览"
                },
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(if (previewOptions) "收起选项" else "预览选项", { previewOptions = !previewOptions },
                minWidth = 0.dp, minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
        }
        if (!compact || previewOptions) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(if (night) "切到日间" else "切到夜间", onNight, Modifier.weight(1f), minWidth = 0.dp, minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
            TextButton("更换背景", onBackground, Modifier.weight(1f), minWidth = 0.dp, minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
            TextButton("重播动画", onReplay, Modifier.weight(1f), minWidth = 0.dp, minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
        }
    }
}

@Composable
internal fun Hint(text: String) {
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
