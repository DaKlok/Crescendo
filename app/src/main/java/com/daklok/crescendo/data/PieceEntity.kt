package com.daklok.crescendo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Jedna skladba (importované PDF) v knižnici. */
@Entity(tableName = "pieces")
data class PieceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Názov súboru v internom úložisku aplikácie (filesDir/pdfs). */
    val fileName: String,
    /** Názov súboru náhľadu (filesDir/thumbs) alebo null. */
    val thumbName: String? = null,
    val pageCount: Int = 0,
    /** Naposledy zobrazená strana (od 0). */
    val lastPage: Int = 0,
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    /** Priečinok, v ktorom skladba je (null = priamo v knižnici). */
    val folderId: Long? = null,
    // Údaje o skladbe (v5)
    val composer: String = "",
    val keySignature: String = "",
    val instrument: String = "",
    /** Obtiažnosť 0–5 (0 = neuvedená). */
    val difficulty: Int = 0,
    /** Štítky oddelené čiarkou. */
    val tags: String = "",
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
