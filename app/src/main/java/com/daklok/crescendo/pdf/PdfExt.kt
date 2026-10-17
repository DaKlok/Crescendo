package com.daklok.crescendo.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

/**
 * Spoločný zámok pre všetky [PdfRenderer] v aplikácii. Natívna knižnica pdfium nie je
 * bezpečná pre viac vlákien naraz – súbežné vykresľovanie / zatváranie viedlo k pádu aplikácie.
 */
internal val PdfLock = Any()

/** Vytvorí PdfRenderer pod spoločným zámkom. */
internal fun openPdfRenderer(fd: ParcelFileDescriptor): PdfRenderer = synchronized(PdfLock) { PdfRenderer(fd) }

/** Zatvorí PdfRenderer pod spoločným zámkom. */
internal fun PdfRenderer.closeLocked() {
    synchronized(PdfLock) { runCatching { close() } }
}

/** Otvorí stranu, spustí blok a stranu vždy zatvorí – celé pod spoločným zámkom. */
internal inline fun <T> PdfRenderer.withPage(index: Int, block: (PdfRenderer.Page) -> T): T =
    synchronized(PdfLock) {
        val page = openPage(index)
        try {
            block(page)
        } finally {
            page.close()
        }
    }
