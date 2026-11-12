// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.zhuhuibin.hyperchange.lite.HookRuntime
import com.zhuhuibin.hyperchange.lite.scope.NotificationScope
import com.zhuhuibin.hyperchange.lite.scope.NotificationRole
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Collapsed group geometry is independent of the notification shade's global stack. */
internal class NotificationGroupStack {
    private lateinit var runtime: HookRuntime
    private lateinit var prefs: SharedPreferences
    private lateinit var schema: Schema
    private val sessions = WeakHashMap<View, Session>()
    private data class Measurement(val owned: OwnedValue<Int>, val token: com.zhuhuibin.hyperchange.lite.scope.NotificationScopeToken, val revision: Long)
    private val measurements = WeakHashMap<View, Measurement>()
    @Volatile private var configurationRevision = 0L
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var roundnessSource: Any? = null
    private var requestRoundness: Method? = null
    private var resetRoundness: Method? = null

    fun install(runtime: HookRuntime) {
        this.runtime = runtime
        prefs = runtime.preferences
        val container = runtime.type("com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer") ?: return
        val row = runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") ?: return
        val viewState = runtime.type("com.android.systemui.statusbar.notification.stack.ViewState") ?: return
        val expandableState = runtime.type("com.android.systemui.statusbar.notification.stack.ExpandableViewState") ?: return
        schema = runCatching { Schema(container, row, viewState, expandableState) }
            .onFailure { runtime.log("Collapsed notification group schema unavailable; native groups retained", it) }.getOrNull() ?: return
        runCatching {
            val source = runtime.type("com.android.systemui.statusbar.notification.SourceType\$Companion\$from\$1")!!
            roundnessSource = source.getMethod("from", String::class.java).invoke(null, "HyperChangeLiteGroupStack")
            requestRoundness = row.methods.first { it.name == "requestRoundness" && it.parameterCount == 4 }
            resetRoundness = row.methods.first { it.name == "requestRoundnessReset" && it.parameterCount == 1 }
        }.onFailure { runtime.log("Group roundness source unavailable; existing corner treatment retained", it) }

        container.declaredMethods.filter { it.name == "updateState" && it.parameterCount == 1 }.forEach { method ->
            runtime.hook(method, "notification-groups:state") { chain ->
                val target = chain.thisObject as? View
                target?.let { sessions.remove(it)?.restore() }
                val result = chain.proceed()
                if (target != null) runCatching { apply(target) }.onFailure {
                    sessions.remove(target)?.restore()
                    runtime.log("Collapsed notification group update failed; restored native state", it)
                }
                result
            }
        }
        container.declaredMethods.filter { it.name == "getIntrinsicHeightAosp" && it.parameterCount == 0 || it.name == "getMinHeight" && it.parameterCount == 3 }
            .forEach { method -> runtime.hook(method, "notification-groups:height:${method.name}") { chain ->
                val result = chain.proceed()
                val target = chain.thisObject as? View
                val count = target?.let(::eligibleCount) ?: 0
                if (target == null || count < 2) result else {
                    val limit = if (method.name == "getMinHeight") (chain.getArg(0) as? Int)?.coerceAtMost(count) ?: count else count
                    height(target, limit).takeIf { it > 0 } ?: result
                }
            } }
        container.methodOrNull("onMeasure", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
            runtime.hook(method, "notification-groups:measure") { chain ->
                val target = chain.thisObject as? View
                target?.let(::releaseMeasurement)
                val result = chain.proceed()
                if (target != null) runCatching {
                    val count = eligibleCount(target)
                    val size = if (count >= 2) height(target, count) else 0
                    if (size > 0) {
                        val owned = OwnedValue(schema.realHeight.getInt(target))
                        schema.measured.invoke(target, target.measuredWidth, size)
                        schema.realHeight.setInt(target, size)
                        owned.record(size)
                        measurements[target] = Measurement(owned, NotificationScope.token(target), configurationRevision)
                    }
                }.onFailure { runtime.log("Notification group measured height unavailable", it) }
                result
            }
        }
        runtime.type("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow\$NotificationViewState")
            ?.methodOrNull("applyToView", View::class.java)?.let { method ->
                runtime.hook(method, "notification-groups:apply-view") { chain ->
                    val result = chain.proceed()
                    val target = chain.getArg(0) as? View
                    val children = target?.let { schema.childrenContainer.get(it) as? View }
                    children?.let { sessions[it]?.draw() }
                    result
                }
            }
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("notification_group_stack_")) {
                configurationRevision++
                Handler(Looper.getMainLooper()).post { releaseStale() }
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
        NotificationScope.addListener(::releaseStale)
    }

