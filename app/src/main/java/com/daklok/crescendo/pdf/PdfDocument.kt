package com.daklok.crescendo.pdf

import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
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
    private val renderer = openPdfRenderer(fd)
    private val mutex = Mutex()

    /** Po zatvorení sa už nič nevykresľuje (zatvorenie počká, kým dobehne rozbehnuté vykresľovanie). */
    @Volatile
    private var closed = false

    private fun ensureOpen() {
        if (closed) throw kotlinx.coroutines.CancellationException("PDF closed")
    }

    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Pomer strán (šírka / výška) danej strany. */
    suspend fun aspectRatio(index: Int): Float = mutex.withLock {
        ensureOpen()
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
            ensureOpen()
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

    private val bounds = HashMap<Int, FloatArray>()

    /**
     * Oblasť strany s obsahom (bez bielych okrajov) ako [ľavá, horná, pravá, dolná] v rozsahu 0..1.
     * Počíta sa z malého náhľadu a pamätá sa.
     */
    suspend fun contentBounds(index: Int): FloatArray {
        synchronized(bounds) { bounds[index] }?.let { return it }
        return mutex.withLock {
            synchronized(bounds) { bounds[index] }?.let { return@withLock it }
            ensureOpen()
            val result = withContext(Dispatchers.IO) {
                renderer.withPage(index) { page ->
                    val w = 300
                    val h = (w * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    bmp.recycle()
                    var minX = w; var minY = h; var maxX = -1; var maxY = -1
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val c = px[y * w + x]
                            val lum = ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10
                            if (lum < 200) {
                                if (x < minX) minX = x
                                if (x > maxX) maxX = x
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                            }
                        }
                    }
                    if (maxX < 0) {
                        floatArrayOf(0f, 0f, 1f, 1f)
                    } else {
                        val pad = 0.015f
                        floatArrayOf(
                            (minX.toFloat() / w - pad).coerceAtLeast(0f),
                            (minY.toFloat() / h - pad).coerceAtLeast(0f),
                            ((maxX + 1).toFloat() / w + pad).coerceAtMost(1f),
                            ((maxY + 1).toFloat() / h + pad).coerceAtMost(1f),
                        )
                    }
                }
            }
            synchronized(bounds) { bounds[index] = result }
            result
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        // nezatvárame „pod rukami“ – počkáme, kým dobehne práve bežiace vykresľovanie
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                renderer.closeLocked()
                runCatching { fd.close() }
            }
        }
    }

    private fun maxCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()
}
