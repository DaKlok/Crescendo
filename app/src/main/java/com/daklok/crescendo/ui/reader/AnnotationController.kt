package com.daklok.crescendo.ui.reader

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

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Čas posledného dotyku perom – slúži na ignorovanie dlane. */
    @Volatile
    var stylusSeenAt: Long = 0L

    private val undoStack = ArrayDeque<EditAction>()
    private val redoStack = ArrayDeque<EditAction>()
    private var eraseBatch: MutableList<EditAction>? = null

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

    fun beginErase() {
        eraseBatch = mutableListOf()
    }

    /** Zmaže ťahy blízko bodu (x, y); [ratio] = šírka/výška strany, [radius] v jednotkách šírky strany. */
    fun eraseAt(page: Int, x: Float, y: Float, ratio: Float, radius: Float = ERASER_RADIUS) {
        val strokes = strokesOf(page)
        if (strokes.isEmpty()) return
        val hit = strokes.filter { hits(it, x, y, ratio, radius) }
        if (hit.isEmpty()) return
        pages[page] = strokes - hit.toSet()
        eraseBatch?.add(EditAction.Removed(page, hit))
    }

    fun endErase() {
        val batch = eraseBatch ?: return
        eraseBatch = null
        when (batch.size) {
            0 -> Unit
            1 -> pushUndo(batch[0])
            else -> pushUndo(EditAction.Composite(batch))
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
        const val ERASER_RADIUS = 0.018f

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
