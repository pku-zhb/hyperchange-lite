// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Material trials keep feature ranges explicit and restore only the trial's parameters. */
@Composable
fun GlassPresetsPage(
    values: Map<String, *>,
    connection: Int,
    hasUndo: Boolean,
    onApply: (GlassPreset) -> Unit,
    onUndo: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    preview: @Composable () -> Unit,
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
    val testRanges = listOf(
        "控制中心" to (values["glass_control_center"] as? Boolean ?: true),
        "音量面板" to (values["glass_volume"] as? Boolean ?: true),
    ).filter { it.second }.map { it.first }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(
                title = "当前参数：${current?.title ?: "自定义"}${if (enabled) "" else "（调节已暂停）"}",
                summary = "HyperChanger 原版六套材质。应用时开启柔光调节，保留当前作用范围和统一材质选择。",
            )
            BasicComponent(
                title = when (connection) {
                    1 -> "模块服务已连接"
                    2 -> "参数已保存在本地，模块同步失败"
                    else -> "当前可在 App 内预览"
                },
                summary = when (connection) {
                    1 -> "连接状态不代表系统作用域已加载；请在已开启的范围内对比。"
                    2 -> "预览可读取本地参数；重新连接后继续同步。"
                    else -> "尚未连接模块服务，系统界面暂不能据此判断生效。"
                },
            )
        }

        preview()
        PresetHint("先把背景选为“对比测试图”，切换“澄光”和“凝雾”，观察边缘、折射和背景模糊。请同时查看上方的原生／示意预览状态。")

        SmallTitle("快速对比")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("builtin-custom-a", "builtin-custom-e").forEach { id ->
                builtInGlassPresets.firstOrNull { it.id == id }?.let { preset ->
                    TextButton(preset.title, { onApply(preset) }, Modifier.weight(1f))
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "启用柔光调节",
                summary = "关闭可在本页预览及系统中对照调节前的材质，参数保留；统一配方和作用范围保持原选择。",
                checked = enabled,
                onCheckedChange = onEnabled,
            )
            ArrowPreference(
                title = "检查材质参数与作用范围",
                summary = if (testRanges.isEmpty()) "控制中心和音量范围都已关闭，请先打开一项再对比系统效果。"
                    else "可先在解锁后的${testRanges.joinToString("、")}中比较同一位置。",
                onClick = onOpenSettings,
            )
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
