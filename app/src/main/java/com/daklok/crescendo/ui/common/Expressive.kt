package com.daklok.crescendo.ui.common

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 * Tvary a prvky Material 3 Expressive pre Crescendo:
 *  CookieShape: kruh s jemnými vlnami (odznaky, ikony, tlačidlo prehrať),
 *  CloverShape: štvorlístok (výrazný tvar pre dlaždice),
 *  SquircleShape: zaoblený štvorec (superelipsa),
 *  PageProgress: kde v skladbe si skončil (malý posuvník),
 *  WavyRing: vlnitý kruhový postup (denný cieľ cvičenia),
 *  WavyLinearProgress: vlnitý vodorovný postup (prehrávanie ukážky),
 *  ShapeIcon: ikona vo výraznom tvare, tvar sa pri stlačení pootočí,
 *  ConnectedChoice: spojená skupina tlačidiel s jedným výberom,
 *  Accent: farebné role (každý nástroj či štatistika má svoju),
 *  FabMenu: plávajúce tlačidlo, ktoré sa rozbalí do ponuky.
 */

/** Kruh s [bumps] jemnými vlnami; [depth] = hĺbka vĺn (zlomok polomeru). */
class CookieShape(private val bumps: Int = 9, private val depth: Float = 0.075f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val amp = r * depth
        val base = r - amp
        val path = Path()
        val n = max(72, bumps * 18)
        for (i in 0..n) {
            val t = 2.0 * PI * i / n
            val rr = base + amp * cos(bumps * t).toFloat()
            val x = cx + rr * cos(t - PI / 2).toFloat()
            val y = cy + rr * sin(t - PI / 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Štvorlístok (4 oblé lístky s jemnými zárezmi). */
class CloverShape(private val depth: Float = 0.18f, private val sharpness: Float = 2f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        val n = 240
        for (i in 0..n) {
            val t = 2.0 * PI * i / n
            val c = abs(cos(2 * t)).toFloat()
            val rr = r * (1f - depth * (1f - c).pow(sharpness))
            val x = cx + rr * cos(t + PI / 4).toFloat()
            val y = cy + rr * sin(t + PI / 4).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Superelipsa: mäkší zaoblený štvorec než RoundedCornerShape. */
class SquircleShape(private val exponent: Float = 4.2f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        val n = 120
        val e = 2f / exponent
        for (i in 0..n) {
            val t = 2.0 * PI * i / n
            val ct = cos(t).toFloat()
            val st = sin(t).toFloat()
            val x = a + a * sign(ct) * abs(ct).pow(e)
            val y = b + b * sign(st) * abs(st).pow(e)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Zdieľané inštancie tvarov (tvary sú bez stavu). */
object ExpressiveShapes {
    val Cookie = CookieShape(9, 0.075f)
    val Cookie12 = CookieShape(12, 0.055f)
    val Clover = CloverShape()
    val Squircle = SquircleShape()
}

/**
 * Kde v skladbe si skončil, ako malý posuvník Material 3 Expressive (len na čítanie, rovnaký vzhľad ako
 * posuvník strán v čítačke): prečítaná časť je plná, aktuálna strana je zvislá rúčka s medzerou z oboch strán,
 * zvyšok je tlmená stopa s bodkou na konci. Pri zmene strany sa rúčka plynulo presunie.
 */
@Composable
fun PageProgress(
    page: Int,
    pages: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.18f),
    trackHeight: Dp = 5.dp,
    handleHeight: Dp = 14.dp,
    handleWidth: Dp = 3.dp,
) {
    val total = pages.coerceAtLeast(1)
    val target = if (total <= 1) 1f else page.coerceIn(0, total - 1).toFloat() / (total - 1)
    val f by animateFloatAsState(target, Motion.gentle(), label = "pageProgress")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(handleHeight),
    ) {
        val w = size.width
        val th = trackHeight.toPx()
        val hw = handleWidth.toPx()
        val gap = hw * 0.9f
        val top = (size.height - th) / 2f
        val outer = CornerRadius(th / 2f, th / 2f)
        // vnútorné rohy pri rúčke sú menšie (ako pri posuvníku Material 3 Expressive)
        val inner = CornerRadius(min(th / 2f, 1.5.dp.toPx()), min(th / 2f, 1.5.dp.toPx()))
        val hx = hw / 2f + f.coerceIn(0f, 1f) * (w - hw)
        val activeEnd = hx - hw / 2f - gap
        if (activeEnd > th * 0.5f) {
            drawPath(
                Path().apply {
                    addRoundRect(RoundRect(0f, top, activeEnd, top + th, outer, inner, inner, outer))
                },
                color,
            )
        }
        val restStart = hx + hw / 2f + gap
        if (w - restStart > th * 0.5f) {
            drawPath(
                Path().apply {
                    addRoundRect(RoundRect(restStart, top, w, top + th, inner, outer, outer, inner))
                },
                trackColor,
            )
            // bodka na konci (posledná strana)
            if (w - restStart > th * 2f) drawCircle(color, radius = th * 0.3f, center = Offset(w - th / 2f, size.height / 2f))
        }
        drawRoundRect(color, Offset(hx - hw / 2f, 0f), Size(hw, size.height), CornerRadius(hw / 2f, hw / 2f))
    }
}

/**
 * Vlnitý kruhový postup (ako v Material 3 Expressive): splnená časť je vlnka,
 * zvyšok tenká rovná stopa s malou medzerou. Pri 100 % sa kruh uzavrie celý vo vlnkách.
 */
@Composable
fun WavyRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    stroke: Dp = 4.dp,
    amplitude: Dp = 1.4.dp,
    wavelength: Dp = 11.dp,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.gentle(), label = "wavyRing")
    Canvas(modifier) {
        val sw = stroke.toPx()
        val amp = amplitude.toPx()
        val r = min(size.width, size.height) / 2f - sw / 2f - amp
        if (r <= 0f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        // celý počet vĺn, aby sa plný kruh uzavrel bez švu
        val waves = max(4, (2f * PI.toFloat() * r / wavelength.toPx()).roundToInt())
        val sweep = 360f * p
        val gapDeg = if (p in 0.001f..0.999f) ((sw * 1.6f) / r) * (180f / PI.toFloat()) else 0f

        // stopa: zvyšok kruhu, mimo vlnky
        val trackSweep = 360f - sweep - 2f * gapDeg
        if (trackSweep > 0.5f) {
            drawArc(
                color = trackColor,
                startAngle = -90f + sweep + gapDeg,
                sweepAngle = trackSweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2f * r, 2f * r),
                style = Stroke(width = sw * 0.75f, cap = StrokeCap.Round),
            )
        }
        if (sweep > 0.5f) {
            val path = Path()
            // aspoň 16 bodov na jednu vlnu, inak je vlnka hranatá
            val steps = max(8, max((sweep / 360f * waves * 16f).roundToInt(), (sweep / 2.5f).roundToInt()))
            for (i in 0..steps) {
                val deg = sweep * i / steps
                val t = Math.toRadians((deg - 90f).toDouble())
                val wave = sin(Math.toRadians(deg.toDouble()) * waves).toFloat()
                val rr = r + amp * wave
                val x = cx + rr * cos(t).toFloat()
                val y = cy + rr * sin(t).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (p >= 0.999f) path.close()
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

/** Ikona vo výraznom tvare; tvar (nie ikona) sa dá pootočiť o [turn] stupňov, napr. pri stlačení. */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    turn: Float = 0f,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn }
                .background(container, shape),
        )
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * Tlačidlo prehrať / pauza vo vlnitom kruhu (Material 3 Expressive). Pri stlačení sa tvar pružne pootočí;
 * každá zmena [pulseKey] (napr. doba metronómu) ho krátko „nadýchne“.
 */
@Composable
fun PlayCookie(
    size: Dp,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    pulseKey: Any? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 24f else 0f, Motion.bouncy(), label = "cookieTurn")
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.bouncy(), label = "cookieScale")
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey != null && playing) {
            pulse.snapTo(1.07f)
            pulse.animateTo(1f, tween(220))
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = turn
                scaleX = scale * pulse.value
                scaleY = scale * pulse.value
            }
            .clip(ExpressiveShapes.Cookie12)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier
                .size(size * 0.46f)
                .graphicsLayer { rotationZ = -turn },
        )
    }
}

