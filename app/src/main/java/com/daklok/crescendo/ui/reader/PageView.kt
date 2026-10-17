package com.daklok.crescendo.ui.reader

import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.pdf.PdfDocument

/**
 * Jedna strana PDF vykreslená do dostupnej plochy (zachová pomer strán, biele pozadie ako papier)
 * s vrstvou poznámok presne nad stranou.
 */
@Composable
fun PageView(
    document: PdfDocument,
    pageIndex: Int,
    annotations: AnnotationController,
    fingerDrawing: Boolean,
    modifier: Modifier = Modifier,
    /** Násobok rozlíšenia vykreslenia (pri priblížení sa zvyšuje, aby text zostal ostrý). */
    renderBoost: Float = 1f,
) {
    if (pageIndex !in 0 until document.pageCount) {
        Box(modifier.fillMaxSize())
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        val ratio by produceState(initialValue = 0f, document, pageIndex) {
            value = document.aspectRatio(pageIndex)
        }

        if (ratio <= 0f) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            return@BoxWithConstraints
        }

        // veľkosť strany po „fit“ do plochy
        val fitWidthPx = minOf(widthPx.toFloat(), heightPx * ratio)
        val fitHeightPx = fitWidthPx / ratio

        val bitmap by produceState<ImageBitmap?>(initialValue = null, document, pageIndex, fitWidthPx, renderBoost) {
            // ×1.5 pre ostrosť + zvýšenie pri priblížení
            value = document.render(pageIndex, (fitWidthPx * 1.5f * renderBoost).toInt()).asImageBitmap()
        }

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { fitWidthPx.toDp() },
                    height = with(density) { fitHeightPx.toDp() },
                )
                .background(Color.White),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = stringResource(R.string.page_n, pageIndex + 1),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
            AnnotationLayer(
                controller = annotations,
                page = pageIndex,
                ratio = ratio,
                fingerDrawing = fingerDrawing,
            )
        }
    }
}
