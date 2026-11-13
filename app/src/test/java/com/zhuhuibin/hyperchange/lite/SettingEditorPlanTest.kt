// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.EditorItem
import com.zhuhuibin.hyperchange.lite.ui.Feature
import com.zhuhuibin.hyperchange.lite.ui.MaterialCategory
import com.zhuhuibin.hyperchange.lite.ui.editorGateOpen
import com.zhuhuibin.hyperchange.lite.ui.editorItems
import com.zhuhuibin.hyperchange.lite.ui.settingDisabledReason
import com.zhuhuibin.hyperchange.lite.ui.settingVisible
import org.junit.Assert.*
import org.junit.Test

class SettingEditorPlanTest {
    private val byKey get() = LiteCatalog.entries.associateBy { it.key }
    private val allEnabled get() = LiteCatalog.entries.filter { it.isToggle }.associate { it.key to true }

    private fun pages(): List<List<EditorItem>> = Feature.entries.flatMap { feature ->
        if (feature.categories.isNotEmpty()) feature.categories.map { category ->
            editorItems(category, category.settings.filter(::settingVisible))
        } else feature.visibleSettings.groupBy { it.group }.values.map { editorItems(null, it) }
    }

    private fun keys(items: List<EditorItem>, values: Map<String, *>? = null): List<String> = items.flatMap { item ->
        when (item) {
            is EditorItem.Setting -> listOf(item.key)
            is EditorItem.Note -> emptyList()
            is EditorItem.Group -> if (values == null || editorGateOpen(item, values)) keys(item.children, values) else emptyList()
        }
    }

    private fun groups(items: List<EditorItem>): List<EditorItem.Group> = items.flatMap { item ->
        if (item is EditorItem.Group) listOf(item) + groups(item.children) else emptyList()
    }

    private fun behaviorPage(master: String): List<EditorItem> {
        val spec = byKey.getValue(master)
        return editorItems(null, Feature.owner(spec).visibleSettings.filter { it.group == spec.group })
    }

    private fun categoryPage(category: MaterialCategory) = editorItems(category, category.settings.filter(::settingVisible))
    private fun reason(key: String, values: Map<String, *>) = settingDisabledReason(byKey.getValue(key), values)

    @Test fun everyVisibleSettingIsReachableAndOnlyTheTwoMastersAreMirrored() {
        val planned = pages().flatten().let(::keys)
        val visible = Feature.entries.flatMap { it.visibleSettings }.map { it.key }.toSet()
        assertEquals(visible, planned.toSet())
        assertEquals(mapOf("glass_enabled" to 3, "glass_shade_enabled" to 2),
            planned.groupingBy { it }.eachCount().filterValues { it > 1 })
        assertTrue(planned.none { it in DeferredSettings.hiddenKeys })
        assertTrue(planned.all { it in byKey })
    }

    @Test fun everyPlannedControlHasAReachableGateState() {
        val pagePlans = pages()
        val configurations = listOf(
            allEnabled,
            allEnabled + ("notification_heads_up_pop_spring" to false),
            allEnabled + ("notification_heads_up_pop_exit_spring" to false),
            allEnabled + mapOf("notification_heads_up_pop_spring" to false, "notification_heads_up_pop_exit_spring" to false),
        )
        val reachable = pagePlans.flatMap { page -> configurations.flatMap { keys(page, it) } }.toSet()
        assertEquals(pagePlans.flatMap(::keys).toSet(), reachable)
        pagePlans.forEach { page ->
            val allGroups = groups(page)
            assertEquals(allGroups.map { it.id }.distinct().size, allGroups.size)
            allGroups.flatMap { it.gate }.forEach { gate -> assertTrue("Not a toggle: $gate", byKey.getValue(gate).isToggle) }
        }
    }

    @Test fun behaviorMastersRemainVisibleAndHideOnlyTheirDependentControls() {
        listOf("notification_heads_up_pop_enabled", "notification_press_tilt_enabled",
            "notification_stack_anim_enabled", "volume_motion_enabled").forEach { master ->
            val page = behaviorPage(master)
            assertEquals(setOf(master), keys(page, allEnabled + (master to false)).toSet())
            assertTrue(keys(page, allEnabled).contains(master))
            keys(page).filterNot { it == master }.forEach { child ->
                assertNotNull("$child must depend on $master", reason(child, allEnabled + (master to false)))
            }
        }
    }

