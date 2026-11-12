// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.util.Collections

class HookRuntime(
    val module: XposedModule,
    val loader: ClassLoader,
    val preferences: SharedPreferences,
    val packageName: String,
) {
    fun type(name: String): Class<*>? = runCatching { loader.loadClass(name) }.getOrNull()

    fun hook(method: Executable, id: String, callback: (XposedInterface.Chain) -> Any?) {
        val key = method to id
        if (!installed.add(key)) return
        try {
            method.isAccessible = true
            runCatching { module.deoptimize(method) }
            module.hook(method).setId("lite:$id")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { callback(it) }
        } catch (error: Throwable) {
            installed.remove(key)
            log("Unavailable hook: $id", error)
        }
    }

    fun log(message: String, error: Throwable? = null) {
        if (error == null) module.log(Log.INFO, "HyperChangeLite", message)
        else module.log(Log.WARN, "HyperChangeLite", message, error)
    }

    companion object {
        private val installed = Collections.synchronizedSet(mutableSetOf<Pair<Executable, String>>())
    }
}
