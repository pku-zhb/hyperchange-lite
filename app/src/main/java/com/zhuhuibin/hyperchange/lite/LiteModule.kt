// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.glass.GlassHooks
import com.zhuhuibin.hyperchange.lite.notifications.NotificationEffects
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** The sole entry point; no framework, launcher, wallpaper or HMC module is loaded. */
class LiteModule : XposedModule() {
    private val dispatched = mutableSetOf<Pair<String, ClassLoader>>()
    private val installing = ThreadLocal<Boolean>()
    private val glasses = GlassHooks
    private val controls = ControlVolumeHooks()
    private val notifications = NotificationEffects()

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val name = param.packageName
        if (name !in SCOPES) return
        val loader = param.defaultClassLoader
        dispatch(name, loader)
        if (name == "com.android.systemui") discoverPlugin(loader)
    }

    private fun dispatch(name: String, loader: ClassLoader) {
        synchronized(dispatched) { if (!dispatched.add(name to loader)) return }
        val runtime = HookRuntime(this, loader, getRemotePreferences(PREFERENCE_GROUP), name)
        runCatching { glasses.install(runtime) }.onFailure { runtime.log("Glass adapter unavailable", it) }
        if (name == "com.android.systemui" || name == "miui.systemui.plugin") {
            runCatching { controls.install(runtime) }.onFailure { runtime.log("Control/volume adapter unavailable", it) }
        }
        if (name == "com.android.systemui") {
            runCatching { notifications.install(runtime) }.onFailure { runtime.log("Notification adapter unavailable", it) }
        }
        runtime.log("Loaded $name with material and interaction scopes only")
    }

    private fun discoverPlugin(loader: ClassLoader) {
        val runtime = HookRuntime(this, loader, getRemotePreferences(PREFERENCE_GROUP), "com.android.systemui")
        // HyperOS loads its Control Center outside the application's ClassLoader. Match
        // only plugin namespace loads; never inspect stack traces for unrelated apps.
        ClassLoader::class.java.declaredMethods.filter {
            it.name == "loadClass" && it.parameterTypes.firstOrNull() == String::class.java
        }.forEach { method ->
            runtime.hook(method, "plugin-discovery:${method.parameterCount}") { chain ->
                val loaded = chain.proceed()
                val name = chain.getArg(0) as? String
                if (installing.get() != true && name != null &&
                    (name.startsWith("miui.systemui.controlcenter.") || name.startsWith("com.android.systemui.miui.volume.")) && loaded is Class<*>) {
                    loaded.classLoader?.let { pluginLoader ->
                        installing.set(true)
                        try { dispatch("miui.systemui.plugin", pluginLoader) }
                        finally { installing.remove() }
                    }
                }
                loaded
            }
        }
    }

    companion object {
        val SCOPES = setOf("com.android.systemui", "miui.systemui.plugin", "com.miui.personalassistant", "com.miui.securitycenter")
    }
}