    private fun releaseMeasurement(view: View) {
        measurements.remove(view)?.owned?.release(schema.realHeight.getInt(view)) { schema.realHeight.setInt(view, it) }
    }

    private fun releaseStale() {
        val targets = sessions.filterValues { it.stale() }.keys.toList() + measurements.filter { (view, entry) ->
            entry.revision != configurationRevision || !NotificationScope.isCurrent(view, entry.token)
        }.keys.toList()
        targets.distinct().forEach { view ->
            sessions.remove(view)?.restore()
            releaseMeasurement(view)
            view.requestLayout()
        }
    }

    private fun children(container: View): List<View> =
        (schema.children.get(container) as? List<*>)?.filterIsInstance<View>().orEmpty()

    private fun eligibleCount(container: View): Int {
        if (!prefs.flag("notification_group_stack_enabled") || !NotificationScope.sceneAllows(container)) return 0
        if (schema.expanded.getBoolean(container) || schema.dragging.getBoolean(container) || schema.minimized.getBoolean(container)) return 0
        val parent = schema.parent.get(container) ?: return 0
        if (parent is View && NotificationScope.role(parent) != NotificationRole.ORDINARY) return 0
        if (parent.call("isHeadsUpState") == true || parent.call("isGroupExpansionChanging") == true) return 0
        if (schema.bundleHeader.get(container) != null) return 0
        val injector = schema.injector.get(container) ?: return 0
        if (injector.read("mMiuiGroupHeader") == null || injector.read("isCollapseAnimating") == true) return 0
        val count = children(container).size.coerceAtMost(3)
        return if (count >= 2) count else 0
    }

    private fun spacing(container: View): Float = prefs.number("notification_group_stack_spacing", 6f, 0f, 32f) * container.resources.displayMetrics.density

    private fun height(container: View, count: Int): Int = children(container).take(count).mapIndexed { index, row ->
        val state = row.call("getViewState")
        val stateHeight = state?.let { schema.stateHeight.getInt(it) } ?: 0
        val h = stateHeight.takeIf { it > 0 } ?: (row.call("getIntrinsicHeight") as? Number)?.toInt() ?: row.measuredHeight
        (h + index * spacing(container)).roundToInt()
    }.maxOrNull() ?: 0

    private fun apply(container: View) {
        val count = eligibleCount(container)
        if (count < 2) return
        val session = Session(container)
        sessions[container] = session
        container.addOnAttachStateChangeListener(session)
        val rows = children(container).take(count)
        val gap = spacing(container)
        val ratio = prefs.number("notification_group_stack_scale", .95f, .5f, 1f)
        val oldLastY = rows.last().call("getViewState")?.let { schema.y.getFloat(it) } ?: 0f
        rows.forEachIndexed { index, row ->
            val state = row.call("getViewState") ?: return@forEachIndexed
            session.write(state, schema.y, index * gap)
            session.write(state, schema.z, 0f)
            val scale = NotificationMotion.group(index, 0f, ratio).scale
            session.write(state, schema.sx, scale); session.write(state, schema.sy, scale)
            session.rows += Row(WeakReference(row), index * gap, scale, Appearance(row))
            session.childLayout(row)
        }
        schema.overflow.get(container)?.let { state ->
            session.write(state, schema.y, schema.y.getFloat(state) + (count - 1) * gap - oldLastY)
        }
        (schema.parent.get(container) as? View)?.let { parent ->
            session.write(parent, schema.noBackground, true)
            (parent.read("mBackgroundNormal") as? View)?.let { session.hide(it, visibility = View.INVISIBLE) }
            for (name in listOf("mPrivateLayout", "mPublicLayout")) (parent.read(name) as? View)?.let { session.hide(it) }
        }
        schema.injector.get(container)?.read("iconArea")?.let { if (it is View) session.hide(it, visibility = View.INVISIBLE) }
        session.draw()
    }

