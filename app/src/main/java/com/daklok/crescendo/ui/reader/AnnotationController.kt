package com.daklok.crescendo.ui.reader

import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daklok.crescendo.data.AnnotationStore
import com.daklok.crescendo.data.InkStroke
import com.daklok.crescendo.data.InkTool
import com.daklok.crescendo.data.ShapeKind
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private sealed interface EditAction {
    class Added(val page: Int, val stroke: InkStroke) : EditAction
    class Removed(val page: Int, val strokes: List<InkStroke>) : EditAction
    class Replaced(val page: Int, val old: List<InkStroke>, val new: List<InkStroke>) : EditAction
    class Composite(val actions: List<EditAction>) : EditAction
    /** Stav celej strany pred a po úprave (guma). */
    class Snapshot(val page: Int, val before: List<InkStroke>, val after: List<InkStroke>) : EditAction
}

/** Stav a logika poznámok pre jednu skladbu: ťahy po stranách, nástroje, undo/redo, ukladanie. */
class AnnotationController(
    private val store: AnnotationStore,
    private val pieceId: Long,
    private val scope: CoroutineScope,
) {
    val pages = mutableStateMapOf<Int, List<InkStroke>>()

    var enabled by mutableStateOf(false)
    var tool by mutableStateOf(InkTool.PEN)
        private set
    var shapeKind by mutableStateOf(ShapeKind.LINE)

    // výber (lasso)
    var selectionPage by mutableIntStateOf(-1)
        private set
    var selection by mutableStateOf<Set<InkStroke>>(emptySet())
        private set
    var moveDx by mutableFloatStateOf(0f)
    var moveDy by mutableFloatStateOf(0f)
    var penColor by mutableIntStateOf(0xFF1B1B1F.toInt())
    var highlightColor by mutableIntStateOf(0xFFFFD60A.toInt())

    /** Hrúbky ako zlomok šírky strany. */
    var penWidth by mutableFloatStateOf(PEN_WIDTHS[1])
    var highlightWidth by mutableFloatStateOf(HIGHLIGHT_WIDTHS[1])

    /** Priemer gumy ako zlomok šírky strany. */
    var eraserSize by mutableFloatStateOf(ERASER_SIZES[1])

    /** Kde je práve guma (normalizované súradnice) – na vykreslenie kruhu pod perom. */
    var eraserCursor by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var eraserCursorPage by mutableIntStateOf(-1)

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Čas posledného dotyku perom – slúži na ignorovanie dlane. */
    @Volatile
    var stylusSeenAt: Long = 0L

    // ---- zapamätanie nástrojov: po zatvorení editora aj po reštarte aplikácie ostane všetko, ako si to nechal ----
    init {
        val p = store.toolPrefs
        tool = InkTool.entries.firstOrNull { it.name == p.getString("tool", null) }
            ?.takeIf { it != InkTool.LASSO } ?: InkTool.PEN
        shapeKind = ShapeKind.entries.firstOrNull { it.name == p.getString("shape", null) } ?: ShapeKind.LINE
        penColor = p.getInt("pen_color", penColor)
        highlightColor = p.getInt("highlight_color", highlightColor)
        penWidth = p.getFloat("pen_width", penWidth)
        highlightWidth = p.getFloat("highlight_width", highlightWidth)
        eraserSize = p.getFloat("eraser_size", eraserSize)

        scope.launch {
            snapshotFlow {
                listOf(tool.name, shapeKind.name, penColor, highlightColor, penWidth, highlightWidth, eraserSize)
            }.drop(1).collect {
                p.edit()
                    .putString("tool", tool.name)
                    .putString("shape", shapeKind.name)
                    .putInt("pen_color", penColor)
                    .putInt("highlight_color", highlightColor)
                    .putFloat("pen_width", penWidth)
                    .putFloat("highlight_width", highlightWidth)
                    .putFloat("eraser_size", eraserSize)
                    .apply()
            }
        }
    }

    private val undoStack = ArrayDeque<EditAction>()
    private val redoStack = ArrayDeque<EditAction>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loaded = false

    suspend fun load() {
        val data = store.load(pieceId)
        data.forEach { (page, strokes) -> if (pages[page].isNullOrEmpty()) pages[page] = strokes }
        loaded = true
    }

    fun strokesOf(page: Int): List<InkStroke> = pages[page] ?: emptyList()

    val currentColor: Int
        get() = if (tool == InkTool.HIGHLIGHTER) highlightColor else penColor

    val currentWidth: Float
        get() = if (tool == InkTool.HIGHLIGHTER) highlightWidth else penWidth

    fun setColor(color: Int) {
        if (tool == InkTool.HIGHLIGHTER) highlightColor = color else penColor = color
    }

    fun setWidth(width: Float) {
        if (tool == InkTool.HIGHLIGHTER) highlightWidth = width else penWidth = width
    }

    // ---- úpravy ----

    fun commit(page: Int, stroke: InkStroke) {
        pages[page] = strokesOf(page) + stroke
        pushUndo(EditAction.Added(page, stroke))
    }

    private var eraseBefore: MutableMap<Int, List<InkStroke>>? = null

    fun beginErase() {
        eraseBefore = mutableMapOf()
    }

    /**
     * Guma ako štetec: zmaže len tie časti ťahov, cez ktoré guma prešla (od bodu x0,y0 po x1,y1),
     * zvyšok ťahu ostane – ťah sa podľa potreby rozdelí na viac kúskov.
     * [ratio] = šírka/výška strany.
     */
    fun eraseAt(page: Int, x0: Float, y0: Float, x1: Float, y1: Float, ratio: Float) {
        val strokes = strokesOf(page)
        if (strokes.isEmpty()) return
        val r = eraserSize / 2f
        var changed = false
        val out = ArrayList<InkStroke>(strokes.size + 4)
        for (st in strokes) {
            val pieces = erasePieces(st, x0, y0, x1, y1, ratio, r)
            if (pieces == null) {
                out.add(st)
            } else {
                changed = true
                out.addAll(pieces)
            }
        }
        if (changed) {
            eraseBefore?.getOrPut(page) { strokes }
            pages[page] = out
        }
    }

    fun endErase() {
        val before = eraseBefore ?: return
        eraseBefore = null
        eraserCursor = null
        eraserCursorPage = -1
        val actions = before.map { (page, list) -> EditAction.Snapshot(page, list, strokesOf(page)) }
        when (actions.size) {
            0 -> Unit
            1 -> pushUndo(actions[0])
            else -> pushUndo(EditAction.Composite(actions))
        }
    }

    fun clearPage(page: Int) {
        val strokes = strokesOf(page)
        if (strokes.isEmpty()) return
        pages[page] = emptyList()
        pushUndo(EditAction.Removed(page, strokes))
    }

    fun undo() {
        clearSelection()
        val action = undoStack.removeLastOrNull() ?: return
        apply(action, inverse = true)
        redoStack.addLast(action)
        refreshFlags()
        scheduleSave()
    }

    fun redo() {
        clearSelection()
        val action = redoStack.removeLastOrNull() ?: return
        apply(action, inverse = false)
        undoStack.addLast(action)
        refreshFlags()
        scheduleSave()
    }

    private fun apply(action: EditAction, inverse: Boolean) {
        when (action) {
            is EditAction.Added -> {
                if (inverse) pages[action.page] = strokesOf(action.page) - action.stroke
                else pages[action.page] = strokesOf(action.page) + action.stroke
            }
            is EditAction.Removed -> {
                if (inverse) pages[action.page] = strokesOf(action.page) + action.strokes
                else pages[action.page] = strokesOf(action.page) - action.strokes.toSet()
            }
            is EditAction.Replaced -> {
                val from = if (inverse) action.new else action.old
                val to = if (inverse) action.old else action.new
                val map = from.zip(to).toMap()
                pages[action.page] = strokesOf(action.page).map { map[it] ?: it }
            }
            is EditAction.Snapshot -> {
                pages[action.page] = if (inverse) action.before else action.after
            }
            is EditAction.Composite -> {
                val list = if (inverse) action.actions.asReversed() else action.actions
                list.forEach { apply(it, inverse) }
            }
        }
    }

    private fun pushUndo(action: EditAction) {
        undoStack.addLast(action)
        while (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        refreshFlags()
        scheduleSave()
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---- nástroje a výber ----

    fun selectTool(t: InkTool) {
        if (t != InkTool.LASSO) clearSelection()
        tool = t
    }

    fun clearSelection() {
        selection = emptySet()
        selectionPage = -1
        moveDx = 0f
        moveDy = 0f
    }

    /** Vyberie ťahy, ktoré ležia aspoň z polovice vnútri polygónu [poly] (dvojice x, y; normalizované). */
    fun selectInside(page: Int, poly: FloatArray) {
        val inside = strokesOf(page).filter { s ->
            var inCount = 0
            for (i in 0 until s.count) if (pointInPolygon(s.pts[i * 3], s.pts[i * 3 + 1], poly)) inCount++
            s.count > 0 && inCount * 2 >= s.count
        }
        selection = inside.toSet()
        selectionPage = if (inside.isEmpty()) -1 else page
        moveDx = 0f
        moveDy = 0f
    }

    /** Hranice výberu (normalizované) vrátane hrúbky ťahov, alebo null. */
    fun selectionBounds(ratio: Float): Rect? {
        if (selection.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (s in selection) {
            val padX = s.width / 2f
            val padY = s.width / 2f / ratio
            for (i in 0 until s.count) {
                val x = s.pts[i * 3]
                val y = s.pts[i * 3 + 1]
                minX = min(minX, x - padX); maxX = max(maxX, x + padX)
                minY = min(minY, y - padY); maxY = max(maxY, y + padY)
            }
        }
        return Rect(minX, minY, maxX, maxY)
    }

    fun moveSelection(dx: Float, dy: Float) {
        val page = selectionPage
        if (page < 0 || selection.isEmpty() || (dx == 0f && dy == 0f)) {
            moveDx = 0f; moveDy = 0f
            return
        }
        val old = strokesOf(page).filter { it in selection }
        val new = old.map { translated(it, dx, dy) }
        val map = old.zip(new).toMap()
        pages[page] = strokesOf(page).map { map[it] ?: it }
        selection = new.toSet()
        moveDx = 0f
        moveDy = 0f
        pushUndo(EditAction.Replaced(page, old, new))
    }

    fun deleteSelection() {
        val page = selectionPage
        if (page < 0 || selection.isEmpty()) return
        val old = strokesOf(page).filter { it in selection }
        pages[page] = strokesOf(page) - old.toSet()
        clearSelection()
        pushUndo(EditAction.Removed(page, old))
    }

    fun duplicateSelection() {
        val page = selectionPage
        if (page < 0 || selection.isEmpty()) return
        val dup = strokesOf(page).filter { it in selection }.map { translated(it, 0.03f, 0.03f) }
        pages[page] = strokesOf(page) + dup
        selection = dup.toSet()
        pushUndo(EditAction.Composite(dup.map { EditAction.Added(page, it) }))
    }

    private fun translated(s: InkStroke, dx: Float, dy: Float): InkStroke {
        val p = s.pts.copyOf()
        var i = 0
        while (i < p.size) {
            p[i] += dx
            p[i + 1] += dy
            i += 3
        }
        return InkStroke(s.tool, s.color, s.width, p)
    }

    // ---- ukladanie ----

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700)
            saveNow()
        }
    }

    private fun saveNow() {
        if (!loaded) return
        val snapshot = pages.toMap()
        ioScope.launch { store.save(pieceId, snapshot) }
    }

    /** Okamžité uloženie (pri opustení čítačky). */
    fun flush() {
        saveJob?.cancel()
        saveNow()
    }

    companion object {
        val PEN_WIDTHS = floatArrayOf(0.0022f, 0.0040f, 0.0075f)
        val HIGHLIGHT_WIDTHS = floatArrayOf(0.014f, 0.024f, 0.040f)
        /** Priemery gumy (zlomok šírky strany): malá, stredná, veľká. */
        val ERASER_SIZES = floatArrayOf(0.012f, 0.028f, 0.06f)

        /**
         * Vráti null, ak sa guma ťahu nedotkla; inak zoznam kúskov, ktoré po zmazaní ostanú
         * (môže byť aj prázdny). Vzdialenosti počítame v jednotkách šírky strany.
         */
        private fun erasePieces(
            s: InkStroke,
            x0: Float, y0: Float, x1: Float, y1: Float,
            ratio: Float,
            r: Float,
        ): List<InkStroke>? {
            val n = s.count
            if (n == 0) return null
            val reach = r + s.width * 0.3f

            // rýchle vylúčenie podľa obdĺžnika okolo ťahu
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until n) {
                val px = s.pts[i * 3]; val py = s.pts[i * 3 + 1] / ratio
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
            }
            val ey0 = y0 / ratio
            val ey1 = y1 / ratio
            if (maxX < min(x0, x1) - reach || minX > max(x0, x1) + reach ||
                maxY < min(ey0, ey1) - reach || minY > max(ey0, ey1) + reach
            ) return null

            // zahustenie bodov, aby sa dlhé úseky (napr. čiara z tvarov) dali gumovať po kúskoch
            val step = (r * 0.5f).coerceAtLeast(0.001f)
            val dense = ArrayList<Float>(n * 3)
            for (i in 0 until n) {
                val px = s.pts[i * 3]; val py = s.pts[i * 3 + 1]; val pp = s.pts[i * 3 + 2]
                if (i > 0) {
                    val qx = s.pts[(i - 1) * 3]; val qy = s.pts[(i - 1) * 3 + 1]; val qp = s.pts[(i - 1) * 3 + 2]
                    val d = hypot(px - qx, (py - qy) / ratio)
                    val extra = (d / step).toInt()
                    for (k in 1..extra) {
                        val t = k.toFloat() / (extra + 1)
                        dense.add(qx + (px - qx) * t); dense.add(qy + (py - qy) * t); dense.add(qp + (pp - qp) * t)
                    }
                }
                dense.add(px); dense.add(py); dense.add(pp)
            }

            val m = dense.size / 3
            val hit = BooleanArray(m) { i ->
                distToSegment(dense[i * 3], dense[i * 3 + 1] / ratio, x0, ey0, x1, ey1) <= reach
            }
            if (hit.none { it }) return null

            // zvyšné súvislé úseky -> nové ťahy
            val pieces = ArrayList<InkStroke>()
            var start = -1
            for (i in 0..m) {
                val inside = i < m && !hit[i]
                if (inside && start < 0) start = i
                if (!inside && start >= 0) {
                    val len = i - start
                    if (len >= 2) {
                        val pts = FloatArray(len * 3)
                        for (k in 0 until len * 3) pts[k] = dense[start * 3 + k]
                        pieces.add(InkStroke(s.tool, s.color, s.width, pts))
                    }
                    start = -1
                }
            }
            return pieces
        }

        val PEN_COLORS = intArrayOf(
            0xFF1B1B1F.toInt(), // čierna
            0xFFD32F2F.toInt(), // červená
            0xFF1565C0.toInt(), // modrá
            0xFF2E7D32.toInt(), // zelená
            0xFFEF6C00.toInt(), // oranžová
            0xFF6A1B9A.toInt(), // fialová
        )
        val HIGHLIGHT_COLORS = intArrayOf(
            0xFFFFD60A.toInt(), // žltá
            0xFF69F0AE.toInt(), // zelená
            0xFF40C4FF.toInt(), // azúrová
            0xFFFF80AB.toInt(), // ružová
            0xFFFFAB40.toInt(), // oranžová
            0xFFB388FF.toInt(), // fialová
        )

        private fun pointInPolygon(x: Float, y: Float, poly: FloatArray): Boolean {
            var inside = false
            val n = poly.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
                val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        private fun hits(s: InkStroke, x: Float, y: Float, ratio: Float, radius: Float): Boolean {
            val n = s.count
            if (n == 0) return false
            val r = radius + s.width / 2f
            // y prepočítame do jednotiek šírky strany
            val yy = y / ratio
            var px = s.pts[0]
            var py = s.pts[1] / ratio
            if (hypot(px - x, py - yy) <= r) return true
            for (i in 1 until n) {
                val cx = s.pts[i * 3]
                val cy = s.pts[i * 3 + 1] / ratio
                if (distToSegment(x, yy, px, py, cx, cy) <= r) return true
                px = cx
                py = cy
            }
            return false
        }

        private fun distToSegment(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            val len2 = dx * dx + dy * dy
            if (len2 == 0f) return hypot(x - x1, y - y1)
            val t = max(0f, min(1f, ((x - x1) * dx + (y - y1) * dy) / len2))
            return hypot(x - (x1 + t * dx), y - (y1 + t * dy))
        }
    }
}
