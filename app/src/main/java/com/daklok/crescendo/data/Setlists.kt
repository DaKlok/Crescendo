package com.daklok.crescendo.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Setlist – zoradený zoznam skladieb (koncert, omša, skúška…). */
@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Jedna položka setlistu. Tá istá skladba môže byť v setliste aj viackrát. */
@Entity(tableName = "setlist_items", indices = [Index("setlistId")])
data class SetlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setlistId: Long,
    val pieceId: Long,
    val position: Int,
)

@Dao
interface SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY createdAt DESC")
    fun observeSetlists(): Flow<List<SetlistEntity>>

    @Query("SELECT * FROM setlist_items ORDER BY setlistId, position")
    fun observeItems(): Flow<List<SetlistItemEntity>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    suspend fun getSetlist(id: Long): SetlistEntity?

    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId ORDER BY position")
    suspend fun itemsOf(setlistId: Long): List<SetlistItemEntity>

    @Insert
    suspend fun insertSetlist(setlist: SetlistEntity): Long

    @Query("UPDATE setlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun deleteSetlist(id: Long)

    @Query("DELETE FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun deleteItemsOf(setlistId: Long)

    @Insert
    suspend fun insertItems(items: List<SetlistItemEntity>)

    @Query("DELETE FROM setlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE setlist_items SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("SELECT MAX(position) FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun maxPosition(setlistId: Long): Int?

    /** Pri zmazaní skladby zmizne aj zo všetkých setlistov. */
    @Query("DELETE FROM setlist_items WHERE pieceId = :pieceId")
    suspend fun deleteItemsForPiece(pieceId: Long)
}

class SetlistRepository(private val dao: SetlistDao) {
    fun observeSetlists() = dao.observeSetlists()
    fun observeItems() = dao.observeItems()

    suspend fun get(id: Long) = dao.getSetlist(id)
    suspend fun itemsOf(id: Long) = dao.itemsOf(id)

    suspend fun create(name: String): Long = dao.insertSetlist(SetlistEntity(name = name))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteSetlist(id)
    }

    /** Pridá skladby na koniec setlistu (v danom poradí). */
    suspend fun addPieces(setlistId: Long, pieceIds: List<Long>) {
        if (pieceIds.isEmpty()) return
        val start = (dao.maxPosition(setlistId) ?: -1) + 1
        dao.insertItems(pieceIds.mapIndexed { i, pid -> SetlistItemEntity(setlistId = setlistId, pieceId = pid, position = start + i) })
    }

    suspend fun removeItem(itemId: Long) = dao.deleteItem(itemId)

    /** Uloží nové poradie položiek. */
    suspend fun reorder(items: List<SetlistItemEntity>) {
        items.forEachIndexed { i, item -> if (item.position != i) dao.setPosition(item.id, i) }
    }
}
