// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import org.json.JSONObject
import java.util.concurrent.Executors

const val PREFERENCE_GROUP = "hyperchange_lite"
private const val GLASS_PRESET_UNDO = "_glass_preset_trial_undo_v1"

/** Local persistence succeeded; callers must not describe the edit as lost. */
internal class SettingsSyncException(cause: Exception) :
    IllegalStateException("Settings saved locally; module sync failed", cause)

/** The app owns its settings. Binding never replaces edits made while disconnected. */
class LiteApplication : Application(), XposedServiceHelper.OnServiceListener {
    @Volatile internal var service: XposedService? = null
    @Volatile internal var syncFailed = false
    internal val settingsLock = Any()
    internal val settingsExecutor = Executors.newSingleThreadExecutor()
    internal val observers = mutableSetOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        synchronized(settingsLock) {
            migrateLocal(getSharedPreferences(PREFERENCE_GROUP, MODE_PRIVATE))
        }
        // OEM glass methods are hidden APIs. No access to another app's data is needed.
        runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/View;") }
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        // Binding publishes the full catalog through IPC and durable commits.
        // Keep it on the same serial queue as edits, never on the App UI thread.
        settingsExecutor.execute {
            synchronized(settingsLock) {
                this.service = service
                runCatching {
                    val local = getSharedPreferences(PREFERENCE_GROUP, MODE_PRIVATE)
                    val remote = service.getRemotePreferences(PREFERENCE_GROUP)
                    if (!local.getBoolean("_initialized", false)) {
                        val restored = SettingsMigration.forBinding(local.all, remote.all)
                        val edit = local.edit()
                        for (spec in LiteCatalog.entries) {
                            val candidate = restored[spec.key] ?: spec.defaultValue
                            put(edit, spec, runCatching { spec.validate(candidate) }.getOrDefault(spec.defaultValue))
                        }
                        check(edit.markMigrations()
                            .putBoolean("_initialized", true).commit()) { "Could not restore settings" }
                    }
                    migrateLocal(local)
                    publish(local, remote)
                    syncFailed = false
                }.onFailure {
                    syncFailed = true
                    android.util.Log.w("HyperChangeLite", "Preference connection failed", it)
                }
            }
            notifyObservers()
        }
    }

    override fun onServiceDied(service: XposedService) {
        settingsExecutor.execute {
            synchronized(settingsLock) { if (this.service === service) this.service = null }
            notifyObservers()
        }
    }

    internal fun notifyObservers() {
        // An old Activity may finish a queued write after rotation. Notify the current
        // repository owners on the main thread instead of only that old Activity.
        main.post { observers.toList().forEach { it() } }
    }

    internal fun publish(local: SharedPreferences, remote: SharedPreferences) {
        val resolved = SettingsMigration.resolve(local.all)
        val edit = remote.edit()
        for (spec in LiteCatalog.entries) {
            val value = resolved[spec.key] ?: spec.defaultValue
            put(edit, spec, runCatching { spec.validate(value) }.getOrDefault(spec.defaultValue))
        }
        check(edit.markMigrations().commit()) {
            "Remote settings write was not accepted"
        }
    }
}

/** Persist only the new keys until first binding; opening the app is not a local edit. */
private fun migrateLocal(preferences: SharedPreferences) {
    val original = preferences.all
    val resolved = SettingsMigration.resolve(original)
    val keys = listOf(SettingsMigration.MEDIA_KEY, SettingsMigration.LOCKSCREEN_KEY,
        SettingsMigration.EDGE_GAP_KEY, SettingsMigration.VERSION_KEY, SettingsMigration.BACKDROP_VERSION_KEY) +
        SettingsMigration.backdropKeys
    val ownsLocalState = SettingsMigration.hasLocalState(original)
    if (keys.all { original[it] == resolved[it] } && (!ownsLocalState || original["_initialized"] == true)) return
    val edit = preferences.edit()
        .putBoolean(SettingsMigration.MEDIA_KEY, resolved[SettingsMigration.MEDIA_KEY] as Boolean)
        .putBoolean(SettingsMigration.LOCKSCREEN_KEY, resolved[SettingsMigration.LOCKSCREEN_KEY] as Boolean)
        .putFloat(SettingsMigration.EDGE_GAP_KEY, resolved[SettingsMigration.EDGE_GAP_KEY] as Float)
        .markMigrations()
    SettingsMigration.backdropValueKeys.forEach { key -> edit.putFloat(key, resolved[key] as Float) }
    edit.putBoolean(SettingsMigration.BACKDROP_ENABLED_KEY, resolved[SettingsMigration.BACKDROP_ENABLED_KEY] as Boolean)
    if (ownsLocalState) edit.putBoolean("_initialized", true)
    check(edit.commit()) { "Could not migrate settings" }
}

