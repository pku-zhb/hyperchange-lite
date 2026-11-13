// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.controls.VolumePlacementModel

/** One resolution path for app storage, remote hook reads and version-1 presets. */
internal object SettingsMigration {
    const val MEDIA_KEY = "glass_media"
    const val LOCKSCREEN_KEY = "notification_lockscreen_effects"
    const val EDGE_GAP_KEY = "volume_edge_gap"
    const val LEGACY_VOLUME_OFFSET_KEY = "volume_offset_x"
    const val VERSION_KEY = "_scope_migration_version"
    const val VERSION = 1

    private val legacyKeys by lazy {
        (LiteCatalog.entries.map { it.key }.toSet() + LEGACY_VOLUME_OFFSET_KEY) -
            setOf(MEDIA_KEY, LOCKSCREEN_KEY, EDGE_GAP_KEY)
    }

    /** Never writes preferences: upgraded hook processes need not wait for the app. */
    fun resolve(values: Map<String, *>): Map<String, Any> {
        val legacy = !isMigrated(values) &&
            (values["_initialized"] == true || values.keys.any { it in legacyKeys })
        return buildMap {
            values.forEach { (key, value) -> if (value != null) put(key, value) }
            put(MEDIA_KEY, values[MEDIA_KEY] as? Boolean
                ?: if (legacy) values["glass_notifications"] as? Boolean ?: true else false)
            put(LOCKSCREEN_KEY, values[LOCKSCREEN_KEY] as? Boolean ?: legacy)
            put(EDGE_GAP_KEY, VolumePlacementModel.resolveEdgeGap(
                (values[EDGE_GAP_KEY] as? Number)?.toFloat(),
                if (EDGE_GAP_KEY in values) null else (values[LEGACY_VOLUME_OFFSET_KEY] as? Number)?.toFloat(),
            ))
            put(VERSION_KEY, VERSION)
        }
    }

    /** Migration-only fresh defaults must not hide an existing remote configuration. */
    fun hasLocalState(values: Map<String, *>): Boolean =
        values["_initialized"] == true || values.keys.any { it in legacyKeys } ||
            (!isMigrated(values) && (MEDIA_KEY in values || LOCKSCREEN_KEY in values))

    fun forBinding(local: Map<String, *>, remote: Map<String, *>): Map<String, Any> =
        resolve(if (hasLocalState(local)) local else remote)

    /** Inherit old combined scopes without inserting unrelated position defaults. */
    fun forPreset(specs: List<LiteSetting>, values: Map<String, Any>): Map<String, Any> {
        val keys = specs.map { it.key }.toSet()
        if (values.isEmpty()) return values
        val hasPosition = EDGE_GAP_KEY in values || LEGACY_VOLUME_OFFSET_KEY in values
        val migrateScopes = MEDIA_KEY in keys && LOCKSCREEN_KEY in keys
        val resolved = if (migrateScopes || (EDGE_GAP_KEY in keys && hasPosition)) resolve(values) else values
        return resolved.filterKeys { it in keys && (it != EDGE_GAP_KEY || hasPosition) }
    }

    private fun isMigrated(values: Map<String, *>): Boolean =
        (values[VERSION_KEY] as? Number)?.toInt()?.let { it >= VERSION } == true
}
