package com.daklok.crescendo.data

import android.content.Context
import com.daklok.crescendo.data.midi.MidiRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Čo zaberá miesto v aplikácii. */
enum class StorageKind(val deletable: Boolean) {
    /** PDF nôt (+ náhľady a poznámky) – mažú sa spolu so skladbou v knižnici. */
    SCORES(false),
    REFERENCES(true),
    RECORDINGS(true),
    /** MIDI pre vizualizér (katalóg / importované). */
    MIDI(true),
    /** Zvuk vyrobený z MIDI – dá sa kedykoľvek vyrobiť znova. */
    RENDERS(true),
    /** Stiahnuté zvuky nástrojov – pri potrebe sa stiahnu znova. */
    INSTRUMENTS(true),
    /** Dočasné súbory (MIDI z katalógu, ukážky…). */
    CACHE(true),
    /** Databáza, nastavenia. */
    OTHER(false),
}

/** Jeden súbor (alebo priečinok) v prehľade úložiska. */
class StorageEntry(
    val kind: StorageKind,
    val file: File,
    val bytes: Long,
    /** Skladba, ku ktorej patrí (ak nejaká). */
    val pieceId: Long?,
    /** Bližší popis (názov nahrávky, nástroja…). */
    val label: String,
    val modified: Long,
)

class StorageGroup(val kind: StorageKind, val entries: List<StorageEntry>) {
    val bytes: Long = entries.sumOf { it.bytes }
}

/**
 * Prehľad úložiska: čo všetko je v aplikácii uložené a koľko to zaberá, s možnosťou zmazať presne to,
 * čo netreba. Zmeny v zálohovaných priečinkoch sa prejavia aj v synchronizácii (pri ďalšom nahratí).
 */
class StorageManager(private val context: Context) {
    private val files get() = context.filesDir

    private fun size(f: File): Long = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()

    private fun idOf(dir: File?): Long? = dir?.name?.toLongOrNull()