internal fun put(edit: SharedPreferences.Editor, spec: LiteSetting, value: Any) {
    if (spec.isToggle) edit.putBoolean(spec.key, value as Boolean)
    else edit.putFloat(spec.key, (value as Number).toFloat())
}

private fun SharedPreferences.Editor.markMigrations(): SharedPreferences.Editor =
    putInt(SettingsMigration.VERSION_KEY, SettingsMigration.VERSION)
        .putInt(SettingsMigration.BACKDROP_VERSION_KEY, SettingsMigration.BACKDROP_VERSION)

class SettingsRepository(context: Context) {
    private val app = context.applicationContext as LiteApplication
    val preferences: SharedPreferences = app.getSharedPreferences(PREFERENCE_GROUP, Context.MODE_PRIVATE)
    var onChange: (() -> Unit)? = null
    val connected: Boolean get() = app.service != null
    val syncFailed: Boolean get() = connected && app.syncFailed
    val hasGlassPresetUndo: Boolean get() = preferences.contains(GLASS_PRESET_UNDO)
    private val observer: () -> Unit = { onChange?.invoke() }

    init {
        // Application.onCreate already migrated local state. Do not wait on the
        // publication lock from Activity creation while background IPC is pending.
        app.observers.add(observer)
    }

    fun set(spec: LiteSetting, value: Any) = synchronized(app.settingsLock) {
        val valid = spec.validate(value)
        val edit = preferences.edit().putBoolean("_initialized", true)
            .markMigrations()
        put(edit, spec, valid)
        check(edit.commit()) { "Could not save settings" }
        sync()
    }

    fun reset() = applyValues(SettingsCodec.defaults(LiteCatalog.entries), clearMaterialTrial = true)

    /** Keep the first pre-trial snapshot while switching between built-in recipes. */
    fun applyGlassPreset(preset: GlassPreset) = synchronized(app.settingsLock) {
        require(preset.values.keys == glassPresetKeys) { "Invalid material preset" }
        val specs = LiteCatalog.entries.filter { it.key in glassPresetKeys }
        val values = specs.associate { it.key to it.validate(preset.values.getValue(it.key)) }
        val edit = preferences.edit().putBoolean("_initialized", true)
            .markMigrations()
        if (!hasGlassPresetUndo) {
            edit.putString(GLASS_PRESET_UNDO, SettingsCodec.encode(specs, preferences.all))
        }
        specs.forEach { put(edit, it, values.getValue(it.key)) }
        check(edit.commit()) { "Could not save material preset" }
        sync()
    }

    /** Restore material parameters only; never re-import compatibility scopes. */
    fun restoreBeforeGlassPresets() = synchronized(app.settingsLock) {
        val saved = preferences.getString(GLASS_PRESET_UNDO, null) ?: return@synchronized
        val specs = LiteCatalog.entries.filter { it.key in glassPresetKeys }
        val values = SettingsCodec.decode(specs, saved)
        require(values.keys == glassPresetKeys) { "Invalid material backup" }
        val edit = preferences.edit().remove(GLASS_PRESET_UNDO)
        specs.forEach { put(edit, it, values.getValue(it.key)) }
        check(edit.commit()) { "Could not restore material settings" }
        sync()
    }

    /** The material-page reset has the same card-only boundary as a built-in recipe. */
    fun resetGlassMaterial() = applyValues(
        SettingsCodec.defaults(LiteCatalog.entries.filter { it.key in glassPresetKeys }), clearMaterialTrial = true,
    )

