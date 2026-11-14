// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

/** Native compositor luma is brightness evidence, not a pixel color or exact WCAG luminance. */
internal class BannerContrastPolicy {
    enum class Tone { LIGHT_TEXT, DARK_TEXT }
    private var changedAt = Long.MIN_VALUE
    var tone: Tone? = null
        private set

    fun select(luma: Float, now: Long): Tone? {
        if (!luma.isFinite() || luma !in 0f..1f) return tone
        val before = tone
        val next = when (before) {
            null -> if (luma < .46f) Tone.LIGHT_TEXT else Tone.DARK_TEXT
            Tone.LIGHT_TEXT -> if (luma >= .54f) Tone.DARK_TEXT else before
            Tone.DARK_TEXT -> if (luma <= .40f) Tone.LIGHT_TEXT else before
        }
        if (before == null || next != before && now >= changedAt && now - changedAt >= HOLD_MS) {
            tone = next
            changedAt = now
        }
        return tone
    }

    companion object {
        const val KEY = "notification_banner_readability"
        const val HOLD_MS = 600L
        fun role(resourcePackage: String, name: String): Boolean? {
            if (resourcePackage != "android" && resourcePackage != "com.android.systemui") return null
            return when (name) {
                "title", "title_big", "conversation_title", "message_name" -> true
                "text", "text2", "big_text", "message_text", "header_text", "app_name_text", "time" -> false
                else -> null
            }
        }
        fun color(tone: Tone, primary: Boolean): Int = when (tone) {
            Tone.LIGHT_TEXT -> if (primary) 0xffffffff.toInt() else 0xffeeeeF2.toInt()
            Tone.DARK_TEXT -> if (primary) 0xff111114.toInt() else 0xff29292d.toInt()
        }
        fun shadow(textColor: Int): Int {
            val brightness = ((textColor shr 16 and 255) * .2126f +
                (textColor shr 8 and 255) * .7152f + (textColor and 255) * .0722f)
            return if (brightness >= 128f) 0xcc000000.toInt() else 0xccffffff.toInt()
        }
    }
}

/** At most eight scalars in one short burst; an old renderer/window callback cannot join a new one. */
internal class BannerSampleWindow {
    private var serial = 0L
    private var open = false
    private var startedAt = Long.MIN_VALUE
    private val values = ArrayList<Float>(8)
    fun delay(now: Long): Long = if (startedAt == Long.MIN_VALUE) 0L
        else (INTERVAL_MS - (now - startedAt)).coerceAtLeast(0L)
    fun start(now: Long): Long {
        check(delay(now) == 0L)
        serial++
        open = true
        startedAt = now
        values.clear()
        return serial
    }
    fun collect(ticket: Long, luma: Float, now: Long): Boolean {
        if (!open || ticket != serial || now < startedAt || now - startedAt > BURST_MS ||
            !luma.isFinite() || luma !in 0f..1f || values.size >= 8) return false
        values += luma
        return true
    }
    fun finish(ticket: Long): Float? {
        if (!open || ticket != serial) return null
        open = false
        val sorted = values.sorted()
        values.clear()
        return sorted.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
    }
    fun cancel() { serial++; open = false; values.clear() }
    companion object { const val INTERVAL_MS = 750L; const val BURST_MS = 250L }
}

/** Restore only the value still owned by this edit; a native rewrite remains authoritative. */
internal class BannerOwnedValue<T>(initial: T) {
    var native = initial
        private set
    private var written: T? = null
    fun nativeWrite(value: T) { native = value; written = null }
    fun wrote(value: T) { written = value }
    fun release(current: T, same: (T, T) -> Boolean): T =
        written?.let { if (same(current, it)) native else current } ?: current
}
