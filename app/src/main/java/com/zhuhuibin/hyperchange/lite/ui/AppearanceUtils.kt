// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun appIsDarkTheme(): Boolean = MiuixTheme.colorScheme.surface.luminance() < .5f
