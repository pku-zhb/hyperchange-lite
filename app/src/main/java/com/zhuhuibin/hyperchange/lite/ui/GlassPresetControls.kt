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
                title = "当前参数：${current?.title ?: "自定义"}${if (enabled) "" else "（系统柔光已暂停）"}",
                summary = "HyperChanger 原版六套材质。应用时开启柔光调节，保留当前作用范围和统一材质选择。",
            )
        }
        PresetHint("选用“对比测试图”背景，在“澄光”和“凝雾”之间切换，观察上方固定预览的边缘、折射和背景模糊。")

        SmallTitle("快速对比")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("builtin-custom-a", "builtin-custom-e").forEach { id ->
                builtInGlassPresets.firstOrNull { it.id == id }?.let { preset ->
                    TextButton(preset.title, { onApply(preset) }, Modifier.weight(1f))
                }
            }
        }
        SmallTitle("全部原版预设")
        Card(Modifier.fillMaxWidth()) {
            builtInGlassPresets.forEach { preset ->
                ArrowPreference(
                    title = preset.title + if (preset.id == current?.id) " · 当前参数" else "",
                    summary = "${preset.description} · 点击应用",
                    onClick = { onApply(preset) },
                )
            }
        }

        if (hasUndo) Card(Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "还原试用前材质",
                summary = "恢复首次试用前的材质参数和柔光调节开关。",
                onClick = onUndo,
            )
        }
        PresetHint("音乐接管、锁屏通知及其他功能按你原来的开关工作。预设会保留这些设置；音乐由 HMC 自定义时，继续关闭 Lite 音乐接管。")
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
