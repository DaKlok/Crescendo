package com.daklok.crescendo.data.share

import android.content.Context
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.PieceMidi
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import com.daklok.crescendo.data.backup.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Prepojenie skladby v knižnici so skladbou v spoločnom priečinku. */
data class SharedLink(
    val pieceId: Long,
    val groupId: String,
    val fileId: String,
    /** Spoločné ID skladby (rovnaké u všetkých členov). */
    val uid: String,
    /** Čas zmeny na Drive, ktorú máme stiahnutú / nahratú. */
    val remoteAt: Long,
    /** Stav miestnych súborov po poslednej synchronizácii (na zistenie vlastných zmien). */
    val localAt: Long,
)

/** Stav skladby zo skupiny oproti knižnici. */
enum class SharedStatus {
    /** Ešte nie je v knižnici. */
    NEW,
    /** Rovnaká ako v skupine. */
    SYNCED,
    /** V skupine je novšia verzia. */
    REMOTE_NEWER,
    /** Mám zmeny, ktoré ostatní ešte nemajú. */
    LOCAL_CHANGES,
    /** Zmenené na oboch stranách. */
    CONFLICT,
}

/**
 * Spoločné noty cez Google Drive: skupina = zdieľaný priečinok, skladba = balík .crescendo
 * (noty, poznámky, záložky, nahrávky, MIDI). Prepojenia so skladbami v knižnici sú
 * v SharedPreferences „shared_links“ – sú v zálohe aj v synchronizácii.
 */
class SharedRepository(private val app: CrescendoApp) {

