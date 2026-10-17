package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.daklok.crescendo.data.InkStroke
import com.daklok.crescendo.data.InkTool
import com.daklok.crescendo.data.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val PALM_REJECT_MS = 800L

private const val MODE_DRAW = 0
private const val MODE_ERASE = 1
private const val MODE_LASSO = 2
private const val MODE_SHAPE = 3
private const val MODE_MOVE = 4

/**
 * Vrstva poznámok nad jednou stranou. Vždy vykresľuje uložené ťahy;
 * kreslenie (pero / zvýrazňovač / tvary / guma / lasso) je aktívne len keď je zapnutý režim poznámok.
 * Vrstva zaberá presne plochu strany, preto sú súradnice normalizované (0..1).
 */
@Composable
fun AnnotationLayer(
    controller: AnnotationController,
    page: Int,
    ratio: Float,
    fingerDrawing: Boolean,
    modifier: Modifier = Modifier,
) {
    val live = remember { mutableStateListOf<Float>() }
    val liveTool = remember { arrayOf(InkTool.PEN) }
    val liveColor = remember { intArrayOf(0) }
    val liveWidth = remember { floatArrayOf(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val cache = remember(page) { InkCache() }

    val enabled = controller.enabled

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, fingerDrawing, page, ratio) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val type = down.type
                    val isPen = type == PointerType.Stylus || type == PointerType.Eraser
                    val now = System.currentTimeMillis()

                    val accepted = when (type) {
                        PointerType.Stylus, PointerType.Eraser, PointerType.Mouse -> true
                        PointerType.Touch -> fingerDrawing && now - controller.stylusSeenAt > PALM_REJECT_MS
                        else -> false
                    }
                    if (!accepted) return@awaitEachGesture

                    if (isPen) controller.stylusSeenAt = now
                    val tool = if (type == PointerType.Eraser) InkTool.ERASER else controller.tool
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)

                    fun pressureOf(p: Float) = if (isPen) p.coerceIn(0.1f, 1f) else 0.5f

                    down.consume()
                    val pointerId = down.id
                    val x0 = down.position.x / w
                    val y0 = down.position.y / h
                    val shapeKind = controller.shapeKind

                    val mode = when (tool) {
                        InkTool.ERASER -> MODE_ERASE
                        InkTool.SHAPE -> MODE_SHAPE
                        InkTool.LASSO -> {
                            val b = if (controller.selectionPage == page) controller.selectionBounds(ratio) else null
                            if (b != null && b.contains(Offset(x0, y0))) MODE_MOVE else MODE_LASSO
                        }
                        else -> MODE_DRAW
                    }

                    live.clear()
                    when (mode) {
                        MODE_ERASE -> {
                            controller.beginErase()
                            controller.eraserCursorPage = page
                            controller.eraserCursor = Offset(x0, y0)
                            controller.eraseAt(page, x0, y0, x0, y0, ratio)
                        }
                        MODE_LASSO -> {
                            controller.clearSelection()
                            liveTool[0] = InkTool.LASSO
                            live.add(x0); live.add(y0); live.add(0.5f)
                        }
                        MODE_SHAPE -> {
                            liveTool[0] = InkTool.PEN
                            liveColor[0] = controller.penColor
                            liveWidth[0] = controller.penWidth
                        }
                        MODE_DRAW -> {
                            liveTool[0] = tool
                            liveColor[0] = controller.currentColor
                            liveWidth[0] = controller.currentWidth
                            live.add(x0); live.add(y0); live.add(pressureOf(down.pressure))
                        }
                    }

                    var aborted = false
                    var last = down.position

                    // Vyhladzovanie vstupu: každý nový bod sa len čiastočne priblíži k skutočnej
                    // polohe pera (odstráni chvenie a „zubaté“ čiary); body bližšie než ~1 px sa vynechajú.
                    val streamline = if (tool == InkTool.HIGHLIGHTER) 0.55f else 0.4f
                    var sx = x0
                    var sy = y0
                    var sp = pressureOf(down.pressure)
                    var ax = x0
                    var ay = y0
                    fun addSmoothed(rawX: Float, rawY: Float, rawP: Float, force: Boolean = false) {
                        sx += (rawX - sx) * (1f - streamline)
                        sy += (rawY - sy) * (1f - streamline)
                        sp += (rawP - sp) * 0.5f
                        val distPx = kotlin.math.hypot((sx - ax) * w, (sy - ay) * h)
                        if (distPx >= 1.2f || force) {
                            live.add(sx); live.add(sy); live.add(sp)
                            ax = sx; ay = sy
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // druhý prst počas kreslenia prstom = štipnutie (zoom) -> ťah zahodíme
                            if (!isPen && event.changes.count { it.pressed } > 1) {
                                aborted = true
                                break
                            }
                            // ďalšie dotyky (dlaň) počas písania perom ignorujeme a pohltíme
                            event.changes.forEach { if (it.id != pointerId) it.consume() }
                            val c = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (isPen) controller.stylusSeenAt = System.currentTimeMillis()
                            if (!c.pressed) {
                                if (mode == MODE_DRAW && live.size >= 6) {
                                    addSmoothed(last.x / w, last.y / h, sp, force = true)
                                }
                                c.consume()
                                break
                            }
                            val nx = c.position.x / w
                            val ny = c.position.y / h
                            when (mode) {
                                MODE_ERASE -> {
                                    // úsek od poslednej polohy, aby rýchly pohyb nevynechal miesta
                                    controller.eraseAt(page, last.x / w, last.y / h, nx, ny, ratio)
                                    controller.eraserCursor = Offset(nx, ny)
                                }
                                MODE_MOVE -> {
                                    controller.moveDx += (c.position.x - last.x) / w
                                    controller.moveDy += (c.position.y - last.y) / h
                                }
                                MODE_SHAPE -> {
                                    val pts = shapePoints(shapeKind, x0, y0, nx, ny)
                                    live.clear()
                                    pts.forEach { live.add(it) }
                                }
                                else -> {
                                    for (hc in c.historical) {
                                        addSmoothed(hc.position.x / w, hc.position.y / h, pressureOf(c.pressure))
                                    }
                                    addSmoothed(nx, ny, pressureOf(c.pressure))
                                }
                            }
                            last = c.position
                            c.consume()
                        }
                    } finally {
                        when (mode) {
                            MODE_ERASE -> controller.endErase()
                            MODE_MOVE -> {
                                if (aborted) {
                                    controller.moveDx = 0f
                                    controller.moveDy = 0f
                                } else {
                                    controller.moveSelection(controller.moveDx, controller.moveDy)
                                }
                            }
                            MODE_LASSO -> {
                                if (!aborted && live.size >= 9) {
                                    val poly = FloatArray(live.size / 3 * 2)
                                    for (i in 0 until live.size / 3) {
                                        poly[i * 2] = live[i * 3]
                                        poly[i * 2 + 1] = live[i * 3 + 1]
                                    }
                                    controller.selectInside(page, poly)
                                }
                            }
                            MODE_SHAPE -> {
                                if (!aborted && live.size >= 6) {
                                    controller.commit(
                                        page,
                                        InkStroke(InkTool.PEN, liveColor[0], liveWidth[0], live.toFloatArray()),
                                    )
                                }
                            }
                            else -> {
                                if (!aborted && live.size >= 3) {
                                    controller.commit(
                                        page,
                                        InkStroke(liveTool[0], liveColor[0], liveWidth[0], live.toFloatArray()),
                                    )
                                }
                            }
                        }
                        live.clear()
                    }
                }
            },
    ) {
        // uložené ťahy (vybrané sa posúvajú spolu s prstom/perom)
        val strokes = controller.pages[page] ?: emptyList()
        Canvas(Modifier.fillMaxSize()) {
            val sel = if (controller.selectionPage == page) controller.selection else emptySet()
            val dx = controller.moveDx * size.width
            val dy = controller.moveDy * size.height
            cache.retainOnly(strokes)
            strokes.forEach { s ->
                if (s in sel && (dx != 0f || dy != 0f)) {
                    withTransform({ translate(dx, dy) }) { drawInk(s, cache) }
                } else {
                    drawInk(s, cache)
                }
            }
        }
        // ťah / lasso / tvar, ktorý sa práve kreslí + rámik výberu
        Canvas(Modifier.fillMaxSize()) {
            if (live.size >= 3) {
                if (liveTool[0] == InkTool.LASSO) {
                    val path = Path()
                    path.moveTo(live[0] * size.width, live[1] * size.height)
                    for (i in 1 until live.size / 3) {
                        path.lineTo(live[i * 3] * size.width, live[i * 3 + 1] * size.height)
                    }
                    drawPath(
                        path = path,
                        color = accent,
                        style = Stroke(
                            width = 2.5f * density,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * density, 9f * density)),
                        ),
                    )
                } else {
                    drawInk(InkStroke(liveTool[0], liveColor[0], liveWidth[0], live.toFloatArray()), null)
                }
            }
            // kruh gumy pod perom/prstom
            val cursor = controller.eraserCursor
            if (cursor != null && controller.eraserCursorPage == page) {
                val c = Offset(cursor.x * size.width, cursor.y * size.height)
                val rad = controller.eraserSize / 2f * size.width
                drawCircle(color = Color.White.copy(alpha = 0.55f), radius = rad, center = c)
                drawCircle(
                    color = Color(0xFF5F6368),
                    radius = rad,
                    center = c,
                    style = Stroke(width = 1.5f * density),
                )
            }
            if (controller.selectionPage == page) {
                controller.selectionBounds(ratio)?.let { b ->
                    val pad = 6f * density
                    val left = (b.left + controller.moveDx) * size.width - pad
                    val top = (b.top + controller.moveDy) * size.height - pad
                    val rw = b.width * size.width + pad * 2
                    val rh = b.height * size.height + pad * 2
                    drawRoundRect(
                        color = accent.copy(alpha = 0.10f),
                        topLeft = Offset(left, top),
                        size = Size(rw, rh),
                        cornerRadius = CornerRadius(10f * density),
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, top),
                        size = Size(rw, rh),
                        cornerRadius = CornerRadius(10f * density),
                        style = Stroke(
                            width = 2.5f * density,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * density, 9f * density)),
                        ),
                    )
                }
            }
        }
    }
}

