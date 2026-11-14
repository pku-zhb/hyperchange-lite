// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Cache accessor resolution, including missing methods; never cache a row's current role. */
internal class SurfaceRoleAccessors(
    private val resolve: (Class<*>, String) -> Method? = { type, name ->
        runCatching { type.getMethod(name) }.getOrNull()
    },
) {
    private class RowAccess(val headsUp: Method?, val injector: Method?)
    private class FocusAccess(val focus: Method?)
    private val rows = ConcurrentHashMap<Class<*>, RowAccess>()
    private val injectors = ConcurrentHashMap<Class<*>, FocusAccess>()
    private val materialRoles = NativeNotificationRole()

    private fun rowAccess(type: Class<*>): RowAccess = rows[type] ?: rows.computeIfAbsent(type) {
        // A supported primary getter returning false is authoritative. The legacy name is
        // resolved only when the primary is absent, not on each ordinary notification frame.
        RowAccess(resolve(it, "isHeadsUpState") ?: resolve(it, "isHeadsUp"), resolve(it, "getInjector"))
    }

    fun headsUp(row: Any): Boolean? = runCatching {
        rowAccess(row.javaClass).headsUp?.invoke(row) as? Boolean
    }.getOrNull()

    fun materialHeadsUp(row: Any): Boolean? {
        val injector = runCatching { rowAccess(row.javaClass).injector?.invoke(row) }.getOrNull()
        return injector?.let(materialRoles::headsUp) ?: headsUp(row)
    }

    fun focus(row: Any): Boolean? = runCatching {
        val injector = rowAccess(row.javaClass).injector?.invoke(row) ?: return@runCatching null
        val type = injector.javaClass
        val access = injectors[type] ?: injectors.computeIfAbsent(type) {
            FocusAccess(resolve(it, "isFocusNotification"))
        }
        access.focus?.invoke(injector) as? Boolean
    }.getOrNull()
}
