// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger SidebarHooks, Copyright 2026 btm_m.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Exact SecurityCenter card owners; unrelated toast/game blur helpers remain unchanged. */
internal class SidebarGlass(private val runtime: HookRuntime) {
    @Volatile private var state = GlassRuntimeSettings.read(runtime.preferences)
    private var dockType: Class<*>? = null
    private val cards = WeakHashMap<View, () -> Unit>()
    private val engine = ViewMaterialEngine(runtime, ::isSurface, {
        state.glass.copy(enabled = state.sidebar, backgroundBlur = 100, backgroundScale = 100, backgroundOpacity = 100)
    })
    private var materialFailureLogged = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("glass_")) {
            state = GlassRuntimeSettings.read(runtime.preferences)
            val owners = synchronized(cards) { cards.toList() }
            owners.forEach { (view, replay) -> view.post { if (view.isAttachedToWindow) runCatching(replay) } }
            engine.scheduleRefresh()
        }
    }

    fun install() {
        dockType = runCatching {
            runtime.loader.loadClass("com.miui.gamebooster.windowmanager.newbox.TurboLayout").getDeclaredMethod("getDockLayout").returnType
        }.onFailure { runtime.log("Sidebar DockLayout signature unavailable", it) }.getOrNull()
        runtime.preferences.registerOnSharedPreferenceChangeListener(listener)
        engine.install()
        installToolCard("gb.p", "t", "b")
        installToolCard("s8.i", "E", "d")
        runCatching {
            val method = runtime.loader.loadClass("com.miui.dock.allapps.f0").getDeclaredMethod("c", ViewGroup::class.java, Context::class.java)
            runtime.hook(method, "lite-glass:sidebar-allapps") { chain ->
                val view = chain.getArg(0) as? View
                val context = chain.getArg(1) as? Context
                val owner = WeakReference(chain.thisObject)
                if (view != null && context != null) {
                    val weak = WeakReference(view)
                    val weakContext = WeakReference(context)
                    synchronized(cards) { cards[view] = {
                        val currentContext = weakContext.get()
                        if (currentContext != null) weak.get()?.let { method.invoke(owner.get(), it, currentContext) }
                        Unit
                    } }
                }
                val result = chain.proceed()
                if (view != null && state.unifySidebar) applyNativeGlass(view)
                result
            }
        }.onFailure { runtime.log("Sidebar all-apps adapter unavailable", it) }
    }

    private fun isSurface(view: View): Boolean = dockType?.isInstance(view) == true || synchronized(cards) { cards.containsKey(view) }

    private fun installToolCard(name: String, methodName: String, fieldName: String) {
        runCatching {
            val type = runtime.loader.loadClass(name)
            val field = type.getDeclaredField(fieldName).apply { isAccessible = true }
            val method = type.getDeclaredMethod(methodName)
            runtime.hook(method, "lite-glass:sidebar-$name") { chain ->
                val result = chain.proceed()
                runCatching {
                    val view = field.get(chain.thisObject) as? View
                    if (view != null) {
                        val owner = WeakReference(chain.thisObject)
                        synchronized(cards) { cards[view] = { owner.get()?.let { method.invoke(it) }; Unit } }
                        if (state.unifySidebar) applyNativeGlass(view)
                    }
                }.onFailure { logMaterialFailure("Could not apply sidebar card material", it) }
                result
            }
        }.onFailure { runtime.log("Sidebar adapter unavailable: $name.$methodName", it) }
    }

    private fun applyNativeGlass(view: View) {
        runCatching {
            val loader = runtime.loader
            val token = loader.loadClass("com.miui.gamebooster.utils.q").getField("a").get(null) ?: return
            val builderType = loader.loadClass("miuix.theme.token.MaterialToken\$b")
            val builder = builderType.getConstructor(Int::class.javaPrimitiveType).newInstance(40)
            builderType.getDeclaredMethod("i", token.javaClass).invoke(builder, token)
            val material = builderType.getDeclaredMethod("a").invoke(builder) ?: return
            val adapterType = loader.loadClass("vq.j")
            val adapter = adapterType.getDeclaredMethod("a", Parcelable::class.java).invoke(null, material) ?: return
            val glass = adapterType.getDeclaredMethod("e").invoke(adapter) ?: return
            val recipe = glass.javaClass.getField("a").get(glass) as? FloatArray ?: return
            if (!hasSoftGlassRecipe(recipe)) return
            val apply = loader.loadClass("vq.g").getDeclaredMethod("c", View::class.java, glass.javaClass)
            val clear = View::class.java.getMethod("clearMiBackgroundBlendColor")
            apply.invoke(null, view, glass)
            clear.invoke(view)
            val transparent = view.resources.getIdentifier("transparent", "color", "com.miui.securitycenter")
            if (transparent != 0) view.setBackgroundResource(transparent)
        }.onFailure { logMaterialFailure("Sidebar native material token unavailable", it) }
    }

    private fun logMaterialFailure(message: String, error: Throwable) {
        if (!materialFailureLogged) { materialFailureLogged = true; runtime.log(message, error) }
    }
}
