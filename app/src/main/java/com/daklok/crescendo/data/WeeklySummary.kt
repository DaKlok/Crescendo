package com.daklok.crescendo.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.widget.PracticeWidget
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Súhrn jedného týždňa cvičenia. */
data class WeekSummary(
    /** Prvý deň týždňa (podľa jazyka: pondelok alebo nedeľa). */
    val start: LocalDate,
    val totalSec: Long,
    /** Dni, keď sa cvičilo, a dni so splneným denným cieľom. */
    val days: Int,
    val goalDays: Int,
    /** Sekundy v každý deň týždňa (7 hodnôt od [start]). */
    val perDay: List<Long>,
    val prevTotalSec: Long,
    val topPieceId: Long?,
    val topPieceSec: Long,
    val bestDay: LocalDate?,
    val bestDaySec: Long,
    /** Séria dní na konci týždňa (alebo dnes, ak týždeň ešte beží). */
    val streak: Int,
) {
    val end: LocalDate get() = start.plusDays(6)

    /** Zmena oproti predošlému týždňu v %, null ak sa vtedy necvičilo. */
    val changePercent: Int?
        get() = if (prevTotalSec <= 0) null else (((totalSec - prevTotalSec) * 100.0) / prevTotalSec).roundToInt()
}

/**
 * Týždenný súhrn: koľko si cvičil, koľko dní, čo najviac a aká je séria. Karta v knižnici sa ukáže
 * raz za týždeň (po zavretí až o týždeň), voliteľne aj ako upozornenie na začiatku týždňa.
 * Nastavenia sú v SharedPreferences „practice“ (záloha aj synchronizácia).
 */
object WeeklySummary {
    private const val PREFS = "practice"
    private const val WORK = "crescendo-weekly-summary"
    const val CHANNEL = "weekly_summary"
    private const val NOTIFICATION_ID = 4102
    private const val NOTIFY_HOUR = 10

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun firstDayOfWeek(): DayOfWeek = WeekStart.current

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))

    /** Súhrn týždňa, ktorý začína [start]. */
    fun compute(
        sessions: List<PracticeSessionEntity>,
        start: LocalDate,
        goalSec: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): WeekSummary {
        val perDay = HashMap<LocalDate, Long>()
        val perPiece = HashMap<Long, Long>()
        val end = start.plusDays(7)
        val prevStart = start.minusDays(7)
        var prev = 0L
        for (s in sessions) {
            val d = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
            perDay[d] = (perDay[d] ?: 0L) + s.durationSec
            if (!d.isBefore(start) && d.isBefore(end)) {
                perPiece[s.pieceId] = (perPiece[s.pieceId] ?: 0L) + s.durationSec
            } else if (!d.isBefore(prevStart) && d.isBefore(start)) {
                prev += s.durationSec
            }
        }
        val week = (0L until 7L).map { start.plusDays(it) }
        val values = week.map { perDay[it] ?: 0L }
        val best = week.maxByOrNull { perDay[it] ?: 0L }?.takeIf { (perDay[it] ?: 0L) > 0L }
        val top = perPiece.maxByOrNull { it.value }
        val until = minOf(end.minusDays(1), today)
        var d = if ((perDay[until] ?: 0L) > 0L) until else until.minusDays(1)
        var streak = 0
        while ((perDay[d] ?: 0L) > 0L && streak < 5000) {
            streak++
            d = d.minusDays(1)
        }
        return WeekSummary(
            start = start,
            totalSec = values.sum(),
            days = values.count { it > 0L },
            goalDays = values.count { it >= goalSec && goalSec > 0 },
            perDay = values,
            prevTotalSec = prev,
            topPieceId = top?.key,
            topPieceSec = top?.value ?: 0L,
            bestDay = best,
            bestDaySec = best?.let { perDay[it] } ?: 0L,
            streak = streak,
        )
    }

    /** Týždeň (epochDay začiatku), ktorého kartu používateľ zavrel. */
    fun dismissedWeek(c: Context): Long = prefs(c).getLong("weekly_dismissed", Long.MIN_VALUE)

    fun dismiss(c: Context, start: LocalDate) {
        prefs(c).edit().putLong("weekly_dismissed", start.toEpochDay()).apply()
    }

    fun notifyEnabled(c: Context): Boolean = prefs(c).getBoolean("weekly_notify", false)

    fun setNotify(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("weekly_notify", on).commit()
        schedule(c, replace = true)
    }

    /** Naplánuje upozornenie na začiatok budúceho týždňa (alebo ho zruší). */
    fun schedule(c: Context, replace: Boolean) {
        val wm = WorkManager.getInstance(c)
        if (!notifyEnabled(c)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = weekStart(now.toLocalDate()).atTime(LocalTime.of(NOTIFY_HOUR, 0))
        while (!next.isAfter(now.plusSeconds(30))) next = next.plusWeeks(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    fun ensure(c: Context) {
        runCatching { schedule(c, replace = false) }
    }

    /** „1 h 20 min“ / „45 min“ mimo Compose. */
    fun durationText(c: Context, sec: Long): String {
        val min = sec / 60
        return when {
            min < 1 -> c.getString(R.string.dur_less_min)
            min < 60 -> c.getString(R.string.dur_min, min.toInt())
            else -> c.getString(R.string.dur_h_min, (min / 60).toInt(), (min % 60).toInt())
        }
    }

    /** Upozornenie so súhrnom minulého týždňa (ak sa vtedy cvičilo). */
    @SuppressLint("MissingPermission")
    suspend fun notifyLastWeek(app: CrescendoApp) {
        if (!notifyEnabled(app) || !PracticeReminder.canNotify(app)) return
        val zone = ZoneId.systemDefault()
        val start = weekStart(LocalDate.now(zone)).minusWeeks(1)
        val since = start.minusDays(400).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = compute(app.practice.since(since), start, PracticeGoal.get(app) * 60L, zone)
        if (s.totalSec <= 0) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, app.getString(R.string.weekly_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val top = s.topPieceId?.let { app.library.getPiece(it)?.title }
        val title = app.getString(R.string.weekly_notif_title, durationText(app, s.totalSec))
        val parts = ArrayList<String>()
        parts.add(app.resources.getQuantityString(R.plurals.weekly_days, s.days, s.days))
        if (top != null) parts.add(app.getString(R.string.weekly_notif_top, top))
        if (s.streak > 1) parts.add(app.getString(R.string.weekly_notif_streak, app.resources.getQuantityString(R.plurals.days, s.streak, s.streak)))
        val text = parts.joinToString(". ") + "."
        val open = PendingIntent.getActivity(
            app, 4, PracticeWidget.openPracticeIntent(app),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_practice)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n) }
    }
}

/** Spustí sa na začiatku týždňa: súhrn minulého týždňa a naplánovanie ďalšieho. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CrescendoApp
        runCatching { WeeklySummary.notifyLastWeek(app) }
        WeeklySummary.schedule(app, replace = true)
        return Result.success()
    }
}
