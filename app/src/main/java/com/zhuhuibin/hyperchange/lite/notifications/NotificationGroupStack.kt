// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.zhuhuibin.hyperchange.lite.HookRuntime
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
                val result = chain.proceed()
                val target = chain.thisObject as? View
                if (target != null) runCatching {
                    val count = eligibleCount(target)
                    val size = if (count >= 2) height(target, count) else 0
                    if (size > 0) {
                        schema.measured.invoke(target, target.measuredWidth, size)
                        schema.realHeight.setInt(target, size)
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
            if (key == null || key.startsWith("notification_group_stack_")) Handler(Looper.getMainLooper()).post {
                sessions.entries.toList().forEach { (view, session) -> session.restore(); view.requestLayout() }
                sessions.clear()
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
    }

    private fun children(container: View): List<View> =
        (schema.children.get(container) as? List<*>)?.filterIsInstance<View>().orEmpty()

    private fun eligibleCount(container: View): Int {
        if (!prefs.flag("notification_group_stack_enabled")) return 0
        if (schema.expanded.getBoolean(container) || schema.dragging.getBoolean(container) || schema.minimized.getBoolean(container)) return 0
        val parent = schema.parent.get(container) ?: return 0
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
            session.remember(state, schema.y); session.remember(state, schema.z)
            session.remember(state, schema.sx); session.remember(state, schema.sy)
            schema.y.setFloat(state, index * gap)
            schema.z.setFloat(state, 0f)
            val scale = NotificationMotion.group(index, 0f, ratio).scale
            schema.sx.setFloat(state, scale); schema.sy.setFloat(state, scale)
            session.rows += Row(WeakReference(row), index * gap, scale, Appearance(row))
            session.childLayout(row)
        }
        schema.overflow.get(container)?.let { state ->
            session.remember(state, schema.y)
            schema.y.setFloat(state, schema.y.getFloat(state) + (count - 1) * gap - oldLastY)
        }
        (schema.parent.get(container) as? View)?.let { parent ->
            session.remember(parent, schema.noBackground)
            schema.noBackground.setBoolean(parent, true)
            (parent.read("mBackgroundNormal") as? View)?.let { session.hide(it, visibility = View.INVISIBLE) }
            for (name in listOf("mPrivateLayout", "mPublicLayout")) (parent.read(name) as? View)?.let { session.hide(it) }
        }
        schema.injector.get(container)?.read("iconArea")?.let { if (it is View) session.hide(it, visibility = View.INVISIBLE) }
        session.draw()
    }

    private data class Row(val view: WeakReference<View>, val y: Float, val scale: Float, val original: Appearance)
    private class Appearance(view: View) {
        val y = view.translationY; val z = view.translationZ; val sx = view.scaleX; val sy = view.scaleY
        val pivotX = view.pivotX; val pivotY = view.pivotY; val alpha = view.alpha; val visibility = view.visibility
        val superX = (view.call("getSuperScaleX") as? Number)?.toFloat()
        val superY = (view.call("getSuperScaleY") as? Number)?.toFloat()
        fun restore(view: View) {
            if (superX != null) view.javaClass.methodOrNull("setSuperScaleX", Float::class.javaPrimitiveType!!)?.invoke(view, superX)
            if (superY != null) view.javaClass.methodOrNull("setSuperScaleY", Float::class.javaPrimitiveType!!)?.invoke(view, superY)
            view.translationY = y; view.translationZ = z; view.scaleX = sx; view.scaleY = sy
            view.pivotX = pivotX; view.pivotY = pivotY; view.alpha = alpha; view.visibility = visibility
        }
    }

    private inner class Session(container: View) : View.OnAttachStateChangeListener {
        private val target = WeakReference(container)
        val rows = mutableListOf<Row>()
        private val fields = mutableListOf<Triple<WeakReference<Any>, Field, Any?>>()
        private val appearance = mutableListOf<Pair<WeakReference<View>, Appearance>>()
        private val childLayouts = mutableListOf<Pair<WeakReference<Any>, Boolean>>()
        private val oldOrder = (container as? ViewGroup)?.let { group -> (0 until group.childCount).map { WeakReference(group.getChildAt(it)) } }.orEmpty()
        private var restored = false

        fun remember(owner: Any, field: Field) { fields += Triple(WeakReference(owner), field, field.get(owner)) }
        fun hide(view: View, visibility: Int? = null) {
            appearance += WeakReference(view) to Appearance(view)
            if (visibility == null) view.alpha = 0f else view.visibility = visibility
        }

        fun childLayout(row: View) {
            remember(row, schema.noBackground)
            schema.noBackground.setBoolean(row, false)
            roundnessSource?.let { requestRoundness?.invoke(row, 1f, 1f, it, true) }
            (row.read("mBackgroundNormal") as? View)?.let {
                appearance += WeakReference(it) to Appearance(it)
                it.visibility = View.VISIBLE; it.alpha = 1f
            }
            for (name in listOf("mPrivateLayout", "mPublicLayout")) row.read(name)?.let { layout ->
                val flag = layout.javaClass.fieldOrNull("mIsChildInGroup") ?: return@let
                childLayouts += WeakReference(layout) to flag.getBoolean(layout)
                layout.javaClass.methodOrNull("setIsChildInGroup", Boolean::class.javaPrimitiveType!!)?.invoke(layout, false)
                    ?: flag.setBoolean(layout, false)
            }
            val state = row.call("getViewState")
            if (state != null) {
                remember(state, schema.stateHeight)
                val intrinsic = (row.call("getIntrinsicHeight") as? Number)?.toInt() ?: 0
                if (intrinsic > 0) schema.stateHeight.setInt(state, intrinsic)
            }
        }

        fun draw() {
            if (restored) return
            rows.forEach { item -> item.view.get()?.let { row ->
                row.pivotX = row.width / 2f; row.pivotY = row.height.toFloat()
                row.translationY = item.y; row.translationZ = 0f
                val superX = row.javaClass.methodOrNull("setSuperScaleX", Float::class.javaPrimitiveType!!)
                val superY = row.javaClass.methodOrNull("setSuperScaleY", Float::class.javaPrimitiveType!!)
                if (superX != null && superY != null) { superX.invoke(row, item.scale); superY.invoke(row, item.scale) }
                else { row.scaleX = item.scale; row.scaleY = item.scale }
            } }
            (target.get() as? ViewGroup)?.let { group ->
                rows.asReversed().forEach { it.view.get()?.let(group::bringChildToFront) }
                // Headers/overflow remain above cards, with existing native touch routing.
                oldOrder.mapNotNull { it.get() }.filter { view -> rows.none { it.view.get() === view } }.forEach(group::bringChildToFront)
            }
        }

        fun restore() {
            if (restored) return
            restored = true
            target.get()?.removeOnAttachStateChangeListener(this)
            fields.asReversed().forEach { (owner, field, value) -> owner.get()?.let { runCatching { field.set(it, value) } } }
            childLayouts.forEach { (reference, wasChild) -> reference.get()?.let { layout -> runCatching {
                layout.javaClass.methodOrNull("setIsChildInGroup", Boolean::class.javaPrimitiveType!!)?.invoke(layout, wasChild)
                    ?: layout.javaClass.fieldOrNull("mIsChildInGroup")?.setBoolean(layout, wasChild)
            } } }
            appearance.asReversed().forEach { (reference, original) -> reference.get()?.let { original.restore(it) } }
            rows.forEach { item -> item.view.get()?.let { row ->
                item.original.restore(row)
                roundnessSource?.let { source -> runCatching { resetRoundness?.invoke(row, source) } }
            } }
            (target.get() as? ViewGroup)?.let { group -> oldOrder.mapNotNull { it.get() }.filter { it.parent === group }.forEach(group::bringChildToFront) }
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
