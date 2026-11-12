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
        // OEM glass methods are hidden APIs. No access to another app's data is needed.
        runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/View;") }
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        main.post {
            synchronized(settingsLock) {
                this.service = service
                runCatching {
                    val local = getSharedPreferences(PREFERENCE_GROUP, MODE_PRIVATE)
                    val remote = service.getRemotePreferences(PREFERENCE_GROUP)
                    if (!local.getBoolean("_initialized", false)) {
                        val edit = local.edit()
                        for (spec in LiteCatalog.entries) {
                            val candidate = remote.all[spec.key] ?: spec.defaultValue
                            put(edit, spec, runCatching { spec.validate(candidate) }.getOrDefault(spec.defaultValue))
                        }
                        check(edit.putBoolean("_initialized", true).commit()) { "Could not restore settings" }
                    }
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
        main.post {
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
        val edit = remote.edit()
        for (spec in LiteCatalog.entries) {
            val value = local.all[spec.key] ?: spec.defaultValue
            put(edit, spec, runCatching { spec.validate(value) }.getOrDefault(spec.defaultValue))
        }
        check(edit.commit()) { "Remote settings write was not accepted" }
    }
}

internal fun put(edit: SharedPreferences.Editor, spec: LiteSetting, value: Any) {
    if (spec.isToggle) edit.putBoolean(spec.key, value as Boolean)
    else edit.putFloat(spec.key, (value as Number).toFloat())
}

class SettingsRepository(context: Context) {
    private val app = context.applicationContext as LiteApplication
    val preferences: SharedPreferences = app.getSharedPreferences(PREFERENCE_GROUP, Context.MODE_PRIVATE)
    var onChange: (() -> Unit)? = null
    val connected: Boolean get() = app.service != null
    val syncFailed: Boolean get() = connected && app.syncFailed
    private val observer: () -> Unit = { onChange?.invoke() }

    init { app.observers.add(observer) }

    fun set(spec: LiteSetting, value: Any) = synchronized(app.settingsLock) {
        val valid = spec.validate(value)
        val edit = preferences.edit().putBoolean("_initialized", true)
        put(edit, spec, valid)
        check(edit.commit()) { "Could not save settings" }
        sync()
    }

    fun reset() = applyValues(LiteCatalog.entries.associate { it.key to it.defaultValue })

    fun exportJson(): String = SettingsCodec.encode(LiteCatalog.entries, preferences.all)

    fun importJson(text: String): Int {
        val values = SettingsCodec.decode(LiteCatalog.entries, text)
        applyValues(values)
        return values.size
    }

    private fun applyValues(values: Map<String, Any>) = synchronized(app.settingsLock) {
        val edit = preferences.edit().putBoolean("_initialized", true)
        for (spec in LiteCatalog.entries) values[spec.key]?.let { put(edit, spec, it) }
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
            throw error
        } finally { app.notifyObservers() }
    }

    fun close() { app.observers.remove(observer); onChange = null }
}

/** Decode and validate the entire document before changing any preference. */
object SettingsCodec {
    fun encode(specs: List<LiteSetting>, values: Map<String, *>): String {
        val settings = JSONObject()
        specs.forEach { settings.put(it.key, it.validate(values[it.key] ?: it.defaultValue)) }
        return JSONObject().put("format", "hyperchange-lite").put("version", 1)
            .put("settings", settings).toString(2)
    }

    fun decode(specs: List<LiteSetting>, text: String): Map<String, Any> {
        require(text.length <= 256 * 1024) { "Preset is too large" }
        val root = JSONObject(text)
        require(root.optString("format") == "hyperchange-lite" && root.optInt("version") == 1) {
            "Unsupported preset format"
        }
        val settings = root.getJSONObject("settings")
        return buildMap {
            specs.forEach { spec -> if (settings.has(spec.key)) put(spec.key, spec.validate(settings.get(spec.key))) }
        }
    }
}