    @Test fun cardAndWholePanelMastersControlSeparateEditors() {
        val card = categoryPage(MaterialCategory.BACKGROUND)
        val shade = categoryPage(MaterialCategory.SHADE)
        val cardOnly = allEnabled + mapOf("glass_enabled" to true, "glass_shade_enabled" to false)
        val shadeOnly = allEnabled + mapOf("glass_enabled" to false, "glass_shade_enabled" to true)
        assertEquals(setOf("glass_shade_enabled"), keys(shade, cardOnly).toSet())
        assertEquals(setOf("glass_enabled"), keys(card, shadeOnly).toSet())
        keys(card, cardOnly).forEach { assertNull(reason(it, cardOnly)) }
        keys(shade, shadeOnly).forEach { assertNull(reason(it, shadeOnly)) }
    }

    @Test fun sharedOpticsRemainAvailableForEitherIndependentAppScope() {
        val page = categoryPage(MaterialCategory.SURFACE)
        val disabled = mapOf("glass_enabled" to false, "glass_assistant" to false, "glass_sidebar" to false)
        assertEquals(listOf("glass_enabled"), keys(page, disabled))
        listOf("glass_enabled", "glass_assistant", "glass_sidebar").forEach { enabled ->
            val values = disabled + (enabled to true)
            assertEquals(keys(page).toSet(), keys(page, values).toSet())
            keys(page).forEach { assertNull("$it must be editable with $enabled", reason(it, values)) }
        }
        // Native material unification remains useful without numeric card tuning.
        assertNull(reason("glass_unify_notifications", disabled))
        assertNull(reason("glass_sidebar_unify", disabled))
    }

    @Test fun cornerOverridesDoNotDependOnTheNarrowVolumeMotionFeature() {
        val values = allEnabled + ("volume_motion_enabled" to false)
        listOf(MaterialCategory.NOTIFICATION, MaterialCategory.CONTROL, MaterialCategory.VOLUME).forEach { category ->
            keys(categoryPage(category), values).forEach { assertNull(reason(it, values)) }
        }
        val page = categoryPage(MaterialCategory.VOLUME)
        val allOff = values + mapOf("volume_corner_enabled" to false, "volume_timer_corner_enabled" to false)
        assertEquals(setOf("volume_corner_enabled", "volume_timer_corner_enabled"), keys(page, allOff).toSet())
        assertNull(reason("volume_hide_ringer", values))
        assertNull(reason("volume_long_press", values))
    }

    @Test fun entryAndExitScaleUseTheirOwnSpringOrCurveControls() {
        val page = behaviorPage("notification_heads_up_pop_enabled")
        val entryCurve = allEnabled + ("notification_heads_up_pop_spring" to false)
        val shown = keys(page, entryCurve).toSet()
        assertTrue("notification_heads_up_pop_curve_x1" in shown)
        assertFalse("notification_heads_up_pop_damping" in shown)
        assertFalse("notification_heads_up_pop_exit_curve_x1" in shown)
        assertTrue("notification_heads_up_pop_exit_damping" in shown)
        assertNull(reason("notification_heads_up_pop_curve_y2", entryCurve))
        assertNotNull(reason("notification_heads_up_pop_damping", entryCurve))
        assertNotNull(reason("notification_heads_up_pop_exit_curve_y2", entryCurve))
        assertNull(reason("notification_heads_up_pop_exit_damping", entryCurve))
        val exitCurve = allEnabled + ("notification_heads_up_pop_exit_spring" to false)
        assertTrue("notification_heads_up_pop_exit_curve_x1" in keys(page, exitCurve))
        assertNull(reason("notification_heads_up_pop_exit_curve_y2", exitCurve))
        assertNotNull(reason("notification_heads_up_pop_exit_damping", exitCurve))
    }

    @Test fun flipAndBlurDampingFollowTheRuntimeSharedEntrySpringEvenOnExit() {
        // NotificationHeadsUp shares the entry spring/curve for flip + blur in both
        // directions. Only the scale phase uses the separate exit spring/curve.
        listOf("notification_heads_up_pop_flip_damping", "notification_heads_up_pop_blur_damping").forEach { key ->
            assertNull(reason(key, allEnabled + ("notification_heads_up_pop_exit_spring" to false)))
            assertNotNull(reason(key, allEnabled + ("notification_heads_up_pop_spring" to false)))
            assertNotNull(reason(key, allEnabled + ("notification_heads_up_pop_enabled" to false)))
        }
    }
}
