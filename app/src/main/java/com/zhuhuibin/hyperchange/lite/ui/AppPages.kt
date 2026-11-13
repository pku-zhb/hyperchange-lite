// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun RootNavigation(page: String, floating: Boolean, blur: Boolean, backdrop: LayerBackdrop, onPage: (String) -> Unit) {
    val items = listOf(Triple("home", RootIcons.home, "主页"), Triple("settings", RootIcons.settings, "设置"), Triple("about", RootIcons.info, "关于"))
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
    onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit, modifier: Modifier,
) {
    val languageNames = listOf(uiText("跟随系统"), uiText("简体中文"), uiText("English"))
    val languages = listOf("system", "zh-CN", "en")
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
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

@Composable
internal fun AboutPage(onCredits: () -> Unit, onLicenses: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(com.zhuhuibin.hyperchange.lite.R.drawable.hc_launcher_foreground), null, Modifier.size(88.dp))
            Text("hc-lite", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(version, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Card(Modifier.fillMaxWidth()) {
            DonationEntry()
            ArrowPreference(uiText("源代码"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite") })
            ArrowPreference(uiText("问题反馈"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite/issues") })
            ArrowPreference(uiText("致谢"), insideMargin = compactPreferenceMargin, onClick = onCredits)
            ArrowPreference(uiText("开源许可"), insideMargin = compactPreferenceMargin, onClick = onLicenses)
        }
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(title = "hmc-duo", summary = uiText("友情链接"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hmc-duo") })
        }
    }
}
