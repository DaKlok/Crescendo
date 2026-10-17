package com.daklok.crescendo.data

import android.content.Context
import android.net.Uri
import com.daklok.crescendo.data.midi.MidiFile
import com.daklok.crescendo.data.midi.MidiRenderer
import com.daklok.crescendo.data.midi.RenderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Odkiaľ sú noty pre vizualizér. */
enum class MidiSourceKind { CATALOG, IMPORTED, RECORDING }

/**
 * Jeden MIDI súbor, ktorý sa dá použiť vo vizualizéri. [key] sa ukladá ako výber:
 * „catalog“, „import:<súbor>“ alebo „rec:<súbor>“.
 */
class MidiSource(val key: String, val kind: MidiSourceKind, val file: File, val label: String, val added: Long)

/**
 * MIDI skladby pre vizualizér (padajúce noty / klaviatúra) a zvuk. Ku skladbe môže byť viac zdrojov naraz –
 * nič sa pri prepnutí nemaže:
 * - filesDir/piece_midi/<id>/catalog.mid (+ catalog.hint) – z online katalógu,
 * - filesDir/piece_midi/<id>/<čas>__<názov>.mid – importované súbory,
 * - vlastné nahrávky z klavíra (súbory .mid v filesDir/recordings/<id> – nekopírujú sa),
 * - filesDir/piece_midi/<id>/selected – ktorý zdroj je vybraný.
 * Všetko je v zálohe aj v synchronizácii (priečinky piece_midi a recordings).
 */
class PieceMidi(private val context: Context) {
    private val root get() = File(context.filesDir, DIR).apply { mkdirs() }

    private fun dir(pieceId: Long): File {
        val d = File(root, pieceId.toString())
        migrateLegacy(pieceId, d)
        return d.apply { mkdirs() }
    }

    /** Staršia verzia: jediný súbor piece_midi/<id>.mid (+ .hint) → presun do priečinka skladby. */
    private fun migrateLegacy(pieceId: Long, d: File) {
        val old = File(root, "$pieceId.mid")
        if (!old.exists()) return
        d.mkdirs()
        val oldHint = File(root, "$pieceId.hint")
        val hint = oldHint.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val fromCatalog = hint.isNotBlank() || context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
            .all.any { (k, v) -> k.startsWith("piece_") && v == pieceId }
        if (old.length() > 0) {
            MidiRenderer.delete(context, old)
            if (fromCatalog && !File(d, CATALOG).exists()) {
                old.copyTo(File(d, CATALOG), overwrite = true)
                if (hint.isNotBlank()) File(d, CATALOG_HINT).writeText(hint)
            } else {
                old.copyTo(File(d, "${old.lastModified()}__MIDI.mid"), overwrite = true)
            }
        }
        old.delete()
        oldHint.delete()
    }

    /** Všetky zdroje ku skladbe: katalóg, importované (najnovšie prvé), nahrávky z klavíra (najnovšie prvé). */
    fun sources(pieceId: Long): List<MidiSource> {
        val d = dir(pieceId)
        val out = ArrayList<MidiSource>()
        File(d, CATALOG).takeIf { it.length() > 0 }?.let {
            out.add(MidiSource(KEY_CATALOG, MidiSourceKind.CATALOG, it, "", it.lastModified()))
        }
        d.listFiles()
            ?.filter { it.isFile && it.extension.equals("mid", true) && it.name != CATALOG && it.length() > 0 }
            ?.map { f ->
                val ts = f.nameWithoutExtension.substringBefore("__").toLongOrNull() ?: f.lastModified()
                val label = f.nameWithoutExtension.substringAfter("__", f.nameWithoutExtension)
                MidiSource("import:${f.name}", MidiSourceKind.IMPORTED, f, label, ts)
            }
            ?.sortedByDescending { it.added }
            ?.let { out.addAll(it) }
        File(File(context.filesDir, "recordings"), pieceId.toString()).listFiles()
            ?.filter { it.isFile && it.extension.equals("mid", true) && it.length() > 0 }
            ?.map { f ->
                val ts = f.nameWithoutExtension.toLongOrNull() ?: f.lastModified()
                MidiSource("rec:${f.name}", MidiSourceKind.RECORDING, f, "", ts)
            }
            ?.sortedByDescending { it.added }
            ?.let { out.addAll(it) }
        return out
    }

    /** Vybraný zdroj; ak nie je vybraný (alebo už neexistuje) – katalóg, inak najnovší importovaný. */
    fun selected(pieceId: Long): MidiSource? {
        val all = sources(pieceId)
        val key = File(dir(pieceId), SELECTED).takeIf { it.exists() }?.readText()?.trim()
        return all.firstOrNull { it.key == key } ?: all.firstOrNull { it.kind != MidiSourceKind.RECORDING }
    }