    private data class Row(val view: WeakReference<View>, val y: Float, val scale: Float, val original: Appearance)
    private class Appearance(view: View) {
        private val values = snapshot(view).mapValues { OwnedValue(it.value) }
        fun mark(view: View, vararg keys: String) {
            val current = snapshot(view)
            keys.forEach { key -> values.getValue(key).record(current[key]) }
        }
        fun restore(view: View) {
            val current = snapshot(view)
            values.forEach { (key, owned) -> owned.release(current[key]) { value ->
                when (key) {
                    "y" -> view.translationY = value as Float
                    "z" -> view.translationZ = value as Float
                    "sx" -> view.scaleX = value as Float
                    "sy" -> view.scaleY = value as Float
                    "px" -> view.pivotX = value as Float
                    "py" -> view.pivotY = value as Float
                    "alpha" -> view.alpha = value as Float
                    "visibility" -> view.visibility = value as Int
                    "superX", "superY" -> if (value != null) view.javaClass.methodOrNull(
                        if (key == "superX") "setSuperScaleX" else "setSuperScaleY", Float::class.javaPrimitiveType!!,
                    )?.invoke(view, value)
                }
            } }
        }
        companion object {
            private fun snapshot(view: View): Map<String, Any?> = mapOf(
                "y" to view.translationY, "z" to view.translationZ, "sx" to view.scaleX, "sy" to view.scaleY,
                "px" to view.pivotX, "py" to view.pivotY, "alpha" to view.alpha, "visibility" to view.visibility,
                "superX" to (view.call("getSuperScaleX") as? Number)?.toFloat(),
                "superY" to (view.call("getSuperScaleY") as? Number)?.toFloat(),
            )
        }
    }

