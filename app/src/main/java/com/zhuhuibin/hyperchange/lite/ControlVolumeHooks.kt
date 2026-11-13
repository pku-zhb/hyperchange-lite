// SPDX-License-Identifier: AGPL-3.0-or-later
// Selected targets and long-press behavior adapted from HyperChanger (Copyright 2026 btm_m).
package com.zhuhuibin.hyperchange.lite

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import com.zhuhuibin.hyperchange.lite.controls.VolumeAnimator
import com.zhuhuibin.hyperchange.lite.controls.VolumePanelLongPress
import com.zhuhuibin.hyperchange.lite.controls.VolumePanelAppearance
import com.zhuhuibin.hyperchange.lite.controls.VolumeButtonVisibility
import java.lang.reflect.Method
import java.util.WeakHashMap

class ControlVolumeHooks {
    private val main = Handler(Looper.getMainLooper())
    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val radiusCalls = WeakHashMap<Any, MutableMap<Method, RadiusBaseline>>()
    private val outlines = WeakHashMap<View, Pair<ViewOutlineProvider?, Boolean>>()
    private val deviceViews = WeakHashMap<View, Boolean>()
    private val ringerVisibility = WeakHashMap<View, VolumeButtonVisibility>()
    private val columns = WeakHashMap<Any, VolumeAnimator>()
    private val panels = WeakHashMap<View, Boolean>()
    private val refreshing = ThreadLocal<Boolean>()
    private val radiusDispatch = ThreadLocal<String>()

