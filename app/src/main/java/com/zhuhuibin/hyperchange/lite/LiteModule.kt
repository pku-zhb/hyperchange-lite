// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.os.Handler
import android.os.Looper
import com.zhuhuibin.hyperchange.lite.glass.GlassHooks
import com.zhuhuibin.hyperchange.lite.loader.MainThreadPluginScheduler
import com.zhuhuibin.hyperchange.lite.loader.PluginClassLoaderDiscovery
import com.zhuhuibin.hyperchange.lite.notifications.NotificationEffects
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** The sole entry point; no framework, launcher, wallpaper or HMC module is loaded. */
class LiteModule : XposedModule() {
    private val dispatched = mutableSetOf<Pair<String, ClassLoader>>()
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
        // Construct the Java callback and its scheduler before enabling either global
        // hook. Kotlin adapter installation happens later, outside the loading callback.
        val discovery = PluginClassLoaderDiscovery(
            MainThreadPluginScheduler(Handler(Looper.getMainLooper())),
            object : PluginClassLoaderDiscovery.Listener {
                override fun onDiscovered(pluginLoader: ClassLoader) {
                    runtime.log("Plugin loader discovered; installing adapters on the main queue")
                    dispatch("miui.systemui.plugin", pluginLoader)
                }
                override fun onFailure(error: Throwable) {
                    runtime.log("Plugin adapter installation failed; no automatic retry for this loader", error)
                }
            },
        )
        ClassLoader::class.java.declaredMethods.filter {
            it.name == "loadClass" && (it.parameterTypes.contentEquals(arrayOf(String::class.java)) ||
                it.parameterTypes.contentEquals(arrayOf(String::class.java, Boolean::class.javaPrimitiveType)))
        }.forEach { method ->
            runtime.hookDirect(method, "plugin-discovery:${method.parameterCount}", discovery)
        }
    }

    companion object {
        val SCOPES = setOf("com.android.systemui", "miui.systemui.plugin", "com.miui.personalassistant", "com.miui.securitycenter")
    }
}
