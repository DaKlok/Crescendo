package com.daklok.crescendo.widget

import android.content.Context
import android.os.Build

/** Farby widgetu: Material You (podľa tapety, Android 12+) alebo farby aplikácie. */
object WidgetPalettes {

    /** Farby aplikácie (rovnaké ako téma Crescenda). */
    fun app(dark: Boolean): ArtPalette = if (!dark) ArtPalette(
        background = 0xFFF1EDF6.toInt(),
        onBackground = 0xFF1B1B1F.toInt(),
        onBackgroundMuted = 0xFF46464F.toInt(),
        heroContainer = 0xFFDDE1FF.toInt(),
        onHeroContainer = 0xFF00105C.toInt(),
        ring = 0xFF4355B9.toInt(),
        ringTrack = 0xFFBFC6FF.toInt(),
        ringDone = 0xFF8B4A6B.toInt(),
        chip = 0xFFFFD8E8.toInt(),
        onChip = 0xFF39071F.toInt(),
        streak = 0xFF8B4A6B.toInt(),
        cta = 0xFF4355B9.toInt(),
        onCta = 0xFFFFFFFF.toInt(),
    ) else ArtPalette(
        background = 0xFF1E1E22.toInt(),
        onBackground = 0xFFEDEDED.toInt(),
        onBackgroundMuted = 0xFFC4C4C8.toInt(),
        heroContainer = 0xFF293CA0.toInt(),
        onHeroContainer = 0xFFDDE1FF.toInt(),
        ring = 0xFFB9C3FF.toInt(),
        ringTrack = 0xFF3E4EA8.toInt(),
        ringDone = 0xFFFFB0D0.toInt(),
        chip = 0xFF6F334F.toInt(),
        onChip = 0xFFFFD8E8.toInt(),
        streak = 0xFFFFB0D0.toInt(),
        cta = 0xFFB9C3FF.toInt(),
        onCta = 0xFF08218A.toInt(),
    )

    /** Material You – tóny systémovej palety (ako farebné role Material 3). */
    @android.annotation.SuppressLint("NewApi")
    fun dynamic(context: Context, dark: Boolean): ArtPalette? {
        if (Build.VERSION.SDK_INT < 31) return null
        fun c(id: Int) = context.getColor(id)
        return runCatching {
            if (!dark) ArtPalette(
                background = c(android.R.color.system_neutral1_50),
                onBackground = c(android.R.color.system_neutral1_900),
                onBackgroundMuted = c(android.R.color.system_neutral2_700),
                heroContainer = c(android.R.color.system_accent1_100),
                onHeroContainer = c(android.R.color.system_accent1_900),
                ring = c(android.R.color.system_accent1_600),
                ringTrack = c(android.R.color.system_accent1_200),
                ringDone = c(android.R.color.system_accent3_600),
                chip = c(android.R.color.system_accent3_100),
                onChip = c(android.R.color.system_accent3_900),
                streak = c(android.R.color.system_accent3_600),
                cta = c(android.R.color.system_accent1_600),
                onCta = c(android.R.color.system_accent1_0),
            ) else ArtPalette(
                background = c(android.R.color.system_neutral1_900),
                onBackground = c(android.R.color.system_neutral1_100),
                onBackgroundMuted = c(android.R.color.system_neutral2_200),
                heroContainer = c(android.R.color.system_accent1_700),
                onHeroContainer = c(android.R.color.system_accent1_100),
                ring = c(android.R.color.system_accent1_200),
                ringTrack = c(android.R.color.system_accent1_600),
                ringDone = c(android.R.color.system_accent3_200),
                chip = c(android.R.color.system_accent3_700),
                onChip = c(android.R.color.system_accent3_100),
                streak = c(android.R.color.system_accent3_200),
                cta = c(android.R.color.system_accent1_200),
                onCta = c(android.R.color.system_accent1_800),
            )
        }.getOrNull()
    }
}
