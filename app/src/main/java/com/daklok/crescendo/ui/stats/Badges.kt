package com.daklok.crescendo.ui.stats

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PracticeSessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Odznaky v denníku cvičenia. Každý odznak má úrovne (napr. 1, 10, 50, 100 hodín) – počítajú sa
 * priamo zo záznamov cvičenia, takže sú aj v zálohe a na každom zariadení rovnaké.
 */

/** Čísla z denníka, z ktorých sa odznaky počítajú. */
class BadgeStats(
    val totalMin: Int,
    val bestStreak: Int,
    val goalDays: Int,
    val pieces: Int,
    val maxDayMin: Int,
    val earlyDays: Int,
    val lateDays: Int,
    val maxPieceDays: Int,
    val weekends: Int,
    val notes: Int,
)

/** Jeden odznak: ikona, názov, popis úrovne („%1$d hodín“) a hranice úrovní. */
class BadgeDef(
    val id: String,
    val icon: ImageVector,
    @StringRes val title: Int,
    /** Popis cieľa úrovne – formátovací reťazec s jedným číslom. */
    @StringRes val goal: Int,
    val tiers: IntArray,
    val value: (BadgeStats) -> Int,
)

val BADGES = listOf(
    BadgeDef("hours", Icons.Outlined.Schedule, R.string.badge_hours, R.string.badge_hours_goal, intArrayOf(1, 10, 50, 100, 250)) { it.totalMin / 60 },
    BadgeDef("streak", Icons.Outlined.LocalFireDepartment, R.string.badge_streak, R.string.badge_streak_goal, intArrayOf(3, 7, 30, 100, 365)) { it.bestStreak },
    BadgeDef("goal", Icons.Outlined.Flag, R.string.badge_goal, R.string.badge_goal_goal, intArrayOf(1, 10, 50, 100, 250)) { it.goalDays },
    BadgeDef("pieces", Icons.Outlined.LibraryMusic, R.string.badge_pieces, R.string.badge_pieces_goal, intArrayOf(3, 10, 25, 50)) { it.pieces },
    BadgeDef("marathon", Icons.Outlined.Timer, R.string.badge_marathon, R.string.badge_marathon_goal, intArrayOf(60, 120, 180)) { it.maxDayMin },
    BadgeDef("loyal", Icons.Outlined.Favorite, R.string.badge_loyal, R.string.badge_loyal_goal, intArrayOf(5, 20, 50)) { it.maxPieceDays },
    BadgeDef("early", Icons.Outlined.WbTwilight, R.string.badge_early, R.string.badge_early_goal, intArrayOf(1, 10, 30)) { it.earlyDays },
    BadgeDef("night", Icons.Outlined.Bedtime, R.string.badge_night, R.string.badge_night_goal, intArrayOf(1, 10, 30)) { it.lateDays },
    BadgeDef("weekend", Icons.Outlined.Weekend, R.string.badge_weekend, R.string.badge_weekend_goal, intArrayOf(1, 5, 20)) { it.weekends },
)

fun BadgeDef.level(s: BadgeStats): Int {
    val v = value(s)
    return tiers.count { v >= it }
}

/** Spočíta čísla pre odznaky zo záznamov cvičenia. */
fun badgeStats(sessions: List<PracticeSessionEntity>, goalMin: Int, bestStreak: Int, zone: ZoneId): BadgeStats {
    fun dayOf(s: PracticeSessionEntity): LocalDate = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
    val perDay = sessions.groupBy { dayOf(it) }.mapValues { (_, l) -> l.sumOf { it.durationSec } / 60 }
    val practiced = perDay.filterValues { it > 0 }.keys
    val hour = { s: PracticeSessionEntity -> Instant.ofEpochMilli(s.startedAt).atZone(zone).hour }
    // víkendy, keď sa cvičilo v sobotu aj v nedeľu
    val weekends = practiced.filter { it.dayOfWeek == DayOfWeek.SATURDAY && it.plusDays(1) in practiced }.size
    val pieceDays = sessions.groupBy { it.pieceId }.values.maxOfOrNull { l -> l.map { dayOf(it) }.distinct().size } ?: 0
    return BadgeStats(
        totalMin = sessions.sumOf { it.durationSec } / 60,
        bestStreak = bestStreak,
        goalDays = perDay.count { it.value >= goalMin.coerceAtLeast(1) },
        pieces = sessions.map { it.pieceId }.distinct().size,
        maxDayMin = perDay.values.maxOrNull() ?: 0,
        earlyDays = sessions.filter { hour(it) < 8 }.map { dayOf(it) }.distinct().size,
        lateDays = sessions.filter { hour(it) >= 22 }.map { dayOf(it) }.distinct().size,
        maxPieceDays = pieceDays,
        weekends = weekends,
        notes = sessions.count { it.note.isNotBlank() },
    )
}

/** Ktoré úrovne používateľ už videl (aby sa nový odznak oslávil len raz). V zálohe („practice“). */
object BadgeSeen {
    private fun prefs(c: Context) = c.getSharedPreferences("practice", Context.MODE_PRIVATE)

    /** null = ešte nikdy neuložené (prvé spustenie – staré odznaky sa neoslavujú). */
    fun get(c: Context): Map<String, Int>? {
        val raw = prefs(c).getString("badges_seen", null) ?: return null
        return raw.split(',').mapNotNull { e ->
            val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toIntOrNull()?.let { k to it }
        }.toMap()
    }

