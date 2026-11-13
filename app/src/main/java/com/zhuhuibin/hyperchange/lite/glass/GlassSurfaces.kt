// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from HyperChanger surface classifiers; Lite removes call-stack ownership.
package com.zhuhuibin.hyperchange.lite.glass

import android.view.View
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import java.util.WeakHashMap

internal enum class GlassSurface { NOTIFICATION, CONTROL_CENTER, VOLUME, SHADE }

internal object GlassSurfaces {
    private val shadeIds = setOf("shade_background", "notification_panel_background", "control_center_background")
    private val volumeIds = setOf("blur_frame", "volume_column_slider", "volume_column_slider_bg_glass", "volume_column_slider_bg_blend")
    private val mediaIds = setOf("media_bg", "media_background", "media_card_background")
    private val mediaRoots = WeakHashMap<View, Boolean>()
    private val roleAccessors = SurfaceRoleAccessors()
    private val decisions = MaterialCallMemo<View, Facts, Long>()

    /** Only for synchronous pure preparation. Never wrap native setters or queued work. */
    fun <T> withFacts(action: () -> T): T = decisions.within(action)

    private class Facts(private val view: View) {
        val parent = view.parent
        private var idValue: String? = null
        private var excludedValue: Boolean? = null
        private var surfaceRead = false
        private var surfaceValue: GlassSurface? = null
        private var mediaValue: Boolean? = null
        private var rowRead = false
        private var rowValue: View? = null
        private var headsUpValue: Boolean? = null
        private var focusValue: Boolean? = null
        private var rootValue: View? = null
        val id get() = idValue ?: idNow(view).also { idValue = it }
        val excluded get() = excludedValue ?: excludedNow(view).also { excludedValue = it }
        val surface get(): GlassSurface? {
            if (!surfaceRead) { surfaceValue = classifyNow(view); surfaceRead = true }
            return surfaceValue
        }
        val media get() = mediaValue ?: mediaNow(view).also { mediaValue = it }
        val row get(): View? {
            if (!rowRead) { rowValue = rowNow(view); rowRead = true }
            return rowValue
        }
        val headsUp get() = headsUpValue ?: headsUpNow(view).also { headsUpValue = it }
        val focus get() = focusValue ?: focusNow(view).also { focusValue = it }
        val root get() = rootValue ?: materialRootNow(view).also { rootValue = it }
    }

    private fun facts(view: View): Facts? {
        val found = decisions.scoped(view, NotificationScope.generation) { Facts(view) } ?: return null
        if (found.parent === view.parent) return found
        decisions.forget(view)
        return decisions.scoped(view, NotificationScope.generation) { Facts(view) }
    }

    fun id(view: View): String = facts(view)?.id ?: idNow(view)
    fun excluded(view: View): Boolean = facts(view)?.excluded ?: excludedNow(view)
    fun classify(view: View): GlassSurface? = facts(view).let { if (it != null) it.surface else classifyNow(view) }
    fun media(view: View): Boolean = facts(view)?.media ?: mediaNow(view)
    fun row(view: View): View? = facts(view).let { if (it != null) it.row else rowNow(view) }
    fun headsUp(view: View): Boolean = facts(view)?.headsUp ?: headsUpNow(view)
    fun focus(view: View): Boolean = facts(view)?.focus ?: focusNow(view)
    fun materialRoot(view: View): View = facts(view)?.root ?: materialRootNow(view)

    private fun idNow(view: View): String = if (view.id == View.NO_ID || view.id == 0) "" else
        runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")

    fun lineage(view: View): Sequence<View> = generateSequence(view) { it.parent as? View }

    /** Unknown or module-owned views fail closed even inside a vendor material call. */
    private fun excludedNow(view: View): Boolean = lineage(view).any { candidate ->
        val name = candidate.javaClass.name
        val resource = id(candidate)
        name.startsWith("com.zhuhuibin.") || name.startsWith("btm.m.") ||
            name.contains("dynamicisland", true) || name.contains("mediaisland", true) ||
            name.contains("TimeView") || name.contains("clock", true) ||
            name.contains("MiuiKeyguardClock") || name.contains("NumPadKey") ||
            resource in setOf("time_group", "clock", "clock_view", "keyguard_clock", "date_view")
    }

