// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhuhuibin.hyperchange.lite.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun GlassPresetControls(values: Map<String, *>, hasUndo: Boolean, onApply: (GlassPreset) -> Unit, onUndo: () -> Unit) {
    val specs = LiteCatalog.entries.filter { it.key in glassPresetKeys }.associateBy { it.key }
    val current = builtInGlassPresets.firstOrNull { preset -> preset.values.all { (key, expected) ->
        key == "glass_enabled" || specs[key]?.let { spec ->
            runCatching { spec.validate(values[key] ?: spec.defaultValue) == expected }.getOrDefault(false)
        } == true
    } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorNote(uiText("预设会开启卡片材质调节，替换玻璃与卡片背景参数；不改下拉背景和作用范围。"))
        Card(Modifier.fillMaxWidth()) {
            builtInGlassPresets.forEach { preset ->
                ArrowPreference(uiText(preset.title) + if (preset.id == current?.id) " ✓" else "",
                    summary = uiText(preset.description), insideMargin = compactPreferenceMargin, onClick = { onApply(preset) })
            }
        }
        if (hasUndo) Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("还原套用预设前的参数"),
                summary = uiText("撤销本轮连续点选预设的改动，不改下拉背景和作用范围。"),
                insideMargin = compactPreferenceMargin, onClick = onUndo)
        }
    }
}
