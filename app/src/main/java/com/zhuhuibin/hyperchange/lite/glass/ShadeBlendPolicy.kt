// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

/**
 * The OEM shade array includes structural color transforms as well as a dim color.
 * HyperLight's retained shade recipe preserves mode 21's neutral transform and only
 * replaces mode 106's tint; the native window self-blur path uses mode 9's dim.
 * Scaling every entry's alpha destroys that distinction.
 */
internal fun shadeBlendColor(color: Int, mode: Int, opacityPercent: Int): Int =
    if (mode == 106 || mode == 9) softGlassColorAlpha(color, opacityPercent) else color
