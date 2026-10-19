package com.daklok.crescendo.ui.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.DialogIcon
import com.daklok.crescendo.ui.common.ExpressiveSwitch
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.ScreenTopBar
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.WavyRing
import com.daklok.crescendo.ui.common.colors
import com.daklok.crescendo.ui.common.decoShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import com.daklok.crescendo.data.PracticeReminder
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimeInput
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.PracticeGoal
import com.daklok.crescendo.data.PracticeSessionEntity
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
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
    val allSpots by remember { app.spots.observeAll() }.collectAsStateWithLifecycle(emptyList())
    /** Týždeň v súhrne: 0 = tento, 1 = minulý… */
    var weekBack by rememberSaveable { mutableIntStateOf(1) }
    BackHandler(onBack = onBack)

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val firstDow = com.daklok.crescendo.data.WeeklySummary.firstDayOfWeek()
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
    var detailPiece by remember { mutableStateOf<Long?>(null) }
    var reminderOn by remember { mutableStateOf(PracticeReminder.enabled(context)) }
    var reminderOpen by remember { mutableStateOf(false) }
    // detail dňa: otvorí sa po ťuknutí na deň v grafe alebo v mriežke
    var dayOpen by remember { mutableStateOf(false) }

    val compact = isCompactWidth()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.practice),
                onBack = onBack,
                actions = {
                    TonalIconButton(
                        icon = if (reminderOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = stringResource(R.string.reminder_title),
                        highlighted = reminderOn,
                        onClick = { reminderOpen = true },
                    )
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding() + 4.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = if (compact) 12.dp else 24.dp, end = if (compact) 12.dp else 24.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (sessions.isEmpty()) {
                    PracticeEmpty()
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
                                StatCard(
                                    Icons.Outlined.CalendarMonth,
                                    stringResource(R.string.practice_week),
                                    formatDuration(weekSec),
                                    ExpressiveShapes.Cookie,
                                    Accent.Primary,
                                    Modifier.weight(1f),
                                )
                                StatCard(
                                    Icons.Outlined.EmojiEvents,
                                    stringResource(R.string.practice_best_streak),
                                    pluralDays(bestStreak),
                                    ExpressiveShapes.Clover,
                                    Accent.Tertiary,
                                    Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    Icons.Outlined.Schedule,
                                    stringResource(R.string.practice_total),
                                    formatDuration(totalSec),
                                    ExpressiveShapes.Squircle,
                                    Accent.Secondary,
                                    Modifier.weight(1f),
                                )
                                StatCard(
                                    Icons.Outlined.MusicNote,
                                    stringResource(R.string.practice_pieces_count),
                                    sessions.map { it.pieceId }.distinct().count { it in pieceById }.toString(),
                                    ExpressiveShapes.Cookie12,
                                    Accent.Primary,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (compact) {
                        todayCard(Modifier.fillMaxWidth())
                        stats(Modifier.fillMaxWidth())
                    } else {
                        // karta dneška rovnako vysoká ako štyri čísla vedľa nej
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            todayCard(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            stats(Modifier.weight(1f))
                        }
                    }

                    // ── týždeň v skratke (dá sa listovať) ──
                    val firstSession = remember(sessions) { sessions.minOfOrNull { it.startedAt } }
                    val oldestBack = remember(firstSession, weekStart) {
                        firstSession?.let {
                            val first = com.daklok.crescendo.data.WeeklySummary.weekStart(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())
                            java.time.temporal.ChronoUnit.WEEKS.between(first, weekStart).toInt().coerceAtLeast(0)
                        } ?: 0
                    }
                    // minulý týždeň bez cvičenia: súhrn začne tým aktuálnym
                    var weekChecked by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(sessions.isNotEmpty()) {
                        if (sessions.isNotEmpty() && !weekChecked) {
                            weekChecked = true
                            val last = com.daklok.crescendo.data.WeeklySummary.compute(sessions, weekStart.minusWeeks(1), goalSec, zone, today)
                            if (last.totalSec <= 0) weekBack = 0
                        }
                    }
                    val shownStart = weekStart.minusWeeks(weekBack.toLong())
                    val weekSummary = remember(sessions, shownStart, goalSec) {
                        com.daklok.crescendo.data.WeeklySummary.compute(sessions, shownStart, goalSec, zone, today)
                    }
                    WeeklyCard(
                        summary = weekSummary,
                        topTitle = weekSummary.topPieceId?.let { pieceById[it]?.title },
                        title = when (weekBack) {
                            0 -> stringResource(R.string.practice_week)
                            1 -> stringResource(R.string.weekly_title_last)
                            else -> stringResource(R.string.weekly_title_week)
                        },
                        goalSec = goalSec,
                        today = today,
                        onPrev = if (weekBack < oldestBack) ({ weekBack += 1 }) else null,
                        onNext = if (weekBack > 0) ({ weekBack -= 1 }) else null,
                    )

                    // ── na precvičenie: otvorené úseky zo všetkých skladieb ──
                    val openSpots = remember(allSpots, pieceById) {
                        allSpots.filter { !it.done && it.pieceId in pieceById }
                            .sortedWith(compareBy({ it.lastPracticedAt != 0L }, { it.lastPracticedAt }))
                    }
                    if (openSpots.isNotEmpty()) {
                        SectionHeader(Icons.Outlined.Flag, stringResource(R.string.spots_title), Modifier.padding(top = 4.dp))
                        SpotsList(
                            spots = openSpots,
                            pieceById = pieceById,
                            onOpen = { spot ->
                                // otvorí skladbu rovno na strane úseku
                                scope.launch {
                                    app.library.saveProgress(spot.pieceId, spot.page)
                                    onOpenPiece(spot.pieceId)
                                }
                            },
                        )
                    }

                    // ── graf (týždeň / mesiac) a odznaky; na širokej obrazovke vedľa seba ──
                    val badgeStats = remember(sessions, goalMin, bestStreak) { badgeStats(sessions, goalMin, bestStreak, zone) }
                    val chart: @Composable (Modifier) -> Unit = { m ->
                        PeriodChart(
                            perDay = perDay,
                            today = today,
                            firstDow = firstDow,
                            goalSec = goalSec,
                            selected = selectedDay,
                            onSelect = { selectedEpoch = it.toEpochDay(); dayOpen = true },
                            modifier = m,
                        )
                    }
                    if (compact) {
                        chart(Modifier.fillMaxWidth())
                        BadgesSection(badgeStats)
                    } else {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            chart(
                                Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight(),
                            )
                            BadgesSection(
                                badgeStats,
                                columns = 3,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }

                    // ── pravidelnosť ──
                    Heatmap(
                        perDay = perDay,
                        today = today,
                        firstDow = firstDow,
                        goalSec = goalSec,
                        weeks = if (compact) 15 else 26,
                        selected = selectedDay,
                        onSelect = { selectedEpoch = it.toEpochDay(); dayOpen = true },
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
                        SectionHeader(Icons.Outlined.Insights, stringResource(R.string.practice_top), Modifier.padding(top = 4.dp))
                        TopPieces(top = top, pieceById = pieceById, onOpen = { detailPiece = it })
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
    if (dayOpen) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { dayOpen = false }) {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp).navigationBarsPadding()) {
                DayCard(
                    day = selectedDay,
                    today = today,
                    sessions = byDay[selectedDay].orEmpty().sortedBy { it.startedAt },
                    pieceById = pieceById,
                    onOpenPiece = { dayOpen = false; detailPiece = it },
                )
            }
        }
    }
    if (reminderOpen) {
        ReminderDialog(
            enabled = reminderOn,
            minuteOfDay = PracticeReminder.minuteOfDay(context),
            weekly = com.daklok.crescendo.data.WeeklySummary.notifyEnabled(context),
            onSave = { on, minute, weekly ->
                PracticeReminder.set(context, on, minute)
                com.daklok.crescendo.data.WeeklySummary.setNotify(context, weekly)
                reminderOn = on
                reminderOpen = false
            },
            onDismiss = { reminderOpen = false },
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

/** Dnešok: veľký vlnitý krúžok s minútami k dennému cieľu, séria dní a zmena cieľa. */
@Composable
private fun TodayCard(todaySec: Long, goalSec: Long, streak: Int, onEditGoal: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = (todaySec.toFloat() / goalSec.coerceAtLeast(60)).coerceAtLeast(0f)
    // krúžok sa pri otvorení naplní od nuly
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceAtMost(1f), Motion.gentle()) }
    val done = todaySec >= goalSec
    val ring by animateColorAsState(if (done) scheme.tertiary else scheme.primary, tween(400), label = "todayRing")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Box(
            // veľký vlnitý kruh v rohu (ozdoba ako na karte Pokračuj)
            Modifier.decoShape(
                shape = ExpressiveShapes.Cookie12,
                color = scheme.primary.copy(alpha = 0.07f),
                diameter = 210.dp,
                alignment = Alignment.TopEnd,
                offset = DpOffset(64.dp, (-72).dp),
                turn = { 10f },
            ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                    WavyRing(
                        progress = anim.value,
                        modifier = Modifier.matchParentSize(),
                        color = ring,
                        trackColor = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                        stroke = 9.dp,
                        amplitude = 2.2.dp,
                        wavelength = 22.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${todaySec / 60}", style = MaterialTheme.typography.displaySmall, maxLines = 1)
                        Text(
                            stringResource(R.string.practice_of_goal, (goalSec / 60).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.practice_today), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (done) stringResource(R.string.practice_goal_done)
                        else stringResource(R.string.practice_goal_left, ((goalSec - todaySec + 59) / 60).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(shape = RoundedCornerShape(50), color = scheme.surface.copy(alpha = 0.6f)) {
                        Row(
                            Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(18.dp), tint = scheme.tertiary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.practice_streak) + ": " + pluralDays(streak),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Surface(
                        onClick = onEditGoal,
                        shape = RoundedCornerShape(50),
                        color = scheme.onPrimaryContainer.copy(alpha = 0.08f),
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.practice_goal_edit), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
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
    modifier: Modifier = Modifier,
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
        stringResource(R.string.time_range, days.first().format(f), days.last().format(f))
    }
    val periodSec = days.sumOf { perDay[it] ?: 0L }
    val activeDays = days.count { (perDay[it] ?: 0L) > 0L }
    val weekLabel = stringResource(R.string.practice_tab_week)
    val monthLabel = stringResource(R.string.practice_tab_month)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            ConnectedChoice(
                options = listOf(false, true),
                selected = month,
                label = { if (it) monthLabel else weekLabel },
                onSelect = {
                    month = it
                    offset = 0
                },
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalIconButton(
                    icon = Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.practice_prev),
                    onClick = { offset-- },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDuration(periodSec) + " · " + stringResource(R.string.practice_active_days, activeDays, days.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TonalIconButton(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.practice_next),
                    onClick = { offset++ },
                    enabled = offset < 0,
                )
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
                // na širokej obrazovke nie príliš hrubé stĺpce
                val barW = minOf(slot * if (month) 0.62f else 0.5f, (if (month) 18.dp else 44.dp).toPx())
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
    onOpenPiece: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val title = if (day == today) stringResource(R.string.practice_today)
    else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(Icons.Outlined.Today, ExpressiveShapes.Cookie12, scheme.primaryContainer, scheme.onPrimaryContainer, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDuration(sessions.sumOf { it.durationSec.toLong() }),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
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
            // jedna skladba = jeden riadok (súčet času, počet sedení); podrobnosti po rozbalení
            val groups = remember(sessions) {
                sessions.groupBy { it.pieceId }.entries
                    .map { (id, l) -> Triple(id, l.sortedBy { it.startedAt }, l.sumOf { it.durationSec.toLong() }) }
                    .sortedByDescending { it.third }
            }
            val dayTotal = groups.sumOf { it.third }.coerceAtLeast(1L)
            var showAll by remember(day) { mutableStateOf(false) }
            var expanded by remember(day) { mutableStateOf<Long?>(null) }
            val visible = if (showAll) groups else groups.take(DAY_PREVIEW)
            visible.forEach { (pieceId, list, total) ->
                val piece = pieceById[pieceId]
                val open = expanded == pieceId
                Column(Modifier.animateContentSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (open) null else pieceId }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(ExpressiveShapes.Cookie)
                                .background(scheme.secondaryContainer)
                                .clickable(enabled = piece != null) { onOpenPiece(pieceId) },
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
                            // podiel na dnešnom čase
                            ShareBar(
                                fraction = total.toFloat() / dayTotal,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDuration(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (list.size > 1 || list.any { it.note.isNotBlank() }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (list.any { it.note.isNotBlank() }) {
                                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(14.dp), tint = scheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    if (list.size > 1) {
                                        Text("${list.size}×", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Icon(
                            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            Modifier.padding(start = 4.dp).size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                    if (open) {
                        // jednotlivé sedenia: čas, dĺžka (len na čítanie – čas meria aplikácia sama)
                        list.forEach { s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 68.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
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
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (groups.size > DAY_PREVIEW) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        if (showAll) stringResource(R.string.practice_show_less)
                        else stringResource(R.string.practice_show_all, groups.size),
                    )
                }
            }
        }
    }
}

/** Koľko skladieb sa v karte dňa ukáže, kým sa nerozbalí celý zoznam. */
private const val DAY_PREVIEW = 4

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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GridView, null, Modifier.size(20.dp), tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.practice_consistency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    drawRoundRect(c, o, s, CornerRadius(s.width * 0.34f))
                    if (day == selected) {
                        drawRoundRect(selColor, o, s, CornerRadius(s.width * 0.34f), style = Stroke(2.dp.toPx()))
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
                            .background(c, RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.practice_more_goal), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Číslo s ikonou vo výraznom tvare (každé číslo má svoj tvar a farbu). */
@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    shape: Shape,
    accent: Accent,
    modifier: Modifier = Modifier,
) {
    val c = accent.colors()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(14.dp)) {
            ShapeIcon(icon = icon, shape = shape, container = c.container, content = c.onContainer, size = 40.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = if (isCompactWidth()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
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

/** Najviac cvičené skladby: poradie vo výraznom tvare, čas a podiel ako pruh. */
@Composable
private fun TopPieces(top: List<Map.Entry<Long, Long>>, pieceById: Map<Long, PieceEntity>, onOpen: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val max = top.first().value.coerceAtLeast(1)
            top.forEachIndexed { i, (id, sec) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(id) }
                        .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RankBadge(i + 1)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pieceById[id]?.title ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatDuration(sec),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        ShareBar(
                            fraction = sec.toFloat() / max,
                            modifier = Modifier.padding(top = 7.dp),
                            color = if (i == 0) scheme.primary else scheme.primary.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        null,
                        Modifier
                            .padding(start = 4.dp)
                            .size(20.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Úseky na precvičenie zo všetkých skladieb: najdlhšie necvičené hore. Ťuk otvorí skladbu na strane úseku.
 */
@Composable
private fun SpotsList(
    spots: List<com.daklok.crescendo.data.PracticeSpotEntity>,
    pieceById: Map<Long, PieceEntity>,
    onOpen: (com.daklok.crescendo.data.PracticeSpotEntity) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) spots else spots.take(6)
    val now = System.currentTimeMillis()
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 8.dp).animateContentSize()) {
            shown.forEachIndexed { i, spot ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(spot) }
                        .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeIcon(
                        Icons.Outlined.Flag,
                        if (i % 2 == 0) ExpressiveShapes.Cookie else ExpressiveShapes.Clover,
                        scheme.tertiaryContainer,
                        scheme.onTertiaryContainer,
                        36.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            spot.name.ifBlank { stringResource(R.string.spot_on_page, spot.page + 1) },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val last = if (spot.lastPracticedAt > 0) {
                            android.text.format.DateUtils.getRelativeTimeSpanString(
                                spot.lastPracticedAt, now, android.text.format.DateUtils.DAY_IN_MILLIS,
                            ).toString()
                        } else {
                            stringResource(R.string.spot_never)
                        }
                        Text(
                            listOf(
                                pieceById[spot.pieceId]?.title.orEmpty(),
                                stringResource(R.string.bookmark_page, spot.page + 1),
                                "${spot.reps}×",
                                last,
                            ).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 4.dp).size(20.dp), tint = scheme.onSurfaceVariant)
                }
            }
            if (spots.size > 6) {
                TextButton(onClick = { all = !all }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (all) stringResource(R.string.spots_show_less) else stringResource(R.string.spots_show_all, spots.size))
                }
            }
        }
    }
}

/** Poradie v rebríčku: prvé miesto výrazné, ďalšie jemnejšie, každé v inom tvare. */
@Composable
private fun RankBadge(rank: Int) {
    val scheme = MaterialTheme.colorScheme
    val (shape, bg, fg) = when (rank) {
        1 -> Triple(ExpressiveShapes.Cookie12, scheme.primary, scheme.onPrimary)
        2 -> Triple(ExpressiveShapes.Cookie, scheme.primaryContainer, scheme.onPrimaryContainer)
        3 -> Triple(ExpressiveShapes.Clover, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> Triple(ExpressiveShapes.Squircle, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
    Box(
        Modifier
            .size(34.dp)
            .background(bg, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** Zaoblený pruh podielu (na dni a v rebríčku skladieb). */
@Composable
private fun ShareBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** Prázdny denník: zložené výrazné tvary a krátke vysvetlenie (ako prázdna knižnica). */
@Composable
private fun PracticeEmpty() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer { rotationZ = 14f }
                    .background(scheme.tertiaryContainer, ExpressiveShapes.Clover),
            )
            Box(
                Modifier
                    .size(98.dp)
                    .background(scheme.primary, ExpressiveShapes.Cookie12),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.practice_empty_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.practice_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

/** Pripomienka cvičenia: zapnutie a čas (pýta si povolenie na notifikácie). */
@Composable
private fun ReminderDialog(
    enabled: Boolean,
    minuteOfDay: Int,
    weekly: Boolean,
    onSave: (Boolean, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(enabled) }
    var weeklyOn by remember { mutableStateOf(weekly) }
    val time = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSave(on, time.hour * 60 + time.minute, weeklyOn) else denied = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { DialogIcon(Icons.Outlined.NotificationsActive) },
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { on = !on }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.reminder_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    ExpressiveSwitch(checked = on, onCheckedChange = { on = it })
                }
                Text(
                    stringResource(R.string.reminder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (on) TimeInput(state = time)
                // týždenný súhrn na začiatku týždňa
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { weeklyOn = !weeklyOn }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.weekly_notify_switch), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.weekly_notify_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ExpressiveSwitch(checked = weeklyOn, onCheckedChange = { weeklyOn = it })
                }
                if (denied) {
                    Text(
                        stringResource(R.string.reminder_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minute = time.hour * 60 + time.minute
                if ((on || weeklyOn) && !PracticeReminder.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33) {
                    permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onSave(on, minute, weeklyOn)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Denný cieľ: posuvník 5 až 180 min a rýchle voľby. */
@Composable
private fun GoalDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    val minutes = ((value / 5f).roundToInt() * 5).coerceIn(5, 180)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { DialogIcon(Icons.Outlined.Flag, ExpressiveShapes.Clover, Accent.Tertiary) },
        title = { Text(stringResource(R.string.practice_goal_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.dur_min, minutes), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.practice_goal_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Slider(value = value, onValueChange = { value = it }, valueRange = 5f..180f)
                Spacer(Modifier.height(4.dp))
                ConnectedChoice(
                    options = listOf(15, 30, 45, 60),
                    selected = minutes,
                    label = { it.toString() },
                    onSelect = { value = it.toFloat() },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(Icons.Outlined.MusicNote, ExpressiveShapes.Cookie12, scheme.primaryContainer, scheme.onPrimaryContainer, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(piece.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (piece.composer.isNotBlank()) {
                        Text(piece.composer, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.Schedule, stringResource(R.string.practice_total), formatDuration(total), ExpressiveShapes.Squircle, Accent.Secondary, Modifier.weight(1f))
                StatCard(Icons.Outlined.CalendarMonth, stringResource(R.string.practice_sessions), sessions.size.toString(), ExpressiveShapes.Cookie, Accent.Primary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    Icons.Outlined.Today,
                    stringResource(R.string.practice_last),
                    last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(dateFormat) } ?: "-",
                    ExpressiveShapes.Clover,
                    Accent.Tertiary,
                    Modifier.weight(1f),
                )
                StatCard(Icons.Outlined.Timer, stringResource(R.string.practice_avg), formatDuration(avg), ExpressiveShapes.Cookie12, Accent.Primary, Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.practice_last8w), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                            val h = size.height * v / max
                            if (h > 0) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
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
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.Outlined.MusicNote, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.practice_open_piece), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
