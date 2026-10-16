package com.daklok.crescendo.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.daklok.crescendo.data.InkStroke
import com.daklok.crescendo.data.InkTool
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private const val HIGHLIGHT_ALPHA = 0.40f

/** Tlak 0..1 -> násobok hrúbky (0.5 = neutrálny, napr. prst). */
private fun widthFactor(pressure: Float): Float = 0.6f + 0.8f * pressure

/** Predpočítaná geometria ťahu pre konkrétnu veľkosť strany. */
class InkGeometry(
    val w: Float,
    val h: Float,
    val path: Path?,
    val dot: Offset?,
    val dotRadius: Float,
    val strokeWidth: Float,
)

/** Cache geometrie ťahov – ťahy sa neprepočítavajú pri každom prekreslení. */
class InkCache {
    private val map = HashMap<InkStroke, InkGeometry>()

    fun get(s: InkStroke, w: Float, h: Float): InkGeometry {
        val cached = map[s]
        if (cached != null && cached.w == w && cached.h == h) return cached
        val g = buildGeometry(s, w, h)
        map[s] = g
        return g
    }

    /** Zahodí geometriu ťahov, ktoré už na strane nie sú. */
    fun retainOnly(strokes: Collection<InkStroke>) {
        if (map.size <= strokes.size + 32) return
        val keep = strokes.toHashSet()
        map.keys.retainAll(keep)
    }
}

fun DrawScope.drawInk(s: InkStroke, cache: InkCache?) {
    val g = cache?.get(s, size.width, size.height) ?: buildGeometry(s, size.width, size.height)
    val color = Color(s.color)
    if (s.tool == InkTool.HIGHLIGHTER) {
        val col = color.copy(alpha = HIGHLIGHT_ALPHA)
        g.path?.let {
            drawPath(
                path = it,
                color = col,
                style = Stroke(width = g.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Multiply,
            )
        }
        g.dot?.let { drawCircle(col, radius = g.dotRadius, center = it, blendMode = BlendMode.Multiply) }
    } else {
        g.path?.let { drawPath(it, color) }
        g.dot?.let { drawCircle(color, radius = g.dotRadius, center = it) }
    }
}

private fun buildGeometry(s: InkStroke, w: Float, h: Float): InkGeometry {
    val n = s.count
    val base = (s.width * w).coerceAtLeast(1f)
    if (n == 0) return InkGeometry(w, h, null, null, 0f, base)

    // body v pixeloch
    val xs = FloatArray(n) { s.pts[it * 3] * w }
    val ys = FloatArray(n) { s.pts[it * 3 + 1] * h }
    val ps = FloatArray(n) { s.pts[it * 3 + 2] }

    var length = 0f
    for (i in 1 until n) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])

    // bodka (ťuk perom)
    if (n == 1 || length < base * 0.35f) {
        val r = if (s.tool == InkTool.HIGHLIGHTER) base / 2f else base * widthFactor(ps.max()) / 2f
        return InkGeometry(w, h, null, Offset(xs[0], ys[0]), r, base)
    }

    if (s.tool == InkTool.HIGHLIGHTER) {
        return InkGeometry(w, h, centerLinePath(xs, ys), null, 0f, base)
    }
    return InkGeometry(w, h, outlinePath(xs, ys, ps, base), null, 0f, base)
}

/** Hladká stredová čiara cez stredy úsečiek (kvadratické Bézierove krivky). */
private fun centerLinePath(xs: FloatArray, ys: FloatArray): Path {
    val n = xs.size
    val path = Path()
    path.moveTo(xs[0], ys[0])
    if (n == 2) {
        path.lineTo(xs[1], ys[1])
        return path
    }
    path.lineTo((xs[0] + xs[1]) / 2f, (ys[0] + ys[1]) / 2f)
    for (i in 1 until n - 1) {
        path.quadraticTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2f, (ys[i] + ys[i + 1]) / 2f)
    }
    path.lineTo(xs[n - 1], ys[n - 1])
    return path
}

/**
 * Obrys ťahu s premenlivou hrúbkou podľa tlaku: ľavý a pravý okraj vyhladené Bézierovými
 * krivkami, oba konce zaoblené. Výsledok je jedna vyplnená plocha – žiadne „korálky“ na spojoch.
 */
private fun outlinePath(xs: FloatArray, ys: FloatArray, ps: FloatArray, base: Float): Path {
    val n = xs.size

    // vyhladený tlak (kĺzavý priemer), aby hrúbka neskákala
    val radius = FloatArray(n)
    for (i in 0 until n) {
        var sum = 0f
        var cnt = 0
        for (j in max(0, i - 2)..minOf(n - 1, i + 2)) {
            sum += ps[j]; cnt++
        }
        radius[i] = base * widthFactor(sum / cnt) / 2f
    }

    val lx = FloatArray(n)
    val ly = FloatArray(n)
    val rx = FloatArray(n)
    val ry = FloatArray(n)
    var lastDx = 1f
    var lastDy = 0f
    for (i in 0 until n) {
        val a = max(0, i - 1)
        val b = minOf(n - 1, i + 1)
        var dx = xs[b] - xs[a]
        var dy = ys[b] - ys[a]
        val len = hypot(dx, dy)
        if (len < 1e-3f) {
            dx = lastDx; dy = lastDy
        } else {
            dx /= len; dy /= len
            lastDx = dx; lastDy = dy
        }
        // normála (otočenie smeru o 90°)
        val nx = -dy
        val ny = dx
        lx[i] = xs[i] + nx * radius[i]
        ly[i] = ys[i] + ny * radius[i]
        rx[i] = xs[i] - nx * radius[i]
        ry[i] = ys[i] - ny * radius[i]
    }

    val path = Path()
    path.fillType = PathFillType.NonZero

    // ľavý okraj dopredu
    path.moveTo(lx[0], ly[0])
    smoothThrough(path, lx, ly, forward = true)

    // koncová polkruhová čiapočka
    val endAngle = Math.toDegrees(atan2((ly[n - 1] - ys[n - 1]).toDouble(), (lx[n - 1] - xs[n - 1]).toDouble())).toFloat()
    val re = radius[n - 1]
    path.arcTo(Rect(xs[n - 1] - re, ys[n - 1] - re, xs[n - 1] + re, ys[n - 1] + re), endAngle, -180f, false)

    // pravý okraj späť
    smoothThrough(path, rx, ry, forward = false)

    // začiatočná polkruhová čiapočka
    val startAngle = Math.toDegrees(atan2((ry[0] - ys[0]).toDouble(), (rx[0] - xs[0]).toDouble())).toFloat()
    val rs = radius[0]
    path.arcTo(Rect(xs[0] - rs, ys[0] - rs, xs[0] + rs, ys[0] + rs), startAngle, -180f, false)

    path.close()
    return path
}

/** Pripojí k ceste hladkú krivku cez body (ľavý okraj dopredu alebo pravý odzadu). */
private fun smoothThrough(path: Path, xs: FloatArray, ys: FloatArray, forward: Boolean) {
    val n = xs.size
    fun x(i: Int) = if (forward) xs[i] else xs[n - 1 - i]
    fun y(i: Int) = if (forward) ys[i] else ys[n - 1 - i]
    if (!forward) path.lineTo(x(0), y(0))
    if (n == 2) {
        path.lineTo(x(1), y(1))
        return
    }
    path.lineTo((x(0) + x(1)) / 2f, (y(0) + y(1)) / 2f)
    for (i in 1 until n - 1) {
        path.quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f)
    }
    path.lineTo(x(n - 1), y(n - 1))
}
