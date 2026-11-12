// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import kotlin.math.roundToInt

data class LiteSetting(
    val key: String,
    val title: String,
    val group: String,
    val defaultValue: Any,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 1f,
    val summary: String = "",
) {
    val isToggle: Boolean get() = defaultValue is Boolean

    fun validate(value: Any): Any {
        if (isToggle) {
            require(value is Boolean) { "Invalid boolean: $key" }
            return value
        }
        require(value is Number) { "Invalid number: $key" }
        val number = value.toFloat()
        require(number.isFinite() && number >= min && number <= max) { "Out of range: $key" }
        return (min + ((number - min) / step).roundToInt() * step).coerceIn(min, max)
    }

    companion object {
        fun toggle(key: String, title: String, group: String, default: Boolean = false, summary: String = "") =
            LiteSetting(key, title, group, default, summary = summary)

        fun number(key: String, title: String, group: String, default: Float, min: Float, max: Float,
                   step: Float = 1f, summary: String = "") =
            LiteSetting(key, title, group, default, min, max, step, summary)
    }
}

object LiteCatalog {
    val entries: List<LiteSetting> by lazy {
        (GlassSettingsSpec.entries + NotificationSettingsSpec.entries + ControlVolumeSettingsSpec.entries).also {
            check(it.map(LiteSetting::key).distinct().size == it.size) { "Duplicate settings keys" }
            it.forEach { spec -> spec.validate(spec.defaultValue) }
        }
    }
}
