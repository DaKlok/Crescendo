package com.daklok.crescendo.pdf

import android.graphics.pdf.PdfRenderer

/** Otvorí stranu, spustí blok a stranu vždy zatvorí (bez závislosti na AutoCloseable). */
internal inline fun <T> PdfRenderer.withPage(index: Int, block: (PdfRenderer.Page) -> T): T {
    val page = openPage(index)
    try {
        return block(page)
    } finally {
        page.close()
    }
}
