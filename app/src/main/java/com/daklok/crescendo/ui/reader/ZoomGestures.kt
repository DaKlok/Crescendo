package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize

const val MIN_ZOOM = 1f
const val MAX_ZOOM = 5f

/** Posun, pri ktorom zväčšený obsah stále pokrýva celú plochu. */
fun clampPan(pan: Offset, zoom: Float, size: IntSize): Offset {
    val maxX = (zoom - 1f) * size.width / 2f
    val maxY = (zoom - 1f) * size.height / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * Štipnutie dvoma prstami = priblíženie + posun. Keď je obsah priblížený, posúva aj jeden prst
 * (ak [singleFingerPan] povolí – napr. keď prstom práve nekreslíš).
 *
 * Beží v prechode Initial, takže dvojprstové gesto zachytí skôr než potiahnutie strán
 * či kreslenie, a deti ho už vidia ako spotrebované.
 */
fun Modifier.zoomPanGestures(
    zoom: () -> Float,
    pan: () -> Offset,
    singleFingerPan: () -> Boolean,
    onChange: (zoom: Float, pan: Offset) -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var panning = false
        var travelled = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val a = pressed[0]
                val b = pressed[1]
                val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                val dist = (a.position - b.position).getDistance()
                if (prevDist > 0f) {
                    val oldZ = zoom()
                    val newZ = (oldZ * dist / prevDist).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val prevC = (a.previousPosition + b.previousPosition) / 2f - center
                    val c = (a.position + b.position) / 2f - center
                    val t = c - (prevC - pan()) * (newZ / oldZ)
                    onChange(newZ, clampPan(t, newZ, size))
                }
                event.changes.forEach { it.consume() }
                panning = true
            } else if (pressed.size == 1 && zoom() > 1.01f && singleFingerPan() &&
                pressed[0].type == PointerType.Touch
            ) {
                val c = pressed[0]
                travelled += c.positionChange()
                if (panning || travelled.getDistance() > slop) {
                    panning = true
                    onChange(zoom(), clampPan(pan() + c.positionChange(), zoom(), size))
                    c.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
