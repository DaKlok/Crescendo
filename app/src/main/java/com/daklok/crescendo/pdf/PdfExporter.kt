package com.daklok.crescendo.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.daklok.crescendo.data.PieceNotes
import com.daklok.crescendo.ui.reader.drawStamp
import com.daklok.crescendo.ui.reader.drawInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * Vytvorí nové PDF: každá strana pôvodných nôt (vykreslená v ~200 DPI)
 * a nad ňou poznámky ako vektorové ťahy – presne ako v čítačke.
 */
object PdfExporter {

    private const val DPI = 200f
    private const val MAX_SIDE_PX = 3400f

    suspend fun export(
        source: File,
        notes: PieceNotes,
        out: OutputStream,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val doc = AndroidPdfDocument()
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    val count = renderer.pageCount
                    // dlhé skladby v nižšom rozlíšení, aby export nezahltil pamäť
                    val dpi = if (count <= 20) DPI else 150f
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    for (i in 0 until count) {
                        ensureActive()
                        renderer.withPage(i) { page ->
                            // veľkosť strany v bodoch (1/72 palca) – zachováme ju
                            val w = page.width
                            val h = page.height
                            val scale = min(dpi / 72f, MAX_SIDE_PX / max(w, h))
                            val bw = (w * scale).roundToInt().coerceAtLeast(1)
                            val bh = (h * scale).roundToInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val info = AndroidPdfDocument.PageInfo.Builder(w, h, i + 1).create()
                            val outPage = doc.startPage(info)
                            val canvas = outPage.canvas
                            canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), paint)
                            bmp.recycle()

                            val pageStrokes = notes.pages[i].orEmpty()
                            val pageStamps = notes.stamps[i].orEmpty()
                            if (pageStrokes.isNotEmpty() || pageStamps.isNotEmpty()) {
                                CanvasDrawScope().draw(
                                    Density(1f),
                                    LayoutDirection.Ltr,
                                    ComposeCanvas(canvas),
                                    Size(w.toFloat(), h.toFloat()),
                                ) {
                                    pageStrokes.forEach { drawInk(it, null) }
                                    pageStamps.forEach { drawStamp(it) }
                                }
                            }
                            doc.finishPage(outPage)
                        }
                        onProgress((i + 1f) / count)
                    }
                } finally {
                    renderer.close()
                }
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Názov súboru bez znakov, ktoré systémy súborov nemajú rady. */
    fun safeFileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().ifEmpty { "Crescendo" }.take(80)
}
