package com.daklok.crescendo.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Farba v HSV (odtieň 0..360, sýtosť 0..1, jas 0..1). */
private data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toArgb(): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

    companion object {
        fun of(argb: Int): Hsv {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, out)
            return Hsv(out[0], out[1], out[2])
        }
    }
}

fun Int.toHex(): String = String.format("#%06X", this and 0xFFFFFF)

/** „#1A2B3C“ / „1A2B3C“ / „#ABC“ -> ARGB, alebo null. */
fun parseHex(text: String): Int? {
    var t = text.trim().removePrefix("#")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

/**
 * Dialóg výberu farby: farebné koliesko (odtieň + sýtosť), posuvník jasu,
 * posuvníky R/G/B a hex kód. Navrchu rýchle farby ([presets]).
 * [onReset] – ak je zadané, zobrazí sa tlačidlo „Predvolená“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    presets: List<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var hsv by remember { mutableStateOf(Hsv.of(initial)) }
    val argb = hsv.toArgb()
    var hexText by remember { mutableStateOf(argb.toHex()) }
    // pri zmene farby inak ako písaním prepíšeme aj pole s kódom
    fun setColor(newHsv: Hsv, fromHex: Boolean = false) {
        hsv = newHsv
        if (!fromHex) hexText = newHsv.toArgb().toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (presets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.forEach { c ->
                            Swatch(Color(c), selected = (c or 0xFF000000.toInt()) == argb) { setColor(Hsv.of(c)) }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorWheel(
                        hue = hsv.h,
                        saturation = hsv.s,
                        value = hsv.v,
                        onChange = { h, s -> setColor(hsv.copy(h = h, s = s, v = if (hsv.v < 0.05f) 1f else hsv.v)) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    // veľká ukážka
                    Box(
                        Modifier
                            .size(64.dp)
                            .background(Color(argb), RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                    )
                }

                // jas
                LabeledSlider(
                    label = stringResource(R.string.color_brightness),
                    value = hsv.v,
                    range = 0f..1f,
                    display = "${(hsv.v * 100).roundToInt()} %",
                    track = Color(Hsv(hsv.h, hsv.s, 1f).toArgb()),
                    onChange = { setColor(hsv.copy(v = it)) },
                )

                // R G B
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                fun rgb(nr: Int, ng: Int, nb: Int) =
                    setColor(Hsv.of(android.graphics.Color.rgb(nr, ng, nb)))
                LabeledSlider("R", r.toFloat(), 0f..255f, "$r", Color(0xFFE53935)) { rgb(it.roundToInt(), g, b) }
                LabeledSlider("G", g.toFloat(), 0f..255f, "$g", Color(0xFF43A047)) { rgb(r, it.roundToInt(), b) }
                LabeledSlider("B", b.toFloat(), 0f..255f, "$b", Color(0xFF1E88E5)) { rgb(r, g, it.roundToInt()) }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t.take(7)
                        parseHex(t)?.let { setColor(Hsv.of(it), fromHex = true) }
                    },
                    label = { Text(stringResource(R.string.color_hex)) },
                    singleLine = true,
                    isError = parseHex(hexText) == null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(argb) }) { Text(stringResource(R.string.choose)) }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.color_default)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Farebné koliesko: uhol = odtieň, vzdialenosť od stredu = sýtosť. */
@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hues = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) }
    }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            fun handle(pos: Offset) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy)
                val dx = pos.x - cx
                val dy = pos.y - cy
                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                if (angle < 0) angle += 360f
                val s = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                onChange(angle, s)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                handle(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.positionChange() != Offset.Zero) {
                        change.consume()
                        handle(change.position)
                    }
                }
            }
        },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.sweepGradient(hues, center), radius, center)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius),
            radius,
            center,
        )
        // tmavší jas ako závoj
        if (value < 1f) drawCircle(Color.Black.copy(alpha = 1f - value), radius, center)
        drawCircle(outline, radius, center, style = Stroke(1.dp.toPx()))

        // ukazovateľ
        val rad = Math.toRadians(hue.toDouble())
        val p = Offset(
            center.x + (cos(rad) * saturation * radius).toFloat(),
            center.y + (sin(rad) * saturation * radius).toFloat(),
        )
        val c = Color.hsv(hue, saturation, value)
        drawCircle(Color.White, 13.dp.toPx(), p)
        drawCircle(c, 10.dp.toPx(), p)
        drawCircle(Color.Black.copy(alpha = 0.25f), 13.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    track: Color,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = track,
                activeTrackColor = track,
                inactiveTrackColor = track.copy(alpha = 0.2f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            display,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(48.dp)
                .padding(start = 8.dp),
        )
    }
}

/** Krúžok s farbou; vybraný má fajku a je väčší. */
@Composable
fun Swatch(color: Color, selected: Boolean, size: Int = 36, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, Motion.bouncy(), label = "swatch")
    Box(
        modifier = Modifier
            .size(size.dp + 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .scale(scale)
                .background(color, CircleShape)
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Farby priečinkov a rýchle farby. */
val FOLDER_COLORS: List<Int> = listOf(
    0xFFE53935, 0xFFF4511E, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342, 0xFF43A047,
    0xFF00897B, 0xFF039BE5, 0xFF3949AB, 0xFF5E35B1, 0xFF8E24AA, 0xFFD81B60,
    0xFF6D4C41, 0xFF546E7A,
).map { it.toInt() }

/** Čitateľná farba textu/ikony na danom pozadí. */
fun contentColorFor(argb: Int): Color =
    if (Color(argb).luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White

@Suppress("unused")
private fun Color.argb(): Int = toArgb()
