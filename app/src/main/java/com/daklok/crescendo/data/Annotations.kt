package com.daklok.crescendo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** PEN a HIGHLIGHTER sa ukladajú ako ťahy; ostatné sú nástroje (STAMP = pečiatka). */
enum class InkTool { PEN, HIGHLIGHTER, ERASER, LASSO, SHAPE, STAMP }

enum class ShapeKind { LINE, RECT, ELLIPSE }

/**
 * Jeden ťah. Súradnice sú normalizované na stranu (0..1 voči šírke/výške strany),
 * takže ťah sedí na akejkoľvek veľkosti displeja. [width] je zlomok šírky strany.
 * [pts] = opakujúce sa trojice x, y, tlak.
 */
class InkStroke(
    val tool: InkTool,
    val color: Int,
    val width: Float,
    val pts: FloatArray,
) {
    val count: Int get() = pts.size / 3
}

/**
 * Hudobná pečiatka (dynamika, prstoklad, fermata, vlastný text…).
 * [text] je buď obyčajný text, alebo symbol začínajúci „§“ (kreslí sa ako tvar, pozri StampRenderer).
 * [x], [y] = stred (normalizované), [size] = výška písma ako zlomok šírky strany.
 */
class Stamp(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Int,
)

/** Všetky poznámky jednej skladby. */
class PieceNotes(
    val pages: Map<Int, List<InkStroke>> = emptyMap(),
    val stamps: Map<Int, List<Stamp>> = emptyMap(),
)

/** Ukladá poznámky každej skladby do JSON súboru v internom úložisku. */
class AnnotationStore(private val context: Context) {

    private val dir = File(context.filesDir, "annotations").apply { mkdirs() }

    /** Posledné nastavenia nástrojov editora (pero, farby, hrúbky…), spoločné pre všetky skladby. */
    val toolPrefs: android.content.SharedPreferences
        get() = context.getSharedPreferences("annotation_tools", Context.MODE_PRIVATE)

    private fun file(pieceId: Long) = File(dir, "$pieceId.json")

    suspend fun load(pieceId: Long): PieceNotes = withContext(Dispatchers.IO) {
        val f = file(pieceId)
        if (!f.exists()) return@withContext PieceNotes()
        try {
            val root = JSONObject(f.readText())
            val pagesJson = root.optJSONObject("pages") ?: JSONObject()
            val pages = HashMap<Int, List<InkStroke>>()
            for (key in pagesJson.keys()) {
                val arr = pagesJson.getJSONArray(key)
                val list = ArrayList<InkStroke>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("p")
                    val pts = FloatArray(p.length()) { p.getDouble(it).toFloat() }
                    list.add(
                        InkStroke(
                            tool = if (o.getInt("t") == 1) InkTool.HIGHLIGHTER else InkTool.PEN,
                            color = o.getInt("c"),
                            width = o.getDouble("w").toFloat(),
                            pts = pts,
                        )
                    )
                }
                pages[key.toInt()] = list
            }

            val stamps = HashMap<Int, List<Stamp>>()
            root.optJSONObject("stamps")?.let { sj ->
                for (key in sj.keys()) {
                    val arr = sj.getJSONArray(key)
                    stamps[key.toInt()] = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        Stamp(
                            text = o.getString("t"),
                            x = o.getDouble("x").toFloat(),
                            y = o.getDouble("y").toFloat(),
                            size = o.getDouble("s").toFloat(),
                            color = o.getInt("c"),
                        )
                    }
                }
            }

            PieceNotes(pages, stamps)
        } catch (e: Exception) {
            PieceNotes()
        }
    }

    suspend fun save(pieceId: Long, notes: PieceNotes) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val pagesJson = JSONObject()
        for ((page, strokes) in notes.pages) {
            if (strokes.isEmpty()) continue
            val arr = JSONArray()
            for (s in strokes) {
                val p = JSONArray()
                for (v in s.pts) p.put(Math.round(v * 100000f) / 100000.0)
                arr.put(
                    JSONObject()
                        .put("t", if (s.tool == InkTool.HIGHLIGHTER) 1 else 0)
                        .put("c", s.color)
                        .put("w", s.width.toDouble())
                        .put("p", p)
                )
            }
            pagesJson.put(page.toString(), arr)
        }
        root.put("pages", pagesJson)

        val stampsJson = JSONObject()
        for ((page, list) in notes.stamps) {
            if (list.isEmpty()) continue
            val arr = JSONArray()
            list.forEach { st ->
                arr.put(
                    JSONObject()
                        .put("t", st.text)
                        .put("x", st.x.toDouble())
                        .put("y", st.y.toDouble())
                        .put("s", st.size.toDouble())
                        .put("c", st.color)
                )
            }
            stampsJson.put(page.toString(), arr)
        }
        root.put("stamps", stampsJson)


        val text = root.toString()
        val tmp = File(dir, "$pieceId.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(pieceId))) {
            file(pieceId).writeText(text)
            tmp.delete()
        }
    }

    /** Posunie všetky poznámky o [shift] strán (po vložení strán na začiatok). */
    suspend fun shiftPages(pieceId: Long, shift: Int) {
        if (shift == 0) return
        val notes = load(pieceId)
        if (notes.pages.isEmpty() && notes.stamps.isEmpty()) return
        save(
            pieceId,
            PieceNotes(
                pages = notes.pages.mapKeys { it.key + shift },
                stamps = notes.stamps.mapKeys { it.key + shift },
            ),
        )
    }

    fun delete(pieceId: Long) {
        file(pieceId).delete()
    }
}
