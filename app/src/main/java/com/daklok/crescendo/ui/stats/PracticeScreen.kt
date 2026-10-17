package com.daklok.crescendo.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

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

/** Denník cvičenia: tento týždeň, séria dní, graf za 7 dní a najviac cvičené skladby. */
@Composable
fun PracticeScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as CrescendoApp
    val sessions by remember { app.practice.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val pieces by remember { app.library.observePieces() }.collectAsStateWithLifecycle(emptyList())
    BackHandler(onBack = onBack)

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val perDay = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .mapValues { (_, list) -> list.sumOf { it.durationSec.toLong() } }
    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekSec = last7.sumOf { perDay[it] ?: 0L }
    val totalSec = sessions.sumOf { it.durationSec.toLong() }
    val streak = run {
        var d = if ((perDay[today] ?: 0L) > 0L) today else today.minusDays(1)
        var n = 0
        while ((perDay[d] ?: 0L) > 0L) {
            n++
            d = d.minusDays(1)
        }
        n
    }
    val titles = pieces.associate { it.id to it.title }
    val top = sessions.groupBy { it.pieceId }
        .mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } }
        .filterKeys { it in titles }
        .entries.sortedByDescending { it.value }
        .take(10)

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
                    }
                    return@Column
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Outlined.CalendarMonth, stringResource(R.string.practice_week), formatDuration(weekSec), Modifier.weight(1f))
                    StatCard(Icons.Outlined.LocalFireDepartment, stringResource(R.string.practice_streak), pluralDays(streak), Modifier.weight(1f))
                    if (!compact) StatCard(Icons.Outlined.Schedule, stringResource(R.string.practice_total), formatDuration(totalSec), Modifier.weight(1f))
                }

                // graf posledných 7 dní
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.practice_last7), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        val maxSec = last7.maxOf { perDay[it] ?: 0L }.coerceAtLeast(60)
                        val grow = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { grow.animateTo(1f, Motion.gentle()) }
                        val barColor = MaterialTheme.colorScheme.primary
                        val todayColor = MaterialTheme.colorScheme.tertiary
                        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        ) {
                            val slot = size.width / 7f
                            val barW = slot * 0.5f
                            last7.forEachIndexed { i, day ->
                                val v = (perDay[day] ?: 0L).toFloat() / maxSec
                                val x = i * slot + (slot - barW) / 2f
                                drawRoundRect(trackColor, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2f))
                                val h = size.height * v * grow.value
                                if (h > 0f) {
                                    drawRoundRect(
                                        if (day == today) todayColor else barColor,
                                        Offset(x, size.height - h),
                                        Size(barW, h),
                                        CornerRadius(barW / 2f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            last7.forEach { day ->
                                Text(
                                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (day == today) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (top.isNotEmpty()) {
                    SectionHeader(Icons.Outlined.Schedule, stringResource(R.string.practice_top))
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val max = top.first().value.coerceAtLeast(1)
                            top.forEach { (id, sec) ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            titles[id] ?: "",
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

@Composable
private fun pluralDays(n: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.days, n, n)

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}
