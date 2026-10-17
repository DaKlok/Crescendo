package com.daklok.crescendo.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Profilová fotka GitHub účtu autora. */
const val AUTHOR_AVATAR_URL = "https://avatars.githubusercontent.com/DaKlok?s=128"

/**
 * Okrúhla profilová fotka z internetu. Uloží sa do medzipamäte (funguje aj offline)
 * a obnoví sa najviac raz za pár dní. Kým sa nenačíta (alebo bez internetu), ukáže sa písmeno.
 */
@Composable
fun RemoteAvatar(url: String?, fallback: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(AvatarCache.peek(url), url) {
        if (!url.isNullOrBlank()) AvatarCache.load(context, url)?.let { value = it }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(300), label = "avatar") { b ->
            if (b != null) {
                Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        fallback.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

object AvatarCache {
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private val memory = HashMap<String, ImageBitmap>()

    fun peek(url: String?): ImageBitmap? = url?.let { synchronized(memory) { memory[it] } }

    suspend fun load(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        peek(url)?.let { return@withContext it }
        val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
        val file = File(dir, Integer.toHexString(url.hashCode()) + ".img")
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (stale) runCatching { download(url, file) }
        val bmp = if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
        if (bmp != null) synchronized(memory) { memory[url] = bmp }
        bmp
    }

    private fun download(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return
            val tmp = File(target.path + ".tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            // len ak je to naozaj obrázok
            if (BitmapFactory.decodeFile(tmp.path) != null) {
                target.delete()
                tmp.renameTo(target)
            } else {
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }
}
