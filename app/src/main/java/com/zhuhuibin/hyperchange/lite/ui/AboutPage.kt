// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuhuibin.hyperchange.lite.R
import com.zhuhuibin.hyperchange.lite.ui.component.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The header scrolls away; the list keeps its full viewport behind the floating bar. */
@Composable
internal fun AboutPage(
    onCredits: () -> Unit, onLicenses: () -> Unit, blur: Boolean,
    bottomPadding: Dp, modifier: Modifier,
) {
    val links = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
    val list = rememberLazyListState()
    val headerHeight = 260.dp
    val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
    val progress by remember(list, headerPx) { derivedStateOf {
        if (list.firstVisibleItemIndex > 0) 1f
        else (list.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f)
    } }
    val background = rememberLayerBackdrop()
    val surface = MiuixTheme.colorScheme.surface
    val cardSurface = MiuixTheme.colorScheme.surfaceContainer
    val titleBrush = remember { Brush.linearGradient(listOf(Color(0xFFDB5A00), Color(0xFFD64179), Color(0xFF526CDC))) }

    Scaffold(
        modifier = modifier,
        popupHost = {},
        topBar = { SmallTopAppBar(
            title = uiText("关于"),
            color = surface.copy(alpha = progress),
            titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = progress),
        ) },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { insets ->
        BgEffectBackground(
            dynamicBackground = progress < 1f,
            isFullSize = true,
            modifier = Modifier.fillMaxSize(),
            bgModifier = if (blur) Modifier.layerBackdrop(background) else Modifier,
            alpha = { 1f - progress },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                overscrollEffect = null,
                contentPadding = PaddingValues(top = insets.calculateTopPadding(), bottom = bottomPadding + 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") { Spacer(Modifier.fillMaxWidth().height(headerHeight)) }
                item(key = "links") {
                    val glass = if (blur) Modifier.textureBlur(
                        backdrop = background, shape = RoundedCornerShape(16.dp), blurRadius = 60f,
                        colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(cardSurface.copy(alpha = .55f)))),
                    ) else Modifier
                    Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth().then(glass),
                        colors = CardDefaults.defaultColors(if (blur) Color.Transparent else cardSurface, Color.Transparent)) {
                        DonationEntry()
                        ArrowPreference(uiText("源代码"), insideMargin = compactPreferenceMargin,
                            onClick = { links.openUri("https://github.com/pku-zhb/hc-lite") })
                        ArrowPreference(uiText("问题反馈"), insideMargin = compactPreferenceMargin,
                            onClick = { links.openUri("https://github.com/pku-zhb/hc-lite/issues") })
                        ArrowPreference(uiText("致谢"), insideMargin = compactPreferenceMargin, onClick = onCredits)
                        ArrowPreference(uiText("开源许可"), insideMargin = compactPreferenceMargin, onClick = onLicenses)
                        ArrowPreference(title = "hmc-duo", summary = uiText("友情链接"), insideMargin = compactPreferenceMargin,
                            onClick = { links.openUri("https://github.com/pku-zhb/hmc-duo") })
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding() + 30.dp)
                    .graphicsLayer { translationY = -24.dp.toPx() * progress },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painterResource(R.drawable.hc_launcher_artwork), null,
                    Modifier.size(100.dp).graphicsLayer {
                        val fade = ((progress - .35f) / .25f).coerceIn(0f, 1f)
                        alpha = 1f - fade
                        scaleX = 1f - .05f * fade; scaleY = scaleX
                    }.squircleClip(28.dp),
                )
                BasicText("hc-lite", Modifier.fillMaxWidth().padding(top = 16.dp).graphicsLayer {
                    alpha = 1f - ((progress - .2f) / .3f).coerceIn(0f, 1f)
                }, style = TextStyle(brush = titleBrush, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                Text(version, Modifier.padding(top = 8.dp).graphicsLayer {
                    alpha = 1f - ((progress - .05f) / .3f).coerceIn(0f, 1f)
                }, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
            }
        }
    }
}