/** Položka ponuky plávajúceho tlačidla. */
data class FabMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Plávajúce tlačidlo s ponukou (Material 3 Expressive): ťuk rozbalí nad tlačidlom veľké tlačidlá s popisom
 * a samotné tlačidlo sa zmení na menší kruh so znakom zavrieť (plus pootočené o 135°).
 * [above] sa ukazuje nad zatvoreným tlačidlom (napr. počítadlo skladieb).
 */
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    icon: ImageVector = Icons.Rounded.Add,
    above: @Composable () -> Unit = {},
) {
    val haptic = rememberHaptic()
    val scheme = MaterialTheme.colorScheme
    val slot = if (large) 80.dp else 64.dp
    val closed = 56.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(220, delayMillis = 80)) + scaleIn(Motion.bouncy(), 0.85f, TransformOrigin(1f, 1f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), 0.9f, TransformOrigin(1f, 1f)),
        ) {
            above()
        }
        items.forEachIndexed { i, item ->
            // položka najbližšie k tlačidlu sa ukáže prvá
            val delay = 45 * (items.lastIndex - i)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160, delayMillis = delay)) +
                    slideInVertically(tween(280, delayMillis = delay)) { it / 2 } +
                    scaleIn(tween(280, delayMillis = delay), 0.6f, TransformOrigin(1f, 0.5f)),
                exit = fadeOut(tween(110)) + scaleOut(tween(140), 0.8f, TransformOrigin(1f, 0.5f)),
            ) {
                FabMenuButton(
                    item = item,
                    modifier = Modifier.padding(end = (slot - closed) / 2),
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onExpandedChange(false)
                        item.onClick()
                    },
                )
            }
        }
        val interaction = remember { MutableInteractionSource() }
        val size by animateDpAsState(if (expanded) closed else slot, Motion.bouncy(), label = "fabSize")
        val corner by animateIntAsState(if (expanded) 50 else 30, Motion.bouncy(), label = "fabCorner")
        val turn by animateFloatAsState(if (expanded) 135f else 0f, Motion.bouncy(), label = "fabTurn")
        // pevné miesto pre tlačidlo, aby ponuka pri zmenšení neposkočila
        Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onExpandedChange(!expanded)
                },
                modifier = Modifier
                    .size(size)
                    .pressScale(interaction, 0.9f),
                shape = RoundedCornerShape(percent = corner),
                color = scheme.primary,
                contentColor = scheme.onPrimary,
                shadowElevation = if (expanded) 3.dp else 6.dp,
                interactionSource = interaction,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .size(if (large) 30.dp else 26.dp)
                            .graphicsLayer { rotationZ = turn },
                    )
                }
            }
        }
    }
}

