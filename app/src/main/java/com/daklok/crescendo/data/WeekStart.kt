package com.daklok.crescendo.data

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.text.util.LocalePreferences
import com.daklok.crescendo.R
import java.time.DayOfWeek
import java.time.temporal.WeekFields

/** Voľba prvého dňa v týždni (automaticky = podľa zariadenia). */
enum class WeekStartChoice(@StringRes val labelRes: Int, val day: DayOfWeek?) {
    AUTO(R.string.week_start_auto, null),
    MONDAY(R.string.week_start_monday, DayOfWeek.MONDAY),
    SUNDAY(R.string.week_start_sunday, DayOfWeek.SUNDAY),
}

/**
 * Prvý deň v týždni pre denník cvičenia, týždenný súhrn a grafy. Automaticky sa berie zo zariadenia
 * (regionálne nastavenia Androidu / krajina), nie z jazyka aplikácie, takže anglická aplikácia
 * na Slovensku začína pondelkom. Uložené v SharedPreferences „practice“ (záloha aj synchronizácia).
 */
object WeekStart {
    private const val PREFS = "practice"
    private const val KEY = "week_start"

    /** Aktuálny prvý deň (načíta sa pri štarte aplikácie a pri zmene). */
    @Volatile
    var current: DayOfWeek = detect()
        private set

    fun init(c: Context) {
        current = resolve(get(c))
    }

    fun get(c: Context): WeekStartChoice =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { n -> WeekStartChoice.entries.firstOrNull { it.name == n } } ?: WeekStartChoice.AUTO

    fun set(c: Context, choice: WeekStartChoice) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
        current = resolve(choice)
        // týždenné upozornenie sa presunie na nový začiatok týždňa
        runCatching { WeeklySummary.schedule(c, replace = true) }
    }

    private fun resolve(choice: WeekStartChoice): DayOfWeek = choice.day ?: detect()

    /** Prvý deň podľa zariadenia (Android 14+ regionálne nastavenia, inak krajina systému). */
    fun detect(): DayOfWeek {
        val sys = runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: java.util.Locale.getDefault()
        val pref = runCatching { LocalePreferences.getFirstDayOfWeek(sys, true) }.getOrNull()
        return when (pref) {
            LocalePreferences.FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
            LocalePreferences.FirstDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
            LocalePreferences.FirstDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
            LocalePreferences.FirstDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
            LocalePreferences.FirstDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
            LocalePreferences.FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
            LocalePreferences.FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
            else -> runCatching { WeekFields.of(sys).firstDayOfWeek }.getOrDefault(DayOfWeek.MONDAY)
        }
    }
}
