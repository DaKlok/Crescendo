package com.daklok.crescendo

import android.app.Application
import com.daklok.crescendo.data.AnnotationStore
import com.daklok.crescendo.data.AppDatabase
import com.daklok.crescendo.data.LibraryRepository
import com.daklok.crescendo.data.SettingsRepository

class CrescendoApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val library by lazy { LibraryRepository(this, database.pieceDao(), database.folderDao()) }
    val settings by lazy { SettingsRepository(this) }
    val annotations by lazy { AnnotationStore(this) }
}
