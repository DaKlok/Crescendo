package com.daklok.crescendo.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.widget.PracticeWidget
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Denná pripomienka cvičenia: v zvolenom čase, ak ešte nie je splnený denný cieľ
 * („Ešte 12 min“, „Nepreruš sériu 5 dní“). Nastavenie je v SharedPreferences „practice“,
 * takže je v zálohe aj v synchronizácii.
 */
object PracticeReminder {
    private const val PREFS = "practice"
    private const val WORK = "crescendo-practice-reminder"
    const val CHANNEL = "practice_reminder"
    private const val NOTIFICATION_ID = 4101
    const val DEFAULT_MINUTE = 18 * 60

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("reminder_on", false)

    /** Čas pripomienky – minúty od polnoci. */
    fun minuteOfDay(c: Context): Int = prefs(c).getInt("reminder_min", DEFAULT_MINUTE)

    fun set(c: Context, on: Boolean, minuteOfDay: Int) {
        prefs(c).edit().putBoolean("reminder_on", on).putInt("reminder_min", minuteOfDay.coerceIn(0, 24 * 60 - 1)).commit()
        schedule(c, replace = true)
    }

    /** Naplánuje najbližšiu pripomienku (alebo zruší, ak je vypnutá). */
    fun schedule(c: Context, replace: Boolean) {
        val wm = WorkManager.getInstance(c)
        if (!enabled(c)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val m = minuteOfDay(c)
        var next = now.toLocalDate().atTime(LocalTime.of(m / 60, m % 60))
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<PracticeReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    /** Pri štarte aplikácie (napr. po obnovení zálohy) – nič nemení, ak už je naplánovaná. */
    fun ensure(c: Context) {
        runCatching { schedule(c, replace = false) }
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Dnešné minúty, cieľ a séria dní. */
    suspend fun status(app: CrescendoApp): Triple<Int, Int, Int> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val since = today.minusDays(400).atStartOfDay(zone).toInstant().toEpochMilli()
        val perDay = app.practice.since(since)
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } }
        var streak = 0
        var d = if ((perDay[today] ?: 0L) > 0) today else today.minusDays(1)
        while ((perDay[d] ?: 0L) > 0) {
            streak++
            d = d.minusDays(1)
        }
        return Triple(((perDay[today] ?: 0L) / 60).toInt(), PracticeGoal.get(app).coerceAtLeast(1), streak)
    }

    /** Zobrazí pripomienku, ak ešte nie je splnený cieľ. */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun notifyIfNeeded(app: CrescendoApp) {
        if (!enabled(app) || !canNotify(app)) return
        val (todayMin, goal, streak) = status(app)
        if (todayMin >= goal) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, app.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val title: String
        val text: String
        when {
            todayMin == 0 && streak > 0 -> {
                title = app.getString(R.string.reminder_streak_title, app.resources.getQuantityString(R.plurals.days, streak, streak))
                text = app.getString(R.string.reminder_streak_text, goal)
            }
            todayMin == 0 -> {
                title = app.getString(R.string.reminder_start_title)
                text = app.getString(R.string.reminder_start_text, goal)
            }
            else -> {
                title = app.getString(R.string.reminder_left_title, goal - todayMin)
                text = app.getString(R.string.reminder_left_text, todayMin, goal)
            }
        }
        val open = PendingIntent.getActivity(
            app, 3, PracticeWidget.openPracticeIntent(app),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_practice)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n) }
    }
}

/** Spustí sa v čase pripomienky: oznámi (ak treba) a naplánuje ďalší deň. */
class PracticeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CrescendoApp
        runCatching { PracticeReminder.notifyIfNeeded(app) }
        PracticeReminder.schedule(app, replace = true)
        return Result.success()
    }
}
