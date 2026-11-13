// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.view.View

internal fun readField(target: Any?, name: String): Any? {
    target ?: return null
    var type: Class<*>? = target.javaClass
    while (type != null) {
        val owner = type
        runCatching { owner.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            .onSuccess { return it }
        type = owner.superclass
    }
    return null
}

internal fun viewFor(target: Any?): View? {
    if (target is View) return target
    for (name in listOf("itemView", "view", "mView", "slider", "sliderView", "contentBg")) {
        (readField(target, name) as? View)?.let { return it }
    }
    for (name in listOf("getView", "getContentBg", "getSliderView", "getSecondaryPanelContainer")) {
        runCatching { target?.javaClass?.getMethod(name)?.invoke(target) as? View }.getOrNull()?.let { return it }
    }
    sliderParts(target).firstOrNull()?.let { return it }
    return null
}

/** Outline and fill have independent native radii; neither can stand in for the other. */
internal fun sliderRadiusParts(target: Any?, progress: Boolean): List<View> {
    target ?: return emptyList()
    val binding = readField(target, "binding")
    val fromBinding = readField(binding, if (progress) "progress" else "progressBg") as? View
    val getter = if (progress) "getVProgress" else "getVProgressBg"
    val fromGetter = runCatching { target.javaClass.getMethod(getter).invoke(target) as? View }.getOrNull()
    return listOfNotNull(fromBinding, fromGetter).distinct()
}

internal fun sliderParts(target: Any?): List<View> {
    target ?: return emptyList()
    val binding = readField(target, "binding")
    return buildList {
        for (name in listOf("progressBg", "progress", "toggleSliderInner")) (readField(binding, name) as? View)?.let(::add)
        for (name in listOf("getVProgressBg", "getVProgress", "getVToggleSliderInner", "getVToggleSlider")) {
            runCatching { target.javaClass.getMethod(name).invoke(target) as? View }.getOrNull()?.let(::add)
        }
    }.distinct()
}

internal fun View.idName(): String? = runCatching { resources.getResourceEntryName(id) }.getOrNull()
