package com.daklok.crescendo.data.backup

import com.daklok.crescendo.ui.common.OverlayKind
import com.daklok.crescendo.ui.common.AppOverlay
import com.daklok.crescendo.R
import android.content.Context
import android.content.Intent
import com.daklok.crescendo.CrescendoApp
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import com.daklok.crescendo.data.LanguageManager
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val label: String, val progress: Float? = null) : BackupState
    data class Done(val message: String) : BackupState
    data class Error(val message: String) : BackupState
}

/**
 * Záloha všetkého na Google Drive: databáza knižnice (skladby, priečinky, obľúbené, posledné strany),
 * všetky PDF, náhľady, poznámky a nastavenia. Všetko sa zbalí do jedného ZIP súboru
 * v skrytom priečinku aplikácie na Drive používateľa.
 */
class BackupManager(private val app: CrescendoApp) {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** Priečinky v `filesDir`, ktoré patria do zálohy. */
    private val folders = listOf("pdfs", "thumbs", "annotations", "datastore")

    /** SharedPreferences, ktoré patria do zálohy (nastavenia nástrojov editora, jazyk, tempá metronómu). */
    private val prefsNames = listOf("annotation_tools", "crescendo_language", "metronome")
    private val dbName = "crescendo.db"

    val authorizationRequest: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveClient.SCOPE_APPDATA)))
            .build()

    fun resetState() {
        if (_state.value !is BackupState.Working) _state.value = BackupState.Idle
    }

    // ───────────────────── prihlásenie ─────────────────────

    /** Token bez zobrazenia čohokoľvek (null = treba prihlásenie cez UI). */
    suspend fun silentToken(): String? = runCatching {
        val result: AuthorizationResult =
            Identity.getAuthorizationClient(app).authorize(authorizationRequest).await()
        if (result.hasResolution()) null else result.accessToken
    }.getOrNull()

    /** Po prihlásení: zistí e-mail a uloží ho. */
    suspend fun connect(token: String): Boolean = guarded(null) {
        _state.value = BackupState.Working(app.getString(R.string.bk_connecting))
        val email = DriveClient(token).userEmail()
        app.settings.update { it.copy(backupEmail = email) }
        _state.value = BackupState.Done(app.getString(R.string.bk_connected, email))
    }

    suspend fun disconnect(token: String?) {
        withContext(Dispatchers.IO) {
            if (token != null) runCatching { GoogleAuthUtil.clearToken(app, token) }
        }
        BackupScheduler.setEnabled(app, false)
        app.settings.update { it.copy(backupEmail = "", autoBackup = false) }
        _state.value = BackupState.Idle
    }

    suspend fun remoteInfo(token: String): RemoteBackup? =
        runCatching { DriveClient(token).listBackups().firstOrNull() }.getOrNull()

    // ───────────────────── záloha ─────────────────────

    suspend fun backup(token: String, quiet: Boolean = false): Boolean = guarded(token) {
        val drive = DriveClient(token)
        if (!quiet) _state.value = BackupState.Working(app.getString(R.string.bk_packing))
        val zip = withContext(Dispatchers.IO) { createArchive() }
        try {
            val old = drive.listBackups()
            if (!quiet) _state.value = BackupState.Working(app.getString(R.string.bk_uploading), 0f)
            drive.upload(zip) { p ->
                if (!quiet) _state.value = BackupState.Working(app.getString(R.string.bk_uploading), p)
            }
            // staré zálohy zmažeme až po úspešnom nahratí novej
            old.forEach { runCatching { drive.delete(it.id) } }
            app.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            if (!quiet) _state.value = BackupState.Done(app.getString(R.string.bk_done))
        } finally {
            zip.delete()
        }
    }

    /** Stiahne zálohu, nahradí ňou všetky dáta a reštartuje aplikáciu. */
    suspend fun restore(token: String): Boolean = guarded(token) {
        val drive = DriveClient(token)
        _state.value = BackupState.Working(app.getString(R.string.bk_searching))
        val remote = drive.listBackups().firstOrNull()
        if (remote == null) {
            _state.value = BackupState.Error(app.getString(R.string.bk_none))
            return@guarded
        }
        val zip = File(app.cacheDir, "restore.zip")
        _state.value = BackupState.Working(app.getString(R.string.bk_downloading), 0f)
        drive.download(remote, zip) { p -> _state.value = BackupState.Working(app.getString(R.string.bk_downloading), p) }
        _state.value = BackupState.Working(app.getString(R.string.bk_restoring))
        withContext(Dispatchers.IO) {
            validateArchive(zip)
            app.database.close()
            extractArchive(zip)
            zip.delete()
        }
        _state.value = BackupState.Done(app.getString(R.string.bk_restored))
        restartApp()
    }

    // ───────────────────── offline záloha (ZIP súbor) ─────────────────────

    /** Uloží celú zálohu do súboru, ktorý si používateľ vybral (napr. Stiahnuté, USB, Drive cez súbory). */
    suspend fun exportToUri(uri: android.net.Uri): Boolean = guarded(null) {
        _state.value = BackupState.Working(app.getString(R.string.bk_packing))
        withContext(Dispatchers.IO) {
            val zip = createArchive()
            try {
                app.contentResolver.openOutputStream(uri, "w")?.use { out ->
                    zip.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException(app.getString(R.string.bk_save_failed))
            } finally {
                zip.delete()
            }
        }
        _state.value = BackupState.Done(app.getString(R.string.bk_saved_file))
    }

    /** Obnoví všetko zo ZIP súboru a reštartuje aplikáciu. */
    suspend fun importFromUri(uri: android.net.Uri): Boolean = guarded(null) {
        _state.value = BackupState.Working(app.getString(R.string.bk_reading))
        val zip = File(app.cacheDir, "restore.zip")
        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(zip).use { input.copyTo(it) }
            } ?: throw IllegalStateException(app.getString(R.string.bk_open_failed))
            try {
                validateArchive(zip)
            } catch (e: Exception) {
                zip.delete()
                throw IllegalStateException(app.getString(R.string.bk_not_backup))
            }
            _state.value = BackupState.Working(app.getString(R.string.bk_restoring))
            app.database.close()
            extractArchive(zip)
            zip.delete()
        }
        _state.value = BackupState.Done(app.getString(R.string.bk_restored))
        restartApp()
    }

    // ───────────────────── ZIP ─────────────────────

    private fun createArchive(): File {
        // všetky zmeny z WAL denníka zapíšeme do hlavného súboru databázy
        runCatching {
            app.database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }
        val out = File(app.cacheDir, "backup.zip")
        ZipOutputStream(FileOutputStream(out).buffered()).use { zos ->
            val manifest = JSONObject()
                .put("app", "crescendo")
                .put("format", 1)
                .put("createdAt", System.currentTimeMillis())
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString().toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("prefs.json"))
            zos.write(exportPrefs().toString().toByteArray())
            zos.closeEntry()

            val db = app.getDatabasePath(dbName)
            if (db.exists()) addFile(zos, db, "db/$dbName")

            folders.forEach { name ->
                val dir = File(app.filesDir, name)
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(zos, f, "files/$name/" + f.relativeTo(dir).invariantSeparatorsPath)
                }
            }
        }
        return out
    }

    private fun addFile(zos: ZipOutputStream, file: File, path: String) {
        zos.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun validateArchive(zip: File) {
        var ok = false
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.name == "manifest.json") {
                    val m = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                    ok = m.optString("app") == "crescendo"
                }
            }
        }
        if (!ok) throw IllegalStateException(app.getString(R.string.bk_corrupt))
    }

    private fun extractArchive(zip: File) {
        // vyčistíme súčasné dáta
        folders.forEach { File(app.filesDir, it).deleteRecursively() }
        val dbFile = app.getDatabasePath(dbName)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }

        val filesRoot = app.filesDir.canonicalFile
        val dbRoot = dbFile.parentFile!!.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory) continue
                if (e.name == "prefs.json") {
                    runCatching { importPrefs(JSONObject(zis.readBytes().toString(Charsets.UTF_8))) }
                    continue
                }
                val target = when {
                    e.name == "db/$dbName" -> dbFile
                    e.name.startsWith("files/") -> File(filesRoot, e.name.removePrefix("files/"))
                    else -> null
                } ?: continue
                // ochrana pred „../“ v názvoch súborov
                val canon = target.canonicalFile
                if (!canon.path.startsWith(filesRoot.path) && !canon.path.startsWith(dbRoot.path)) continue
                canon.parentFile?.mkdirs()
                FileOutputStream(canon).use { zis.copyTo(it) }
            }
        }
    }

    private fun exportPrefs(): JSONObject {
        val root = JSONObject()
        prefsNames.forEach { name ->
            val obj = JSONObject()
            app.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, v) ->
                val entry = when (v) {
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> null
                }
                if (entry != null) obj.put(key, entry)
            }
            root.put(name, obj)
        }
        return root
    }

    private fun importPrefs(root: JSONObject) {
        prefsNames.forEach { name ->
            val obj = root.optJSONObject(name) ?: return@forEach
            val ed = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (key in obj.keys()) {
                val e = obj.getJSONObject(key)
                when (e.optString("t")) {
                    "b" -> ed.putBoolean(key, e.getBoolean("v"))
                    "i" -> ed.putInt(key, e.getInt("v"))
                    "l" -> ed.putLong(key, e.getLong("v"))
                    "f" -> ed.putFloat(key, e.getDouble("v").toFloat())
                    "s" -> ed.putString(key, e.getString("v"))
                    "set" -> {
                        val arr = e.getJSONArray("v")
                        ed.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
        }
        // na Androide 13+ jazyk drží systém – nastavíme ho podľa zálohy
        LanguageManager.applySaved(app)
    }

    private fun restartApp() {
        AppOverlay.markPending(app, OverlayKind.RESTORED)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component ?: return)
        app.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }

    // ───────────────────── spoločné ─────────────────────

    /** Spustí [block] tak, aby naraz bežala len jedna operácia, a chyby preloží na stav. */
    private suspend fun guarded(token: String?, block: suspend () -> Unit): Boolean = mutex.withLock {
        try {
            block()
            true
        } catch (e: UnauthorizedException) {
            if (token != null) withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(app, token) } }
            _state.value = BackupState.Error(app.getString(R.string.bk_expired))
            false
        } catch (e: java.net.UnknownHostException) {
            _state.value = BackupState.Error(app.getString(R.string.bk_offline))
            false
        } catch (e: Exception) {
            _state.value = BackupState.Error(e.message ?: app.getString(R.string.error_generic))
            false
        }
    }
}

/** Čakanie na Google Play Task bez ďalšej knižnice. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Uľahčenie pre UI: kontext → BackupManager. */
val Context.backupManager: BackupManager
    get() = (applicationContext as CrescendoApp).backup
