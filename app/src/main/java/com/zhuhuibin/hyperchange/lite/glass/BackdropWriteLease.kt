// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import java.lang.ref.WeakReference

/** One exact native owner may reclaim an adjusted channel, never a historical value. */
internal class BackdropWriteLease<O : Any, V : Any>(owner: O, view: V, root: Any, control: Any? = null) {
    private val owner = WeakReference(owner)
    private val view = WeakReference(view)
    private val root = WeakReference(root)
    private val hadControl = control != null
    private val control = control?.let(::WeakReference)
    @Volatile var retired = false
        private set

    /** Inspect without surrendering delivery responsibility before the native batch can run. */
    fun candidate(needsOverride: Boolean, currentOwner: O?, currentView: V?, currentRoot: Any?,
                  attached: Boolean, nativeWriteCurrent: Boolean, currentControl: Any? = null,
                  controlValid: Boolean = true): O? = synchronized(this) {
        if (retired) return null
        val actualOwner = owner.get()
        val actualView = view.get()
        val actualRoot = root.get()
        val actualControl = control?.get()
        val same = actualOwner != null && actualOwner === currentOwner && actualView != null &&
            actualView === currentView && actualRoot != null && actualRoot === currentRoot &&
            attached && nativeWriteCurrent && (!hadControl ||
                (actualControl != null && actualControl === currentControl && controlValid))
        if (!same) { retired = true; return null }
        if (needsOverride) null else actualOwner
    }

    @Synchronized fun consume(currentOwner: O): Boolean {
        if (retired || owner.get() !== currentOwner) return false
        retired = true
        return true
    }

    fun retire(needsOverride: Boolean, currentOwner: O?, currentView: V?, currentRoot: Any?,
               attached: Boolean, nativeWriteCurrent: Boolean, currentControl: Any? = null,
               controlValid: Boolean = true, fresh: (O) -> Unit): Boolean {
        val restore = candidate(needsOverride, currentOwner, currentView, currentRoot, attached,
            nativeWriteCurrent, currentControl, controlValid) ?: return false
        if (!consume(restore)) return false
        fresh(restore)
        return true
    }
}
