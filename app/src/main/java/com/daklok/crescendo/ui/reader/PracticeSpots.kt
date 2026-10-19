package com.daklok.crescendo.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PracticeSpotEntity
import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.DialogIcon
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cvičebné úseky jednej strany priamo v notách: jemne podfarbený obdĺžnik a počítadlo v rohu
 * (ťuk = ešte raz prejdené, podržanie = otvorí úsek v zozname). Pri označovaní ([marking])
 * ťahanie prstom nakreslí nový úsek. Vrstva leží presne na strane (súradnice 0..1).
 */
@Composable
fun PracticeSpotsLayer(
    page: Int,
    spots: List<PracticeSpotEntity>,
    marking: Boolean,
    visible: Boolean,
    flashId: Long?,
    onCreate: (page: Int, x1: Float, y1: Float, x2: Float, y2: Float) -> Unit,
    onRep: (PracticeSpotEntity) -> Unit,
    onOpen: (PracticeSpotEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageSpots = remember(spots, page) { spots.filter { it.page == page } }
    if (pageSpots.isEmpty() && !marking) return
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var draft by remember { mutableStateOf<Rect?>(null) }
    val create by rememberUpdatedState(onCreate)
    val flash = remember { Animatable(0f) }
    LaunchedEffect(flashId, pageSpots.any { it.id == flashId }) {
        if (flashId != null && pageSpots.any { it.id == flashId }) {
            repeat(3) {
                flash.animateTo(1f, tween(240))
                flash.animateTo(0f, tween(320))
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val accent = scheme.tertiary
        val doneColor = scheme.outline
        val flashColor = scheme.primary
        if (visible || marking) {
            Canvas(Modifier.fillMaxSize()) {
                val r = CornerRadius(10.dp.toPx())
                pageSpots.forEach { s ->
                    val tl = Offset(s.x1 * size.width, s.y1 * size.height)
                    val sz = Size((s.x2 - s.x1) * size.width, (s.y2 - s.y1) * size.height)
                    if (s.done) {
                        drawRoundRect(
                            doneColor.copy(alpha = 0.55f), tl, sz, r,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    } else {
                        drawRoundRect(accent.copy(alpha = 0.09f), tl, sz, r)
                        drawRoundRect(accent.copy(alpha = 0.75f), tl, sz, r, style = Stroke(2.dp.toPx()))
                    }
                    if (s.id == flashId && flash.value > 0f) {
                        val g = 5.dp.toPx()
                        drawRoundRect(
                            flashColor.copy(alpha = 0.45f * flash.value),
                            tl - Offset(g, g),
                            Size(sz.width + g * 2, sz.height + g * 2),
                            CornerRadius(r.x + g),
                            style = Stroke(5.dp.toPx()),
                        )
                    }
                }
                draft?.let { d ->
                    drawRoundRect(flashColor.copy(alpha = 0.12f), d.topLeft, d.size, r)
                    drawRoundRect(
                        flashColor, d.topLeft, d.size, r,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))),
                    )
                }
            }
            val half = with(density) { 14.dp.roundToPx() }
            pageSpots.forEach { s ->
                SpotBadge(
                    spot = s,
                    enabled = !marking,
                    onTap = { onRep(s) },
                    onLongPress = { onOpen(s) },
                    modifier = Modifier.offset {
                        IntOffset((s.x1 * w).roundToInt() + half / 2, ((s.y1 * h).roundToInt() - half).coerceAtLeast(0))
                    },
                )
            }
        }
        if (marking) {
            // označovanie: ťahanie prstom (alebo perom) nakreslí obdĺžnik; dva prsty = zrušené (priblíženie)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val start = down.position
                            var end = start
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) {
                                    cancelled = true
                                    break
                                }
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                end = c.position
                                c.consume()
                                if (!c.pressed) break
                                draft = Rect(
                                    Offset(min(start.x, end.x), min(start.y, end.y)),
                                    Offset(max(start.x, end.x), max(start.y, end.y)),
                                )
                            }
                            draft = null
                            val sw = size.width.toFloat().coerceAtLeast(1f)
                            val sh = size.height.toFloat().coerceAtLeast(1f)
                            val x1 = (min(start.x, end.x) / sw).coerceIn(0f, 1f)
                            val x2 = (max(start.x, end.x) / sw).coerceIn(0f, 1f)
                            val y1 = (min(start.y, end.y) / sh).coerceIn(0f, 1f)
                            val y2 = (max(start.y, end.y) / sh).coerceIn(0f, 1f)
                            // príliš malý obdĺžnik (ťuk) sa nepočíta
                            if (!cancelled && abs(x2 - x1) > 0.04f && abs(y2 - y1) > 0.015f) create(page, x1, y1, x2, y2)
                        }
                    },
            )
        }
    }
}

