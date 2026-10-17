package com.daklok.crescendo.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Záloha uložená na Drive (v skrytom priečinku aplikácie). */
data class RemoteBackup(
    val id: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** Chyba 401 – prístupový token vypršal alebo bol odvolaný. */
class UnauthorizedException : IOException("Unauthorized")

/**
 * Minimálny klient Google Drive REST API v3. Pracuje iba v `appDataFolder` – skrytom priečinku,
 * ktorý patrí len tejto aplikácii (používateľ ho nevidí medzi svojimi súbormi a aplikácia
 * nevidí jeho ostatné súbory).
 */
class DriveClient(private val accessToken: String) {

    companion object {
        const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val BACKUP_NAME = "crescendo-backup.zip"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }

    /** E-mail prihláseného účtu. */
    suspend fun userEmail(): String = withContext(Dispatchers.IO) {
        val json = getJson("$API/about?fields=user(emailAddress,displayName)")
        json.getJSONObject("user").optString("emailAddress", "")
    }

    /** Všetky zálohy (najnovšia prvá). */
    suspend fun listBackups(): List<RemoteBackup> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name = '$BACKUP_NAME' and trashed = false", "UTF-8")
        val json = getJson(
            "$API/files?spaces=appDataFolder&q=$q" +
                "&fields=files(id,size,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=20"
        )
        val arr = json.optJSONArray("files") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteBackup(
                id = o.getString("id"),
                sizeBytes = o.optString("size", "0").toLongOrNull() ?: 0L,
                modifiedAt = parseTime(o.optString("modifiedTime")),
            )
        }
    }

    /**
     * Nahrá súbor ako novú zálohu (resumable upload, zvládne aj stovky MB).
     * Vráti ID nového súboru.
     */
    suspend fun upload(file: File, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        // 1) založenie relácie
        val init = open("$UPLOAD/files?uploadType=resumable&fields=id", "POST")
        init.doOutput = true
        init.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        init.setRequestProperty("X-Upload-Content-Type", "application/zip")
        init.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        val meta = JSONObject()
            .put("name", BACKUP_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
            .toByteArray()
        init.outputStream.use { it.write(meta) }
        check(init)
        val session = init.getHeaderField("Location") ?: throw IOException("Google Drive: missing upload session")
        init.disconnect()

        // 2) samotné dáta
        val put = (URL(session).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
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
        val id = JSONObject(put.inputStream.bufferedReader().use { it.readText() }).getString("id")
        put.disconnect()
        id
    }

    /** Stiahne zálohu do [dest]. */
    suspend fun download(backup: RemoteBackup, dest: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val conn = open("$API/files/${backup.id}?alt=media", "GET")
        check(conn)
        val total = backup.sizeBytes.coerceAtLeast(1)
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

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val conn = open("$API/files/$id", "DELETE")
        val code = conn.responseCode
        conn.disconnect()
        if (code == 401) throw UnauthorizedException()
    }

    // ---- pomocné ----

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

    private fun getJson(url: String): JSONObject {
        val conn = open(url, "GET")
        check(conn)
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(text)
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code !in 200..299) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val msg = runCatching { JSONObject(err ?: "").getJSONObject("error").getString("message") }.getOrNull()
            throw IOException("Google Drive: ${msg ?: "HTTP $code"}")
        }
    }

    private fun parseTime(s: String): Long = runCatching {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s)?.time ?: 0L
    }.getOrDefault(0L)
}
