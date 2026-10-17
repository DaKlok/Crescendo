package com.daklok.crescendo.data

import android.content.Context
import java.io.File

/**
 * Zvukové súbory skladby:
 * - referenčná nahrávka (MP3/M4A… nahraná používateľom): filesDir/audio/<id>.<prípona>
 * - vlastné nahrávky z mikrofónu: filesDir/recordings/<id>/<čas>.m4a
 * - nahrávky hry z digitálneho klavíra (MIDI): filesDir/recordings/<id>/<čas>.mid
 */
class PieceAudio(private val context: Context) {
    private val audioDir get() = File(context.filesDir, "audio").apply { mkdirs() }
    private fun recDir(pieceId: Long) = File(File(context.filesDir, "recordings"), pieceId.toString()).apply { mkdirs() }

    fun reference(pieceId: Long): File? =
        audioDir.listFiles()?.firstOrNull { it.nameWithoutExtension == pieceId.toString() }

    /** Skopíruje vybraný zvukový súbor ako referenčnú nahrávku (nahradí predošlú). */
    fun setReference(pieceId: Long, uri: android.net.Uri, ext: String): File? {
        reference(pieceId)?.delete()
        val target = File(audioDir, "$pieceId.${ext.ifBlank { "mp3" }}")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            target
        }.getOrNull()
    }

    fun removeReference(pieceId: Long) {
        reference(pieceId)?.delete()
    }

    fun recordings(pieceId: Long): List<File> =
        recDir(pieceId).listFiles()?.filter { it.extension == "m4a" || it.extension == "mid" }
            ?.sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }.orEmpty()

    fun newRecordingFile(pieceId: Long): File = File(recDir(pieceId), "${System.currentTimeMillis()}.m4a")

    fun newMidiFile(pieceId: Long): File = File(recDir(pieceId), "${System.currentTimeMillis()}.mid")

    fun deleteAll(pieceId: Long) {
        reference(pieceId)?.delete()
        File(File(context.filesDir, "recordings"), pieceId.toString()).deleteRecursively()
        com.daklok.crescendo.data.midi.MidiRenderer.deleteForPiece(context, pieceId)
    }
}