/** Body tvaru (trojice x, y, tlak) pre ťah z (x0, y0) do (x1, y1). */
private fun shapePoints(kind: ShapeKind, x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
    val p = 0.5f
    return when (kind) {
        ShapeKind.LINE -> floatArrayOf(x0, y0, p, x1, y1, p)
        // hrany zahustené bodmi, aby vyhladzovanie zaoblilo len samotné rohy (minimálne)
        ShapeKind.RECT -> densify(
            floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1, x0, y0),
            perEdge = 24,
        )
        ShapeKind.ELLIPSE -> {
            val cx = (x0 + x1) / 2f
            val cy = (y0 + y1) / 2f
            val rx = abs(x1 - x0) / 2f
            val ry = abs(y1 - y0) / 2f
            val steps = 64
            val out = FloatArray((steps + 1) * 3)
            for (i in 0..steps) {
                val a = (2.0 * PI * i / steps)
                out[i * 3] = cx + rx * cos(a).toFloat()
                out[i * 3 + 1] = cy + ry * sin(a).toFloat()
                out[i * 3 + 2] = p
            }
            out
        }
    }
}

/** Polyline (dvojice x, y) -> trojice x, y, tlak s [perEdge] bodmi na každej hrane. */
private fun densify(corners: FloatArray, perEdge: Int): FloatArray {
    val edges = corners.size / 2 - 1
    val out = ArrayList<Float>(edges * perEdge * 3 + 3)
    for (e in 0 until edges) {
        val ax = corners[e * 2]; val ay = corners[e * 2 + 1]
        val bx = corners[e * 2 + 2]; val by = corners[e * 2 + 3]
        for (k in 0 until perEdge) {
            val t = k.toFloat() / perEdge
            out.add(ax + (bx - ax) * t); out.add(ay + (by - ay) * t); out.add(0.5f)
        }
    }
    out.add(corners[corners.size - 2]); out.add(corners[corners.size - 1]); out.add(0.5f)
    return out.toFloatArray()
}
