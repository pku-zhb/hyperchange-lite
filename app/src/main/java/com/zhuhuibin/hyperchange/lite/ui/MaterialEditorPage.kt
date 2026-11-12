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
    var query by rememberSaveable(category.name) { mutableStateOf("") }
    var listCategory by rememberSaveable { mutableStateOf(category.name) }
    val listState = rememberLazyListState()
    LaunchedEffect(category) {
        if (listCategory != category.name) {
            listState.scrollToItem(0)
            listCategory = category.name
        }
    }
    val sections = category.settings.filter { spec ->
        query.isBlank() || listOf(spec.title, spec.summary, spec.group).any { it.contains(query.trim(), true) }
    }.groupBy { it.group }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep room for controls on short landscape windows, without changing the
        // requested top position or nesting the preview inside the settings list.
        val previewHeight = (maxHeight.value * .34f).coerceIn(100f, 250f).toInt()
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
                            title = "预览场景", items = GlassPlayground.Scene.entries.map { it.title },
                            selectedIndex = scene, onSelectedIndexChange = onScene,
                        )
                        WindowDropdownPreference(
                            title = "调节分类", items = MaterialCategory.entries.map { it.title },
                            selectedIndex = category.ordinal,
                            onSelectedIndexChange = { onCategory(MaterialCategory.entries[it]) },
                        )
                    }
                }
                if (connection != 1) item("connection") {
                    Hint(if (connection == 2) "参数已保存在本地，模块同步失败；重新连接后继续同步。"
                        else "参数会保存在本地；连接模块服务后同步到系统。")
                }
                if (category == MaterialCategory.PRESETS) {
                    item("presets") { GlassPresetControls(values, hasUndo, onApply, onUndo) }
                } else {
                    item("search") {
                        TextField(
                            value = query, onValueChange = { query = it }, label = "搜索${category.title}",
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (sections.isEmpty()) item("empty") { Hint("没有找到相关设置，换一个关键词试试。") }
                    sections.forEach { (group, specs) ->
                        item("${category.name}-$group") {
                            SettingSection(group, specs, values, searching = true, onWrite = onWrite, onEdit = onEdit)
                        }
                    }
                    item("scope-hint") {
                        Hint(when (category) {
                            MaterialCategory.SCOPES -> "锁屏通知效果同时约束通知材质与行为。预览仅在 App 内运行，不会启动或重载系统功能。"
                            MaterialCategory.ASSISTANT -> "负一屏与侧边栏有独立开关，共用材质细调参数。可切换上方场景逐项对照。"
                            else -> "开关关闭时保留数值；预览按当前场景的开关与作用范围显示。系统效果仍取决于作用域是否已加载。"
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
