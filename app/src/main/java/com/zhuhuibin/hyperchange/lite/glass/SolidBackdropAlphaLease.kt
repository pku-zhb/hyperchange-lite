// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.ref.WeakReference

/** A one-shot right to ask the native owner to replace our exact alpha-zero write. */
internal class SolidBackdropAlphaLease<O : Any, V : Any, B : Any>(
    owner: O, view: V, root: Any, background: B?,
) {
    private val owner = WeakReference(owner)
    private val view = WeakReference(view)
    private val root = WeakReference(root)
    private val hadBackground = background != null
    private val background = background?.let(::WeakReference)
    @Volatile var retired = false
        private set

    fun retire(needsOverride: Boolean, currentOwner: O?, currentView: V?, currentRoot: Any?,
               currentBackground: B?, attached: Boolean, alpha: Float, fresh: (O) -> Unit): Boolean {
        val restore = synchronized(this) {
            if (retired) return false
            val actualOwner = owner.get()
            val actualView = view.get()
            val actualRoot = root.get()
            val actualBackground = background?.get()
            val same = actualOwner != null && actualOwner === currentOwner && actualView != null &&
                actualView === currentView && actualRoot != null && actualRoot === currentRoot &&
                (if (hadBackground) actualBackground != null && actualBackground === currentBackground else currentBackground == null)
            if (needsOverride && same && attached && alpha == 0f) return false
            // Consume before the native callback: it can reenter or create a new lease.
            retired = true
            if (!needsOverride && same && attached && alpha == 0f) actualOwner else null
        } ?: return false
        fresh(restore)
        return true
    }
}