/** Počítadlo úseku v notách: ťuk pripočíta prejdenie (s malým poskočením), podržanie otvorí úsek. */
@Composable
private fun SpotBadge(
    spot: PracticeSpotEntity,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val bump = remember { Animatable(1f) }
    val bg = if (spot.done) scheme.surfaceContainerHighest else scheme.tertiary
    val fg = if (spot.done) scheme.onSurfaceVariant else scheme.onTertiary
    Row(
        modifier
            .scale(bump.value)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onTap()
                    scope.launch {
                        bump.snapTo(1f)
                        bump.animateTo(1.28f, tween(90))
                        bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 600f))
                    }
                },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (spot.done) Icons.Outlined.TaskAlt else Icons.Outlined.Add, null, Modifier.size(15.dp), tint = fg)
        Spacer(Modifier.width(3.dp))
        Text(
            spot.reps.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
        )
    }
}

/** Názov úseku: vlastný alebo „Úsek 3“. */
@Composable
fun spotTitle(spot: PracticeSpotEntity, index: Int): String =
    spot.name.ifBlank { stringResource(R.string.spot_default_name, index + 1) }

/**
 * Panel „Na precvičenie“: úseky skladby s počítadlom (ťuk na číslo = +1), skok na stranu,
 * zrýchľovanie s metronómom, premenovanie, zvládnuté a zmazanie. Dole tlačidlo na označenie nového úseku.
 */
