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

/**
 * MIDI skladby pre vizualizér (padajúce noty / klaviatúra) a zvuk:
 * filesDir/piece_midi/<id>.mid (+ <id>.hint = podľa čoho vybrať nástroj, napr. „guitar“).
 * Zálohuje sa spolu s ostatnými dátami.
 */
class PieceMidi(private val context: Context) {
    private val dir get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(pieceId: Long): File? = File(dir, "$pieceId.mid").takeIf { it.length() > 0 }

    fun hint(pieceId: Long): String = File(dir, "$pieceId.hint").takeIf { it.exists() }?.readText()?.trim().orEmpty()

    fun options(pieceId: Long) = RenderOptions(hint = hint(pieceId))

    /** Zmeny (nové MIDI) – vizualizér sa podľa toho obnoví (spoločné pre všetky inštancie). */
    val version: StateFlow<Int> get() = _version.asStateFlow()

    /** Uloží MIDI z vybraného súboru. Vráti súbor, alebo null, ak to nie je platné MIDI. */
    suspend fun import(pieceId: Long, uri: Uri): File? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        val valid = runCatching { MidiFile.parse(bytes).any { it.status == 0x90 } }.getOrDefault(false)
        if (!valid) return@withContext null
        save(pieceId, bytes, "")
    }

    /** Uloží MIDI zo súboru (napr. z katalógu alebo vlastnej nahrávky z klavíra). */
    suspend fun setFrom(pieceId: Long, source: File, hint: String): File? = withContext(Dispatchers.IO) {
        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return@withContext null
        save(pieceId, bytes, hint)
    }

    private fun save(pieceId: Long, bytes: ByteArray, hint: String): File? {
        val target = File(dir, "$pieceId.mid")
        file(pieceId)?.let { MidiRenderer.delete(context, it) } // starý vyrobený zvuk
        val tmp = File(target.path + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        val hintFile = File(dir, "$pieceId.hint")
        if (hint.isBlank()) hintFile.delete() else hintFile.writeText(hint)
        _version.update { it + 1 }
        return target
    }

    fun delete(pieceId: Long) {
        file(pieceId)?.let { MidiRenderer.delete(context, it) }
        File(dir, "$pieceId.mid").delete()
        File(dir, "$pieceId.hint").delete()
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
        private val _version = MutableStateFlow(0)
    }
}
