package com.daklok.crescendo.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Prechod farieb Pro (primárna do terciárnej), rovnaký všade, kde sa Pro ukazuje. Text na ňom je biely,
 * preto v tmavej téme (kde sú primárna a terciárna svetlé) berie tmavšie kontajnerové farby.
 */
@Composable
fun proBrush(): Brush {
    val s = MaterialTheme.colorScheme
    return Brush.linearGradient(
        if (proDark()) listOf(s.primaryContainer, s.tertiaryContainer) else listOf(s.primary, s.tertiary),
    )
}

/** Tmavá téma pre farby Pro: primárna farba je svetlá. */
@Composable
fun proDark(): Boolean = MaterialTheme.colorScheme.primary.luminance() > 0.5f

/** Farba textu na bielom tlačidle v karte Pro (čitateľná vo svetlej aj tmavej téme). */
@Composable
fun proOnWhite(): Color = if (proDark()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary

/** Štítok „PRO“ – pri položkách menu, nastaveniach a funkciách, ktoré sú v Pro. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(proBrush(), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "PRO",
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
        )
    }
}

/** Malá hviezdička Pro v rohu ikony (tlačidlá nástrojov, ikony v lište). */
@Composable
fun ProDot(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(1.5.dp)
            .background(proBrush(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/**
 * Verzia zadarmo: koľko skladieb je v knižnici z limitu (pri tlačidlách importu).
 * Ťuk otvorí okno Pro.
 */
@Composable
fun LibraryQuota(count: Int, limit: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val full = count >= limit
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (full) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (full) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { (count.toFloat() / limit).coerceIn(0f, 1f) },
                modifier = Modifier.size(20.dp),
                strokeWidth = 3.dp,
                color = if (full) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(
                if (full) androidx.compose.ui.res.stringResource(com.daklok.crescendo.R.string.pro_quota_full, limit)
                else androidx.compose.ui.res.stringResource(com.daklok.crescendo.R.string.pro_quota, count.coerceAtMost(limit), limit),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            ProBadge()
        }
    }
}
