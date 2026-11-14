// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.notifications

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.TextAppearanceSpan
import android.widget.TextView

/** Native edits replace the restore baseline; only color-bearing spans are wrapped. */
internal class BannerTextLease(val view: TextView, val primary: Boolean,
                              private val writing: (() -> Unit) -> Unit) {
    private data class Shadow(val radius: Float, val dx: Float, val dy: Float, val color: Int)
    private fun shadow() = Shadow(view.shadowRadius, view.shadowDx, view.shadowDy, view.shadowColor)
    private val colors = BannerOwnedValue(view.textColors)
    private val shadows = BannerOwnedValue(shadow())
    private val text = BannerOwnedValue<CharSequence>(nativeText(view.text))
    private var tone: BannerContrastPolicy.Tone? = null
    private var textRevision = 0L
    private var appliedTextRevision = -1L
    private var appliedTone: BannerContrastPolicy.Tone? = null

    fun nativeColors() { colors.nativeWrite(view.textColors); apply(tone) }
    fun nativeShadow() { shadows.nativeWrite(shadow()); apply(tone) }
    fun nativeText() {
        text.nativeWrite(nativeText(view.text))
        textRevision++
        apply(tone)
    }

    fun apply(next: BannerContrastPolicy.Tone?) {
        tone = next
        writing {
            val color = next?.let { BannerContrastPolicy.color(it, primary) }
            val desired = color?.let(ColorStateList::valueOf) ?: colors.native
            if (view.textColors != desired) view.setTextColor(desired)
            colors.wrote(view.textColors)
            if (textRevision != appliedTextRevision || next != appliedTone) {
                val source = text.native
                val adjusted = if (color != null) adjustedText(source, color) else source
                // Plain template text needs only setTextColor, avoiding a new layout.
                if (adjusted !== source || next == null && view.text !== source && appliedTone != null) {
                    view.text = adjusted
                    text.wrote(view.text)
                }
                appliedTone = next
                appliedTextRevision = textRevision
            }
            val shadowColor = BannerContrastPolicy.shadow(color ?: colors.native.defaultColor)
            val radius = 1.25f * view.resources.displayMetrics.density
            view.setShadowLayer(radius, 0f, 0f, shadowColor)
            shadows.wrote(shadow())
        }
    }

    fun restore() = writing {
        val restoredColors = colors.release(view.textColors) { a, b -> a === b }
        if (restoredColors !== view.textColors) view.setTextColor(restoredColors)
        val restoredText = text.release(view.text) { a, b -> a === b }
        if (restoredText !== view.text) view.text = restoredText
        val restoredShadow = shadows.release(shadow()) { a, b -> a == b }
        if (restoredShadow != shadow()) view.setShadowLayer(restoredShadow.radius, restoredShadow.dx,
            restoredShadow.dy, restoredShadow.color)
    }

    /** Preserve all metric/link/font behavior while replacing just TextAppearanceSpan's ink. */
    private class Appearance(val native: TextAppearanceSpan, private val color: Int) : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) = native.updateMeasureState(paint)
        override fun updateDrawState(paint: TextPaint) { native.updateDrawState(paint); paint.color = color }
    }
    private class Foreground(val native: ForegroundColorSpan, color: Int) : ForegroundColorSpan(color)

    companion object {
        private fun nativeText(value: CharSequence): CharSequence {
            if (value !is Spanned) return value
            val copy = SpannableString(value)
            for (span in copy.getSpans(0, copy.length, Any::class.java)) {
                val original = when (span) { is Appearance -> span.native; is Foreground -> span.native; else -> null }
                if (original != null) {
                    val start = copy.getSpanStart(span); val end = copy.getSpanEnd(span); val flags = copy.getSpanFlags(span)
                    copy.removeSpan(span); copy.setSpan(original, start, end, flags)
                }
            }
            return SpannedString(copy)
        }

        private fun adjustedText(value: CharSequence, color: Int): CharSequence {
            if (value !is Spanned) return value
            val copy = SpannableString(value)
            var changed = false
            for (span in copy.getSpans(0, copy.length, Any::class.java)) {
                val replacement = when (span) {
                    is ForegroundColorSpan -> Foreground(span, color)
                    is TextAppearanceSpan -> if (span.textColor != null) Appearance(span, color) else null
                    else -> null
                } ?: continue
                val start = copy.getSpanStart(span); val end = copy.getSpanEnd(span); val flags = copy.getSpanFlags(span)
                copy.removeSpan(span); copy.setSpan(replacement, start, end, flags)
                changed = true
            }
            return if (changed) SpannedString(copy) else value
        }
    }
}
