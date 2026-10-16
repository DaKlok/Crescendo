package com.daklok.crescendo.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Obal nad [PdfRenderer]. PdfRenderer dovoľuje mať otvorenú len jednu stranu naraz,
 * preto sú všetky vykreslenia serializované cez [Mutex]. Hotové bitmapy sa držia v LRU cache.
 */
class PdfDocument(file: File) : AutoCloseable {

    private val fd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()

    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Pomer strán (šírka / výška) danej strany. */
    suspend fun aspectRatio(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.withPage(index) { it.width.toFloat() / it.height.toFloat() }
        }
    }

    /** Vykreslí stranu na zadanú šírku v pixeloch (výška podľa pomeru strán). */
    suspend fun render(index: Int, targetWidthPx: Int): Bitmap {
        val key = "$index@$targetWidthPx"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                renderer.withPage(index) { page ->
                    val width = targetWidthPx.coerceIn(64, 4096)
                    val height = (width * page.height.toFloat() / page.width.toFloat())
                        .toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp)
                    bmp
                }
            }
        }
    }

    override fun close() {
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    private fun maxCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()
}
