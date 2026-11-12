// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.content.SharedPreferences
import java.lang.reflect.Field
import java.lang.reflect.Method

internal fun SharedPreferences.flag(key: String, fallback: Boolean = false): Boolean =
    runCatching { getBoolean(key, fallback) }.getOrDefault(fallback)
internal fun SharedPreferences.number(key: String, fallback: Float, min: Float, max: Float): Float {
    val value = runCatching { getFloat(key, fallback) }.getOrElse { (all[key] as? Number)?.toFloat() ?: fallback }
    return (value.takeIf { it.isFinite() } ?: fallback).coerceIn(min, max)
}

internal fun Class<*>.fieldOrNull(name: String): Field? {
    var type: Class<*>? = this
    while (type != null) {
        try { return type.getDeclaredField(name).apply { isAccessible = true } }
        catch (_: NoSuchFieldException) { type = type.superclass }
    }
    return null
}
internal fun Class<*>.methodOrNull(name: String, vararg parameters: Class<*>): Method? {
    var type: Class<*>? = this
    while (type != null) {
        try { return type.getDeclaredMethod(name, *parameters).apply { isAccessible = true } }
        catch (_: NoSuchMethodException) { type = type.superclass }
    }
    return null
}
internal fun Any.read(name: String): Any? = javaClass.fieldOrNull(name)?.get(this)
internal fun Any.call(name: String): Any? = javaClass.methodOrNull(name)?.invoke(this)
