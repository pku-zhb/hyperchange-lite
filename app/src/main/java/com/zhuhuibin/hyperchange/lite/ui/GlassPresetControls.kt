// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuhuibin.hyperchange.lite.GlassPreset
import com.zhuhuibin.hyperchange.lite.LiteCatalog
import com.zhuhuibin.hyperchange.lite.builtInGlassPresets
import com.zhuhuibin.hyperchange.lite.glassPresetKeys
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Material trials keep feature ranges explicit and restore only the trial's parameters. */
@Composable
internal fun GlassPresetControls(
    values: Map<String, *>,
    hasUndo: Boolean,
    onApply: (GlassPreset) -> Unit,
    onUndo: () -> Unit,
) {
    val specs = remember { LiteCatalog.entries.filter { it.key in glassPresetKeys }.associateBy { it.key } }
    val enabled = values["glass_enabled"] as? Boolean ?: false
    val current = builtInGlassPresets.firstOrNull { preset ->
        preset.values.all { (key, expected) ->
            key == "glass_enabled" || specs[key]?.let { spec ->
                runCatching { spec.validate(values[key] ?: spec.defaultValue) == expected }.getOrDefault(false)
            } == true
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(
                title = "当前参数：${current?.title ?: "自定义"}${if (enabled) "" else "（系统材质调节已关闭）"}",
                summary = "点按预设即可应用，并开启系统材质调节。生效位置、材质统一和通知动画保留原设置。",
            )
        }
        PresetHint("可在“预览选项”中更换背景，再用下面两套预设对比模糊与边缘效果。")

        SmallTitle("快速对比")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("builtin-custom-a", "builtin-custom-e").forEach { id ->
                builtInGlassPresets.firstOrNull { it.id == id }?.let { preset ->
                    TextButton(preset.title, { onApply(preset) }, Modifier.weight(1f))
                }
            }
        }
        SmallTitle("全部材质预设")
        Card(Modifier.fillMaxWidth()) {
            builtInGlassPresets.forEach { preset ->
                ArrowPreference(
                    title = preset.title + if (preset.id == current?.id) "（当前参数）" else "",
                    summary = preset.description,
                    onClick = { onApply(preset) },
                )
            }
        }

        if (hasUndo) Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "还原试用前材质",
                summary = "恢复本轮首次应用预设前的玻璃参数和系统材质调节开关。",
                onClick = onUndo,
            )
        }
        PresetHint("预设来自 HyperChanger。音乐使用 HMC 自定义背景时，请在“启用与范围”中关闭 Lite 音乐材质。")
    }
}

@Composable
private fun PresetHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
