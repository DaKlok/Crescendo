package com.daklok.crescendo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.daklok.crescendo.R

/** Kreslenie widgetu na Android [Canvas] – písma Google Sans Flex a Material Symbols z aplikácie. */
class AndroidArtCanvas(private val canvas: Canvas, private val fonts: Fonts) : ArtCanvas {

    /** Písma sa načítajú raz (na pozadí) a opakovane použijú. */
    class Fonts(val display: Typeface, val text: Typeface, val icon: Typeface) {
        companion object {
            @Volatile
            private var cached: Fonts? = null

            fun get(context: Context): Fonts = cached ?: run {
                val r = context.resources
                fun load(id: Int, fallback: Typeface) = runCatching { r.getFont(id) }.getOrNull() ?: fallback
                Fonts(
                    display = load(R.font.widget_display, Typeface.create("sans-serif", Typeface.BOLD)),
                    text = load(R.font.widget_text, Typeface.create("sans-serif-medium", Typeface.NORMAL)),
                    icon = load(R.font.widget_icons, Typeface.DEFAULT),
                ).also { cached = it }
            }
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val bounds = Rect()

    private fun typeface(f: ArtFont) = when (f) {
        ArtFont.DISPLAY -> fonts.display
        ArtFont.TEXT -> fonts.text
        ArtFont.ICON -> fonts.icon
    }

    private fun setup(font: ArtFont, size: Float, tracking: Float): Paint = textPaint.apply {
        typeface = typeface(font)
        textSize = size
        letterSpacing = tracking
    }

    override fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.color = color
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, r, fill)
    }

    override fun fillPolygon(xs: FloatArray, ys: FloatArray, color: Int) {
        val p = Path()
        p.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) p.lineTo(xs[i], ys[i])
        p.close()
        fill.color = color
        canvas.drawPath(p, fill)
    }

    override fun strokePolyline(xs: FloatArray, ys: FloatArray, width: Float, color: Int, closed: Boolean) {
        val p = Path()
        p.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) p.lineTo(xs[i], ys[i])
        if (closed) p.close()
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawPath(p, stroke)
    }

    override fun text(s: String, x: Float, baseline: Float, font: ArtFont, size: Float, color: Int, tracking: Float) {
        val p = setup(font, size, tracking)
        p.color = color
        canvas.drawText(s, x, baseline, p)
    }

    override fun measure(s: String, font: ArtFont, size: Float, tracking: Float): Float =
        setup(font, size, tracking).measureText(s)

    override fun capHeight(font: ArtFont, size: Float): Float {
        val p = setup(font, size, 0f)
        val probe = if (font == ArtFont.ICON) "" else "H"
        p.getTextBounds(probe, 0, probe.length, bounds)
        return -bounds.top.toFloat()
    }
}
