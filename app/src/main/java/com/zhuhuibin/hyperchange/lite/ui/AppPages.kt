// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.zhuhuibin.hyperchange.lite.ui.component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun RootNavigation(page: String, floating: Boolean, liquid: Boolean, blur: Boolean, backdrop: LayerBackdrop, onPage: (String) -> Unit) {
    val items = listOf(Triple("home", RootIcons.home, "主页"), Triple("settings", RootIcons.settings, "设置"), Triple("about", RootIcons.info, "关于"))
    if (floating && liquid) {
        IosLiquidGlassNavigationBar(
            items = items.map { (_, icon, title) -> NavigationItem(uiText(title), icon) },
            selectedIndex = items.indexOfFirst { it.first == page }.coerceAtLeast(0),
            onItemClick = { onPage(items[it].first) }, backdrop = backdrop, isBlurActive = blur,
        )
        return
    }
    val shape = RoundedCornerShape(if (floating) 32.dp else 0.dp)
    val baseColor = if (floating) MiuixTheme.colorScheme.surfaceContainer else MiuixTheme.colorScheme.surface
    val effect = if (blur) Modifier.textureBlur(backdrop = backdrop, shape = shape,
        blurRadius = 25f, colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(baseColor.copy(alpha = .65f))))) else Modifier
    if (floating) {
        FloatingNavigationBar(modifier = effect, color = if (blur) Color.Transparent else baseColor) {
            items.forEach { (key, icon, title) -> FloatingNavigationBarItem(page == key, { onPage(key) }, icon, uiText(title)) }
        }
    } else {
        NavigationBar(modifier = effect, color = if (blur) Color.Transparent else baseColor) {
            items.forEach { (key, icon, title) -> NavigationBarItem(page == key, { onPage(key) }, icon, uiText(title)) }
        }
    }
}

/** Small native vectors keep navigation independent of the optional extended-icon artifact. */
private object RootIcons {
    val home by lazy { icon("Home") {
        moveTo(3.5f, 10.5f); lineTo(12f, 3.5f); lineTo(20.5f, 10.5f)
        moveTo(5.5f, 9f); lineTo(5.5f, 20f); lineTo(9.5f, 20f)
        lineTo(9.5f, 14f); lineTo(14.5f, 14f); lineTo(14.5f, 20f)
        lineTo(18.5f, 20f); lineTo(18.5f, 9f)
    } }
    val settings by lazy { icon("Settings") {
        moveTo(3f, 6f); lineTo(8f, 6f); moveTo(12f, 6f); lineTo(21f, 6f)
        moveTo(3f, 12f); lineTo(14f, 12f); moveTo(18f, 12f); lineTo(21f, 12f)
        moveTo(3f, 18f); lineTo(6f, 18f); moveTo(10f, 18f); lineTo(21f, 18f)
        ring(10f, 6f, 2f); ring(16f, 12f, 2f); ring(8f, 18f, 2f)
    } }
    val info by lazy { icon("About") {
        ring(12f, 12f, 8.5f)
        moveTo(12f, 10.5f); lineTo(12f, 16f)
        moveTo(12f, 7.4f); lineTo(12f, 7.6f)
    } }

    private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { draw() }
        }.build()

    private fun PathBuilder.ring(x: Float, y: Float, radius: Float) {
        val control = radius * .552285f
        moveTo(x + radius, y)
        curveTo(x + radius, y + control, x + control, y + radius, x, y + radius)
        curveTo(x - control, y + radius, x - radius, y + control, x - radius, y)
        curveTo(x - radius, y - control, x - control, y - radius, x, y - radius)
        curveTo(x + control, y - radius, x + radius, y - control, x + radius, y)
        close()
    }
}

@Composable
internal fun SettingsPage(
    language: String, onLanguage: (String) -> Unit, onTheme: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit, bottomPadding: Dp, modifier: Modifier,
) {
    val languageNames = listOf(uiText("跟随系统"), uiText("简体中文"), uiText("English"))
    val languages = listOf("system", "zh-CN", "en")
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding + 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("界面主题"), insideMargin = compactPreferenceMargin, onClick = onTheme)
            WindowDropdownPreference(title = uiText("界面语言"), items = languageNames,
                selectedIndex = languages.indexOf(language).coerceAtLeast(0), insideMargin = compactPreferenceMargin,
                onSelectedIndexChange = { onLanguage(languages[it]) })
        }
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("导出全部设置"), summary = uiText("保存所有参数、开关和应用界面设置为 JSON 文件"), insideMargin = compactPreferenceMargin, onClick = onExport)
            ArrowPreference(uiText("从文件恢复设置"), summary = uiText("用文件中的设置覆盖对应项目"), insideMargin = compactPreferenceMargin, onClick = onImport)
            ArrowPreference(uiText("恢复全部默认"), summary = uiText("所有功能开关与参数恢复默认值"), insideMargin = compactPreferenceMargin, onClick = onReset)
        }
    }
}
