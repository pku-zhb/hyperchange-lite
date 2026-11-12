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

private val notificationAccessors = NotificationAccessorCache()

internal fun Class<*>.fieldOrNull(name: String): Field? = notificationAccessors.field(this, name)
internal fun Class<*>.methodOrNull(name: String, vararg parameters: Class<*>): Method? =
    notificationAccessors.method(this, name, parameters)
internal fun Any.read(name: String): Any? = javaClass.fieldOrNull(name)?.get(this)
internal fun Any.call(name: String): Any? = javaClass.methodOrNull(name)?.invoke(this)
