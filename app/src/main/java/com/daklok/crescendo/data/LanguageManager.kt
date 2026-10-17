package com.daklok.crescendo.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.daklok.crescendo.R
import java.util.Locale

/** Jazyk aplikácie zvolený v nastaveniach. */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.lang_system),
    ENGLISH("en", R.string.lang_en),
    SLOVAK("sk", R.string.lang_sk);

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag?.lowercase()?.take(2)) {
            "en" -> ENGLISH
            "sk" -> SLOVAK
            else -> SYSTEM
        }
    }
}

/**
 * Prepínanie jazyka v aplikácii.
 * - Android 13+: cez systémový LocaleManager (rovnaké nastavenie ako Nastavenia → Aplikácie → Jazyk).
 * - Staršie verzie: voľbu si pamätáme sami a kontext obalíme zvoleným jazykom.
 */
object LanguageManager {
    private const val PREFS = "crescendo_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (locales == null || locales.isEmpty) AppLanguage.SYSTEM
            else AppLanguage.fromTag(locales[0].language)
        }
        return AppLanguage.fromTag(savedTag(context))
    }

    fun set(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // systém jazyk uloží a aktivitu sám znovu vytvorí
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language.tag)
        } else {
            // staršie Androidy: aplikáciu reštartujeme, aby sa jazyk prejavil všade
            val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
            val component = launch.component ?: return
            activity.startActivity(Intent.makeRestartActivityTask(component))
            Runtime.getRuntime().exit(0)
        }
    }

    /** Android 13+: nastaví systémový jazyk aplikácie podľa uloženej voľby (po obnove zálohy). */
    fun applySaved(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = savedTag(context) ?: return
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** Pre Android 12 a starší: obalí kontext zvoleným jazykom (volá sa v attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = savedTag(base)
        if (tag.isNullOrEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
