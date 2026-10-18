package com.daklok.crescendo.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.PracticeGoal
import com.daklok.crescendo.data.PracticeSessionEntity
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/** „1 h 20 min“, „45 min“, „< 1 min“. */
@Composable
fun formatDuration(sec: Long): String {
    val min = sec / 60
    return when {
        min < 1 -> stringResource(R.string.dur_less_min)
        min < 60 -> stringResource(R.string.dur_min, min.toInt())
        else -> stringResource(R.string.dur_h_min, (min / 60).toInt(), (min % 60).toInt())
    }
}

/**
 * Denník cvičenia: dnešný cieľ, týždeň / mesiac v grafe, pravidelnosť (mriežka dní), záznamy vybraného dňa
 * (s poznámkami, dajú sa upraviť aj pridať ručne) a najviac cvičené skladby s detailom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(onBack: () -> Unit, onOpenPiece: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val scope = rememberCoroutineScope()
    val sessions by remember { app.practice.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val pieces by remember { app.library.observePieces() }.collectAsStateWithLifecycle(emptyList())
    BackHandler(onBack = onBack)

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    fun dayOf(s: PracticeSessionEntity): LocalDate = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()

    val byDay = remember(sessions) { sessions.groupBy { dayOf(it) } }
    val perDay = remember(byDay) { byDay.mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } } }
    var goalMin by remember { mutableIntStateOf(PracticeGoal.get(context)) }
    val goalSec = goalMin * 60L

    val streak = remember(perDay, today) {
        var d = if ((perDay[today] ?: 0L) > 0L) today else today.minusDays(1)
        var n = 0
        while ((perDay[d] ?: 0L) > 0L) {
            n++
            d = d.minusDays(1)
        }
        n
    }
    val bestStreak = remember(perDay) {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in perDay.keys.filter { (perDay[it] ?: 0) > 0 }.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        best
    }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDow))
    val weekSec = (0L..6L).sumOf { perDay[weekStart.plusDays(it)] ?: 0L }
    val totalSec = sessions.sumOf { it.durationSec.toLong() }
    val pieceById = remember(pieces) { pieces.associateBy { it.id } }

    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selectedDay = LocalDate.ofEpochDay(selectedEpoch)
    var editGoal by remember { mutableStateOf(false) }
    var editSession by remember { mutableStateOf<PracticeSessionEntity?>(null) }
    var addSession by remember { mutableStateOf(false) }
    var detailPiece by remember { mutableStateOf<Long?>(null) }

    val compact = isCompactWidth()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                title = { Text(stringResource(R.string.practice), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding() + 8.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (sessions.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(Icons.Outlined.Schedule, active = true, size = 88.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.practice_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.practice_empty_text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = { addSession = true }, shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.practice_add))
                        }
                    }
                } else {
                    // ── dnes + čísla ──
                    val todayCard: @Composable (Modifier) -> Unit = { m ->
                        TodayCard(
                            todaySec = perDay[today] ?: 0L,
                            goalSec = goalSec,
                            streak = streak,
                            onEditGoal = { editGoal = true },
                            modifier = m,
                        )
                    }
                    val stats: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(Icons.Outlined.CalendarMonth, stringResource(R.string.practice_week), formatDuration(weekSec), Modifier.weight(1f))
                                StatCard(Icons.Outlined.EmojiEvents, stringResource(R.string.practice_best_streak), pluralDays(bestStreak), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(Icons.Outlined.Schedule, stringResource(R.string.practice_total), formatDuration(totalSec), Modifier.weight(1f))
                                StatCard(
                                    Icons.Outlined.MusicNote,
                                    stringResource(R.string.practice_pieces_count),
                                    sessions.map { it.pieceId }.distinct().count { it in pieceById }.toString(),
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (compact) {
                        todayCard(Modifier.fillMaxWidth())
                        stats(Modifier.fillMaxWidth())
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            todayCard(Modifier.weight(1f))
                            stats(Modifier.weight(1f))
                        }
                    }

                    // ── graf: týždeň / mesiac ──
                    PeriodChart(
                        perDay = perDay,
                        today = today,
                        firstDow = firstDow,
                        goalSec = goalSec,
                        selected = selectedDay,
                        onSelect = { selectedEpoch = it.toEpochDay() },
                    )

                    // ── záznamy vybraného dňa ──
                    DayCard(
                        day = selectedDay,
                        today = today,
                        sessions = byDay[selectedDay].orEmpty().sortedBy { it.startedAt },
                        pieceById = pieceById,
                        onEdit = { editSession = it },
                        onAdd = { addSession = true },
                        onOpenPiece = { detailPiece = it },
                    )

                    // ── pravidelnosť ──
                    Heatmap(
                        perDay = perDay,
                        today = today,
                        firstDow = firstDow,
                        goalSec = goalSec,
                        weeks = if (compact) 15 else 26,
                        selected = selectedDay,
                        onSelect = { selectedEpoch = it.toEpochDay() },
                    )

                    // ── najviac cvičené ──
                    val top = remember(sessions, pieceById) {
                        sessions.groupBy { it.pieceId }
                            .mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } }
                            .filterKeys { it in pieceById }
                            .entries.sortedByDescending { it.value }
                            .take(10)
                    }
                    if (top.isNotEmpty()) {
                        SectionHeader(Icons.Outlined.Schedule, stringResource(R.string.practice_top))
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                val max = top.first().value.coerceAtLeast(1)
                                top.forEach { (id, sec) ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { detailPiece = id }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                pieceById[id]?.title ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                formatDuration(sec),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { sec.toFloat() / max },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50)),
                                            drawStopIndicator = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editGoal) {
        GoalDialog(
            current = goalMin,
            onSave = {
                goalMin = it
                PracticeGoal.set(context, it)
                editGoal = false
            },
            onDismiss = { editGoal = false },
        )
    }
    editSession?.let { s ->
        SessionDialog(
            title = pieceById[s.pieceId]?.title ?: "",
            pieces = null,
            initialMinutes = (s.durationSec / 60).coerceAtLeast(1),
            initialNote = s.note,
            onSave = { _, minutes, note ->
                scope.launch(Dispatchers.IO) { app.practice.update(s.copy(durationSec = minutes * 60, note = note)) }
                editSession = null
            },
            onDelete = {
                scope.launch(Dispatchers.IO) { app.practice.delete(s.id) }
                editSession = null
            },
            onDismiss = { editSession = null },
        )
    }
    if (addSession) {
        val day = if (sessions.isEmpty()) today else selectedDay
        SessionDialog(
            title = stringResource(R.string.practice_add_for, day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
            pieces = pieces,
            initialMinutes = 20,
            initialNote = "",
            onSave = { pieceId, minutes, note ->
                if (pieceId != null) {
                    // dnes = teraz (koniec cvičenia), iný deň = poludnie
                    val start = if (day == today) System.currentTimeMillis() - minutes * 60_000L
                    else day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
                    scope.launch(Dispatchers.IO) {
                        app.practice.insert(PracticeSessionEntity(pieceId = pieceId, startedAt = start, durationSec = minutes * 60, note = note))
                    }
                }
                addSession = false
            },
            onDelete = null,
            onDismiss = { addSession = false },
        )
    }
    detailPiece?.let { id ->
        val piece = pieceById[id]
        if (piece == null) {
            detailPiece = null
        } else {
            PieceSheet(
                piece = piece,
                sessions = sessions.filter { it.pieceId == id },
                today = today,
                firstDow = firstDow,
                zone = zone,
                onOpen = {
                    detailPiece = null
                    onOpenPiece(id)
                },
                onDismiss = { detailPiece = null },
            )
        }
    }
}

@Composable
private fun pluralDays(n: Int): String = pluralStringResource(R.plurals.days, n, n)

/** Dnešok: krúžok s pokrokom k dennému cieľu a séria dní. */
@Composable
private fun TodayCard(todaySec: Long, goalSec: Long, streak: Int, onEditGoal: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = (todaySec.toFloat() / goalSec.coerceAtLeast(60)).coerceAtLeast(0f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceAtMost(1f), Motion.gentle()) }
    val done = todaySec >= goalSec
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                val track = scheme.onPrimaryContainer.copy(alpha = 0.14f)
                val arc = if (done) scheme.tertiary else scheme.primary
                Canvas(Modifier.size(124.dp)) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                    drawArc(arc, -90f, 360f * anim.value, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${todaySec / 60}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("/ ${goalSec / 60} min", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.practice_today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (done) stringResource(R.string.practice_goal_done)
                    else stringResource(R.string.practice_goal_left, ((goalSec - todaySec + 59) / 60).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(shape = RoundedCornerShape(50), color = scheme.surface.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(16.dp), tint = scheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.practice_streak) + ": " + pluralDays(streak),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                    }
                }
                TextButton(onClick = onEditGoal, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                    Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.practice_goal_edit))
                }
            }
        }
    }
}

