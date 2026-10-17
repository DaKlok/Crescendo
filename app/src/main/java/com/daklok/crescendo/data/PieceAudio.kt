package com.daklok.crescendo.data

import android.content.Context
import java.io.File

/**
 * Zvukové súbory skladby:
 * - referenčné nahrávky (z katalógu aj vlastné MP3/M4A…): filesDir/audio/<id>/
 * - vlastné nahrávky z mikrofónu: filesDir/recordings/<id>/<čas>.m4a
 * - nahrávky hry z digitálneho klavíra (MIDI): filesDir/recordings/<id>/<čas>.mid
 */
class PieceAudio(private val context: Context) {
    private val audioDir get() = File(context.filesDir, "audio").apply { mkdirs() }
    private fun refDir(pieceId: Long) = File(audioDir, pieceId.toString())
    private fun recDir(pieceId: Long) = File(File(context.filesDir, "recordings"), pieceId.toString()).apply { mkdirs() }
    private val prefs get() = context.getSharedPreferences("reference_audio", Context.MODE_PRIVATE)

    /**
     * Referenčné nahrávky skladby: filesDir/audio/<id>/ –
     * „official.*“ = nahrávka z katalógu (vyrobená z MIDI), ostatné „<čas>__<názov>.*“ pridal používateľ.
     * Staršia verzia mala jedinú nahrávku filesDir/audio/<id>.<prípona> – presunie sa sem.
     */
    fun references(pieceId: Long): List<ReferenceAudio> {
        migrate(pieceId)
        val files = refDir(pieceId).listFiles()?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }.orEmpty()
        return files.map { f ->
            val official = f.nameWithoutExtension == OFFICIAL
            val added = f.name.substringBefore("__", "").toLongOrNull() ?: f.lastModified()
            val label = if (official) "" else f.nameWithoutExtension.substringAfter("__")
            ReferenceAudio(f, official, label, added)
        }.sortedWith(compareBy<ReferenceAudio> { !it.official }.thenBy { it.added })
    }

    /** Vybraná (naposledy použitá) referenčná nahrávka, inak oficiálna / prvá. */
    fun reference(pieceId: Long): File? {
        val all = references(pieceId)
        val selected = prefs.getString("sel_$pieceId", null)
        return (all.firstOrNull { it.file.name == selected } ?: all.firstOrNull())?.file
    }

    fun select(pieceId: Long, file: File) {
        prefs.edit().putString("sel_$pieceId", file.name).apply()
    }

    fun hasOfficial(pieceId: Long): Boolean = references(pieceId).any { it.official }

    /** Pridá vlastnú referenčnú nahrávku (zo súboru vybraného v telefóne) – ostatné ostanú. */
    fun addReference(pieceId: Long, uri: android.net.Uri, displayName: String?, ext: String): File? {
        val dir = refDir(pieceId).apply { mkdirs() }
        val base = (displayName?.substringBeforeLast('.') ?: "audio")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "audio" }
        val target = File(dir, "${System.currentTimeMillis()}__$base.${ext.ifBlank { "mp3" }}")
        return copy(uri, target)?.also { select(pieceId, it) }
    }

    /** Nastaví oficiálnu nahrávku z katalógu (nahradí len predošlú oficiálnu). */
    fun setOfficial(pieceId: Long, source: File): File? {
        val dir = refDir(pieceId).apply { mkdirs() }
        dir.listFiles()?.filter { it.nameWithoutExtension == OFFICIAL }?.forEach { it.delete() }
        val target = File(dir, "$OFFICIAL.${source.extension.ifBlank { "m4a" }}")
        // nový súbor (nie prepísanie na mieste) – prehrávač, ktorý má otvorený starý, ho nezamieša s novým
        val tmp = File(dir, "$OFFICIAL.new.part")
        return runCatching {
            source.copyTo(tmp, overwrite = true)
            target.delete()
            if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true)
            tmp.delete()
            target
        }.getOrNull()
    }

    /** Pridá súbor priamo (napr. z balíka .crescendo). */
    fun addFile(pieceId: Long, source: File, label: String = "audio"): File? {
        val dir = refDir(pieceId).apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}__$label.${source.extension.ifBlank { "m4a" }}")
        return runCatching { source.copyTo(target, overwrite = true) }.getOrNull()
    }

    fun removeReference(pieceId: Long, file: File) {
        file.delete()
        if (prefs.getString("sel_$pieceId", null) == file.name) prefs.edit().remove("sel_$pieceId").apply()
    }

    /** Premenovanie vlastnej nahrávky (oficiálna sa nepremenúva). */
    fun rename(pieceId: Long, ref: ReferenceAudio, newLabel: String): File? {
        if (ref.official) return ref.file
        val clean = newLabel.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { return ref.file }
        val target = File(ref.file.parentFile, "${ref.added}__$clean.${ref.file.extension}")
        val wasSelected = prefs.getString("sel_$pieceId", null) == ref.file.name
        return if (ref.file.renameTo(target)) {
            if (wasSelected) select(pieceId, target)
            target
        } else ref.file
    }

    private fun copy(uri: android.net.Uri, target: File): File? = runCatching {
        val tmp = File(target.path + ".part")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (tmp.length() > 0 && tmp.renameTo(target)) target else {
            tmp.delete()
            null
        }
    }.getOrNull()

    /** Stará jediná nahrávka audio/<id>.<prípona> → audio/<id>/… (z katalógu ako oficiálna). */
    private fun migrate(pieceId: Long) {
        val legacy = audioDir.listFiles()?.firstOrNull { it.isFile && it.nameWithoutExtension == pieceId.toString() } ?: return
        val dir = refDir(pieceId).apply { mkdirs() }
        val fromCatalog = context.getSharedPreferences("catalog", Context.MODE_PRIVATE).all
            .any { (k, v) -> k.startsWith("piece_") && v == pieceId }
        val name = if (fromCatalog && legacy.extension == "m4a") "$OFFICIAL.m4a" else "${legacy.lastModified()}__audio.${legacy.extension}"
        if (!legacy.renameTo(File(dir, name))) {
            runCatching { legacy.copyTo(File(dir, name), overwrite = true) }
            legacy.delete()
        }
    }

    companion object {
        private const val OFFICIAL = "official"
    }

    fun recordings(pieceId: Long): List<File> =
        recDir(pieceId).listFiles()?.filter { it.extension == "m4a" || it.extension == "mid" }
            ?.sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }.orEmpty()

    fun newRecordingFile(pieceId: Long): File = File(recDir(pieceId), "${System.currentTimeMillis()}.m4a")

    fun newMidiFile(pieceId: Long): File = File(recDir(pieceId), "${System.currentTimeMillis()}.mid")

    fun deleteAll(pieceId: Long) {
        audioDir.listFiles()?.filter { it.isFile && it.nameWithoutExtension == pieceId.toString() }?.forEach { it.delete() }
        refDir(pieceId).deleteRecursively()
        prefs.edit().remove("sel_$pieceId").apply()
        File(File(context.filesDir, "recordings"), pieceId.toString()).deleteRecursively()
        com.daklok.crescendo.data.midi.MidiRenderer.deleteForPiece(context, pieceId)
        PieceMidi(context).deleteAll(pieceId)
    }
}

/** Jedna referenčná nahrávka: [official] = z katalógu; [label] = názov vlastnej nahrávky. */
data class ReferenceAudio(val file: File, val official: Boolean, val label: String, val added: Long)