    fun install(runtime: HookRuntime) {
        val p = runtime.preferences
        preferences = p
        if (listener == null) {
            listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> main.post { refresh() } }
            p.registerOnSharedPreferenceChangeListener(listener)
        }
        installDimensions(runtime)
        val targets = listOf(
            Triple("miui.systemui.controlcenter.qs.tileview.QSCardItemView", "setCornerRadius", "cc_card_radius"),
            Triple("miui.systemui.controlcenter.panel.main.media.MediaPlayerPanel", "setCornerRadius", "cc_media_radius"),
            Triple("miui.systemui.controlcenter.panel.main.media.MediaPlayerController\$MediaPlayerViewHolder", "setCornerRadius", "cc_media_radius"),
            Triple("miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder", "setOutlineRadius", "cc_slider_radius"),
            Triple("miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelSliderDelegate", "setOutlineRadius", "cc_slider_radius"),
            Triple("miui.systemui.controlcenter.panel.secondary.volume.VolumePanelController", "getCornerRadius", "cc_slider_radius"),
            Triple("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", "setCornerRadius", "cc_tile_radius"),
            Triple("miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase", "setContentBgRadius", "cc_secondary_radius"),
        )
        var radiusTargets = 0
        targets.forEach { (name, methodName, key) ->
            runtime.type(name)?.declaredMethods?.filter {
                it.name == methodName && ((it.parameterCount == 1 && it.parameterTypes[0] == Float::class.javaPrimitiveType) ||
                    (it.parameterCount == 0 && it.returnType == Float::class.javaPrimitiveType))
            }?.forEach { method ->
                radiusTargets++
                runtime.hook(method, "radius:$key:${method.toGenericString()}") { chain ->
                    // A panel setter delegates to its holder. Only the outer owner may
                    // tune/capture a category, or the child would save our output as OEM.
                    if (radiusDispatch.get() == key) return@hook chain.proceed()
                    val receiver = chain.thisObject ?: return@hook chain.proceed()
                    val view = viewFor(receiver) ?: return@hook chain.proceed()
                    val active = p.getBoolean("cc_radius_enabled", false)
                    if (method.parameterCount == 0) {
                        val original = chain.proceed()
                        if (active) number(key, 26f) * view.resources.displayMetrics.density else original
                    } else {
                        val incoming = chain.getArg(0) as Float
                        val baseline = radiusCalls.getOrPut(receiver) { mutableMapOf() }
                            .getOrPut(method) { RadiusBaseline(incoming) }
                        val args = chain.args.toTypedArray()
                        args[0] = baseline.resolve(incoming,
                            if (active) number(key, 26f) * view.resources.displayMetrics.density else null,
                            refreshing.get() != true)
                        val previous = radiusDispatch.get()
                        radiusDispatch.set(key)
                        try {
                            val result = chain.proceed(args)
                            if (key in setOf("cc_card_radius", "cc_media_radius")) applyOutline(view, if (active) args[0] as Float else null)
                            if (key in setOf("cc_media_radius", "cc_secondary_radius")) {
                                (view.background as? GradientDrawable)?.mutate()?.let { (it as GradientDrawable).cornerRadius = args[0] as Float }
                            }
                            if (key == "cc_slider_radius") {
                                sliderParts(receiver).forEach { part ->
                                    (part.background as? GradientDrawable)?.mutate()?.let { (it as GradientDrawable).cornerRadius = args[0] as Float }
                                }
                            }
                            result
                        } finally {
                            if (previous == null) radiusDispatch.remove() else radiusDispatch.set(previous)
                        }
                    }
                }
            }
        }
        installDeviceOutline(runtime)
        installVolumeRadius(runtime, "VolumeColumnRes", "volume_corner_enabled", "volume_corner")
        installVolumeRadius(runtime, "TimerItemRes", "volume_timer_corner_enabled", "volume_timer_corner")
        installVolume(runtime)
        runtime.log("Control/volume radius setters available: $radiusTargets")
    }

    private fun number(key: String, fallback: Float) = preferences?.getFloat(key, fallback) ?: fallback

    private fun installDimensions(runtime: HookRuntime) {
        for (name in listOf("getDimension", "getDimensionPixelSize", "getDimensionPixelOffset")) {
            val method = Resources::class.java.getDeclaredMethod(name, Int::class.javaPrimitiveType)
            runtime.hook(method, "dimensions:$name") { chain ->
                val original = chain.proceed()
                val resources = chain.thisObject as? Resources ?: return@hook original
                val id = chain.getArg(0) as? Int ?: return@hook original
                val owner = runCatching { resources.getResourcePackageName(id) }.getOrNull()
                if (owner != "com.android.systemui" && owner != "miui.systemui.plugin") return@hook original
                val resource = runCatching { resources.getResourceEntryName(id) }.getOrNull()
                val p = runtime.preferences
                val dp = when {
                    resource == "notification_item_bg_radius" && p.getBoolean("notification_radius_enabled", false) -> number("notification_radius", 26f)
                    resource in setOf("miui_volume_column_height", "flip_tiny_miui_volume_column_height") && p.getBoolean("volume_motion_enabled", false) -> number("volume_height", 150f)
                    else -> return@hook original
                }
                val px = dp * resources.displayMetrics.density
                if (name == "getDimension") px else (px + .5f).toInt()
            }
        }
    }

    private fun installVolumeRadius(runtime: HookRuntime, simpleName: String, switch: String, key: String) {
        val type = runtime.type("com.android.systemui.miui.volume.$simpleName") ?: return
        type.declaredMethods.filter {
            it.name == "getRadius" && it.parameterTypes.firstOrNull() == Context::class.java &&
                it.returnType in setOf(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        }.forEach { method ->
            runtime.hook(method, "volume-radius:$simpleName:${method.parameterCount}") { chain ->
                val original = chain.proceed()
                if (!runtime.preferences.getBoolean(switch, false)) return@hook original
                val context = chain.getArg(0) as? Context ?: return@hook original
                val radius = number(key, 40f) * context.resources.displayMetrics.density
                if (method.returnType == Int::class.javaPrimitiveType) (radius + .5f).toInt() else radius
            }
        }
    }

    private fun installDeviceOutline(runtime: HookRuntime) {
        val type = runtime.type("miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryFrameLayout") ?: return
        val method = generateSequence(type) { it.superclass }.flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == "onAttachedToWindow" && it.parameterCount == 0 } ?: return
        runtime.hook(method, "device-center-outline") { chain ->
            val result = chain.proceed()
            val view = chain.thisObject as? View
            if (view != null && type.isInstance(view)) {
                deviceViews[view] = true
                val radius = if (runtime.preferences.getBoolean("cc_radius_enabled", false)) number("cc_device_radius", 26f) * view.resources.displayMetrics.density else null
                applyOutline(view, radius)
            }
            result
        }
    }

    private fun applyOutline(view: View, radius: Float?) {
        if (radius == null) {
            outlines.remove(view)?.let { (provider, clipped) -> view.outlineProvider = provider; view.clipToOutline = clipped }
        } else {
            outlines.getOrPut(view) { view.outlineProvider to view.clipToOutline }
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) { outline.setRoundRect(0, 0, v.width, v.height, radius) }
            }
            view.clipToOutline = true
        }
        view.invalidateOutline()
    }

    private fun panelFor(view: View): View? {
        var parent = view.parent
        while (parent is View) {
            if (parent.javaClass.name.endsWith("MiuiVolumeDialogView")) return parent
            parent = parent.parent
        }
        return null
    }

    private val longPress = VolumePanelLongPress(
        canExpand = { slider ->
            val panel = panelFor(slider)
            preferences?.getBoolean("volume_long_press", false) == true && panel != null &&
                readField(panel, "isControlCenterPanel") == false && readField(panel, "expandListener") is View.OnClickListener &&
                runCatching { panel.javaClass.getMethod("isExpanded").invoke(panel) == false }.getOrDefault(false)
        },
        expand = { slider -> (readField(panelFor(slider), "expandListener") as? View.OnClickListener)?.onClick(slider) },
    )

    private fun installVolume(runtime: HookRuntime) {
        val prefix = "com.android.systemui.miui.volume."
        runtime.type(prefix + "MiuiVolumeDialogRes")?.declaredMethods?.filter { method ->
            method.returnType == Int::class.javaPrimitiveType && when (method.name) {
                "getMarginRight" -> method.parameterTypes.contentEquals(arrayOf(Context::class.java,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
                "getMarginTop" -> method.parameterTypes.contentEquals(arrayOf(Context::class.java,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
                else -> false
            }
        }?.forEach { method ->
            runtime.hook(method, "volume-margin:${method.name}") { chain ->
                val original = chain.proceed()
                if (!runtime.preferences.getBoolean("volume_motion_enabled", false)) original
                else {
                    // HyperLight offsets are integer physical pixels applied to OEM margins.
                    val key = if (method.name == "getMarginRight") "volume_offset_x" else "volume_offset_y"
                    (original as Int) + number(key, 0f).toInt()
                }
            }
        }
        runtime.type(prefix + "MiuiVolumeDialogView")?.let { type ->
            type.declaredMethods.filter { it.name in setOf("onFinishInflate", "updateFooterVisibility", "onExpandStateUpdated") }.forEach { method ->
                runtime.hook(method, "volume-buttons:${method.name}:${method.parameterCount}") { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { panel ->
                        panels[panel] = true; updatePanel(panel, method.name != "onExpandStateUpdated")
                        if (method.name == "onExpandStateUpdated") panel.post {
                            columns.values.toList().filter { it.belongsToPanel(panel) }.forEach { it.refresh() }
                        }
                    }
                    result
                }
            }
        }
        runtime.type(prefix + "VolumeColumn")?.let { type ->
            type.declaredMethods.filter { it.name == "initColumn" }.forEach { method ->
                runtime.hook(method, "volume-column:${method.parameterCount}") { chain ->
                    // Release old ownership before OEM applies a new folded/rotated layout.
                    columns.remove(chain.thisObject)?.dispose()
                    val result = chain.proceed()
                    chain.thisObject?.let { owner ->
                        columns[owner] = VolumeAnimator(owner, runtime.preferences)
                    }
                    result
                }
            }
            type.declaredMethods.filter { it.name == "release" && it.parameterCount == 0 }.forEach { method ->
                runtime.hook(method, "volume-column-release") { chain ->
                    columns.remove(chain.thisObject)?.dispose()
                    chain.proceed()
                }
            }
        }
        runtime.type(prefix + "MiuiVolumeSeekBar")?.declaredMethods?.filter {
            it.name == "onTouchEvent" && it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.forEach { method ->
            runtime.hook(method, "volume-touch") { chain ->
                val view = chain.thisObject as? View
                val event = chain.getArg(0) as? MotionEvent
                if (view == null || event == null) return@hook chain.proceed()
                columns.values.toList().firstOrNull { it.matches(view) }?.touch(event)
                if (longPress.onTouch(view, event)) true else chain.proceed()
            }
        }
        runtime.type(prefix + "SlideContainerAnim")?.declaredMethods?.filter { it.name == "animKeyDown" }?.forEach { method ->
            runtime.hook(method, "volume-nudge:${method.parameterCount}") { chain ->
                val result = chain.proceed()
                columns.values.toList().firstOrNull { it.matchesSlideAnimation(chain.thisObject) }?.nudge()
                result
            }
        }
        runtime.type(prefix + "MiuiVolumeDialogMotion")?.declaredMethods?.filter {
            it.name in setOf("showVolumePanelAnimation", "dismissVolumePanelAnimation", "expandVolumePanelAnimation", "collapsedVolumePanelAnimation")
        }?.forEach { method ->
            runtime.hook(method, "volume-motion:${method.name}:${method.parameterCount}") { chain ->
                val affected = columns.values.toList().filter { it.belongsToMotion(chain.thisObject) }
                if (method.name in setOf("dismissVolumePanelAnimation", "expandVolumePanelAnimation")) affected.forEach { it.restore() }
                val result = chain.proceed()
                affected.forEach { animator -> when (method.name) {
                    "showVolumePanelAnimation" -> animator.show()
                    "collapsedVolumePanelAnimation" -> main.post { animator.refresh() }
                } }
                result
            }
        }
    }

    private fun updatePanel(panel: View, nativeUpdated: Boolean) {
        val p = preferences ?: return
        val expanded = runCatching { panel.javaClass.getMethod("isExpanded").invoke(panel) as Boolean }.getOrDefault(true)
        val isControlCenter = readField(panel, "isControlCenterPanel") != false
        val hideRinger = VolumePanelAppearance.hideRinger(isControlCenter, expanded,
            p.getBoolean("volume_motion_enabled", false), p.getBoolean("volume_hide_ringer", false))
        val hideExpand = VolumePanelAppearance.hideExpand(isControlCenter, expanded,
            p.getBoolean("volume_long_press", false))
        for ((field, hide) in listOf("mRingerModeLayout" to hideRinger, "mExpandButton" to hideExpand)) {
            val target = readField(panel, field) as? View ?: continue
            val owned = if (hide) ringerVisibility.getOrPut(target) { VolumeButtonVisibility() }
                else ringerVisibility.remove(target)
            owned?.let { target.visibility = it.resolve(target.visibility, hide, nativeUpdated, View.GONE) }
        }
    }

    private fun refresh() {
        if (refreshing.get() == true) return
        refreshing.set(true)
        try {
            radiusCalls.entries.toList().forEach { (target, calls) ->
                calls.toMap().forEach { (method, baseline) -> runCatching { method.invoke(target, baseline.original) } }
            }
            deviceViews.keys.toList().forEach { view ->
                applyOutline(view, if (preferences?.getBoolean("cc_radius_enabled", false) == true) number("cc_device_radius", 26f) * view.resources.displayMetrics.density else null)
            }
            panels.keys.toList().forEach { updatePanel(it, false) }
            columns.values.toList().forEach { it.refresh() }
        } finally { refreshing.remove() }
    }
}
