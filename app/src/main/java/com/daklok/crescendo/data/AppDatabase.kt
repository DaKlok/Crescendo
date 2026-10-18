package com.daklok.crescendo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PieceEntity::class, FolderEntity::class, SetlistEntity::class, SetlistItemEntity::class, PracticeSessionEntity::class],
    version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pieceDao(): PieceDao
    abstract fun folderDao(): FolderDao
    abstract fun setlistDao(): SetlistDao
    abstract fun practiceDao(): PracticeDao

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

        /** v2 -> v3: farba priečinka. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `color` INTEGER")
            }
        }

        /** v3 -> v4: setlisty. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `setlists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `setlist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`setlistId` INTEGER NOT NULL, " +
                        "`pieceId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_setlist_items_setlistId` ON `setlist_items` (`setlistId`)")
            }
        }

        /** v4 -> v5: údaje o skladbe (skladateľ, tónina, nástroj, obtiažnosť, štítky) + denník cvičenia. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `composer` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `keySignature` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `instrument` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `difficulty` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `practice_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pieceId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`durationSec` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_sessions_pieceId` ON `practice_sessions` (`pieceId`)")
            }
        }

        /** v5 -> v6: poznámka k cvičeniu v denníku. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `practice_sessions` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "crescendo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