    fun exportJson(): String = SettingsCodec.encode(LiteCatalog.entries, preferences.all)

    fun importJson(text: String): Int {
        val values = SettingsCodec.decode(LiteCatalog.entries, text)
        applyValues(values, clearMaterialTrial = true)
        return values.size
    }

    private fun applyValues(values: Map<String, Any>, clearMaterialTrial: Boolean = false) = synchronized(app.settingsLock) {
        val edit = preferences.edit().putBoolean("_initialized", true)
            .markMigrations()
        for (spec in LiteCatalog.entries) values[spec.key]?.let { put(edit, spec, it) }
        if (clearMaterialTrial) edit.remove(GLASS_PRESET_UNDO)
        check(edit.commit()) { "Could not save settings" }
        sync()
    }

    private fun sync() {
        try {
            app.service?.let {
                // Republish the full local state so a successful edit also retries earlier failures.
                app.publish(preferences, it.getRemotePreferences(PREFERENCE_GROUP))
                app.syncFailed = false
            }
        } catch (error: Exception) {
            app.syncFailed = true
            throw SettingsSyncException(error)
        } finally { app.notifyObservers() }
    }

    fun close() { app.observers.remove(observer); onChange = null }
}

/** Decode and validate the entire document before changing any preference. */
object SettingsCodec {
    /** Reset stores the same canonical values that publishing and importing accept. */
    fun defaults(specs: List<LiteSetting>): Map<String, Any> =
        specs.associate { it.key to it.validate(it.defaultValue) }

    fun encode(specs: List<LiteSetting>, values: Map<String, *>): String {
        val resolved = SettingsMigration.resolve(values)
        val settings = JSONObject()
        specs.forEach { settings.put(it.key, it.validate(resolved[it.key] ?: it.defaultValue)) }
        return JSONObject().put("format", "hyperchange-lite").put("version", 1)
            .apply {
                if (specs.any { it.key in SettingsMigration.backdropKeys })
                    put(SettingsMigration.BACKDROP_JSON_VERSION_KEY, SettingsMigration.BACKDROP_VERSION)
            }.put("settings", settings).toString(2)
    }

    fun decode(specs: List<LiteSetting>, text: String): Map<String, Any> {
        require(text.length <= 256 * 1024) { "Preset is too large" }
        val root = JSONObject(text)
        require(root.optString("format") == "hyperchange-lite" && root.optInt("version") == 1) {
            "Unsupported preset format"
        }
        val settings = root.getJSONObject("settings")
        val backdropMigrated = if (root.has(SettingsMigration.BACKDROP_JSON_VERSION_KEY)) {
            val raw = root.get(SettingsMigration.BACKDROP_JSON_VERSION_KEY)
            require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() >= SettingsMigration.BACKDROP_VERSION) {
                "Invalid backdrop migration version"
            }
            true
        } else false
        val validated = buildMap {
            specs.forEach { spec -> if (settings.has(spec.key)) put(spec.key, spec.validate(settings.get(spec.key))) }
            if (specs.any { it.key == SettingsMigration.EDGE_GAP_KEY } &&
                !settings.has(SettingsMigration.EDGE_GAP_KEY) && settings.has(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY)) {
                val raw = settings.get(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY)
                require(raw is Number) { "Invalid number: ${SettingsMigration.LEGACY_VOLUME_OFFSET_KEY}" }
                val offset = raw.toDouble()
                require(offset.isFinite() && offset in -50.0..50.0) {
                    "Out of range: ${SettingsMigration.LEGACY_VOLUME_OFFSET_KEY}"
                }
                // Keep the validated alias through migration so old partial presets retain
                // their former combined-scope defaults; forPreset removes the alias.
                put(SettingsMigration.LEGACY_VOLUME_OFFSET_KEY, offset.toFloat())
            }
        }
        return SettingsMigration.forPreset(specs, validated, backdropMigrated).mapValues { (key, value) ->
            specs.first { it.key == key }.validate(value)
        }
    }
}
