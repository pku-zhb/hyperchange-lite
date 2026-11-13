// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun ThemePage(mode: Int, onMode: (Int) -> Unit, floating: Boolean, onFloating: (Boolean) -> Unit,
    blur: Boolean, onBlur: (Boolean) -> Unit, modifier: Modifier) {
    val names = listOf("跟随系统", "浅色", "深色", "壁纸取色 · 跟随系统", "壁纸取色 · 浅色", "壁纸取色 · 深色").map { uiText(it) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            WindowDropdownPreference(title = uiText("主题模式"), summary = names[mode.coerceIn(0, 5)],
                items = names, selectedIndex = mode.coerceIn(0, 5), insideMargin = compactPreferenceMargin, onSelectedIndexChange = onMode)
            SwitchPreference(title = uiText("浮动导航栏"), checked = floating, insideMargin = compactPreferenceMargin, onCheckedChange = onFloating)
            SwitchPreference(title = uiText("应用内模糊"), checked = blur, insideMargin = compactPreferenceMargin, onCheckedChange = onBlur)
        }
    }
}

@Composable
internal fun CreditsPage(modifier: Modifier) {
    val links = LocalUriHandler.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference("HyperChanger", summary = uiText("项目上游；沿用其柔光材质、预设与部分系统适配"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/ColdP/HyperChanger") })
            BasicComponent(title = "HyperLight", summary = uiText("通知与音量交互的行为参考；未获源码授权，不分发其反编译源码或资源"), insideMargin = compactPreferenceMargin)
            ArrowPreference("Miuix", summary = uiText("设置界面与主题组件"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/compose-miuix-ui/miuix") })
            ArrowPreference("LSPosed / libxposed", summary = uiText("模块框架、进程通信与隐藏接口适配"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/libxposed") })
            ArrowPreference("AndroidX / Jetpack Compose / Kotlin", summary = uiText("应用运行库与声明式界面"), insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://developer.android.com/jetpack/compose") })
        }
    }
}

@Composable
internal fun LicensePage(modifier: Modifier) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var notices by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        notices = withContext(Dispatchers.IO) { runCatching {
            context.assets.open("licenses/NOTICE").bufferedReader().use { it.readText() } + "\n\n" +
                context.assets.open("licenses/AGPL-3.0.txt").bufferedReader().use { it.readText() } + "\n\n" +
                context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use { it.readText() }
        }.getOrNull() }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference("hc-lite · AGPL-3.0-or-later", insideMargin = compactPreferenceMargin,
                onClick = { links.openUri("https://github.com/pku-zhb/hc-lite/blob/main/LICENSE") })
            BasicComponent(summary = uiText("第三方组件保留各自版权与许可；以下为随应用提供的完整许可文本。"), insideMargin = compactPreferenceMargin)
        }
        Text(notices ?: uiText("正在加载许可文本"), Modifier.padding(8.dp))
    }
}
