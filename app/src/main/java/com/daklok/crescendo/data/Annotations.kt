package com.daklok.crescendo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** PEN a HIGHLIGHTER sa ukladajú; ERASER, LASSO a SHAPE sú len nástroje. */
enum class InkTool { PEN, HIGHLIGHTER, ERASER, LASSO, SHAPE }

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

/** Ukladá poznámky každej skladby do JSON súboru v internom úložisku. */
class AnnotationStore(private val context: Context) {

    private val dir = File(context.filesDir, "annotations").apply { mkdirs() }

    private fun file(pieceId: Long) = File(dir, "$pieceId.json")

    suspend fun load(pieceId: Long): Map<Int, List<InkStroke>> = withContext(Dispatchers.IO) {
        val f = file(pieceId)
        if (!f.exists()) return@withContext emptyMap()
        try {
            val pages = JSONObject(f.readText()).getJSONObject("pages")
            val out = HashMap<Int, List<InkStroke>>()
            for (key in pages.keys()) {
                val arr = pages.getJSONArray(key)
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
                out[key.toInt()] = list
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun save(pieceId: Long, pages: Map<Int, List<InkStroke>>) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val pagesJson = JSONObject()
        for ((page, strokes) in pages) {
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
        val tmp = File(dir, "$pieceId.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file(pieceId))) {
            file(pieceId).writeText(root.toString())
            tmp.delete()
        }
    }

    fun delete(pieceId: Long) {
        file(pieceId).delete()
    }
}
