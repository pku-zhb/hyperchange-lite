// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Cache accessors, never their values: missing OEM members need only one hierarchy scan. */
internal class NotificationAccessorCache(
    private val declaredField: (Class<*>, String) -> Field = { type, name -> type.getDeclaredField(name) },
    private val declaredMethod: (Class<*>, String, Array<out Class<*>>) -> Method =
        { type, name, parameters -> type.getDeclaredMethod(name, *parameters) },
) {
    private data class MethodKey(val name: String, val parameters: List<Class<*>>)
    private class Lookup<T>(val member: T?)
    private class Members {
        val fields = ConcurrentHashMap<String, Lookup<Field>>()
        val methods = ConcurrentHashMap<MethodKey, Lookup<Method>>()
    }

    // Class identity includes its loader. ClassValue also releases the cache with that class.
    private val classes = object : ClassValue<Members>() {
        override fun computeValue(type: Class<*>): Members = Members()
    }

    @Suppress("DEPRECATION")
    fun field(type: Class<*>, name: String): Field? = classes.get(type).fields.computeIfAbsent(name) {
        Lookup(findField(type, name))
    }.member?.also {
        // A caller may have reset the cached accessor; preserve fieldOrNull's accessibility.
        if (!it.isAccessible) it.isAccessible = true
    }

    @Suppress("DEPRECATION")
    fun method(type: Class<*>, name: String, parameters: Array<out Class<*>>): Method? {
        val key = MethodKey(name, parameters.toList())
        return classes.get(type).methods.computeIfAbsent(key) {
            Lookup(findMethod(type, name, key.parameters.toTypedArray()))
        }.member?.also { if (!it.isAccessible) it.isAccessible = true }
    }

    private fun findField(start: Class<*>, name: String): Field? {
        var type: Class<*>? = start
        while (type != null) {
            try { return declaredField(type, name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }

    private fun findMethod(start: Class<*>, name: String, parameters: Array<out Class<*>>): Method? {
        var type: Class<*>? = start
        while (type != null) {
            try { return declaredMethod(type, name, parameters).apply { isAccessible = true } }
            catch (_: NoSuchMethodException) { type = type.superclass }
        }
        return null
    }
}