    fun set(c: Context, levels: Map<String, Int>) {
        prefs(c).edit().putString("badges_seen", levels.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }
}

// ───────────────────────── UI ─────────────────────────

/** Sekcia odznakov v denníku: mriežka medailí; ťuk ukáže podrobnosti. Nový odznak sa oslávi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesSection(stats: BadgeStats, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<BadgeDef?>(null) }
    val levels = BADGES.associate { it.id to it.level(stats) }
    val earned = BADGES.count { (levels[it.id] ?: 0) > 0 }

    // oslava nových úrovní (porovnanie s tým, čo už bolo videné)
    var celebrate by remember { mutableStateOf<List<BadgeDef>>(emptyList()) }
    LaunchedEffect(levels) {
        val seen = BadgeSeen.get(context)
        if (seen == null) {
            BadgeSeen.set(context, levels)
        } else {
            val fresh = BADGES.filter { (levels[it.id] ?: 0) > (seen[it.id] ?: 0) }
            if (fresh.isNotEmpty()) celebrate = fresh
            BadgeSeen.set(context, levels)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.badges_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.badges_count, earned, BADGES.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BADGES.forEach { b ->
                    val lvl = levels[b.id] ?: 0
                    Column(
                        Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { detail = b }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgeMedal(b, lvl, progress(b, stats), size = 64.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(b.title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (lvl > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (lvl > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LevelDots(lvl, b.tiers.size)
                    }
                }
            }
        }
    }

    detail?.let { b -> BadgeDialog(b, stats, onDismiss = { detail = null }) }
    if (celebrate.isNotEmpty()) {
        val b = celebrate.first()
        BadgeDialog(b, stats, celebrate = true, onDismiss = { celebrate = celebrate.drop(1) })
    }
}

/** Postup k ďalšej úrovni (0..1). */
private fun progress(b: BadgeDef, s: BadgeStats): Float {
    val lvl = b.level(s)
    if (lvl >= b.tiers.size) return 1f
    val from = if (lvl == 0) 0 else b.tiers[lvl - 1]
    val to = b.tiers[lvl]
    return ((b.value(s) - from).toFloat() / (to - from)).coerceIn(0f, 1f)
}

/** Bodky pod medailou: získané úrovne z celkového počtu. */
@Composable
private fun LevelDots(level: Int, total: Int) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/**
 * Medaila v tvare „cookie“ (Material 3 Expressive): farba podľa úrovne, ikona v strede.
 * Nezískaná je sivá s krúžkom postupu k prvej úrovni.
 */
@Composable
fun BadgeMedal(b: BadgeDef, level: Int, progress: Float, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val (fill, iconColor) = when (level) {
        0 -> scheme.surfaceVariant to scheme.onSurfaceVariant.copy(alpha = 0.55f)
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        3 -> scheme.primaryContainer to scheme.onPrimaryContainer
        4 -> scheme.tertiary to scheme.onTertiary
        else -> scheme.primary to scheme.onPrimary
    }
    val ring = scheme.primary
    val track = scheme.outlineVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(this.size.width, this.size.height) / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            // postup k ďalšej úrovni – tenký krúžok okolo
            val stroke = r * 0.07f
            val ringR = r - stroke / 2f
            drawCircle(track, ringR, c, style = Stroke(stroke))
            if (progress > 0f && level < b.tiers.size) {
                drawArc(
                    ring, -90f, 360f * progress, false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            } else if (level >= b.tiers.size) {
                drawCircle(ring, ringR, c, style = Stroke(stroke))
            }
            // tvar medaily: kruh s 9 jemnými vlnami
            val base = r * 0.74f
            val amp = r * 0.06f
            val path = Path()
            val n = 180
            for (i in 0..n) {
                val t = 2 * PI * i / n
                val rr = base + amp * cos(9 * t).toFloat()
                val x = c.x + rr * cos(t - PI / 2).toFloat()
                val y = c.y + rr * sin(t - PI / 2).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, fill)
        }
        Icon(
            if (level == 0) Icons.Outlined.Lock else b.icon,
            null,
            Modifier.size(size * if (level == 0) 0.3f else 0.38f),
            tint = iconColor,
        )
    }
}

@Composable
private fun BadgeDialog(b: BadgeDef, stats: BadgeStats, celebrate: Boolean = false, onDismiss: () -> Unit) {
    val lvl = b.level(stats)
    val value = b.value(stats)
    val next = b.tiers.getOrNull(lvl)
    val pop = remember { Animatable(if (celebrate) 0.4f else 1f) }
    LaunchedEffect(Unit) {
        if (celebrate) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.scale(pop.value)) { BadgeMedal(b, lvl, progress(b, stats), size = 112.dp) }
        },
        title = {
            Text(
                if (celebrate) stringResource(R.string.badge_new) else stringResource(b.title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (celebrate) {
                    Text(stringResource(b.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    stringResource(R.string.badge_level, lvl, b.tiers.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                // splnené úrovne a ďalší cieľ
                b.tiers.forEachIndexed { i, t ->
                    val done = i < lvl
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(b.goal, t),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (i == lvl) FontWeight.Bold else FontWeight.Normal,
                            color = if (done || i == lvl) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (next != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (value.toFloat() / next).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.badge_progress, value.coerceAtMost(next), next),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.badge_max), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (celebrate) R.string.badge_great else R.string.close)) } },
    )
}