    private fun classifyNow(view: View): GlassSurface? {
        if (excluded(view)) return null
        val name = view.javaClass.name
        val resource = id(view)
        // The OEM effect can write the header/root as well as its background. Determine
        // media first so none of those setters can fall into the ordinary range.
        if (media(view)) return GlassSurface.NOTIFICATION
        if (name.contains("NotificationBackgroundView") || (resource in mediaIds && media(view))) return GlassSurface.NOTIFICATION
        // HyperChanger's isNotificationRowBackground selects the background class,
        // not every row child. Reused IDs/ancestors cannot make a row child a panel backdrop.
        if (row(view) != null) return null
        if (resource in shadeIds || name.contains("ShadeBackground") || name.contains("NotificationPanelBackground") || name.contains("ControlCenterBackground")) return GlassSurface.SHADE
        if (lineage(view).any { candidate ->
                val n = candidate.javaClass.name
                n == "com.android.systemui.miui.volume.VolumePanelView" ||
                    n.endsWith("ExpandBlurFrameLayout") || n.endsWith("VolumeBlurFrameLayout") ||
                    (id(candidate) in volumeIds && n.startsWith("android.").not())
            }) return GlassSurface.VOLUME
        // Limit control-center views by actual owner classes, never by an unrelated call stack.
        if (lineage(view).any { it.javaClass.name.startsWith("miui.systemui.controlcenter.") }) return GlassSurface.CONTROL_CENTER
        return null
    }

    fun isShadeWindow(view: View): Boolean = view.javaClass.name == "com.android.systemui.shade.NotificationShadeWindowView"

    /** Effect entry supplies identity for an OEM plain FrameLayout before attachment. */
    fun markMediaRoot(view: View) {
        if (!excluded(view) && !controlCenter(view)) synchronized(mediaRoots) { mediaRoots[view] = true }
    }

    private fun materialRootNow(view: View): View = if (media(view)) {
        lineage(view).firstOrNull { it.javaClass.name.endsWith(".MiuiMediaHeaderView") } ?: lineage(view).firstOrNull {
            synchronized(mediaRoots) { mediaRoots.containsKey(it) }
        } ?: view
    } else row(view) ?: view

    private fun mediaNow(view: View): Boolean {
        if (controlCenter(view)) return false
        return lineage(view).any {
            synchronized(mediaRoots) { mediaRoots.containsKey(it) } || mediaOwner(it.javaClass.name)
        }
    }

    internal fun mediaOwner(name: String): Boolean =
        name.startsWith("com.android.systemui.statusbar.notification.mediacontrol.") ||
            (name.startsWith("com.android.systemui.") &&
                (name.contains("mediaheader", true) || name.contains("miuimedia", true) || name.contains("mediarow", true)) &&
                !name.contains("mediaisland", true) && !name.contains("controlcenter", true))

    private fun controlCenter(view: View): Boolean = lineage(view).any {
        it.javaClass.name.startsWith("miui.systemui.controlcenter.") ||
            it.javaClass.name.startsWith("com.miui.systemui.controlcenter.")
    }

    private fun rowNow(view: View): View? = lineage(view).firstOrNull { it.javaClass.name.contains("ExpandableNotificationRow") }

    private fun headsUpNow(view: View): Boolean = row(view)?.let { row ->
        roleAccessors.headsUp(row) == true
    } ?: false

    private fun focusNow(view: View): Boolean = row(view)?.let { row ->
        roleAccessors.focus(row) == true
    } ?: false
}

/** Shared observation clock for native setters and complete OEM effect applications. */
internal object NotificationMaterialWrites {
    private val clock = MaterialWriteClock<View>()
    fun touch(view: View) { clock.touch(GlassSurfaces.materialRoot(view)) }
    fun observe(view: View, permitted: Boolean) { clock.observe(GlassSurfaces.materialRoot(view), permitted) }
    fun ticket(view: View): MaterialWriteClock.Ticket<View> = clock.ticket(GlassSurfaces.materialRoot(view))
    fun current(ticket: MaterialWriteClock.Ticket<View>): Boolean = clock.current(ticket)
}
