package com.daklok.crescendo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PieceDao {
    @Query("SELECT * FROM pieces")
    fun observeAll(): Flow<List<PieceEntity>>

    @Query("SELECT * FROM pieces WHERE id = :id")
    suspend fun getById(id: Long): PieceEntity?

    @Insert
    suspend fun insert(piece: PieceEntity): Long

    @Delete
    suspend fun delete(piece: PieceEntity)

    @Query("UPDATE pieces SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE pieces SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE pieces SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?)

    /** Skladby zo zrušeného priečinka sa presunú o úroveň vyššie. */
    @Query("UPDATE pieces SET folderId = :newFolder WHERE folderId = :oldFolder")
    suspend fun reparent(oldFolder: Long, newFolder: Long?)

    @Query("UPDATE pieces SET lastPage = :page, lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, openedAt: Long)
}
