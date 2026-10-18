package com.daklok.crescendo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Jedno cvičenie: ako dlho bola skladba otvorená (aplikácia v popredí), alebo ručne pridaný záznam. */
@Entity(tableName = "practice_sessions", indices = [Index("pieceId")])
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pieceId: Long,
    val startedAt: Long,
    val durationSec: Int,
    /** Poznámka k cvičeniu (čo išlo, čo nie…). */
    val note: String = "",
)

@Dao
interface PracticeDao {
    @Insert
    suspend fun insert(session: PracticeSessionEntity): Long

    @Update
    suspend fun update(session: PracticeSessionEntity)

    @Query("UPDATE practice_sessions SET durationSec = :sec WHERE id = :id")
    suspend fun updateDuration(id: Long, sec: Int)

    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM practice_sessions WHERE startedAt >= :since")
    suspend fun since(since: Long): List<PracticeSessionEntity>

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<PracticeSessionEntity>>

    @Query("DELETE FROM practice_sessions WHERE pieceId = :pieceId")
    suspend fun deleteForPiece(pieceId: Long)
}

/**
 * Meria čas cvičenia jednej skladby (len kým je aplikácia v popredí). Záznam sa do denníka ukladá
 * priebežne (každých pár desiatok sekúnd), takže sa nestratí ani pri zabití aplikácie.
 * Kratšie ako 30 s sa neukladá.
 */
class PracticeTracker(private val dao: PracticeDao, private val scope: CoroutineScope, private val pieceId: Long) {
    private val startedAt = System.currentTimeMillis()
    private var total = 0L
    private var resumedAt = 0L
    private var rowId: Long? = null
    private val lock = Mutex()

    fun resume() {
        if (resumedAt == 0L) resumedAt = System.currentTimeMillis()
    }

    fun pause() {
        if (resumedAt > 0) {
            total += System.currentTimeMillis() - resumedAt
            resumedAt = 0L
        }
    }

    private fun seconds(): Int {
        val running = if (resumedAt > 0) System.currentTimeMillis() - resumedAt else 0L
        return ((total + running) / 1000).toInt()
    }

    /** Uloží aktuálny stav (nový záznam alebo predĺženie existujúceho). */
    fun flush() {
        val sec = seconds()
        if (sec < MIN_SEC) return
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val id = rowId
                    if (id == null) rowId = dao.insert(PracticeSessionEntity(pieceId = pieceId, startedAt = startedAt, durationSec = sec))
                    else dao.updateDuration(id, sec)
                }
            }
        }
    }

    companion object {
        const val MIN_SEC = 30
    }
}

/** Denný cieľ cvičenia (minúty) – je v zálohe aj v synchronizácii. */
object PracticeGoal {
    private const val PREFS = "practice"
    const val DEFAULT_MIN = 30

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("daily_goal_min", DEFAULT_MIN)

    fun set(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("daily_goal_min", minutes).apply()
    }
}
