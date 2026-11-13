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
        Card(Modifier.fillMaxWidth()) {
            builtInGlassPresets.forEach { preset ->
                ArrowPreference(uiText(preset.title) + if (preset.id == current?.id) " ✓" else "",
                    insideMargin = compactPreferenceMargin, onClick = { onApply(preset) })
            }
        }
        if (hasUndo) Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("还原试用前材质"), insideMargin = compactPreferenceMargin, onClick = onUndo)
        }
    }
}
