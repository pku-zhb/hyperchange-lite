// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger WidgetGlassHooks, Copyright 2026 btm_m.
package com.zhuhuibin.hyperchange.lite.glass

import android.content.SharedPreferences
import android.view.View
import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** Keeps the OEM widget sampler and MAML material writer; no replacement renderer. */
internal class AssistantGlass(private val runtime: HookRuntime) {
    @Volatile private var state = GlassRuntimeSettings.read(runtime.preferences)
    private var widgetTypes: List<Class<*>> = emptyList()
    private var layerOwner: Field? = null
    private var layerRoot: Method? = null
    private var rootManager: Method? = null
    private var sampler: Method? = null
    private var registeredTag: Int? = null
    private val samplingTags = WeakHashMap<View, Any?>()
    private val engine = ViewMaterialEngine(runtime, ::isSurface, {
        state.glass.copy(enabled = state.assistant, backgroundBlur = 100, backgroundScale = 100, backgroundOpacity = 100)
    })
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("glass_")) {
            state = GlassRuntimeSettings.read(runtime.preferences)
            engine.scheduleRefresh()
            val views = synchronized(samplingTags) { samplingTags.keys.toList() }
            views.forEach { view -> view.post { if (view.isAttachedToWindow) runCatching { sampler?.invoke(null, view) } } }
        }
    }

    fun install() {
        widgetTypes = listOf("com.miui.personalassistant.core.view.WidgetHostView", "com.miui.personalassistant.core.view.MaMlHostView",
            "com.miui.personalassistant.core.blur.BlurPresentDrawView", "com.miui.personalassistant.service.servicedelivery.ServiceDeliveryWidgetCard").mapNotNull(runtime::type)
        if (widgetTypes.isEmpty()) { runtime.log("Personal Assistant widget classes unavailable; glass hooks skipped"); return }
        runtime.preferences.registerOnSharedPreferenceChangeListener(listener)
        installMamlWriter()
        installSampling()
        engine.install()
    }

    private fun installSampling() {
        runCatching {
            val observer = runtime.loader.loadClass("a6.l")
            val needsBackground = observer.getMethod("needBackgroundBlur")
            val isContainer = observer.getMethod("isBlurContainer")
            val supports = observer.getMethod("supportWidgetBackgroundBlur")
            val maml = runtime.loader.loadClass("com.miui.personalassistant.core.view.MaMlHostView")
            val tag = runtime.loader.loadClass("com.miui.personalassistant.R\$id").getField("pa_tag_widget_container_glass_registered").getInt(null)
            val helper = listOf("o", "l").firstNotNullOfOrNull { name ->
                runCatching {
                    val type = runtime.loader.loadClass("com.miui.personalassistant.homepage.utils.$name")
                    val token = type.getDeclaredMethod("c")
                    val update = type.getDeclaredMethod("d", View::class.java)
                    check(token.returnType.name == "miuix.theme.token.a" && Modifier.isStatic(update.modifiers) && update.returnType == Void.TYPE)
                    update
                }.getOrNull()
            } ?: error("Widget native sampler signature unavailable")
            sampler = helper
            registeredTag = tag
            runtime.hook(helper, "lite-glass:assistant-sampling") { chain ->
                val view = chain.getArg(0) as? View
                if (view != null && widgetTypes.any { it.isInstance(view) } && observer.isInstance(view)) {
                    val eligible = runCatching { needsBackground.invoke(view) == true && isContainer.invoke(view) == false &&
                        (maml.isInstance(view) || supports.invoke(view) == true) }.getOrDefault(false)
                    if (state.assistant && eligible) {
                        synchronized(samplingTags) { if (!samplingTags.containsKey(view)) samplingTags[view] = view.getTag(tag) }
                        view.setTag(tag, true)
                    } else if (!state.assistant) {
                        synchronized(samplingTags) {
                            if (samplingTags.containsKey(view)) { view.setTag(tag, samplingTags.remove(view)) }
                        }
                    }
                }
                chain.proceed()
            }
        }.onFailure { runtime.log("Personal Assistant sampling adapter unavailable", it) }
    }

    private fun installMamlWriter() {
        runCatching {
            val layer = runtime.loader.loadClass("com.miui.maml.elements.LayerScreenElement")
            val view = runtime.loader.loadClass("com.miui.maml.elements.LayerScreenElement\$LayerView")
            layerOwner = view.declaredFields.single { layer.isAssignableFrom(it.type) }.apply { isAccessible = true }
            layerRoot = layer.getMethod("getRoot")
            rootManager = runtime.loader.loadClass("com.miui.maml.ScreenElementRoot").getMethod("getViewManager")
            val writer = runtime.loader.loadClass("com.miui.maml.util.ReflectionHelper").getDeclaredMethod("callObjectMethod", Any::class.java,
                String::class.java, emptyArray<Class<*>>().javaClass, Array<Any>::class.java)
            runtime.hook(writer, "lite-glass:assistant-maml") { chain ->
                val target = chain.getArg(0) as? View ?: return@hook chain.proceed()
                val name = chain.getArg(1) as? String ?: return@hook chain.proceed()
                if (name !in setOf("setMiGlass", "setMiGlassBlurRadius") || !isSurface(target)) return@hook chain.proceed()
                val input = chain.getArg(3) as? Array<*> ?: return@hook chain.proceed()
                val method = when (name) {
                    "setMiGlass" -> {
                        if (input.size != 1 || input[0] !is FloatArray) return@hook chain.proceed()
                        View::class.java.getMethod(name, FloatArray::class.java)
                    }
                    else -> {
                        if (input.size != 2 || input[0] !is Int || input[1] !is Int) return@hook chain.proceed()
                        View::class.java.getMethod(name, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    }
                }
                val output = engine.adjustExternal(target, method, Array<Any?>(input.size) { input[it] })
                engine.bypass(target) { chain.proceed(arrayOf(target, name, chain.getArg(2), output)) }
            }
        }.onFailure { runtime.log("Personal Assistant MAML writer unavailable", it) }
    }

    private fun isSurface(view: View): Boolean {
        val field = layerOwner
        if (field != null && field.declaringClass.isInstance(view)) {
            return runCatching {
                val layer = field.get(view)
                val root = layerRoot?.invoke(layer)
                val manager = root?.let { rootManager?.invoke(it) }
                widgetTypes.any { it.isInstance(manager) }
            }.getOrDefault(false)
        }
        return GlassSurfaces.lineage(view).any { target -> widgetTypes.any { it.isInstance(target) } }
    }
}
