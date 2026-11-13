// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

/** Functional OEM glass inputs, verified against SystemUI SHA 074486d6 (see design/BANNER_MATERIAL.md). */
internal fun headsUpPreviewRecipe(night: Boolean): FloatArray = if (night) floatArrayOf(
    .8f, 1f, 0f, 1f, .2f, 2f, .14f, .1f, 0f, 0f, .02f, .27f, .27f, .27f, .6f, 0f,
    .2f, 1.2f, 1f, 72f, 3.8f, 80f, 600f, 1f, .8f, -.4f, .6f, -.8f, 1.5f, 1f, .8f, 1.15f,
    3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
) else floatArrayOf(
    .5f, 1f, 0f, .8f, .5f, 1.2f, 0f, .2f, 0f, 0f, .03f, 1f, 1f, 1f, 1.5f, 0f,
    .6f, .6f, 1f, 62f, 3.8f, 80f, 600f, 1f, .8f, -.4f, .6f, -.8f, 1.2f, .6f, .8f, 1.15f,
    3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
)
