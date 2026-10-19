package com.daklok.crescendo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.WeekSummary
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.decoShape
import com.daklok.crescendo.ui.common.isCompactWidth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** „6. okt.“ podľa jazyka. */
@Composable
fun shortDate(d: LocalDate): String {
    val locale = Locale.getDefault()
    val f = remember(locale) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    }
    return d.format(f)
}

/**
 * Týždeň v skratke: celkový čas a zmena oproti predošlému týždňu, sedem dní (vyplnené = cvičil si,
 * vlnitý kruh = splnený denný cieľ), séria, najlepší deň a najviac cvičená skladba.
 * V knižnici sa dá zavrieť ([onDismiss]), v denníku cvičenia listovať po týždňoch ([onPrev] / [onNext]).
 */
@Composable
fun WeeklyCard(
    summary: WeekSummary,
    topTitle: String?,
    title: String,
    goalSec: Long,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val compact = isCompactWidth()
    val content: @Composable () -> Unit = {
        Column(
            Modifier
                .decoShape(
                    shape = ExpressiveShapes.Clover,
                    color = scheme.tertiary.copy(alpha = 0.08f),
                    diameter = 190.dp,
                    alignment = Alignment.TopEnd,
                    offset = DpOffset(56.dp, (-64).dp),
                    turn = { 18f },
                )
                .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // hlavička
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(Icons.Outlined.Insights, ExpressiveShapes.Clover, scheme.tertiary, scheme.onTertiary, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.time_range, shortDate(summary.start), shortDate(summary.end)),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onTertiaryContainer.copy(alpha = 0.75f),
                    )
                }
                if (onPrev != null) {
                    IconButton(onClick = onPrev) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.weekly_prev)) }
                }
                if (onNext != null) {
                    IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.weekly_next)) }
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
            }
            // celkový čas a zmena
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    formatDuration(summary.totalSec),
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                summary.changePercent?.let { ch ->
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(50), color = scheme.surface.copy(alpha = 0.6f)) {
                        Row(
                            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (ch >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                null,
                                Modifier.size(18.dp),
                                tint = if (ch >= 0) scheme.tertiary else scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(if (ch >= 0) R.string.weekly_more else R.string.weekly_less, abs(ch)),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // sedem dní
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                summary.perDay.forEachIndexed { i, sec ->
                    val day = summary.start.plusDays(i.toLong())
                    DayDot(
                        label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        practiced = sec > 0,
                        goal = goalSec > 0 && sec >= goalSec,
                        future = day.isAfter(today),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    pluralStringResource(R.plurals.weekly_days, summary.days, summary.days),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // séria, najlepší deň, najviac cvičená skladba
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                if (summary.streak > 0) {
                    InfoLine(
                        Icons.Outlined.LocalFireDepartment,
                        stringResource(R.string.practice_streak) + ": " + pluralStringResource(R.plurals.days, summary.streak, summary.streak),
                    )
                }
                summary.bestDay?.let { d ->
                    InfoLine(
                        Icons.Outlined.Star,
                        stringResource(
                            R.string.weekly_best_day,
                            d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            formatDuration(summary.bestDaySec),
                        ),
                    )
                }
                if (topTitle != null) {
                    InfoLine(Icons.Outlined.MusicNote, stringResource(R.string.weekly_top, topTitle, formatDuration(summary.topPieceSec)))
                }
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        ) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        ) { content() }
    }
}

/** Deň v týždni: plný kruh = cvičil si, vlnitý kruh = splnený cieľ, prázdny = nie, budúci = len obrys. */
@Composable
private fun DayDot(label: String, practiced: Boolean, goal: Boolean, future: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val size = if (isCompactWidth()) 30.dp else 34.dp
    Box(
        Modifier
            .size(size)
            .then(
                when {
                    goal -> Modifier.background(scheme.tertiary, ExpressiveShapes.Cookie)
                    practiced -> Modifier.background(scheme.tertiary.copy(alpha = 0.45f), CircleShape)
                    future -> Modifier.border(1.dp, scheme.onTertiaryContainer.copy(alpha = 0.18f), CircleShape)
                    else -> Modifier.background(scheme.onTertiaryContainer.copy(alpha = 0.08f), CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (practiced) FontWeight.Bold else FontWeight.Normal,
            color = when {
                goal -> scheme.onTertiary
                practiced -> scheme.onTertiaryContainer
                else -> scheme.onTertiaryContainer.copy(alpha = 0.55f)
            },
        )
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
