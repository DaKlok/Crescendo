package com.daklok.crescendo.data.backup

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daklok.crescendo.CrescendoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Stav synchronizácie tohto zariadenia. Je v samostatných SharedPreferences, ktoré sa
 * NEzálohujú – každé zariadenie má vlastné ID a vlastný čas poslednej synchronizácie.
 */
object SyncState {
    private const val PREFS = "sync_state"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceId(c: Context): String {
        val p = prefs(c)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("device_id", it).commit()
        }
    }

    fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
        .ifBlank { "Android" }

    /** Čas (lokálny), kedy boli dáta naposledy zhodné s Drive. */
    fun lastSyncLocal(c: Context): Long = prefs(c).getLong("synced_local", 0L)

    /** Čas úpravy zálohy na Drive, s ktorou sme naposledy zosúladení. */
    fun lastSyncRemote(c: Context): Long = prefs(c).getLong("synced_remote", 0L)

    /** Zálohu z iného zariadenia s týmto časom si používateľ odložil na „neskôr“. */
    /** „Neskôr“ platí len do ďalšieho spustenia aplikácie – potom sa otázka zopakuje. */
    @Volatile
    private var dismissedAt = 0L

    fun dismissedRemote(@Suppress("UNUSED_PARAMETER") c: Context): Long = dismissedAt

    fun dismiss(@Suppress("UNUSED_PARAMETER") c: Context, remoteAt: Long) {
        dismissedAt = remoteAt
    }

    /** commit() – volá sa aj tesne pred reštartom aplikácie po obnove. */
    fun markSynced(c: Context, remoteAt: Long) {
        prefs(c).edit()
            .putLong("synced_local", System.currentTimeMillis())
            .putLong("synced_remote", remoteAt)
            .commit()
    }

    /** Zmenilo sa niečo v aplikácii od poslednej synchronizácie? */
    fun hasLocalChanges(c: Context): Boolean {
        val since = lastSyncLocal(c) + 2_000
        val files = c.filesDir
        val dirs = listOf("pdfs", "annotations", "audio", "recordings", "datastore", "piece_midi")
        val db = c.getDatabasePath("crescendo.db")
        // nastavenia, ktoré sú v zálohe (napr. vybraná referenčná nahrávka, skladby z katalógu)
        val prefsDir = File(c.applicationInfo.dataDir, "shared_prefs")
        val prefs = listOf("annotation_tools", "metronome", "catalog", "reference_audio", "practice").map { File(prefsDir, "$it.xml") }
        val candidates = sequenceOf(db, File(db.path + "-wal")) + prefs.asSequence() +
            dirs.asSequence().flatMap { File(files, it).walkTopDown().filter { f -> f.isFile } }
        // prázdny -wal vytvorí SQLite už pri otvorení databázy (napr. po obnove) – to nie je zmena
        return candidates.any { it.exists() && it.length() > 0 && it.lastModified() > since }
    }
}

/** Čo treba ukázať používateľovi. */
sealed interface SyncPrompt {
    /** Na Drive sú novšie dáta z iného zariadenia a tu sa nič nezmenilo. */
    data class RemoteNewer(val deviceName: String, val at: Long) : SyncPrompt

    /** Zmeny sú tu aj na Drive (z iného zariadenia) – treba vybrať. */
    data class Conflict(val deviceName: String, val at: Long) : SyncPrompt
}

/**
 * Synchronizácia medzi zariadeniami cez zálohu na Google Drive:
 * - pri odchode z aplikácie sa zmeny potichu nahrajú (ak na Drive nečaká novší stav z iného zariadenia),
 * - pri návrate do aplikácie sa skontroluje, či iné zariadenie nenahralo novší stav – a ponúkne ho.
 * Nikdy nič neprepíše bez opýtania.
 */
object SyncManager {
    private val _prompt = MutableStateFlow<SyncPrompt?>(null)
    val prompt: StateFlow<SyncPrompt?> = _prompt.asStateFlow()

