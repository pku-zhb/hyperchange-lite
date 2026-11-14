// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Read the same live flows as the OEM material selector, not an animation-tail flag. */
internal class NativeNotificationRole {
    private class FieldAccess(val field: Field?)
    private class ValueAccess(val method: Method?)
    private val fields = ConcurrentHashMap<Pair<Class<*>, String>, FieldAccess>()
    private val values = ConcurrentHashMap<Class<*>, ValueAccess>()

    private fun field(type: Class<*>, name: String): Field? = fields.computeIfAbsent(type to name) {
        var owner: Class<*>? = type
        var found: Field? = null
        while (owner != null && found == null) {
            val current = owner
            found = runCatching { current.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
            owner = current.superclass
        }
        FieldAccess(found)
    }.field

    fun headsUp(injector: Any): Boolean? = runCatching {
        val modelField = field(injector.javaClass, "viewModel") ?: return null
        val model = modelField.get(injector) ?: return false
        val head = field(model.javaClass, "headUpState") ?: return null
        val drag = field(model.javaClass, "hasCalledHUNDragDownDisappear") ?: return null
        val showing = value(head.get(model))
        val dragged = value(drag.get(model))
        when {
            showing == true || dragged == true -> true
            showing == false && dragged == false -> false
            else -> null
        }
    }.getOrNull()

    private fun value(readonly: Any?): Boolean? {
        // The native binder treats a missing model/flow as false during inflation.
        if (readonly == null) return false
        val flowField = field(readonly.javaClass, "flow")
        val flow = if (flowField != null) flowField.get(readonly) ?: return false else readonly
        val getter = values.computeIfAbsent(flow.javaClass) { type -> ValueAccess(
            runCatching { type.getMethod("getValue").apply { isAccessible = true } }.getOrNull(),
        ) }.method ?: return null
        return getter.invoke(flow) as? Boolean
    }
}
