package com.daklok.crescendo.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
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
    /** Dátum pridania do Mutopie (RRRR-MM-DD) – na zoradenie „najnovšie“. */
    val added: String = "",
) {
    /** Organová skladba – ukážka a nahrávka znejú ako organ. */
    val isOrgan: Boolean
        get() = instrument.contains("organ", ignoreCase = true) && !instrument.contains("piano", ignoreCase = true)

    /** Podľa čoho vybrať nástroj, ak MIDI nemá nastavené nástroje (organ, guitar, strings, winds, voice…). */
    val renderHint: String
        get() = if (isOrgan) "organ" else category

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
        const val PREVIEW_SECONDS = 60.0

        /** Verzia výroby oficiálnych nahrávok (2 = organ ako organ, bez zdvojení o oktávu). */
        const val REFERENCE_VERSION = 3
    }

    private val cacheFile get() = File(context.filesDir, "catalog_cache.json")
    private val prefs get() = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    /** Pribalený alebo naposledy stiahnutý zoznam (hneď, bez internetu). */
    /**
     * Pribalený alebo stiahnutý zoznam – použije sa ten s vyššou verziou („version“ v JSON).
     * Starší zoznam z webu (napr. pred aktualizáciou aplikácie) tak nikdy neprekryje novší pribalený.
     */
    suspend fun local(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val bundled = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val cached = cacheFile.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        if (cached != null && version(cached) > version(bundled)) parse(cached) else {
            if (cached != null) cacheFile.delete() // zastaraný – zahodiť
            parse(bundled)
        }
    }

    /** Skúsi stiahnuť novší zoznam z webu (ticho – pri chybe alebo staršej verzii vráti null). */
    suspend fun refresh(): List<CatalogItem>? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val bundledVersion = context.assets.open("catalog.json").bufferedReader().use { version(it.readText()) }
                if (version(text) <= bundledVersion) return@runCatching null
                val items = parse(text)
                if (items.isNotEmpty()) cacheFile.writeText(text)
                items.takeIf { it.isNotEmpty() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun version(text: String): Int = runCatching { JSONObject(text).optInt("version", 0) }.getOrDefault(0)

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
                added = o.optString("added"),
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

    /** Skopíruje odpoveď do súboru a hlási priebeh 0..1 (−1 = veľkosť nie je známa). */
    private fun copyWithProgress(conn: HttpURLConnection, target: File, onProgress: (Float) -> Unit) {
        val total = conn.contentLengthLong
        var done = 0L
        var last = -1f
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                    if (p < 0 || p - last >= 0.02f) {
                        last = p
                        onProgress(p)
                    }
                }
            }
        }
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
    /** Skladby, pri ktorých Mutopia MIDI nemá (overené – server vrátil 404). */
    fun noMidiIds(): Set<String> = prefs.getStringSet("no_midi", emptySet()).orEmpty()

    private fun markNoMidi(id: String) {
        prefs.edit().putStringSet("no_midi", noMidiIds() + id).apply()
    }

    /** Má skladba (pravdepodobne) MIDI? Po neúspešnom pokuse sa to zapamätá. */
    fun hasMidi(item: CatalogItem) = item.midi.isNotBlank() && item.id !in noMidiIds()

    /**
     * MIDI skladby (stiahne raz, potom z vyrovnávacej pamäte). Skladby s viacerými časťami majú na Mutopii
     * namiesto jedného súboru ZIP s MIDI pre každú časť – tie sa spoja za sebou.
     */
    suspend fun midiFile(item: CatalogItem): File? = withContext(Dispatchers.IO) {
        if (item.midi.isBlank()) return@withContext null
        val dir = File(context.cacheDir, "catalog_midi").apply { mkdirs() }
        val f = File(dir, item.id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".mid")
        if (f.exists() && f.length() > 0) return@withContext f
        val single = runCatching { fetchStatus(item.midi, f) }.getOrElse { return@withContext null } // bez internetu
        if (single == 200 && f.length() > 0) return@withContext f
        // viac častí: <názov>-mids.zip
        val zip = File(dir, f.nameWithoutExtension + "-mids.zip")
        val zipUrl = item.midi.removeSuffix(".mid") + "-mids.zip"
        val zipped = runCatching { fetchStatus(zipUrl, zip) }.getOrElse { return@withContext null }
        if (zipped == 200 && zip.length() > 0) {
            val merged = runCatching { mergeZip(zip, f) }.getOrDefault(false)
            zip.delete()
            if (merged) return@withContext f
        }
        if (single == 404 && zipped == 404) markNoMidi(item.id)
        f.delete()
        null
    }

    /** Stiahne súbor; vráti HTTP kód (200 = OK). Pri chybe siete vyhodí výnimku. */
    private fun fetchStatus(url: String, target: File): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) return code
            val tmp = File(target.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return 200
        } finally {
            conn.disconnect()
        }
    }

    /** Spojí MIDI časti zo ZIP-u (v poradí podľa čísla časti) do jedného súboru s 1,5 s pauzou medzi časťami. */
    private fun mergeZip(zip: File, target: File): Boolean {
        val parts = ArrayList<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (!e.isDirectory && e.name.endsWith(".mid", ignoreCase = true)) parts.add(e.name to zin.readBytes())
            }
        }
        if (parts.isEmpty()) return false
        fun number(name: String) = Regex("(\\d+)\\.mid$", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        parts.sortWith(compareBy({ number(it.first) }, { it.first }))
        val all = ArrayList<com.daklok.crescendo.data.midi.NoteEvent>()
        var offset = 0.0
        for ((_, bytes) in parts) {
            val events = runCatching { com.daklok.crescendo.data.midi.MidiFile.parse(bytes) }.getOrNull() ?: continue
            if (events.isEmpty()) continue
            all += events.map { it.copy(time = it.time + offset) }
            offset += events.maxOf { it.time } + 1.5
        }
        if (all.none { it.status == 0x90 }) return false
        com.daklok.crescendo.data.midi.MidiFile.write(target, all)
        return target.length() > 0
    }

    /**
     * K stiahnutej skladbe pridá referenčnú nahrávku vyrobenú z MIDI (beží na pozadí, môže chvíľu trvať).
     * Uloží sa ako „oficiálna“ – vlastné referenčné nahrávky ostanú nedotknuté.
     */
    suspend fun attachReference(item: CatalogItem, pieceId: Long, audio: PieceAudio) {
        if (item.midi.isBlank()) return
        val pieceMidi = PieceMidi(context)
        _referenceJobs.update { it + (pieceId to -1f) }
        try {
            val cached = midiFile(item) ?: return
            // MIDI ku skladbe – vizualizér (padajúce noty) je pripravený hneď po stiahnutí
            val mid = pieceMidi.file(pieceId) ?: pieceMidi.setFrom(pieceId, cached, item.renderHint) ?: cached
            if (audio.hasOfficial(pieceId) && !officialOutdated(pieceId, item)) return
            val sound = com.daklok.crescendo.data.midi.MidiRenderer.renderNow(
                context,
                mid,
                com.daklok.crescendo.data.midi.RenderOptions(hint = item.renderHint),
            ) { p -> _referenceJobs.update { m -> if (pieceId in m) m + (pieceId to p) else m } } ?: return
            withContext(Dispatchers.IO) {
                if (!audio.hasOfficial(pieceId) || officialOutdated(pieceId, item)) {
                    audio.setOfficial(pieceId, sound)
                    prefs.edit().putInt("ref_v_$pieceId", REFERENCE_VERSION).apply()
                }
            }
        } finally {
            _referenceJobs.update { it - pieceId }
        }
    }

    /**
     * Vlastné MIDI (importované alebo nahrávka z klavíra): vyrobí z neho zvuk a pridá ho ako referenčnú nahrávku
     * s názvom [label] – ostatné nahrávky ostanú. Priebeh je v [referenceJobs].
     */
    suspend fun referenceFromPieceMidi(pieceId: Long, label: String, audio: PieceAudio) {
        val pieceMidi = PieceMidi(context)
        _referenceJobs.update { it + (pieceId to 0f) }
        try {
            val sound = pieceMidi.sound(pieceId) { p -> _referenceJobs.update { m -> if (pieceId in m) m + (pieceId to p) else m } }
                ?: return
            withContext(Dispatchers.IO) { audio.addFile(pieceId, sound, label) }
        } finally {
            _referenceJobs.update { it - pieceId }
        }
    }

    /**
     * Oficiálna nahrávka vyrobená staršou verziou aplikácie (napr. organ zahratý flautou) – treba ju vyrobiť znova.
     * Klavírne skladby sa nemenili.
     */
    fun officialOutdated(pieceId: Long, item: CatalogItem): Boolean =
        item.renderHint != "piano" && prefs.getInt("ref_v_$pieceId", 1) < REFERENCE_VERSION

    /** Pri otvorení skladby: ak je oficiálna nahrávka zastaraná, na pozadí ju vyrobí znova. */
    suspend fun refreshOfficialIfOutdated(pieceId: Long, audio: PieceAudio) {
        if (pieceId in _referenceJobs.value) return
        val item = itemForPiece(pieceId) ?: return
        if (audio.hasOfficial(pieceId) && officialOutdated(pieceId, item)) attachReference(item, pieceId, audio)
    }

    /** Položka katalógu, z ktorej bola skladba stiahnutá (alebo null). */
    suspend fun itemForPiece(pieceId: Long): CatalogItem? {
        val id = prefs.all.entries.firstOrNull { (k, v) -> k.startsWith("piece_") && v == pieceId }?.key?.removePrefix("piece_")
            ?: return null
        return local().firstOrNull { it.id == id }?.takeIf { it.midi.isNotBlank() }
    }

    /** Ukážka (prvá minúta) – hneď, aj pri dlhých skladbách. */
    suspend fun preview(item: CatalogItem, onProgress: (Float) -> Unit): File? {
        val mid = midiFile(item) ?: return null
        return com.daklok.crescendo.data.midi.MidiRenderer.renderNow(
            context,
            mid,
            com.daklok.crescendo.data.midi.RenderOptions(hint = item.renderHint, maxSeconds = PREVIEW_SECONDS),
            onProgress,
        )
    }

    /** Práve vyrábané referenčné nahrávky: ID skladby → priebeh 0..1 (−1 = sťahuje sa). */
    private val _referenceJobs = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, Float>>(emptyMap())
    val referenceJobs: kotlinx.coroutines.flow.StateFlow<Map<Long, Float>> = _referenceJobs

    /** Stiahne PDF a pridá ho do knižnice (do [folderId]). Vráti ID novej skladby. */
    suspend fun download(
        item: CatalogItem,
        library: LibraryRepository,
        folderId: Long?,
        onProgress: (Float) -> Unit = {},
    ): Result<Long> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "catalog").apply { mkdirs() }
            val tmp = File(dir, "${item.id.ifBlank { "piece" }}.pdf")
            try {
                val conn = URL(item.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    copyWithProgress(conn, tmp, onProgress)
                } finally {
                    conn.disconnect()
                }
                onProgress(-1f) // import do knižnice – bez presného priebehu
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
