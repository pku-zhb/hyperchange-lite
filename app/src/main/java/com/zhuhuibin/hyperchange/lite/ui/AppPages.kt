// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

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
internal fun RootNavigation(page: String, onPage: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(page == "home", { onPage("home") }, RootIcons.home, uiText("主页"))
        NavigationBarItem(page == "settings", { onPage("settings") }, RootIcons.settings, uiText("设置"))
        NavigationBarItem(page == "about", { onPage("about") }, RootIcons.info, uiText("关于"))
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
    themeMode: Int, language: String, onThemeMode: (Int) -> Unit, onLanguage: (String) -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit, modifier: Modifier,
) {
    val themeNames = listOf(uiText("跟随系统"), uiText("浅色"), uiText("深色"))
    val languageNames = listOf(uiText("跟随系统"), uiText("简体中文"), uiText("English"))
    val languages = listOf("system", "zh-CN", "en")
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            WindowDropdownPreference(title = uiText("主题"), items = themeNames,
                selectedIndex = themeMode.coerceIn(0, 2), insideMargin = compactPreferenceMargin,
                onSelectedIndexChange = onThemeMode)
            WindowDropdownPreference(title = uiText("语言"), items = languageNames,
                selectedIndex = languages.indexOf(language).coerceAtLeast(0), insideMargin = compactPreferenceMargin,
                onSelectedIndexChange = { onLanguage(languages[it]) })
        }
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("导出配置"), insideMargin = compactPreferenceMargin, onClick = onExport)
            ArrowPreference(uiText("导入配置"), insideMargin = compactPreferenceMargin, onClick = onImport)
            ArrowPreference(uiText("恢复默认"), insideMargin = compactPreferenceMargin, onClick = onReset)
        }
    }
}

@Composable
internal fun AboutPage(modifier: Modifier) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(title = "hc-lite", insideMargin = compactPreferenceMargin, endActions = { Text(version) })
        }
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(uiText("源代码"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite") })
            ArrowPreference(uiText("问题反馈"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite/issues") })
            ArrowPreference(uiText("上游项目"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/ColdP/HyperChanger") })
            ArrowPreference(uiText("开源许可"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite/blob/main/LICENSE") })
        }
    }
}
