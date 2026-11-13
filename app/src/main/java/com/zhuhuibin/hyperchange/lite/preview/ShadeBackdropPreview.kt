// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import com.zhuhuibin.hyperchange.lite.ui.localizedText

/** A whole pulled-down panel over App content. Foreground cards never receive this blur. */
internal class ShadeBackdropPreview(context: Context) : FrameLayout(context) {
    private val app = AppLayer(context)
    private val scrim = View(context)
    private val panel = PanelLayer(context)
    private var applied: List<Any>? = null

    init {
        addView(app, LayoutParams(-1, -1))
        addView(scrim, LayoutParams(-1, -1))
        addView(panel, LayoutParams(-1, -1))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun update(enabled: Boolean, blur: Int, dim: Int, night: Boolean) {
        val values = listOf(enabled, blur, dim, night)
        if (applied == values) return
        applied = values
        val state = shadeBackdropPreviewState(enabled, blur, dim)
        app.night = night
        panel.night = night
        val radius = 8f * resources.displayMetrics.density * state.blurRatio
        app.setRenderEffect(if (radius > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
        scrim.setBackgroundColor(Color.argb((state.dimRatio * 160f).toInt().coerceIn(0, 255), 0, 0, 0))
        app.invalidate(); panel.invalidate()
    }

    fun setBackdrop(image: Bitmap?, style: Int) {
        app.image = image
        app.style = style
        app.invalidate()
    }

    private class AppLayer(context: Context) : View(context) {
        var night = false
        var image: Bitmap? = null
        var style = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val matrix = Matrix()
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val bitmap = image
            if (bitmap != null && !bitmap.isRecycled) {
                val scale = maxOf(w / bitmap.width, h / bitmap.height)
                matrix.reset(); matrix.setScale(scale, scale)
                matrix.postTranslate((w - bitmap.width * scale) / 2f, (h - bitmap.height * scale) / 2f)
                canvas.drawBitmap(bitmap, matrix, paint)
                return
            }
            val colors = when (style) {
                1 -> intArrayOf(0xff594173.toInt(), 0xffe4b898.toInt())
                2 -> intArrayOf(0xffe4d3bc.toInt(), 0xff9ac8bf.toInt())
                3 -> intArrayOf(0xff263346.toInt(), 0xff9babc0.toInt())
                else -> intArrayOf(0xff396575.toInt(), 0xffb8d6e7.toInt())
            }
            paint.shader = LinearGradient(0f, 0f, w, h, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            paint.color = 0xffd9b187.toInt(); canvas.drawCircle(w * .8f, h * .25f, h * .6f, paint)
            paint.color = if (night) 0xff19232e.toInt() else 0xff5b8d87.toInt()
            canvas.drawCircle(w * .15f, h * 1.1f, w * .65f, paint)
            val pad = w * .055f
            paint.textSize = h * .12f; paint.color = Color.WHITE
            canvas.drawText("24°", pad, h * .24f, paint)
            repeat(3) { i ->
                paint.color = if (night) 0xff344255.toInt() else 0xffe9f0f3.toInt()
                rect.set(pad, h * (.34f + i * .19f), w - pad, h * (.49f + i * .19f))
                canvas.drawRoundRect(rect, h * .035f, h * .035f, paint)
                paint.color = intArrayOf(0xffcf7b5b.toInt(), 0xff6d91b6.toInt(), 0xff73a285.toInt())[i]
                canvas.drawCircle(pad * 1.7f, rect.centerY(), h * .038f, paint)
                paint.color = if (night) 0xff9aaabd.toInt() else 0xff8a9da7.toInt()
                rect.set(pad * 2.4f, rect.centerY() - h * .01f, w * (.66f - i * .04f), rect.centerY() + h * .01f)
                canvas.drawRoundRect(rect, h * .01f, h * .01f, paint)
            }
        }
    }

    private class PanelLayer(context: Context) : View(context) {
        var night = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val title = localizedText(context, "通知")
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val pad = w * .055f
            paint.color = Color.WHITE; paint.textSize = h * .075f
            canvas.drawText("9:41", pad, h * .13f, paint)
            paint.textSize = h * .055f; canvas.drawText(title, pad, h * .255f, paint)
            repeat(2) { i ->
                rect.set(pad, h * (.32f + i * .245f), w - pad, h * (.52f + i * .245f))
                paint.color = if (night) 0xe03b424b.toInt() else 0xf2ffffff.toInt()
                canvas.drawRoundRect(rect, h * .045f, h * .045f, paint)
                paint.color = if (i == 0) 0xff639c83.toInt() else 0xff779bcb.toInt()
                canvas.drawCircle(pad * 1.65f, rect.centerY(), h * .045f, paint)
                paint.color = if (night) 0xffb7c0c8.toInt() else 0xff8c979f.toInt()
                val center = rect.centerY()
                rect.set(pad * 2.4f, center - h * .035f, w * .63f, center - h * .015f)
                canvas.drawRoundRect(rect, h * .01f, h * .01f, paint)
                rect.set(pad * 2.4f, center + h * .015f, w * .8f, center + h * .03f)
                canvas.drawRoundRect(rect, h * .0075f, h * .0075f, paint)
            }
            paint.color = 0xffeeeeee.toInt()
            rect.set(w * .43f, h * .9f, w * .57f, h * .915f)
            canvas.drawRoundRect(rect, h * .008f, h * .008f, paint)
        }
    }
}

/** Preview uses a representative local blur, never pretends to be a WindowManager surface. */
internal data class ShadeBackdropPreviewState(val blurRatio: Float, val dimRatio: Float)
internal fun shadeBackdropPreviewState(enabled: Boolean, blur: Int, dim: Int): ShadeBackdropPreviewState =
    ShadeBackdropPreviewState((if (enabled) blur else 100).coerceIn(0, 200) / 100f,
        (if (enabled) dim else 100).coerceIn(0, 200) / 100f)
