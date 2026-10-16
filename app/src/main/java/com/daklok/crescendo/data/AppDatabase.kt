package com.daklok.crescendo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PieceEntity::class, FolderEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pieceDao(): PieceDao
    abstract fun folderDao(): FolderDao

    companion object {
        /** v1 -> v2: priečinky. Existujúce skladby zostanú v knižnici (folderId = null). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `folderId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`parentId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "crescendo.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
