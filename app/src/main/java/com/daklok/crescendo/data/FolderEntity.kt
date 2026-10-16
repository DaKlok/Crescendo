package com.daklok.crescendo.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Priečinok v knižnici. [parentId] = null znamená priečinok priamo v knižnici (vnorenie je povolené). */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    /** Podpriečinky zrušeného priečinka sa presunú o úroveň vyššie. */
    @Query("UPDATE folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentChildren(oldParent: Long, newParent: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
