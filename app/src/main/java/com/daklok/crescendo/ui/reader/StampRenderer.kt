package com.daklok.crescendo.ui.reader

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.daklok.crescendo.data.Stamp

/** Ponuka pečiatok po skupinách. Symboly začínajú „§“ a kreslia sa ako tvary. */
object StampCatalog {
    val DYNAMICS = listOf("pp", "p", "mp", "mf", "f", "ff", "sfz", "fp")
    val WORDS = listOf("§cresc", "§dim", "cresc.", "dim.", "rit.", "a tempo")
    val SYMBOLS = listOf("§fermata", "§accent", "§breath", "§up", "§down", "§eyes")
    val FINGERS = listOf("1", "2", "3", "4", "5")

    val GROUPS = listOf(DYNAMICS, WORDS, SYMBOLS, FINGERS)

    private val dynamicSet = setOf("ppp", "pp", "p", "mp", "mf", "f", "ff", "fff", "sfz", "sf", "fp", "rfz", "sffz")
    private val wordSet = setOf("cresc.", "dim.", "rit.", "rall.", "a tempo", "accel.", "dolce", "legato", "stacc.", "poco", "molto")

    fun isDynamic(t: String) = t in dynamicSet
    fun isWord(t: String) = t in wordSet
}

private val dynamicFace: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
private val wordFace: Typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
private val fingerFace: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
private val textFace: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

/** Vykreslí pečiatku; [dx], [dy] = posun v pixeloch (pri ťahaní). */
fun DrawScope.drawStamp(st: Stamp, dx: Float = 0f, dy: Float = 0f) {
    val s = st.size * size.width
    val cx = st.x * size.width + dx
    val cy = st.y * size.height + dy
    drawStampAt(st.text, Color(st.color), cx, cy, s)
}

/** Kreslenie pečiatky so stredom v (cx, cy) a výškou [s] px – aj pre náhľady v ponuke. */
fun DrawScope.drawStampAt(text: String, color: Color, cx: Float, cy: Float, s: Float) {
    if (text.startsWith("§")) {
        drawSymbol(text, color, cx, cy, s)
        return
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = s
        typeface = when {
            StampCatalog.isDynamic(text) -> dynamicFace
            StampCatalog.isWord(text) -> wordFace
            text.length <= 2 && text.all { it.isDigit() } -> fingerFace
            else -> textFace
        }
        if (StampCatalog.isDynamic(text)) textSize = s * 1.25f
    }
    val fm = paint.fontMetrics
    val baseline = cy - (fm.ascent + fm.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
}

private fun DrawScope.drawSymbol(kind: String, color: Color, cx: Float, cy: Float, s: Float) {
    val w = s * 0.1f
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        "§fermata" -> {
            val p = Path().apply {
                moveTo(cx - 0.6f * s, cy + 0.25f * s)
                cubicTo(cx - 0.6f * s, cy - 0.55f * s, cx + 0.6f * s, cy - 0.55f * s, cx + 0.6f * s, cy + 0.25f * s)
            }
            drawPath(p, color, style = Stroke(width = w * 1.3f, cap = StrokeCap.Round))
            drawCircle(color, radius = w * 1.1f, center = Offset(cx, cy + 0.15f * s))
        }
        "§accent" -> {
            val p = Path().apply {
                moveTo(cx - 0.55f * s, cy - 0.3f * s)
                lineTo(cx + 0.55f * s, cy)
                lineTo(cx - 0.55f * s, cy + 0.3f * s)
            }
            drawPath(p, color, style = stroke)
        }
        "§breath" -> {
            // dychová značka – „čiarka“
            drawCircle(color, radius = 0.17f * s, center = Offset(cx, cy - 0.15f * s))
            val p = Path().apply {
                moveTo(cx + 0.15f * s, cy - 0.1f * s)
                quadraticTo(cx + 0.15f * s, cy + 0.35f * s, cx - 0.15f * s, cy + 0.5f * s)
            }
            drawPath(p, color, style = Stroke(width = w * 1.2f, cap = StrokeCap.Round))
        }
        "§up" -> {
            val p = Path().apply {
                moveTo(cx - 0.35f * s, cy - 0.45f * s)
                lineTo(cx, cy + 0.45f * s)
                lineTo(cx + 0.35f * s, cy - 0.45f * s)
            }
            drawPath(p, color, style = stroke)
        }
        "§down" -> {
            val p = Path().apply {
                moveTo(cx - 0.4f * s, cy + 0.4f * s)
                lineTo(cx - 0.4f * s, cy - 0.35f * s)
                lineTo(cx + 0.4f * s, cy - 0.35f * s)
                lineTo(cx + 0.4f * s, cy + 0.4f * s)
            }
            drawPath(p, color, style = stroke)
            drawLine(color, Offset(cx - 0.4f * s, cy - 0.32f * s), Offset(cx + 0.4f * s, cy - 0.32f * s), strokeWidth = w * 2.2f)
        }
        "§cresc", "§dim" -> {
            val open = if (kind == "§cresc") 1f else -1f
            val p = Path().apply {
                moveTo(cx + open * 1.1f * s, cy - 0.28f * s)
                lineTo(cx - open * 1.1f * s, cy)
                lineTo(cx + open * 1.1f * s, cy + 0.28f * s)
            }
            drawPath(p, color, style = stroke)
        }
        "§eyes" -> {
            // „pozor“ – okuliare
            val r = 0.32f * s
            drawCircle(color, r, Offset(cx - 0.45f * s, cy), style = stroke)
            drawCircle(color, r, Offset(cx + 0.45f * s, cy), style = stroke)
            drawLine(color, Offset(cx - 0.13f * s, cy - 0.05f * s), Offset(cx + 0.13f * s, cy - 0.05f * s), strokeWidth = w)
            drawLine(color, Offset(cx - 0.77f * s, cy - 0.1f * s), Offset(cx - 1.0f * s, cy - 0.3f * s), strokeWidth = w)
            drawLine(color, Offset(cx + 0.77f * s, cy - 0.1f * s), Offset(cx + 1.0f * s, cy - 0.3f * s), strokeWidth = w)
        }
    }
}