    private inner class Session(container: View) : View.OnAttachStateChangeListener {
        private val target = WeakReference(container)
        private val token = NotificationScope.token(container)
        private val revision = configurationRevision
        fun stale() = revision != configurationRevision || target.get()?.let { !NotificationScope.isCurrent(it, token) } != false
        val rows = mutableListOf<Row>()
        private val fields = mutableListOf<Triple<WeakReference<Any>, Field, OwnedValue<Any?>>>()
        private val appearance = mutableListOf<Pair<WeakReference<View>, Appearance>>()
        private val childLayouts = mutableListOf<Pair<WeakReference<Any>, OwnedValue<Boolean>>>()
        private val oldOrder = (container as? ViewGroup)?.let { group -> (0 until group.childCount).map { WeakReference(group.getChildAt(it)) } }.orEmpty()
        private var lastOrder: List<WeakReference<View>>? = null
        private var restored = false

        fun write(owner: Any, field: Field, value: Any?) {
            val owned = OwnedValue(field.get(owner))
            fields += Triple(WeakReference(owner), field, owned)
            field.set(owner, value)
            owned.record(value)
        }
        fun hide(view: View, visibility: Int? = null) {
            val original = Appearance(view)
            appearance += WeakReference(view) to original
            if (visibility == null) view.alpha = 0f else view.visibility = visibility
            original.mark(view, if (visibility == null) "alpha" else "visibility")
        }

        fun childLayout(row: View) {
            write(row, schema.noBackground, false)
            roundnessSource?.let { requestRoundness?.invoke(row, 1f, 1f, it, true) }
            (row.read("mBackgroundNormal") as? View)?.let {
                val original = Appearance(it)
                appearance += WeakReference(it) to original
                it.visibility = View.VISIBLE; it.alpha = 1f
                original.mark(it, "visibility", "alpha")
            }
            for (name in listOf("mPrivateLayout", "mPublicLayout")) row.read(name)?.let { layout ->
                val flag = layout.javaClass.fieldOrNull("mIsChildInGroup") ?: return@let
                val owned = OwnedValue(flag.getBoolean(layout))
                childLayouts += WeakReference(layout) to owned
                layout.javaClass.methodOrNull("setIsChildInGroup", Boolean::class.javaPrimitiveType!!)?.invoke(layout, false)
                    ?: flag.setBoolean(layout, false)
                owned.record(false)
            }
            val state = row.call("getViewState")
            if (state != null) {
                val intrinsic = (row.call("getIntrinsicHeight") as? Number)?.toInt() ?: 0
                if (intrinsic > 0) write(state, schema.stateHeight, intrinsic)
            }
        }

        fun draw() {
            if (restored) return
            val container = target.get() ?: return
            val count = eligibleCount(container)
            if (restored || stale() || count < 2) {
                sessions.remove(container, this); restore(); container.requestLayout(); return
            }
            rows.forEach { item -> item.view.get()?.let { row ->
                row.pivotX = row.width / 2f; item.original.mark(row, "px")
                row.pivotY = row.height.toFloat(); item.original.mark(row, "py")
                row.translationY = item.y; item.original.mark(row, "y")
                row.translationZ = 0f; item.original.mark(row, "z")
                val superX = row.javaClass.methodOrNull("setSuperScaleX", Float::class.javaPrimitiveType!!)
                val superY = row.javaClass.methodOrNull("setSuperScaleY", Float::class.javaPrimitiveType!!)
                if (superX != null && superY != null) {
                    superX.invoke(row, item.scale); item.original.mark(row, "superX")
                    superY.invoke(row, item.scale); item.original.mark(row, "superY")
                } else {
                    row.scaleX = item.scale; item.original.mark(row, "sx")
                    row.scaleY = item.scale; item.original.mark(row, "sy")
                }
            } }
            (target.get() as? ViewGroup)?.let { group ->
                rows.asReversed().forEach { it.view.get()?.let(group::bringChildToFront) }
                // Headers/overflow remain above cards, with existing native touch routing.
                oldOrder.mapNotNull { it.get() }.filter { view -> rows.none { it.view.get() === view } }.forEach(group::bringChildToFront)
                lastOrder = (0 until group.childCount).map { WeakReference(group.getChildAt(it)) }
            }
        }

        fun restore() {
            if (restored) return
            restored = true
            target.get()?.removeOnAttachStateChangeListener(this)
            fields.asReversed().forEach { (owner, field, owned) -> owner.get()?.let { instance -> runCatching {
                owned.release(field.get(instance)) { field.set(instance, it) }
            } } }
            childLayouts.forEach { (reference, owned) -> reference.get()?.let { layout -> runCatching {
                val flag = layout.javaClass.fieldOrNull("mIsChildInGroup") ?: return@runCatching
                owned.release(flag.getBoolean(layout)) { wasChild ->
                    layout.javaClass.methodOrNull("setIsChildInGroup", Boolean::class.javaPrimitiveType!!)?.invoke(layout, wasChild)
                        ?: flag.setBoolean(layout, wasChild)
                }
            } } }
            appearance.asReversed().forEach { (reference, original) -> reference.get()?.let { original.restore(it) } }
            rows.forEach { item -> item.view.get()?.let { row ->
                item.original.restore(row)
                roundnessSource?.let { source -> runCatching { resetRoundness?.invoke(row, source) } }
            } }
            (target.get() as? ViewGroup)?.let { group ->
                val current = (0 until group.childCount).map { group.getChildAt(it) }
                if (current == lastOrder?.mapNotNull { it.get() }) {
                    oldOrder.mapNotNull { it.get() }.filter { it.parent === group }.forEach(group::bringChildToFront)
                }
            }
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { sessions.remove(v); restore() }
    }

    private class Schema(container: Class<*>, row: Class<*>, state: Class<*>, expandable: Class<*>) {
        fun required(type: Class<*>, name: String) = requireNotNull(type.fieldOrNull(name)) { "${type.name}.$name" }
        val children = required(container, "mAttachedChildren")
        val expanded = required(container, "mChildrenExpanded")
        val dragging = required(container, "mIsUserSwipingToExpandRow")
        val minimized = required(container, "mIsMinimized")
        val parent = required(container, "mContainingNotification")
        val injector = required(container, "mInjector")
        val bundleHeader = required(container, "mBundleHeaderView")
        val overflow = required(container, "mGroupOverFlowState")
        val realHeight = required(container, "mRealHeight")
        val childrenContainer = required(row, "mChildrenContainer")
        val noBackground = required(row, "mShowNoBackground")
        val y = required(state, "mYTranslation")
        val z = required(state, "mZTranslation")
        val sx = required(state, "mScaleX")
        val sy = required(state, "mScaleY")
        val stateHeight = required(expandable, "height")
        val measured: Method = requireNotNull(View::class.java.methodOrNull("setMeasuredDimension", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
    }
}
