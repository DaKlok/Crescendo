package com.daklok.crescendo.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Jedno cvičenie: ako dlho bola skladba otvorená (aplikácia v popredí). */
@Entity(tableName = "practice_sessions", indices = [Index("pieceId")])
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pieceId: Long,
    val startedAt: Long,
    val durationSec: Int,
)

@Dao
interface PracticeDao {
    @Insert
    suspend fun insert(session: PracticeSessionEntity)

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<PracticeSessionEntity>>

    @Query("DELETE FROM practice_sessions WHERE pieceId = :pieceId")
    suspend fun deleteForPiece(pieceId: Long)
}
