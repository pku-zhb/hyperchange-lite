// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger normal notification recipe and focus/media enforcement.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.reflect.Method
import java.util.WeakHashMap

internal class NotificationMaterial(private val runtime: HookRuntime, private val selected: () -> Boolean) {
    private data class Recipe(val configuration: Int, val values: FloatArray)
    private val recipes = WeakHashMap<Resources, Recipe>()
    private val applying = ThreadLocal<Boolean>()
    private val effectRoots = WeakHashMap<View, Pair<Any?, Method>>()
    private val effectPrefix = "com.android.systemui.statusbar.notification.style.vieweffect."
    private val installedLoaders = WeakHashMap<ClassLoader, Boolean>()
    private var firstFailureLogged = false

    fun shouldUnify(view: View): Boolean = selected() && !GlassSurfaces.excluded(view) && !GlassSurfaces.headsUp(view) &&
        (GlassSurfaces.media(view) || GlassSurfaces.focus(view))

    fun normalRecipe(view: View): FloatArray? {
        val resource = runCatching {
            val local = view.resources
            if (local.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui") != 0) local
            else view.context.createPackageContext("com.android.systemui", 0).resources
        }.getOrNull() ?: return null
        val configuration = resource.configuration.hashCode()
        synchronized(recipes) { recipes[resource]?.takeIf { it.configuration == configuration }?.let { return it.values.copyOf() } }
        val value = runCatching {
            val id = resource.getIdentifier("notification_glass_params_normal", "array", "com.android.systemui")
            if (id == 0) return@runCatching null
            resource.getStringArray(id).map { it.toFloatOrNull() ?: return@runCatching null }.toFloatArray().takeIf(::hasSoftGlassRecipe)
        }.getOrNull() ?: return null
        synchronized(recipes) { recipes[resource] = Recipe(configuration, value.copyOf()) }
        return value
    }

    fun normalBlends(view: View): ArrayList<Point>? {
        if (!shouldUnify(view)) return null
        return runCatching {
            val resources = view.context.createPackageContext("com.android.systemui", 0).resources
            ArrayList((1..2).map { index ->
                val color = resources.getIdentifier("notification_element_blend_shade_color_$index", "color", "com.android.systemui")
                val mode = resources.getIdentifier("notification_element_blend_shade_mode_$index", "integer", "com.android.systemui")
                check(color != 0 && mode != 0)
                Point(resources.getColor(color, null), resources.getInteger(mode))
            })
        }.getOrNull()
    }

    fun installEffects(loader: ClassLoader) {
        if (installedLoaders.put(loader, true) != null) return
        val focus = listOf("FocusNotificationNormalEffect", "FocusNotificationNormalCustomBgEffect", "FocusNotificationBlurEffect", "FocusNotificationBlurOnKeyguardEffect", "FocusNotificationGlassEffect", "FocusNotificationGlassCustomBgEffect", "FocusNotificationGlassOnKeyguardEffect", "FocusNotificationGlassOnKeyguardLightWallPaperEffect", "FocusNotificationGlassFullAodEffect")
        val media = listOf("MediaViewNormalEffect", "MediaViewBlurEffect", "MediaViewBlurOnKeyguardEffect", "MediaViewGlassEffect", "MediaViewGlassOnKeyguardEffect", "MediaViewGlassOnKeyguardLightWallPaperEffect", "MediaViewGlassFullAodEffect")
        var found = 0
        (focus + media).forEach { name ->
            val type = runCatching { loader.loadClass(effectPrefix + name) }.getOrNull() ?: return@forEach
            val method = type.declaredMethods.firstOrNull { it.name == "apply" && it.parameterCount == 2 && Context::class.java.isAssignableFrom(it.parameterTypes[1]) } ?: return@forEach
            runtime.hook(method, "lite-glass:effect:$name") { chain ->
                val result = chain.proceed()
                if (applying.get() == true) return@hook result
                val root = chain.getArg(0) as? View ?: return@hook result
                synchronized(effectRoots) { effectRoots[root] = chain.thisObject to method }
                if (shouldUnify(root)) {
                    applyNormal(root, loader, name in media)
                    root.post { if (root.isAttachedToWindow && shouldUnify(root)) applyNormal(root, loader, name in media) }
                }
                result
            }
            found++
        }
        val injector = runCatching { loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector") }.getOrNull()
        injector?.declaredMethods?.filter { it.name == "updateFullAodAnimState" }?.forEachIndexed { index, method ->
            runtime.hook(method, "lite-glass:full-aod-$index") { chain ->
                val result = chain.proceed()
                val view = field(chain.thisObject, "view") as? View
                if (view != null && shouldUnify(view)) applyNormal(view, loader, false)
                result
            }
        }
        runtime.log("Native notification material effects installed: $found")
    }

    /** Replay the last OEM effect on both enable and disable, then the setter engine reapplies tuning. */
    fun refresh() {
        val roots = synchronized(effectRoots) { effectRoots.toMap() }
        roots.forEach { (view, call) ->
            if (view.isAttachedToWindow) view.post {
                runCatching { call.second.invoke(call.first, view, view.context) }.onFailure { logOnce("Could not refresh notification material", it) }
            }
        }
    }

    private fun applyNormal(view: View, loader: ClassLoader, media: Boolean) {
        if (applying.get() == true || GlassSurfaces.headsUp(view)) return
        applying.set(true)
        try {
            val name = if (media) "MediaViewGlassEffect" else "NotificationRowGlassEffect"
            val type = loader.loadClass(effectPrefix + name)
            val singleton = type.declaredFields.firstOrNull { it.name == "INSTANCE" }?.apply { isAccessible = true }?.get(null) ?: return
            val apply = type.methods.firstOrNull { it.name == "apply" && it.parameterCount == 2 } ?: return
            apply.invoke(singleton, view, view.context)
        } catch (error: Throwable) { logOnce("Could not apply normal notification material", error) }
        finally { applying.remove() }
    }

    private fun logOnce(message: String, error: Throwable) {
        if (!firstFailureLogged) { firstFailureLogged = true; runtime.log(message, error) }
    }

    private fun field(instance: Any?, name: String): Any? {
        var type = instance?.javaClass
        while (type != null) {
            val current = type
            runCatching { current.getDeclaredField(name).apply { isAccessible = true }.get(instance) }.getOrNull()?.let { return it }
            type = type.superclass
        }
        return null
    }
}
