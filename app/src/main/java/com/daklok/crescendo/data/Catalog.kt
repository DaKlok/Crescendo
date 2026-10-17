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
    /** Kód skladateľa (Mutopia, napr. „BachJS“) – na filter. */
    val composerKey: String = "",
    /** Skupina nástrojov: piano, keyboard, guitar, strings, winds, voice, ensemble, other. */
    val category: String = "other",
    /** Obdobie / štýl: baroque, classical, romantic, renaissance, folk, hymn, jazz, … */
    val style: String = "other",
    val opus: String = "",
    /** MIDI nahrávka skladby (na vypočutie a referenčnú nahrávku) – len pri klávesových skladbách. */
    val midi: String = "",
    val featured: Boolean = false,
    val license: String = "",
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
                composerKey = o.optString("composerKey"),
                category = o.optString("category", "other"),
                style = o.optString("style", "other"),
                opus = o.optString("opus"),
                midi = o.optString("midi").takeIf { it.startsWith("https://") }.orEmpty(),
                featured = o.optBoolean("featured", false),
                license = o.optString("license"),
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

    /** Stiahne súbor z [url] do [target] (prepíše ho). */
    private fun fetch(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val tmp = File(target.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** MIDI skladby (stiahne raz, potom z vyrovnávacej pamäte). */
    suspend fun midiFile(item: CatalogItem): File? = withContext(Dispatchers.IO) {
        if (item.midi.isBlank()) return@withContext null
        val dir = File(context.cacheDir, "catalog_midi").apply { mkdirs() }
        val f = File(dir, item.id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".mid")
        if (f.exists() && f.length() > 0) return@withContext f
        runCatching { fetch(item.midi, f) }.getOrNull()
        f.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * K stiahnutej skladbe pridá referenčnú nahrávku vyrobenú z MIDI (beží na pozadí, môže chvíľu trvať).
     * Existujúcu referenčnú nahrávku nikdy neprepíše.
     */
    suspend fun attachReference(item: CatalogItem, pieceId: Long, audio: PieceAudio) {
        if (item.midi.isBlank() || audio.reference(pieceId) != null) return
        val mid = midiFile(item) ?: return
        val sound = com.daklok.crescendo.data.midi.MidiRenderer.renderNow(context, mid) ?: return
        withContext(Dispatchers.IO) {
            if (audio.reference(pieceId) == null) audio.setReference(pieceId, Uri.fromFile(sound), sound.extension)
        }
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
