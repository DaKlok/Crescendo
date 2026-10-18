package com.daklok.crescendo.data.share

import com.daklok.crescendo.data.backup.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Spoločný priečinok (skupina) na Google Drive. */
data class SharedGroup(
    val id: String,
    val name: String,
    val ownedByMe: Boolean,
    val ownerName: String,
    val ownerEmail: String,
    val canEdit: Boolean,
    val canShare: Boolean,
    val modifiedAt: Long,
)

/** Skladba (balík .crescendo) v spoločnom priečinku. */
data class SharedPiece(
    val id: String,
    val uid: String,
    val title: String,
    val composer: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val modifiedByMe: Boolean,
    val canEdit: Boolean,
)

/** Člen skupiny (oprávnenie na priečinku). */
data class SharedMember(
    val permissionId: String,
    val email: String,
    val name: String,
    val photo: String,
    /** owner / writer / reader … */
    val role: String,
)

/**
 * Google Drive REST v3 pre spoločné noty. Používa celý rozsah Drive (`drive`), aby aplikácia videla
 * aj priečinky, ktoré s používateľom zdieľal niekto iný. Pracuje len so súbormi označenými
 * vlastnosťami aplikácie (appProperties) – priečinky skupín a balíky skladieb.
 */
class SharedDriveClient(private val accessToken: String) {

    companion object {
        const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER = "application/vnd.google-apps.folder"
        const val PROP_GROUP = "crescendoGroup"
        const val PROP_PIECE = "crescendoPiece"
    }

    /** E-mail a ID oprávnenia prihláseného účtu. */
    suspend fun me(): Pair<String, String> = withContext(Dispatchers.IO) {
        val u = getJson("$API/about?fields=user(emailAddress,permissionId)").getJSONObject("user")
        u.optString("emailAddress") to u.optString("permissionId")
    }

    /** Všetky skupiny – vlastné aj zdieľané so mnou. */
    suspend fun listGroups(): List<SharedGroup> = withContext(Dispatchers.IO) {
        val q = "mimeType = '$FOLDER' and trashed = false and appProperties has { key='$PROP_GROUP' and value='1' }"
        val json = getJson(
            "$API/files?q=${enc(q)}&pageSize=100&orderBy=modifiedTime%20desc" +
                "&fields=files(id,name,ownedByMe,modifiedTime,appProperties,owners(displayName,emailAddress)," +
                "capabilities(canEdit,canShare))",
        )
        json.optJSONArray("files").objects().map { o ->
            val owner = o.optJSONArray("owners")?.optJSONObject(0)
            val caps = o.optJSONObject("capabilities")
            SharedGroup(
                id = o.getString("id"),
                name = o.optJSONObject("appProperties")?.optString("groupName")?.takeIf { it.isNotBlank() }
                    ?: o.optString("name"),
                ownedByMe = o.optBoolean("ownedByMe"),
                ownerName = owner?.optString("displayName").orEmpty(),
                ownerEmail = owner?.optString("emailAddress").orEmpty(),
                canEdit = caps?.optBoolean("canEdit") ?: false,
                canShare = caps?.optBoolean("canShare") ?: false,
                modifiedAt = parseTime(o.optString("modifiedTime")),
            )
        }
    }

    /** Založí novú skupinu (priečinok v Drive používateľa). Vráti jej ID. */
    suspend fun createGroup(name: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", "Crescendo – $name")
            .put("mimeType", FOLDER)
            .put("appProperties", JSONObject().put(PROP_GROUP, "1").put("groupName", name.take(100)))
        postJson("$API/files?fields=id", body).getString("id")
    }

    suspend fun renameGroup(id: String, name: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", "Crescendo – $name")
            .put("appProperties", JSONObject().put("groupName", name.take(100)))
        send("$API/files/$id?fields=id", "PATCH", body)
    }

    /** Zmaže skupinu (do koša) – zmizne všetkým členom. */
    suspend fun trash(id: String) = withContext(Dispatchers.IO) {
        send("$API/files/$id?fields=id", "PATCH", JSONObject().put("trashed", true))
    }

    suspend fun members(groupId: String): List<SharedMember> = withContext(Dispatchers.IO) {
        val json = getJson("$API/files/$groupId/permissions?fields=permissions(id,type,role,emailAddress,displayName,photoLink)")
        json.optJSONArray("permissions").objects()
            .filter { it.optString("type") == "user" }
            .map {
                SharedMember(
                    permissionId = it.getString("id"),
                    email = it.optString("emailAddress"),
                    name = it.optString("displayName"),
                    photo = it.optString("photoLink"),
                    role = it.optString("role"),
                )
            }
    }

    /** Pozve človeka (Google mu pošle e-mail s odkazom). */
    suspend fun invite(groupId: String, email: String, canEdit: Boolean, message: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", "user")
            .put("role", if (canEdit) "writer" else "reader")
            .put("emailAddress", email.trim())
        send("$API/files/$groupId/permissions?sendNotificationEmail=true&emailMessage=${enc(message)}&fields=id", "POST", body)
    }