    suspend fun enabled(app: CrescendoApp): Boolean {
        val s = app.settings.settings.first()
        return s.syncEnabled && s.backupEmail.isNotBlank()
    }

    /** Je záloha na Drive novšia ako náš posledný zosúladený stav a nahralo ju iné zariadenie? */
    private suspend fun foreignNewer(app: CrescendoApp, remote: RemoteBackup): Boolean {
        if (remote.modifiedAt <= SyncState.lastSyncRemote(app) + 1_000) return false
        if (remote.deviceId.isNotBlank()) return remote.deviceId != SyncState.deviceId(app)
        // stará záloha bez ID zariadenia: naša je len vtedy, ak sedí s časom poslednej zálohy odtiaľto
        val last = app.settings.settings.first().lastBackupAt
        return kotlin.math.abs(remote.modifiedAt - last) > 10 * 60_000L
    }

    /** Nahralo iné zariadenie medzitým novší stav? (vtedy sa nesmie potichu prepísať) */
    suspend fun otherDeviceNewer(app: CrescendoApp, token: String): Boolean =
        app.backup.remoteInfo(token)?.let { foreignNewer(app, it) } ?: false

    /** Návrat do aplikácie: je na Drive niečo novšie z iného zariadenia? */
    suspend fun checkRemote(app: CrescendoApp) {
        if (!enabled(app)) return
        val token = app.backup.silentToken() ?: return
        val remote = app.backup.remoteInfo(token) ?: return
        if (!foreignNewer(app, remote) || remote.modifiedAt == SyncState.dismissedRemote(app)) return
        val local = withContext(Dispatchers.IO) { SyncState.hasLocalChanges(app) }
        val name = remote.deviceName.ifBlank { "Google Drive" }
        _prompt.value = if (local) SyncPrompt.Conflict(name, remote.modifiedAt) else SyncPrompt.RemoteNewer(name, remote.modifiedAt)
    }

    /** Odchod z aplikácie: ak sa tu niečo zmenilo, naplánuje nahranie (keď bude internet). */
    suspend fun scheduleUploadIfChanged(app: CrescendoApp) {
        if (!enabled(app)) return
        if (_prompt.value != null) return // čaká rozhodnutie – nič neprepisujeme
        val changed = withContext(Dispatchers.IO) { SyncState.hasLocalChanges(app) }
        if (!changed) return
        val request = OneTimeWorkRequestBuilder<SyncUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("crescendo-sync-upload", ExistingWorkPolicy.REPLACE, request)
    }

    /** „Ponechať toto zariadenie“ – nahrá tento stav na Drive (prepíše ten z iného zariadenia). */
    suspend fun keepLocal(app: CrescendoApp) {
        _prompt.value = null
        val token = app.backup.silentToken() ?: return
        app.backup.backup(token, quiet = true)
    }

    /** „Neskôr“ – na túto zálohu sa už nebude pýtať. */
    fun later(app: CrescendoApp) {
        (_prompt.value as? SyncPrompt.RemoteNewer)?.let { SyncState.dismiss(app, it.at) }
        (_prompt.value as? SyncPrompt.Conflict)?.let { SyncState.dismiss(app, it.at) }
        _prompt.value = null
    }

    fun clearPrompt() {
        _prompt.value = null
    }
}

/** Nahratie zmien na pozadí – ale len ak na Drive medzitým nepribudlo nič novšie z iného zariadenia. */
class SyncUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CrescendoApp
        if (!SyncManager.enabled(app)) return Result.success()
        val token = app.backup.silentToken() ?: return Result.success()
        if (SyncManager.otherDeviceNewer(app, token)) {
            // konflikt – rozhodne používateľ pri ďalšom otvorení aplikácie
            return Result.success()
        }
        if (!SyncState.hasLocalChanges(app)) return Result.success()
        return if (app.backup.backup(token, quiet = true)) Result.success() else Result.retry()
    }
}
