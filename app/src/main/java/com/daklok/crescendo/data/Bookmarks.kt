package com.daklok.crescendo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Pomenované miesto v skladbe (napr. „Repríza“, „Coda“) – strana od 0. */
data class Bookmark(val page: Int, val name: String, val created: Long = System.currentTimeMillis())

/**
 * Záložky skladieb: filesDir/bookmarks/<id>.json. Je v zálohe aj v synchronizácii
 * a aj v balíku .crescendo pri zdieľaní skladby.
 */
class BookmarkStore(private val context: Context) {
    private val dir get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(pieceId: Long) = File(dir, "$pieceId.json")

    fun list(pieceId: Long): List<Bookmark> = runCatching {
        val f = file(pieceId)
        if (!f.exists()) return emptyList()
        fromJson(f.readText())
    }.getOrDefault(emptyList())

    fun save(pieceId: Long, list: List<Bookmark>) {
        val f = file(pieceId)
        if (list.isEmpty()) {
            f.delete()
            return
        }
        val tmp = File(f.path + ".tmp")
        tmp.writeText(toJson(list))
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }

    fun delete(pieceId: Long) {
        file(pieceId).delete()
    }

    companion object {
        const val DIR = "bookmarks"

        fun toJson(list: List<Bookmark>): String = JSONArray().apply {
            list.sortedBy { it.page }.forEach { b ->
                put(JSONObject().put("page", b.page).put("name", b.name).put("created", b.created))
            }
        }.toString()

        fun fromJson(text: String): List<Bookmark> {
            val arr = JSONArray(text)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Bookmark(o.optInt("page"), o.optString("name"), o.optLong("created"))
            }.sortedBy { it.page }
        }
    }
}
