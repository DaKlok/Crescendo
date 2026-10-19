package com.daklok.crescendo.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Cvičebný úsek: ťažké miesto v notách (obdĺžnik na strane), ktoré si chceš precvičiť.
 * Počíta, koľkokrát si ho prešiel (spolu aj dnes), a môže mať vlastné tempo pre postupné zrýchľovanie.
 * Súradnice sú normalizované na celú stranu (0..1), rovnako ako poznámky.
 */
@Entity(tableName = "practice_spots", indices = [Index("pieceId")])
data class PracticeSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pieceId: Long,
    /** Strana (od 0). */
    val page: Int,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val name: String = "",
    /** Koľkokrát bol úsek prejdený (spolu). */
    val reps: Int = 0,
    /** Prejdenia v deň [repsDay] (epochDay), napr. „dnes 5×“. */
    val dayReps: Int = 0,
    val repsDay: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long = 0,
    /** Zvládnuté: presunie sa medzi hotové, v notách sa ukazuje nenápadne. */
    val done: Boolean = false,
    /** Začiatočné tempo zrýchľovania, s ktorým sa úsek naposledy cvičil (0 = podľa skladby). */
    val fromBpm: Int = 0,
    /** Najvyššie tempo, na ktorom bol úsek prejdený s metronómom (0 = zatiaľ nič). */
    val bestBpm: Int = 0,
) {
    /** Dnešné prejdenia (po polnoci sa začína od nuly). */
    fun today(): Int = if (repsDay == LocalDate.now().toEpochDay()) dayReps else 0
}

@Dao
interface PracticeSpotDao {
    @Insert
    suspend fun insert(spot: PracticeSpotEntity): Long

    @Update
    suspend fun update(spot: PracticeSpotEntity)

    @Query("SELECT * FROM practice_spots WHERE id = :id")
    suspend fun get(id: Long): PracticeSpotEntity?

    @Query("DELETE FROM practice_spots WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM practice_spots WHERE pieceId = :pieceId ORDER BY page, y1, x1")
    fun observeForPiece(pieceId: Long): Flow<List<PracticeSpotEntity>>

    @Query("SELECT * FROM practice_spots WHERE pieceId = :pieceId ORDER BY page, y1, x1")
    suspend fun forPiece(pieceId: Long): List<PracticeSpotEntity>

    @Query("SELECT * FROM practice_spots ORDER BY lastPracticedAt DESC")
    fun observeAll(): Flow<List<PracticeSpotEntity>>

    @Query("DELETE FROM practice_spots WHERE pieceId = :pieceId")
    suspend fun deleteForPiece(pieceId: Long)

    @Query("UPDATE practice_spots SET page = page + :by WHERE pieceId = :pieceId")
    suspend fun shiftPages(pieceId: Long, by: Int)
}

/** Pripočíta (alebo odpočíta) prejdenie úseku; vráti nový stav. */
suspend fun PracticeSpotDao.addRep(id: Long, delta: Int = 1): PracticeSpotEntity? {
    val s = get(id) ?: return null
    val today = LocalDate.now().toEpochDay()
    val day = if (s.repsDay == today) s.dayReps else 0
    val updated = s.copy(
        reps = (s.reps + delta).coerceAtLeast(0),
        dayReps = (day + delta).coerceAtLeast(0),
        repsDay = today,
        lastPracticedAt = if (delta > 0) System.currentTimeMillis() else s.lastPracticedAt,
    )
    update(updated)
    return updated
}

/** Úseky po úprave strán: [pages] = nové poradie pôvodných strán (s otočením); otočené a vynechané strany úseky stratia. */
suspend fun PracticeSpotDao.remapPages(pieceId: Long, pages: List<Pair<Int, Int>>) {
    val spots = forPiece(pieceId)
    for (s in spots) {
        val newIndex = pages.indexOfFirst { it.first == s.page && it.second % 360 == 0 }
        if (newIndex < 0) delete(s.id) else if (newIndex != s.page) update(s.copy(page = newIndex))
    }
}
