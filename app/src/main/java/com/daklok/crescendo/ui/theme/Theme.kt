package com.daklok.crescendo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.daklok.crescendo.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.ThemeMode

// ---- Svetlá schéma: sýta indigová + teplý terciárny akcent ----
private val LightScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF8B4A6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF39071F),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceBright = Color(0xFFFEFBFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF1EDF6),
    surfaceContainerHigh = Color(0xFFEBE7F1),
    surfaceContainerHighest = Color(0xFFE5E1EB),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB0D0),
    onTertiary = Color(0xFF541D3A),
    tertiaryContainer = Color(0xFF6F334F),
    onTertiaryContainer = Color(0xFFFFD8E8),
    // neutrálne, takmer čierne plochy (ako v Bible Lock Screen)
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFC4C4C8),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    // svetlejšia plocha na tmavých kartách (tlačidlá ukážky a pridania v katalógu)
    surfaceBright = Color(0xFF3F3F3F),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF383838),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
)

/**
 * „Papierová“ schéma ladená pre matný NXTPAPER displej: teplé neutrálne plochy,
 * vysoký kontrast textu a tlmené, menej sýte akcenty (bez žiarivých farieb a lesku).
 */
private val PaperScheme = lightColorScheme(
    primary = Color(0xFF2F3E8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCDFF2),
    onPrimaryContainer = Color(0xFF0B1448),
    secondary = Color(0xFF505368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E3EA),
    onSecondaryContainer = Color(0xFF1A1B2A),
    tertiary = Color(0xFF7A4A3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEFDDD4),
    onTertiaryContainer = Color(0xFF2E140A),
    background = Color(0xFFF3F0E8),
    onBackground = Color(0xFF161614),
    surface = Color(0xFFF3F0E8),
    onSurface = Color(0xFF161614),
    surfaceVariant = Color(0xFFE2DFD5),
    onSurfaceVariant = Color(0xFF3D3C37),
    surfaceContainerLowest = Color(0xFFFAF8F2),
    surfaceBright = Color(0xFFFAF8F2),
    surfaceContainerLow = Color(0xFFEFECE3),
    surfaceContainer = Color(0xFFE9E6DC),
    surfaceContainerHigh = Color(0xFFE3E0D5),
    surfaceContainerHighest = Color(0xFFDCD9CD),
    outline = Color(0xFF6B6A62),
    outlineVariant = Color(0xFFC4C1B5),
)

/** Veľké, výrazne zaoblené tvary – charakteristické pre Material 3 Expressive. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun CrescendoTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        else -> false
    }
    val useDynamic = dynamicColor && mode != ThemeMode.PAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme: ColorScheme = when {
        mode == ThemeMode.PAPER -> PaperScheme
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = CrescendoTypography,
        content = content,
    )
}

/** Či je aktuálne zvolená tmavá schéma (pre farbu systémových líšt). */
@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    else -> false
}

// ───────────────────────── Písmo ─────────────────────────

/*
 * Google Sans Flex (variabilné písmo, OFL) – rovnaké ako vo widgete. Jedno písmo, tri „hlasy“:
 *  - Expressive: nadpisy – ťažké, zaoblené koncovky (os ROND), veľká optická veľkosť,
 *  - Title: názvy kariet a riadkov – polotučné, mierne zaoblené,
 *  - Text: telo a popisky – pokojné, bez zaoblenia, optická veľkosť pre malý text.
 */
@OptIn(ExperimentalTextApi::class)
private fun gsf(weight: Int, round: Float, opsz: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
        FontVariation.Setting("opsz", opsz),
    ),
)

/** Písma Crescenda (aj na použitie mimo typografie, napr. veľké čísla). */
object CrescendoFonts {
    /** Nadpisy a veľké čísla – ťažké a zaoblené. */
    val Expressive = FontFamily(gsf(600, 100f, 72f), gsf(700, 100f, 72f), gsf(800, 100f, 72f), gsf(900, 100f, 72f))

    /** Názvy (karty, riadky, tlačidlá). */
    val Title = FontFamily(gsf(500, 50f, 24f), gsf(600, 50f, 24f), gsf(700, 50f, 24f), gsf(800, 50f, 24f))

    /** Bežný text. */
    val Text = FontFamily(gsf(300, 0f, 16f), gsf(400, 0f, 16f), gsf(500, 0f, 16f), gsf(600, 20f, 16f), gsf(700, 20f, 16f), gsf(800, 20f, 16f))
}

/** Typografia Material 3 Expressive: výrazné, zaoblené nadpisy, pokojné telo textu. */
private val CrescendoTypography = Typography().run {
    fun TextStyle.ex(w: Int, track: Float) =
        copy(fontFamily = CrescendoFonts.Expressive, fontWeight = FontWeight(w), letterSpacing = track.sp)
    fun TextStyle.ti(w: Int, track: Float = 0f) =
        copy(fontFamily = CrescendoFonts.Title, fontWeight = FontWeight(w), letterSpacing = track.sp)
    fun TextStyle.tx(w: Int, track: Float) =
        copy(fontFamily = CrescendoFonts.Text, fontWeight = FontWeight(w), letterSpacing = track.sp)
    copy(
        displayLarge = displayLarge.ex(800, -1.2f),
        displayMedium = displayMedium.ex(800, -0.9f),
        displaySmall = displaySmall.ex(800, -0.6f),
        headlineLarge = headlineLarge.ex(800, -0.5f),
        headlineMedium = headlineMedium.ex(800, -0.35f),
        headlineSmall = headlineSmall.ex(700, -0.2f),
        titleLarge = titleLarge.ti(700, -0.1f),
        titleMedium = titleMedium.ti(600, 0f),
        titleSmall = titleSmall.ti(600, 0f),
        bodyLarge = bodyLarge.tx(400, 0.1f),
        bodyMedium = bodyMedium.tx(400, 0.1f),
        bodySmall = bodySmall.tx(400, 0.2f),
        labelLarge = labelLarge.tx(600, 0.1f),
        labelMedium = labelMedium.tx(600, 0.3f),
        labelSmall = labelSmall.tx(600, 0.3f),
    )
}