@Composable
private fun FabMenuButton(item: FabMenuItem, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(item.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Vlnitý vodorovný postup (Material 3 Expressive): splnená časť je vlnka, zvyšok rovná stopa
 * s bodkou na konci. Keď [moving], vlnka plynie dopredu (prehrávanie, sťahovanie);
 * keď nie je [wavy], plynulo sa vyrovná (napr. pozastavené prehrávanie).
 */
@Composable
fun WavyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    wavy: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    stroke: Dp = 4.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 26.dp,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(220, easing = LinearEasing), label = "wavyLinear")
    val amp by animateFloatAsState(if (wavy) 1f else 0f, tween(450), label = "wavyAmp")
    val phase = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        while (true) {
            phase.snapTo(phase.value % 1f)
            phase.animateTo(phase.value + 1f, tween(1500, easing = LinearEasing))
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(stroke + amplitude * 2),
    ) {
        val sw = stroke.toPx()
        val a = amplitude.toPx() * amp
        val cy = size.height / 2f
        val start = sw / 2f
        val end = size.width - sw / 2f
        if (end <= start) return@Canvas
        val head = start + (end - start) * p
        // stopa (s medzerou za vlnkou) a bodka na jej konci
        val trackFrom = if (p > 0.001f) head + sw * 2f else start
        if (trackFrom < end) {
            drawLine(trackColor, Offset(trackFrom, cy), Offset(end, cy), strokeWidth = sw, cap = StrokeCap.Round)
        }
        if (p < 0.999f) drawCircle(color, radius = sw / 2f, center = Offset(end, cy))
        if (p > 0.001f) {
            val lambda = wavelength.toPx()
            val shift = phase.value
            fun y(x: Float) = cy + a * sin(2f * PI.toFloat() * (x / lambda - shift))
            val path = Path()
            path.moveTo(start, y(start))
            var x = start
            while (x < head) {
                x = min(x + 2f, head)
                path.lineTo(x, y(x))
            }
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Spojená skupina tlačidiel s jedným výberom (Material 3 Expressive): vybrané tlačidlo je celé zaoblené,
 * ostatné majú vnútorné rohy menšie, takže skupina pôsobí ako jeden celok.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    ConnectedChoice(options = options, selected = selected, onSelect = onSelect, modifier = modifier, height = height) { option, _ ->
        Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Spojená skupina s vlastným obsahom tlačidla (napr. len ikona). */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    item: @Composable (option: T, selected: Boolean) -> Unit,
) {
    val haptic = rememberHaptic()
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, option ->
            val isSel = option == selected
            val first = i == 0
            val last = i == options.lastIndex
            val outer = height / 2
            val inner = 8.dp
            val shape = if (isSel) RoundedCornerShape(50) else RoundedCornerShape(
                topStart = if (first) outer else inner,
                bottomStart = if (first) outer else inner,
                topEnd = if (last) outer else inner,
                bottomEnd = if (last) outer else inner,
            )
            val bg by animateColorAsState(if (isSel) scheme.primary else scheme.surfaceContainerHighest, tween(200), label = "choiceBg")
            val fg by animateColorAsState(if (isSel) scheme.onPrimary else scheme.onSurfaceVariant, tween(200), label = "choiceFg")
            Surface(
                selected = isSel,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSelect(option)
                },
                shape = shape,
                color = bg,
                contentColor = fg,
                modifier = Modifier
                    .weight(1f)
                    .height(height),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    item(option, isSel)
                }
            }
        }
    }
}

