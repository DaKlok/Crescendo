package com.daklok.crescendo.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Jedna skladba v katalógu voľných nôt. */
data class CatalogItem(
    val id: String,
    val title: String,
    val titleSk: String,
    val composer: String,
    val instrument: String,
    val difficulty: Int,
    val url: String,
) {
    fun localizedTitle(): String =
        if (java.util.Locale.getDefault().language == "sk" && titleSk.isNotBlank()) titleSk else title
}

/**
 * Katalóg voľných nôt (Mutopia Project – voľné diela a Creative Commons).
 * Zoznam je pribalený v aplikácii a dá sa rozšíriť bez novej verzie: ak je dostupný
 * novší catalog.json na webe aplikácie, použije sa ten.
 */
class CatalogRepository(private val context: Context) {

    companion object {
        const val REMOTE_URL = "https://daklok.github.io/Crescendo/catalog.json"
        const val SOURCE_URL = "https://www.mutopiaproject.org"
    }

    private val cacheFile get() = File(context.filesDir, "catalog_cache.json")
    private val prefs get() = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    /** Pribalený alebo naposledy stiahnutý zoznam (hneď, bez internetu). */
    suspend fun local(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val text = cacheFile.takeIf { it.exists() }?.readText()
            ?: context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        parse(text)
    }

    /** Skúsi stiahnuť novší zoznam z webu (ticho – pri chybe vráti null). */
    suspend fun refresh(): List<CatalogItem>? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val items = parse(text)
                if (items.isNotEmpty()) cacheFile.writeText(text)
                items
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun parse(text: String): List<CatalogItem> {
        val arr = JSONObject(text).optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CatalogItem(
                id = o.optString("id"),
                title = o.optString("title"),
                titleSk = o.optString("titleSk"),
                composer = o.optString("composer"),
                instrument = o.optString("instrument"),
                difficulty = o.optInt("difficulty", 0),
                url = o.optString("url"),
            ).takeIf { it.url.startsWith("https://") && it.title.isNotBlank() }
        }
    }

    /** ID skladieb, ktoré už boli stiahnuté do knižnice. */
    fun downloadedIds(): Set<String> = prefs.getStringSet("downloaded", emptySet()).orEmpty()

    /** Stiahnuté skladby, ktoré sú ešte v knižnici: ID v katalógu → ID skladby. */
    suspend fun downloaded(library: LibraryRepository): Map<String, Long> = withContext(Dispatchers.IO) {
        downloadedIds().mapNotNull { id ->
            val pieceId = prefs.getLong("piece_$id", -1L)
            if (pieceId > 0 && library.getPiece(pieceId) != null) id to pieceId else null
        }.toMap()
    }

    private fun markDownloaded(id: String, pieceId: Long) {
        prefs.edit().putStringSet("downloaded", downloadedIds() + id).putLong("piece_$id", pieceId).apply()
    }

    /** Stiahne PDF a pridá ho do knižnice (do [folderId]). Vráti ID novej skladby. */
    suspend fun download(item: CatalogItem, library: LibraryRepository, folderId: Long?): Result<Long> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "catalog").apply { mkdirs() }
            val tmp = File(dir, "${item.id.ifBlank { "piece" }}.pdf")
            try {
                val conn = URL(item.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                val result = library.importPdf(Uri.fromFile(tmp), folderId, item.localizedTitle())
                result.onSuccess { id ->
                    library.getPiece(id)?.let { piece ->
                        library.updateDetails(
                            piece.copy(
                                composer = item.composer,
                                instrument = item.instrument,
                                difficulty = item.difficulty,
                                tags = "Mutopia",
                            ),
                        )
                    }
                    markDownloaded(item.id, id)
                }
                result
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tmp.delete()
            }
        }
}