/** Stĺpcový graf za týždeň alebo mesiac; posun šípkami, ťuk na deň = jeho záznamy. */
@Composable
private fun PeriodChart(
    perDay: Map<LocalDate, Long>,
    today: LocalDate,
    firstDow: java.time.DayOfWeek,
    goalSec: Long,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var month by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) } // 0 = aktuálne obdobie, -1 = predchádzajúce…
    val days: List<LocalDate> = if (month) {
        val ym = YearMonth.from(today).plusMonths(offset.toLong())
        (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    } else {
        val start = today.with(TemporalAdjusters.previousOrSame(firstDow)).plusWeeks(offset.toLong())
        (0L..6L).map { start.plusDays(it) }
    }
    val locale = Locale.getDefault()
    val label = if (month) {
        days.first().format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    } else {
        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        days.first().format(f) + " – " + days.last().format(f)
    }
    val periodSec = days.sumOf { perDay[it] ?: 0L }
    val activeDays = days.count { (perDay[it] ?: 0L) > 0L }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !month,
                    onClick = {
                        month = false
                        offset = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.practice_tab_week)) }
                SegmentedButton(
                    selected = month,
                    onClick = {
                        month = true
                        offset = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.practice_tab_month)) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.practice_prev)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDuration(periodSec) + " · " + stringResource(R.string.practice_active_days, activeDays, days.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { offset++ }, enabled = offset < 0) {
                    Icon(Icons.Outlined.ChevronRight, stringResource(R.string.practice_next))
                }
            }
            Spacer(Modifier.height(12.dp))
            val maxSec = maxOf(days.maxOf { perDay[it] ?: 0L }, goalSec, 60L)
            val grow = remember(month, offset) { Animatable(0f) }
            LaunchedEffect(month, offset) { grow.animateTo(1f, Motion.gentle()) }
            val barColor = scheme.primary
            val lowColor = scheme.primary.copy(alpha = 0.5f)
            val selColor = scheme.tertiary
            val trackColor = scheme.outlineVariant.copy(alpha = 0.3f)
            val goalColor = scheme.tertiary.copy(alpha = 0.8f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(days) {
                        detectTapGestures { pos ->
                            val i = (pos.x / (size.width / days.size.toFloat())).toInt().coerceIn(0, days.lastIndex)
                            if (!days[i].isAfter(today)) onSelect(days[i])
                        }
                    },
            ) {
                val slot = size.width / days.size
                val barW = slot * if (month) 0.62f else 0.5f
                days.forEachIndexed { i, day ->
                    val v = (perDay[day] ?: 0L).toFloat() / maxSec
                    val x = i * slot + (slot - barW) / 2f
                    drawRoundRect(trackColor, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2f))
                    val h = size.height * v * grow.value
                    if (h > 0f) {
                        val c = when {
                            day == selected -> selColor
                            (perDay[day] ?: 0L) >= goalSec -> barColor
                            else -> lowColor
                        }
                        drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2f))
                    } else if (day == selected) {
                        drawRoundRect(selColor.copy(alpha = 0.35f), Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2f))
                    }
                }
                // čiara denného cieľa
                val gy = size.height * (1f - goalSec.toFloat() / maxSec)
                drawLine(
                    goalColor,
                    Offset(0f, gy),
                    Offset(size.width, gy),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                days.forEachIndexed { i, day ->
                    val text = if (month) {
                        if (day.dayOfMonth == 1 || day.dayOfMonth % 5 == 0) day.dayOfMonth.toString() else ""
                    } else {
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            day == selected -> scheme.tertiary
                            day == today -> scheme.primary
                            else -> scheme.onSurfaceVariant
                        },
                        fontWeight = if (day == selected || day == today) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(goalColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.practice_goal_line, (goalSec / 60).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Záznamy jedného dňa: skladba, čas, dĺžka, poznámka. Ťuk = upraviť. */
@Composable
private fun DayCard(
    day: LocalDate,
    today: LocalDate,
    sessions: List<PracticeSessionEntity>,
    pieceById: Map<Long, PieceEntity>,
    onEdit: (PracticeSessionEntity) -> Unit,
    onAdd: () -> Unit,
    onOpenPiece: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val title = if (day == today) stringResource(R.string.practice_today)
    else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Today, null, Modifier.size(20.dp), tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDuration(sessions.sumOf { it.durationSec.toLong() }),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.practice_add))
                }
            }
            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.practice_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            sessions.forEach { s ->
                val piece = pieceById[s.pieceId]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(scheme.secondaryContainer, CircleShape)
                            .clickable(enabled = piece != null) { onOpenPiece(s.pieceId) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.MusicNote, null, Modifier.size(20.dp), tint = scheme.onSecondaryContainer) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            piece?.title ?: stringResource(R.string.practice_deleted_piece),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime().format(timeFormat) + " · " + formatDuration(s.durationSec.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        if (s.note.isNotBlank()) {
                            Text(
                                s.note,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.Notes,
                        stringResource(R.string.practice_edit),
                        Modifier.size(18.dp),
                        tint = if (s.note.isNotBlank()) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** Pravidelnosť: mriežka posledných týždňov (stĺpec = týždeň), farba podľa splnenia denného cieľa. */
@Composable
private fun Heatmap(
    perDay: Map<LocalDate, Long>,
    today: LocalDate,
    firstDow: java.time.DayOfWeek,
    goalSec: Long,
    weeks: Int,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val start = today.with(TemporalAdjusters.previousOrSame(firstDow)).minusWeeks((weeks - 1).toLong())
    fun level(sec: Long): Int = when {
        sec <= 0 -> 0
        sec < goalSec / 3 -> 1
        sec < goalSec * 2 / 3 -> 2
        sec < goalSec -> 3
        else -> 4
    }
    val colors = listOf(
        scheme.outlineVariant.copy(alpha = 0.3f),
        scheme.primary.copy(alpha = 0.28f),
        scheme.primary.copy(alpha = 0.5f),
        scheme.primary.copy(alpha = 0.75f),
        scheme.primary,
    )
    val selColor = scheme.tertiary
    val active = (0 until weeks * 7).count { (perDay[start.plusDays(it.toLong())] ?: 0L) > 0L }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GridView, null, Modifier.size(18.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.practice_consistency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.practice_active_days, active, weeks * 7),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(weeks / 7f)
                    .pointerInput(start, weeks) {
                        detectTapGestures { pos ->
                            val cell = minOf(size.width / weeks.toFloat(), size.height / 7f)
                            val w = (pos.x / cell).toInt()
                            val d = (pos.y / cell).toInt()
                            if (w in 0 until weeks && d in 0..6) {
                                val day = start.plusDays((w * 7 + d).toLong())
                                if (!day.isAfter(today)) onSelect(day)
                            }
                        }
                    },
            ) {
                val cell = minOf(size.width / weeks, size.height / 7f)
                val gap = cell * 0.16f
                for (w in 0 until weeks) for (d in 0..6) {
                    val day = start.plusDays((w * 7 + d).toLong())
                    if (day.isAfter(today)) continue
                    val c = colors[level(perDay[day] ?: 0L)]
                    val o = Offset(w * cell + gap / 2, d * cell + gap / 2)
                    val s = Size(cell - gap, cell - gap)
                    drawRoundRect(c, o, s, CornerRadius(s.width * 0.28f))
                    if (day == selected) {
                        drawRoundRect(selColor, o, s, CornerRadius(s.width * 0.28f), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.practice_less), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                colors.forEach { c ->
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(12.dp)
                            .background(c, RoundedCornerShape(3.dp)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.practice_more_goal), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Denný cieľ: posuvník 5–180 min a rýchle voľby. */
@Composable
private fun GoalDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    val minutes = ((value / 5f).roundToInt() * 5).coerceIn(5, 180)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.Flag, null) },
        title = { Text(stringResource(R.string.practice_goal_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.dur_min, minutes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.practice_goal_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Slider(value = value, onValueChange = { value = it }, valueRange = 5f..180f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { value = m.toFloat() },
                            label = { Text("$m") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Úprava záznamu (dĺžka, poznámka, zmazanie) alebo ručné pridanie ([pieces] != null – treba vybrať skladbu).
 */
@Composable
private fun SessionDialog(
    title: String,
    pieces: List<PieceEntity>?,
    initialMinutes: Int,
    initialNote: String,
    onSave: (pieceId: Long?, minutes: Int, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }
    var note by remember { mutableStateOf(initialNote) }
    var query by remember { mutableStateOf("") }
    var pieceId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val minutes = minutesText.toIntOrNull()?.takeIf { it in 1..600 }
    val canSave = minutes != null && (pieces == null || pieceId != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pieces != null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(stringResource(R.string.practice_pick_piece)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filtered = remember(pieces, query) {
                        pieces.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.composer.contains(query, ignoreCase = true) }
                            .sortedByDescending { it.lastOpenedAt }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(filtered, key = { it.id }) { p ->
                                val sel = p.id == pieceId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { pieceId = p.id }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        p.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { t -> minutesText = t.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.practice_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = minutes == null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.practice_note)) },
                    placeholder = { Text(stringResource(R.string.practice_note_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.practice_delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pieceId, minutes ?: 0, note.trim()) }, enabled = canSave) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.practice_delete_title)) },
            text = { Text(stringResource(R.string.practice_delete_text)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Detail skladby: koľko, ako často, posledných 8 týždňov a poznámky. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PieceSheet(
    piece: PieceEntity,
    sessions: List<PracticeSessionEntity>,
    today: LocalDate,
    firstDow: java.time.DayOfWeek,
    zone: ZoneId,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val total = sessions.sumOf { it.durationSec.toLong() }
    val last = sessions.maxOfOrNull { it.startedAt }
    val avg = if (sessions.isEmpty()) 0L else total / sessions.size
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDow))
    val weeks = (7 downTo 0).map { weekStart.minusWeeks(it.toLong()) }
    val perWeek = weeks.map { ws ->
        sessions.filter {
            val d = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            !d.isBefore(ws) && d.isBefore(ws.plusWeeks(1))
        }.sumOf { it.durationSec.toLong() }
    }
    val notes = sessions.filter { it.note.isNotBlank() }.sortedByDescending { it.startedAt }.take(5)
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaceContainerLow) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(piece.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (piece.composer.isNotBlank()) {
                    Text(piece.composer, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.Schedule, stringResource(R.string.practice_total), formatDuration(total), Modifier.weight(1f))
                StatCard(Icons.Outlined.CalendarMonth, stringResource(R.string.practice_sessions), sessions.size.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.Today, stringResource(R.string.practice_last), last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(dateFormat) } ?: "—", Modifier.weight(1f))
                StatCard(Icons.Outlined.Schedule, stringResource(R.string.practice_avg), formatDuration(avg), Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.practice_last8w), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    val max = perWeek.maxOrNull()?.coerceAtLeast(60) ?: 60
                    val bar = scheme.primary
                    val track = scheme.outlineVariant.copy(alpha = 0.3f)
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                    ) {
                        val slot = size.width / perWeek.size
                        val w = slot * 0.55f
                        perWeek.forEachIndexed { i, v ->
                            val x = i * slot + (slot - w) / 2
                            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 3))
                            val h = size.height * v / max
                            if (h > 0) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 3))
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) {
                SectionHeader(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.practice_notes))
                notes.forEach { s ->
                    Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate().format(dateFormat),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(s.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            FilledTonalButton(onClick = onOpen, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.practice_open_piece))
            }
        }
    }
}