// ───────────────────────── Farebné role ─────────────────────────

/** Farebná rola výrazného prvku: každý nástroj, štatistika či dlaždica má svoju, aby sa dali rozlíšiť na prvý pohľad. */
enum class Accent { Primary, Secondary, Tertiary }

/** Farby jednej roly: výrazná farba, farba na nej, jemný podklad a farba na podklade. */
@Immutable
data class AccentColors(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

@Composable
@ReadOnlyComposable
fun Accent.colors(): AccentColors {
    val s = MaterialTheme.colorScheme
    return when (this) {
        Accent.Primary -> AccentColors(s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer)
        Accent.Secondary -> AccentColors(s.secondary, s.onSecondary, s.secondaryContainer, s.onSecondaryContainer)
        Accent.Tertiary -> AccentColors(s.tertiary, s.onTertiary, s.tertiaryContainer, s.onTertiaryContainer)
    }
}

/** Prvok presahuje vodorovne o [horizontal] na každú stranu (napr. vodorovný zoznam až po okraje panela). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val wide = constraints.copy(
        minWidth = constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
    )
    val placeable = measurable.measure(wide)
    val width = (placeable.width - extra).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Ikona dialógu vo výraznom tvare (vlnitý kruh). Pri [destructive] (odstránenie) je v chybovej farbe.
 */
@Composable
fun DialogIcon(
    icon: ImageVector,
    shape: Shape = ExpressiveShapes.Cookie,
    accent: Accent = Accent.Primary,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val c = accent.colors()
    ShapeIcon(
        icon = icon,
        shape = shape,
        container = if (destructive) scheme.errorContainer else c.container,
        content = if (destructive) scheme.onErrorContainer else c.onContainer,
        size = 56.dp,
    )
}

/**
 * Ozdobný výrazný tvar za obsahom (len sa kreslí, nezaberá miesto v rozložení, takže nezväčší kartu).
 * [alignment] a [offset] určujú polohu tvaru v prvku, [turn] jeho pootočenie v stupňoch (číta sa pri kreslení,
 * takže animácia neprekresľuje celú kartu). Orezáva ho tvar rodiča (karta, Surface).
 */
fun Modifier.decoShape(
    shape: Shape,
    color: Color,
    diameter: Dp,
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset.Zero,
    turn: () -> Float = { 0f },
): Modifier = drawBehind {
    val d = diameter.toPx()
    val side = d.roundToInt()
    val pos = alignment.align(IntSize(side, side), IntSize(size.width.roundToInt(), size.height.roundToInt()), layoutDirection)
    val outline = shape.createOutline(Size(d, d), layoutDirection, this)
    translate(left = pos.x + offset.x.toPx(), top = pos.y + offset.y.toPx()) {
        rotate(turn(), pivot = Offset(d / 2f, d / 2f)) { drawOutline(outline, color) }
    }
}
