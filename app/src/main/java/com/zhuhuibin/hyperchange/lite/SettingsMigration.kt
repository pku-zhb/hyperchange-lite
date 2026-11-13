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
    const val BACKDROP_VERSION_KEY = "_backdrop_migration_version"
    const val BACKDROP_VERSION = 1
    const val BACKDROP_JSON_VERSION_KEY = "backdrop_migration_version"
    const val BACKDROP_ENABLED_KEY = "glass_shade_enabled"
    val backdropValueKeys = setOf("glass_shade_blur", "glass_shade_scale", "glass_shade_opacity")
    val backdropKeys = backdropValueKeys + BACKDROP_ENABLED_KEY
    private val backdropSources = mapOf(
        "glass_shade_blur" to "glass_background_blur",
        "glass_shade_scale" to "glass_background_scale",
        "glass_shade_opacity" to "glass_background_opacity",
    )
    // Later opt-ins must neither make an old full export incomplete nor turn a
    // new partial edit into evidence of the legacy combined scopes.
    private val laterScopeKeys = setOf(MEDIA_KEY, LOCKSCREEN_KEY, EDGE_GAP_KEY, "glass_heads_up")

    private val legacyKeys by lazy {
        (LiteCatalog.entries.map { it.key }.toSet() + LEGACY_VOLUME_OFFSET_KEY) -
            (laterScopeKeys + backdropKeys)
    }
    private val legacySnapshotKeys by lazy {
        LiteCatalog.entries.map { it.key }.toSet() -
            (backdropKeys + laterScopeKeys)
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
            backdropSources.forEach { (target, source) ->
                val raw = values[target] ?: if (!isBackdropMigrated(values)) values[source] else null
                val value = (raw as? Number)?.toFloat()?.takeIf(Float::isFinite) ?: 100f
                put(target, value.coerceIn(0f, 200f))
            }
            put(BACKDROP_ENABLED_KEY, values[BACKDROP_ENABLED_KEY] as? Boolean ?: if (!isBackdropMigrated(values))
                values["glass_enabled"] == true && values["glass_shade"] != false else false)
            put(VERSION_KEY, VERSION)
            put(BACKDROP_VERSION_KEY, BACKDROP_VERSION)
        }
    }

    /** Migration-only fresh defaults must not hide an existing remote configuration. */
    fun hasLocalState(values: Map<String, *>): Boolean =
        values["_initialized"] == true || values.keys.any { it in legacyKeys } ||
            (!isMigrated(values) && (MEDIA_KEY in values || LOCKSCREEN_KEY in values)) ||
            (!isBackdropMigrated(values) && values.keys.any { it in backdropKeys })

    fun forBinding(local: Map<String, *>, remote: Map<String, *>): Map<String, Any> =
        resolve(if (hasLocalState(local)) local else remote)

    /** Inherit old combined scopes without inserting unrelated position defaults. */
    fun forPreset(specs: List<LiteSetting>, values: Map<String, Any>, backdropMigrated: Boolean = false): Map<String, Any> {
        val keys = specs.map { it.key }.toSet()
        if (values.isEmpty()) return values
        val hasPosition = EDGE_GAP_KEY in values || LEGACY_VOLUME_OFFSET_KEY in values
        // A new whole-window partial has no old combined card scope to inherit.
        // Keep legacy card partials' scope migration, without turning a backdrop
        // edit into implicit media/lockscreen scope writes.
        val backdropOnly = values.keys.all { it in backdropKeys }
        val hasScopeInput = values.keys.any { it in legacyKeys || it == MEDIA_KEY || it == LOCKSCREEN_KEY }
        val migrateScopes = !backdropOnly && hasScopeInput && MEDIA_KEY in keys && LOCKSCREEN_KEY in keys
        // Old full exports shared these values across both layers. A card-only
        // preset or any partial import must never acquire an implicit whole-window edit.
        val migrateBackdrop = !backdropMigrated && keys.containsAll(backdropKeys) &&
            values.keys.containsAll(legacySnapshotKeys)
        val resolved = if (migrateScopes || (EDGE_GAP_KEY in keys && hasPosition)) resolve(values) else values
        return resolved.filterKeys {
            it in keys && (it != EDGE_GAP_KEY || hasPosition) &&
                (it !in backdropKeys || it in values || migrateBackdrop)
        }
    }

    private fun isMigrated(values: Map<String, *>): Boolean =
        (values[VERSION_KEY] as? Number)?.toInt()?.let { it >= VERSION } == true

    private fun isBackdropMigrated(values: Map<String, *>): Boolean =
        (values[BACKDROP_VERSION_KEY] as? Number)?.toInt()?.let { it >= BACKDROP_VERSION } == true
}
