// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small local vectors keep the preview toolbar independent of optional icon packages. */
internal object PreviewIcons {
    val palette by lazy { icon("PreviewBackground") {
        moveTo(12f, 3f)
        curveTo(7f, 3f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        curveTo(15f, 21f, 13f, 17f, 15f, 16f)
        curveTo(17f, 15f, 21f, 17f, 21f, 12f)
        curveTo(21f, 7f, 17f, 3f, 12f, 3f); close()
        moveTo(7f, 10f); lineTo(7.2f, 10f)
        moveTo(10f, 7f); lineTo(10.2f, 7f)
        moveTo(15f, 7f); lineTo(15.2f, 7f)
        moveTo(17f, 11f); lineTo(17.2f, 11f)
    } }
    val image by lazy { icon("PreviewImage") {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(4f, 16f); lineTo(10f, 10f); lineTo(15f, 16f)
        moveTo(13f, 14f); lineTo(16f, 11f); lineTo(20f, 15f)
        moveTo(15.5f, 7.5f); lineTo(15.7f, 7.5f)
    } }
    private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { draw() }
        }.build()
}