@Composable
fun PracticeSpotsPanel(
    spots: List<PracticeSpotEntity>,
    visibleOnPage: Boolean,
    focusId: Long?,
    onToggleVisible: () -> Unit,
    onMark: () -> Unit,
    onJump: (PracticeSpotEntity) -> Unit,
    onRep: (PracticeSpotEntity, Int) -> Unit,
    onLadder: (PracticeSpotEntity) -> Unit,
    onRename: (PracticeSpotEntity) -> Unit,
    onDone: (PracticeSpotEntity, Boolean) -> Unit,
    onDelete: (PracticeSpotEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val open = spots.filter { !it.done }
    val done = spots.filter { it.done }
    val todayTotal = spots.sumOf { it.today() }
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    ToolPanel(
        title = stringResource(R.string.spots_title),
        subtitle = if (spots.isEmpty()) null
        else pluralStringResource(R.plurals.spots_n, open.size, open.size) + ", " + stringResource(R.string.spots_today, todayTotal),
        icon = Icons.Outlined.Flag,
        shape = ExpressiveShapes.Clover,
        container = scheme.tertiaryContainer,
        content = scheme.onTertiaryContainer,
        onClose = onClose,
        modifier = modifier.widthIn(max = 400.dp),
        actions = {
            if (spots.isNotEmpty()) {
                ToolButton(
                    icon = if (visibleOnPage) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    description = stringResource(R.string.spots_show_on_page),
                    active = !visibleOnPage,
                    onClick = onToggleVisible,
                )
            }
        },
    ) {
        Column(
            Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (spots.isEmpty()) {
                Text(
                    stringResource(R.string.spots_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            open.forEach { s ->
                SpotRow(
                    spot = s,
                    index = spots.indexOf(s),
                    focused = s.id == focusId,
                    onJump = { onJump(s) },
                    onRep = { d -> onRep(s, d) },
                    onLadder = { onLadder(s) },
                    onRename = { onRename(s) },
                    onDone = { onDone(s, true) },
                    onDelete = { onDelete(s) },
                )
            }
            if (open.isEmpty() && done.isNotEmpty()) {
                Text(
                    stringResource(R.string.spots_all_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (done.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { doneOpen = !doneOpen }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.TaskAlt, null, Modifier.size(18.dp), tint = scheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.spots_done_section, done.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(if (doneOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = scheme.primary)
                }
                if (doneOpen) {
                    done.forEach { s ->
                        SpotRow(
                            spot = s,
                            index = spots.indexOf(s),
                            focused = s.id == focusId,
                            onJump = { onJump(s) },
                            onRep = { d -> onRep(s, d) },
                            onLadder = { onLadder(s) },
                            onRename = { onRename(s) },
                            onDone = { onDone(s, false) },
                            onDelete = { onDelete(s) },
                        )
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = onMark,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.HighlightAlt, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.spots_mark), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Jeden úsek v zozname: číslo vo výraznom tvare, názov a štatistika, počítadlo (+1) a ponuka. */
@Composable
private fun SpotRow(
    spot: PracticeSpotEntity,
    index: Int,
    focused: Boolean,
    onJump: () -> Unit,
    onRep: (Int) -> Unit,
    onLadder: () -> Unit,
    onRename: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val container by animateColorAsState(
        if (focused) scheme.tertiaryContainer else scheme.surfaceContainerHighest,
        tween(260),
        label = "spotRow",
    )
    Surface(shape = RoundedCornerShape(20.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(onClick = onJump)
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(
                        if (spot.done) scheme.surfaceContainerLow else scheme.tertiaryContainer,
                        if (spot.done) ExpressiveShapes.Squircle else ExpressiveShapes.Cookie,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (spot.done) {
                    Icon(Icons.Outlined.TaskAlt, null, Modifier.size(18.dp), tint = scheme.primary)
                } else {
                    Text(
                        (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spotTitle(spot, index),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = buildList {
                    add(stringResource(R.string.bookmark_page, spot.page + 1))
                    if (spot.today() > 0) add(stringResource(R.string.spots_today, spot.today()))
                    if (spot.bestBpm > 0) add(stringResource(R.string.spot_best_bpm, spot.bestBpm))
                }
                Text(
                    parts.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            // počítadlo: ťuk = ešte raz prejdené
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onRep(1)
                },
                shape = RoundedCornerShape(50),
                color = if (spot.done) scheme.surfaceContainerLow else scheme.tertiary,
                contentColor = if (spot.done) scheme.onSurfaceVariant else scheme.onTertiary,
            ) {
                Row(
                    Modifier
                        .height(36.dp)
                        .padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${spot.reps}×", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.Add, stringResource(R.string.spot_add_rep), Modifier.size(18.dp))
                }
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    if (!spot.done) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.spot_ladder)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.TrendingUp, null) },
                            onClick = { menu = false; onLadder() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.spot_remove_rep)) },
                        leadingIcon = { Icon(Icons.Outlined.Remove, null) },
                        enabled = spot.reps > 0,
                        onClick = { menu = false; onRep(-1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (spot.done) R.string.spot_undone else R.string.spot_done)) },
                        leadingIcon = { Icon(if (spot.done) Icons.Outlined.Restore else Icons.Outlined.TaskAlt, null) },
                        onClick = { menu = false; onDone() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Pomenovanie úseku (nový aj premenovanie) s rýchlymi návrhmi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpotNameDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val suggestions = listOf(
        R.string.spot_sug_run, R.string.spot_sug_leap, R.string.spot_sug_rhythm, R.string.spot_sug_chords,
        R.string.spot_sug_fingering, R.string.spot_sug_left, R.string.spot_sug_right, R.string.spot_sug_transition,
    ).map { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { DialogIcon(Icons.Outlined.Flag, ExpressiveShapes.Clover, Accent.Tertiary) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.spot_name)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEach { sug ->
                        SuggestionChip(onClick = { name = sug }, label = { Text(sug) }, shape = RoundedCornerShape(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Nápoveda počas označovania úseku (hore nad notami) so zrušením. */
@Composable
fun SpotMarkingHint(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.HighlightAlt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.inversePrimary)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.spots_mark_hint),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