    fun select(pieceId: Long, key: String) {
        File(dir(pieceId), SELECTED).writeText(key)
        _version.update { it + 1 }
    }

    /** Súbor, z ktorého sa práve kreslia noty a vyrába zvuk. */
    fun file(pieceId: Long): File? = selected(pieceId)?.file

    fun catalogFile(pieceId: Long): File? = File(dir(pieceId), CATALOG).takeIf { it.length() > 0 }

    fun hint(pieceId: Long): String {
        val s = selected(pieceId) ?: return ""
        if (s.kind != MidiSourceKind.CATALOG) return ""
        return File(dir(pieceId), CATALOG_HINT).takeIf { it.exists() }?.readText()?.trim().orEmpty()
    }

    fun options(pieceId: Long) = RenderOptions(hint = hint(pieceId))

    /** Zmeny (nové MIDI, iný výber) – vizualizér sa podľa toho obnoví (spoločné pre všetky inštancie). */
    val version: StateFlow<Int> get() = _version.asStateFlow()

    /** Pridá MIDI z vybraného súboru (ostatné ostanú) a vyberie ho. Null, ak to nie je platné MIDI. */
    suspend fun import(pieceId: Long, uri: Uri, name: String?): File? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        val valid = runCatching { MidiFile.parse(bytes).any { it.status == 0x90 } }.getOrDefault(false)
        if (!valid) return@withContext null
        val label = (name?.substringBeforeLast('.') ?: "MIDI")
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(60).ifBlank { "MIDI" }
        val target = File(dir(pieceId), "${System.currentTimeMillis()}__$label.mid")
        writeAtomic(target, bytes)
        select(pieceId, "import:${target.name}")
        target
    }

    /** MIDI z katalógu (nahradí len predchádzajúce z katalógu). Vyberie sa, len ak ešte nič vybrané nie je. */
    suspend fun setCatalog(pieceId: Long, source: File, hint: String): File? = withContext(Dispatchers.IO) {
        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return@withContext null
        val d = dir(pieceId)
        val target = File(d, CATALOG)
        if (target.exists()) MidiRenderer.delete(context, target)
        writeAtomic(target, bytes)
        val hintFile = File(d, CATALOG_HINT)
        if (hint.isBlank()) hintFile.delete() else hintFile.writeText(hint)
        _version.update { it + 1 }
        target
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.path + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    /** Odstráni importovaný súbor alebo MIDI z katalógu (nahrávky z klavíra sa mažú v „Moje nahrávky“). */
    fun remove(pieceId: Long, source: MidiSource) {
        if (source.kind == MidiSourceKind.RECORDING) return
        MidiRenderer.delete(context, source.file)
        source.file.delete()
        if (source.kind == MidiSourceKind.CATALOG) File(dir(pieceId), CATALOG_HINT).delete()
        _version.update { it + 1 }
    }

    /** Pri zmazaní skladby. */
    fun deleteAll(pieceId: Long) {
        File(root, pieceId.toString()).deleteRecursively()
        File(root, "$pieceId.mid").delete()
        File(root, "$pieceId.hint").delete()
        _version.update { it + 1 }
    }

    /** Zvuk z MIDI skladby (vyrobí sa raz, potom je hneď k dispozícii). */
    suspend fun sound(pieceId: Long, onProgress: (Float) -> Unit = {}): File? {
        val mid = file(pieceId) ?: return null
        return MidiRenderer.renderNow(context, mid, options(pieceId), onProgress)
    }

    /** Kľúč, pod ktorým je výroba zvuku v [MidiRenderer.rendering] (priebeh). */
    fun renderKey(pieceId: Long): String? = file(pieceId)?.let { it.path + options(pieceId).suffix }

    /**
     * Začne vyrábať zvuk na pozadí (pokračuje aj po zatvorení vizualizéra). Priebeh: [MidiRenderer.rendering][renderKey].
     */
    fun prepare(pieceId: Long) {
        if (file(pieceId) == null || soundReady(pieceId) != null) return
        val key = renderKey(pieceId) ?: return
        if (key in MidiRenderer.rendering.value) return
        val app = context.applicationContext as com.daklok.crescendo.CrescendoApp
        app.appScope.launch(Dispatchers.Default) { runCatching { sound(pieceId) } }
    }

    fun soundReady(pieceId: Long): File? = file(pieceId)?.let { MidiRenderer.playable(context, it, options(pieceId)) }

    companion object {
        const val DIR = "piece_midi"
        private const val CATALOG = "catalog.mid"
        private const val CATALOG_HINT = "catalog.hint"
        private const val SELECTED = "selected"
        private const val KEY_CATALOG = "catalog"
        private val _version = MutableStateFlow(0)

        /** Pre ostatné časti aplikácie (napr. po zmazaní nahrávky) – vizualizér sa obnoví. */
        fun notifyChanged() = _version.update { it + 1 }
    }
}