    suspend fun scan(): List<StorageGroup> = withContext(Dispatchers.IO) {
        val out = ArrayList<StorageGroup>()

        // noty – PDF podľa skladby (názov súboru = ID), náhľady a poznámky pripočítané k nim
        val scores = File(files, "pdfs").listFiles()?.filter { it.isFile }?.map { f ->
            StorageEntry(StorageKind.SCORES, f, f.length(), f.nameWithoutExtension.toLongOrNull(), f.name, f.lastModified())
        }.orEmpty()
        val extras = listOf("thumbs", "annotations").map { File(files, it) }.filter { it.exists() }
            .map { d -> StorageEntry(StorageKind.SCORES, d, size(d), null, d.name, d.lastModified()) }
        out += StorageGroup(StorageKind.SCORES, (scores + extras).sortedByDescending { it.bytes })

        // referenčné nahrávky: priečinok audio/<id>
        val refs = File(files, "audio").listFiles()?.flatMap { d ->
            if (d.isDirectory) d.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.map { f ->
                val label = if (f.nameWithoutExtension == "official") "official" else f.nameWithoutExtension.substringAfter("__", f.nameWithoutExtension)
                StorageEntry(StorageKind.REFERENCES, f, f.length(), idOf(d), label, f.lastModified())
            }.orEmpty()
            else listOf(StorageEntry(StorageKind.REFERENCES, d, d.length(), d.nameWithoutExtension.toLongOrNull(), "official", d.lastModified()))
        }.orEmpty()
        out += StorageGroup(StorageKind.REFERENCES, refs.sortedByDescending { it.bytes })

        // vlastné nahrávky: priečinok recordings/<id>
        val recs = File(files, "recordings").listFiles()?.filter { it.isDirectory }?.flatMap { d ->
            d.listFiles()?.filter { it.isFile }?.map { f ->
                StorageEntry(StorageKind.RECORDINGS, f, f.length(), idOf(d), f.name, f.nameWithoutExtension.toLongOrNull() ?: f.lastModified())
            }.orEmpty()
        }.orEmpty()
        out += StorageGroup(StorageKind.RECORDINGS, recs.sortedByDescending { it.bytes })

        // MIDI pre vizualizér: súbory .mid v piece_midi/<id>
        val midi = File(files, PieceMidi.DIR).listFiles()?.filter { it.isDirectory }?.flatMap { d ->
            d.listFiles()?.filter { it.isFile && it.extension.equals("mid", true) }?.map { f ->
                val label = if (f.name == "catalog.mid") "catalog" else f.nameWithoutExtension.substringAfter("__", f.nameWithoutExtension)
                StorageEntry(StorageKind.MIDI, f, f.length(), idOf(d), label, f.lastModified())
            }.orEmpty()
        }.orEmpty()
        out += StorageGroup(StorageKind.MIDI, midi.sortedByDescending { it.bytes })

        // zvuky vyrobené z MIDI: midi_render/<id>_<názov>….m4a (ukážky z katalógu: catalog_midi_…)
        val renders = File(files, "midi_render").listFiles()?.filter { it.isFile }?.map { f ->
            val id = f.name.substringBefore('_').toLongOrNull()
            val label = if (id == null && f.name.startsWith("catalog_midi_")) "preview" else f.nameWithoutExtension.substringAfter('_')
            StorageEntry(StorageKind.RENDERS, f, f.length(), id, label, f.lastModified())
        }.orEmpty()
        out += StorageGroup(StorageKind.RENDERS, renders.sortedByDescending { it.bytes })

        // stiahnuté nástroje: instruments/<názov>/
        val inst = File(files, "instruments").listFiles()?.filter { it.isDirectory }?.map { d ->
            StorageEntry(StorageKind.INSTRUMENTS, d, size(d), null, d.name, d.lastModified())
        }.orEmpty()
        out += StorageGroup(StorageKind.INSTRUMENTS, inst.filter { it.bytes > 0 }.sortedByDescending { it.bytes })

        // vyrovnávacia pamäť
        val cache = context.cacheDir.listFiles()?.map { f ->
            StorageEntry(StorageKind.CACHE, f, size(f), null, f.name, f.lastModified())
        }.orEmpty()
        out += StorageGroup(StorageKind.CACHE, cache.filter { it.bytes > 0 }.sortedByDescending { it.bytes })

        // ostatné: databáza, nastavenia
        val db = context.getDatabasePath("crescendo.db").parentFile
        val other = listOfNotNull(
            db?.takeIf { it.exists() }?.let { StorageEntry(StorageKind.OTHER, it, size(it), null, "database", it.lastModified()) },
            File(files, "datastore").takeIf { it.exists() }?.let { StorageEntry(StorageKind.OTHER, it, size(it), null, "settings", it.lastModified()) },
            File(context.applicationInfo.dataDir, "shared_prefs").takeIf { it.exists() }
                ?.let { StorageEntry(StorageKind.OTHER, it, size(it), null, "prefs", it.lastModified()) },
        )
        out += StorageGroup(StorageKind.OTHER, other)
        out
    }

    /** Zmaže položku (len tie, ktoré sa dajú zmazať). Vráti uvoľnené bajty. */
    suspend fun delete(entry: StorageEntry): Long = withContext(Dispatchers.IO) {
        if (!entry.kind.deletable) return@withContext 0L
        val freed = entry.bytes
        when (entry.kind) {
            StorageKind.REFERENCES -> {
                val id = entry.pieceId
                if (id != null && entry.file.parentFile?.name == id.toString()) PieceAudio(context).removeReference(id, entry.file)
                else entry.file.delete()
            }
            StorageKind.RECORDINGS -> {
                entry.file.delete()
                if (MidiRenderer.isMidi(entry.file)) MidiRenderer.delete(context, entry.file)
                PieceMidi.notifyChanged()
            }
            StorageKind.MIDI -> {
                val id = entry.pieceId
                val pm = PieceMidi(context)
                val src = id?.let { pm.sources(it).firstOrNull { s -> s.file.path == entry.file.path } }
                if (src != null && id != null) pm.remove(id, src) else entry.file.delete()
            }
            StorageKind.INSTRUMENTS, StorageKind.CACHE -> entry.file.deleteRecursively()
            else -> entry.file.delete()
        }
        if (entry.kind == StorageKind.RENDERS || entry.kind == StorageKind.MIDI) PieceMidi.notifyChanged()
        freed
    }

    /** Zmaže celú skupinu (napr. všetky vyrobené zvuky alebo vyrovnávaciu pamäť). */
    suspend fun deleteAll(group: StorageGroup): Long {
        var freed = 0L
        for (e in group.entries) freed += delete(e)
        return freed
    }
}