    suspend fun removeMember(groupId: String, permissionId: String) = withContext(Dispatchers.IO) {
        send("$API/files/$groupId/permissions/$permissionId", "DELETE", null)
    }

    suspend fun listPieces(groupId: String, myEmail: String): List<SharedPiece> = withContext(Dispatchers.IO) {
        val q = "'$groupId' in parents and trashed = false and appProperties has { key='$PROP_PIECE' and value='1' }"
        val json = getJson(
            "$API/files?q=${enc(q)}&pageSize=500&orderBy=modifiedTime%20desc" +
                "&fields=files(id,size,modifiedTime,appProperties,lastModifyingUser(displayName,emailAddress,me)," +
                "capabilities(canEdit))",
        )
        json.optJSONArray("files").objects().map { o ->
            val props = o.optJSONObject("appProperties")
            val by = o.optJSONObject("lastModifyingUser")
            SharedPiece(
                id = o.getString("id"),
                uid = props?.optString("uid").orEmpty(),
                title = props?.optString("title").orEmpty(),
                composer = props?.optString("composer").orEmpty(),
                sizeBytes = o.optString("size", "0").toLongOrNull() ?: 0L,
                modifiedAt = parseTime(o.optString("modifiedTime")),
                modifiedBy = by?.optString("displayName")?.takeIf { it.isNotBlank() } ?: by?.optString("emailAddress").orEmpty(),
                modifiedByMe = by?.optBoolean("me") == true ||
                    (myEmail.isNotBlank() && by?.optString("emailAddress").equals(myEmail, ignoreCase = true)),
                canEdit = o.optJSONObject("capabilities")?.optBoolean("canEdit") ?: false,
            )
        }
    }

    /**
     * Nahrá balík skladby: nový súbor v skupine ([existingId] = null) alebo nová verzia existujúceho.
     * Vráti (ID, čas zmeny).
     */
    suspend fun uploadPiece(
        groupId: String,
        existingId: String?,
        file: File,
        uid: String,
        title: String,
        composer: String,
        onProgress: (Float) -> Unit,
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val props = JSONObject()
            .put(PROP_PIECE, "1")
            .put("uid", uid)
            .put("title", title.take(100))
            .put("composer", composer.take(100))
        val meta = JSONObject().put("name", safeName(title) + ".crescendo").put("appProperties", props)
        if (existingId == null) meta.put("parents", JSONArray().put(groupId))
        val url = if (existingId == null) "$UPLOAD/files?uploadType=resumable&fields=id,modifiedTime"
        else "$UPLOAD/files/$existingId?uploadType=resumable&fields=id,modifiedTime"
        val init = open(url, if (existingId == null) "POST" else "PATCH")
        init.doOutput = true
        init.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        init.setRequestProperty("X-Upload-Content-Type", "application/zip")
        init.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        init.outputStream.use { it.write(meta.toString().toByteArray()) }
        check(init)
        val session = init.getHeaderField("Location") ?: throw IOException("Google Drive: missing upload session")
        init.disconnect()

        val put = (URL(session).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/zip")
            setFixedLengthStreamingMode(file.length())
        }
        val total = file.length().coerceAtLeast(1)
        var sent = 0L
        file.inputStream().use { input ->
            put.outputStream.use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    onProgress(sent.toFloat() / total)
                }
            }
        }
        check(put)
        val res = JSONObject(put.inputStream.bufferedReader().use { it.readText() })
        put.disconnect()
        res.getString("id") to parseTime(res.optString("modifiedTime"))
    }

    suspend fun download(fileId: String, size: Long, dest: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val conn = open("$API/files/$fileId?alt=media", "GET")
        check(conn)
        val total = size.coerceAtLeast(1)
        var got = 0L
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    onProgress((got.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        conn.disconnect()
    }

    /** Odstráni skladbu zo skupiny (do koša). */
    suspend fun trashPiece(fileId: String) = trash(fileId)

    // ───────────── pomocné ─────────────

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "piece" }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        // HttpURLConnection nepozná PATCH – Google akceptuje POST s hlavičkou
        if (method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        return conn
    }

    private fun getJson(url: String): JSONObject {
        val conn = open(url, "GET")
        check(conn)
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(text)
    }

    private fun postJson(url: String, body: JSONObject): JSONObject = send(url, "POST", body)

    private fun send(url: String, method: String, body: JSONObject?): JSONObject {
        val conn = open(url, method)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        check(conn)
        val text = runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
        conn.disconnect()
        return runCatching { JSONObject(text) }.getOrDefault(JSONObject())
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code !in 200..299) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val msg = runCatching { JSONObject(err ?: "").getJSONObject("error").getString("message") }.getOrNull()
            throw SharedDriveException(code, msg ?: "HTTP $code")
        }
    }

    private fun parseTime(s: String): Long = runCatching {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s)?.time ?: 0L
    }.getOrDefault(0L)
}

/** Chyba Drive s HTTP kódom (403 = nemáš oprávnenie, 404 = neexistuje / už nie si členom). */
class SharedDriveException(val code: Int, message: String) : IOException("Google Drive: $message")