    val authorizationRequest: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SharedDriveClient.SCOPE_DRIVE)))
            .build()

    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Token bez zobrazenia čohokoľvek (null = treba povolenie cez UI). */
    suspend fun silentToken(): String? = runCatching {
        val result: AuthorizationResult = Identity.getAuthorizationClient(app).authorize(authorizationRequest).await()
        if (result.hasResolution()) null else result.accessToken
    }.getOrNull()

    // ───────────── prepojenia ─────────────

    fun links(): List<SharedLink> = prefs.all.entries.filter { it.key.startsWith("p_") }.mapNotNull { (k, v) ->
        runCatching {
            val o = JSONObject(v as String)
            SharedLink(
                pieceId = k.removePrefix("p_").toLong(),
                groupId = o.getString("group"),
                fileId = o.getString("file"),
                uid = o.optString("uid"),
                remoteAt = o.optLong("remote"),
                localAt = o.optLong("local"),
            )
        }.getOrNull()
    }

    fun link(pieceId: Long): SharedLink? = links().firstOrNull { it.pieceId == pieceId }

    private fun saveLink(l: SharedLink) {
        val o = JSONObject().put("group", l.groupId).put("file", l.fileId).put("uid", l.uid)
            .put("remote", l.remoteAt).put("local", l.localAt)
        prefs.edit().putString("p_${l.pieceId}", o.toString()).commit()
    }

    fun unlink(pieceId: Long) {
        prefs.edit().remove("p_$pieceId").commit()
    }

    /** Skryté skupiny (keď sa zo zdieľania nedá odhlásiť – len sa nezobrazujú). */
    fun hiddenGroups(): Set<String> = prefs.getStringSet("hidden", emptySet()).orEmpty()

    fun hideGroup(id: String) {
        prefs.edit().putStringSet("hidden", hiddenGroups() + id).commit()
    }

    /** Čas poslednej zmeny miestnych súborov skladby (noty, poznámky, záložky, nahrávky, MIDI). */
    fun localStamp(piece: PieceEntity): Long {
        val files = app.filesDir
        val singles = listOf(
            app.library.pdfFile(piece),
            File(files, "annotations/${piece.id}.json"),
            File(files, "bookmarks/${piece.id}.json"),
        )
        val dirs = listOf(
            File(File(files, "audio"), piece.id.toString()),
            File(File(files, "recordings"), piece.id.toString()),
            File(File(files, PieceMidi.DIR), piece.id.toString()),
        )
        val all = singles.filter { it.exists() }.map { it.lastModified() } +
            dirs.flatMap { d -> d.listFiles()?.filter { it.isFile }?.map { it.lastModified() }.orEmpty() }
        return all.maxOrNull() ?: 0L
    }

    fun status(remote: SharedPiece, pieces: Map<Long, PieceEntity>): Pair<SharedStatus, Long?> {
        val l = links().firstOrNull { (it.fileId == remote.id || (remote.uid.isNotBlank() && it.uid == remote.uid)) && pieces.containsKey(it.pieceId) }
            ?: return SharedStatus.NEW to null
        val piece = pieces.getValue(l.pieceId)
        val remoteNewer = remote.modifiedAt > l.remoteAt + 1000
        val localNewer = localStamp(piece) > l.localAt + 1500
        val s = when {
            remoteNewer && localNewer -> SharedStatus.CONFLICT
            remoteNewer -> SharedStatus.REMOTE_NEWER
            localNewer -> SharedStatus.LOCAL_CHANGES
            else -> SharedStatus.SYNCED
        }
        return s to l.pieceId
    }

    // ───────────── operácie ─────────────

    /** Pridá skladbu z knižnice do skupiny. */
    suspend fun share(token: String, groupId: String, piece: PieceEntity, onProgress: (Float) -> Unit) {
        val existing = link(piece.id)?.takeIf { it.groupId == groupId }
        upload(token, groupId, piece, existing?.fileId, existing?.uid ?: UUID.randomUUID().toString(), onProgress)
    }

    /** Odošle moje zmeny (nová verzia súboru v skupine). */
    suspend fun push(token: String, piece: PieceEntity, onProgress: (Float) -> Unit) {
        val l = link(piece.id) ?: error("not linked")
        upload(token, l.groupId, piece, l.fileId, l.uid, onProgress)
    }

    private suspend fun upload(
        token: String, groupId: String, piece: PieceEntity, fileId: String?, uid: String, onProgress: (Float) -> Unit,
    ) {
        val tmp = tempFile()
        try {
            // aktuálne poznámky sú v súbore (čítačka ich ukladá priebežne)
            app.library.exportPackage(piece, tmp)
            val (id, modified) = SharedDriveClient(token).uploadPiece(groupId, fileId, tmp, uid, piece.title, piece.composer, onProgress)
            saveLink(SharedLink(piece.id, groupId, id, uid, modified, localStamp(piece)))
        } finally {
            tmp.delete()
        }
    }

    /** Stiahne skladbu zo skupiny: novú do knižnice, alebo prepíše prepojenú (ponechá jej ID). */
    suspend fun pull(token: String, groupId: String, remote: SharedPiece, onProgress: (Float) -> Unit): Long {
        val tmp = tempFile()
        try {
            SharedDriveClient(token).download(remote.id, remote.sizeBytes, tmp, onProgress)
            val linked = links().firstOrNull { it.fileId == remote.id || (remote.uid.isNotBlank() && it.uid == remote.uid) }
            val existing = linked?.let { app.library.getPiece(it.pieceId) }
            val pieceId = if (existing != null) {
                app.library.updateFromPackage(existing, tmp).getOrThrow()
                existing.id
            } else {
                app.library.importPackage(android.net.Uri.fromFile(tmp), null).getOrThrow()
            }
            val piece = app.library.getPiece(pieceId) ?: error("missing piece")
            saveLink(SharedLink(pieceId, groupId, remote.id, remote.uid.ifBlank { UUID.randomUUID().toString() }, remote.modifiedAt, localStamp(piece)))
            return pieceId
        } finally {
            tmp.delete()
        }
    }

    private suspend fun tempFile(): File = withContext(Dispatchers.IO) {
        File(File(app.cacheDir, "shared").apply { mkdirs() }, "s_${System.nanoTime()}.crescendo")
    }

    companion object {
        const val PREFS = "shared_links"
    }
}
