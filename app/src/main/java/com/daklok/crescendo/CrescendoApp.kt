package com.daklok.crescendo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.daklok.crescendo.data.LanguageManager
import android.content.Context
import android.app.Application
import com.daklok.crescendo.data.AnnotationStore
import com.daklok.crescendo.data.AppDatabase
import com.daklok.crescendo.data.LibraryRepository
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.SetlistRepository
import com.daklok.crescendo.data.backup.BackupManager

class CrescendoApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.wrap(base))
    }

    val database by lazy { AppDatabase.create(this) }
    val library by lazy { LibraryRepository(this, database.pieceDao(), database.folderDao(), database.setlistDao(), database.practiceDao()) }
    val setlists by lazy { SetlistRepository(database.setlistDao()) }
    val settings by lazy { SettingsRepository(this) }
    val annotations by lazy { AnnotationStore(this) }
    val backup by lazy { BackupManager(this) }
    val practice by lazy { database.practiceDao() }
    val pieceAudio by lazy { com.daklok.crescendo.data.PieceAudio(this) }
    val catalog by lazy { com.daklok.crescendo.data.CatalogRepository(this) }
    /** Pedále klavíra cez MIDI (USB / Bluetooth). */
    val midi by lazy { com.daklok.crescendo.data.MidiPedals(this) }

    /**
     * Rozsah pre práce, ktoré musia dobehnúť aj po zatvorení obrazovky / panela,
     * ktorý ich spustil (zmena jazyka, obnova zálohy).
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
