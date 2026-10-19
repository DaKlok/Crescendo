package com.daklok.crescendo.ui.reader

import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clipToBounds
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
/** Invertovanie farieb strany (nočný režim). */
private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

@Composable
fun PageView(
    document: PdfDocument,
    pageIndex: Int,
    annotations: AnnotationController,
    fingerDrawing: Boolean,
    modifier: Modifier = Modifier,
    /** Násobok rozlíšenia vykreslenia (pri priblížení sa zvyšuje, aby text zostal ostrý). */
    renderBoost: Float = 1f,
    /** Orezať biele okraje – noty sú väčšie. */
    cropMargins: Boolean = false,
    /** Nočný režim – invertované farby nôt. */
    night: Boolean = false,
    /** Ďalšia vrstva presne nad stranou (napr. cvičebné úseky); dostane číslo strany. */
    overlay: @Composable (page: Int) -> Unit = {},
    /** Rozlíšenie vykreslenia oproti ploche (×1,5 pre ostrosť; pri posúvaní stačí ×1, strany sú veľké). */
    renderScale: Float = 1.5f,
) {
    if (pageIndex !in 0 until document.pageCount) {
        Box(modifier.fillMaxSize())
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        val ratio by produceState(initialValue = 0f, document, pageIndex) {
            value = document.aspectRatio(pageIndex)
        }
        val crop by produceState(initialValue = FULL_PAGE, document, pageIndex, cropMargins) {
            value = if (cropMargins) document.contentBounds(pageIndex) else FULL_PAGE
        }

        if (ratio <= 0f) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            return@BoxWithConstraints
        }

        // Orezaná oblasť (normalizovaná) sa „fitne“ do plochy; celá strana je potom väčšia
        // a posunutá tak, aby bola orezaná oblasť v strede – poznámky ostávajú na strane.
        val cw = (crop[2] - crop[0]).coerceAtLeast(0.05f)
        val ch = (crop[3] - crop[1]).coerceAtLeast(0.05f)
        val cropRatio = cw / ch * ratio
        val cropFitW = minOf(widthPx.toFloat(), heightPx * cropRatio)
        val cropFitH = cropFitW / cropRatio
        val fitWidthPx = cropFitW / cw
        val fitHeightPx = fitWidthPx / ratio
        val offsetX = (widthPx - cropFitW) / 2f - crop[0] * fitWidthPx
        val offsetY = (heightPx - cropFitH) / 2f - crop[1] * fitHeightPx

        val bitmap by produceState<ImageBitmap?>(initialValue = null, document, pageIndex, fitWidthPx, renderBoost, renderScale) {
            // ×1.5 pre ostrosť + zvýšenie pri priblížení
            value = document.render(pageIndex, (fitWidthPx * renderScale * renderBoost).toInt()).asImageBitmap()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(fitWidthPx.roundToInt().coerceAtLeast(1), fitHeightPx.roundToInt().coerceAtLeast(1)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(offsetX.roundToInt(), offsetY.roundToInt())
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (night) Color.Black else Color.White),
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = stringResource(R.string.page_n, pageIndex + 1),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = if (night) ColorFilter.colorMatrix(InvertMatrix) else null,
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
                overlay(pageIndex)
            }
        }
    }
}

private val FULL_PAGE = floatArrayOf(0f, 0f, 1f, 1f)
