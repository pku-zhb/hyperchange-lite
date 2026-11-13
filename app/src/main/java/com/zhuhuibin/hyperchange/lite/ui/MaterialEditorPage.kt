// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhuhuibin.hyperchange.lite.GlassPreset
import com.zhuhuibin.hyperchange.lite.LiteSetting
import com.zhuhuibin.hyperchange.lite.SettingsMigration
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/** One fixed preview; category navigation and every material parameter scroll below it. */
@Composable
internal fun MaterialEditorPage(
    category: MaterialCategory,
    onCategory: (MaterialCategory) -> Unit,
    scene: Int,
    onScene: (Int) -> Unit,
    values: Map<String, *>,
    connection: Int,
    hasUndo: Boolean,
    onApply: (GlassPreset) -> Unit,
    onUndo: () -> Unit,
    onWrite: (LiteSetting, Any) -> Unit,
    onEdit: (LiteSetting) -> Unit,
    preview: @Composable (height: Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var listCategory by rememberSaveable { mutableStateOf(category.name) }
    val listState = rememberLazyListState()
    LaunchedEffect(category) {
        if (listCategory != category.name) {
            listState.scrollToItem(0)
            listCategory = category.name
        }
    }
    val searching = query.isNotBlank()
    val sections = (if (searching) Feature.MATERIAL.settings else category.settings).filter { spec ->
        !searching || listOf(spec.title, spec.summary, spec.group,
            MaterialCategory.ownerOrNull(spec)?.title.orEmpty()).any { it.contains(query.trim(), true) }
    }.groupBy { spec ->
        if (searching) "${MaterialCategory.ownerOrNull(spec)?.title} · ${settingSectionTitle(spec)}"
        else settingSectionTitle(spec)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep room for controls on short landscape windows, without changing the
        // requested top position or nesting the preview inside the settings list.
        val previewHeight = (maxHeight.value * .30f).coerceIn(80f, 230f).toInt()
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                preview(previewHeight)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("navigation") {
                    Card(Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            title = "预览位置（仅预览）", items = GlassPlayground.Scene.entries.map { it.title },
                            selectedIndex = scene, onSelectedIndexChange = onScene,
                        )
                        WindowDropdownPreference(
                            title = "调节项目", items = MaterialCategory.entries.map { it.title },
                            selectedIndex = category.ordinal,
                            onSelectedIndexChange = { query = ""; onCategory(MaterialCategory.entries[it]) },
                        )
                    }
                }
                if (connection != 1) item("connection") {
                    Hint(if (connection == 2) "参数已保存在本地，模块同步失败；重新连接后继续同步。"
                        else "参数会保存在本地；连接模块服务后同步到系统。")
                }
                item("search") {
                    TextField(
                        value = query, onValueChange = { query = it }, label = "搜索全部材质与圆角设置",
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (searching) TextButton("清除搜索", { query = "" }, modifier = Modifier.fillMaxWidth())
                    else Hint(category.summary + if (category == MaterialCategory.PRESETS) "" else "。点数值可直接输入，加减按钮用于微调。")
                }
                if (category == MaterialCategory.PRESETS && !searching) {
                    item("presets") { GlassPresetControls(values, hasUndo, onApply, onUndo) }
                } else {
                    if (sections.isEmpty()) item("empty") { Hint("没有找到相关设置。可搜索“通知”“模糊”或“圆角”。") }
                    sections.forEach { (group, specs) ->
                        item("${category.name}-$group") {
                            SettingSection(group, specs, values, searching = true, onWrite = onWrite, onEdit = onEdit)
                        }
                    }
                    item("scope-hint") {
                        Hint(when (category) {
                            MaterialCategory.SCOPES -> "与 HMC 共用时，请关闭 Lite 的锁屏普通通知效果；音乐使用 HMC 自定义背景时，也请关闭 Lite 音乐材质。"
                            MaterialCategory.ASSISTANT -> "这两个位置不受“系统材质调节”开关控制。调节玻璃质感时，请在上方选对应的预览位置。"
                            else -> "参数会自动保存。关闭效果会保留数值；预览只显示 App 内样例，系统效果以实际面板为准。"
                        })
                    }
                }
            }
        }
    }
}

internal fun materialSceneForCategory(category: MaterialCategory): GlassPlayground.Scene? = when (category) {
    MaterialCategory.BACKGROUND -> GlassPlayground.Scene.SHADE
    MaterialCategory.NOTIFICATION -> GlassPlayground.Scene.NOTIFICATION
    MaterialCategory.CONTROL -> GlassPlayground.Scene.CONTROL
    MaterialCategory.VOLUME -> GlassPlayground.Scene.VOLUME
    MaterialCategory.ASSISTANT -> GlassPlayground.Scene.WIDGET
    else -> null
}

/** Local scope controls select a relevant sample instead of leaving an unrelated scene visible. */
internal fun materialSceneForSetting(spec: LiteSetting): GlassPlayground.Scene? = when {
    spec.key.startsWith("glass_sidebar") -> GlassPlayground.Scene.SIDEBAR
    spec.key == "glass_assistant" -> GlassPlayground.Scene.WIDGET
    spec.key == SettingsMigration.MEDIA_KEY || spec.key == "glass_unify_notifications" -> GlassPlayground.Scene.MEDIA
    spec.key == "glass_shade" || spec.key.startsWith("glass_background_") -> GlassPlayground.Scene.SHADE
    spec.key.startsWith("notification_radius") || spec.key == "glass_notifications" || spec.key == SettingsMigration.LOCKSCREEN_KEY -> GlassPlayground.Scene.NOTIFICATION
    spec.key.startsWith("cc_") || spec.key == "glass_control_center" -> GlassPlayground.Scene.CONTROL
    spec.key.startsWith("volume_") || spec.key == "glass_volume" -> GlassPlayground.Scene.VOLUME
    else -> null
}
