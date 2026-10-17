package com.daklok.crescendo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.GestureKind
import com.daklok.crescendo.data.GesturePreset
import com.daklok.crescendo.data.GestureTrigger
import com.daklok.crescendo.data.PageAction
import com.daklok.crescendo.data.usesKind
import com.daklok.crescendo.ui.common.EnhancedSlider
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SettingSwitchRow
import com.daklok.crescendo.ui.common.rememberHaptic
import java.util.Locale

private fun GestureKind.icon(): ImageVector = when (this) {
    GestureKind.TURN -> Icons.Outlined.SwapHoriz
    GestureKind.TILT -> Icons.Outlined.Rotate90DegreesCw
    GestureKind.NOD -> Icons.Outlined.SwapVert
    GestureKind.WINK -> Icons.Outlined.Visibility
    GestureKind.SMILE -> Icons.Outlined.SentimentSatisfied
}

/**
 * Priradenie gest: dve skupiny „Ďalšia strana“ a „Predchádzajúca strana“, v každej sa ťukaním
 * vyberú gestá. Jedno gesto môže patriť len k jednej akcii (výber v druhej skupine ho presunie).
 * Navrchu sú rýchle predvoľby, dole citlivosť len pre použité druhy gest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestureMappingEditor(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    val haptic = rememberHaptic()
    val map = settings.gestureMap

    fun setMap(newMap: Map<GestureTrigger, PageAction>) {
        haptic(HapticFeedbackType.TextHandleMove)
        update { it.copy(gestureMap = newMap) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── nadpis ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Gesture, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.gestures), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.gestures_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── rýchle predvoľby ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.quick_setup),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GesturePreset.entries.forEach { preset ->
                    val selected = map == preset.map
                    FilterChip(
                        selected = selected,
                        onClick = { setMap(preset.map) },
                        label = { Text(stringResource(preset.labelRes)) },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = if (selected) {
                            { Icon(Icons.Outlined.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        }

        // ── priradenie ──
        ActionGroup(
            title = stringResource(R.string.next_page),
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
            action = PageAction.NEXT,
            map = map,
            onChange = ::setMap,
        )
        ActionGroup(
            title = stringResource(R.string.prev_page),
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            action = PageAction.PREVIOUS,
            map = map,
            onChange = ::setMap,
        )

        AnimatedVisibility(
            visible = map.values.none { it != PageAction.NONE },
            enter = expandVertically(Motion.bouncy()) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.no_gesture_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ── citlivosť len pre použité gestá ──
        val usesAngles = map.usesKind(GestureKind.TURN) || map.usesKind(GestureKind.TILT) ||
            map.usesKind(GestureKind.NOD) || map.usesKind(GestureKind.WINK)
        AnimatedVisibility(
            visible = usesAngles,
            enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.sensitivity_header), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                KindOptions(map.usesKind(GestureKind.TURN)) {
                    EnhancedSlider(
                        label = stringResource(R.string.sensitivity_turn),
                        icon = Icons.Outlined.Straighten,
                        value = settings.yawThresholdDeg,
                        range = 10f..50f,
                        defaultValue = 22f,
                        step = 1f,
                        format = { "${it.toInt()}°" },
                        onValueChange = { v -> update { it.copy(yawThresholdDeg = v) } },
                    )
                }
                KindOptions(map.usesKind(GestureKind.TILT)) {
                    EnhancedSlider(
                        label = stringResource(R.string.sensitivity_tilt),
                        icon = Icons.Outlined.Straighten,
                        value = settings.tiltThresholdDeg,
                        range = 8f..35f,
                        defaultValue = 16f,
                        step = 1f,
                        format = { "${it.toInt()}°" },
                        onValueChange = { v -> update { it.copy(tiltThresholdDeg = v) } },
                    )
                }
                KindOptions(map.usesKind(GestureKind.NOD)) {
                    EnhancedSlider(
                        label = stringResource(R.string.sensitivity_nod),
                        icon = Icons.Outlined.Straighten,
                        value = settings.nodThresholdDeg,
                        range = 8f..30f,
                        defaultValue = 14f,
                        step = 1f,
                        format = { "${it.toInt()}°" },
                        onValueChange = { v -> update { it.copy(nodThresholdDeg = v) } },
                    )
                }
                KindOptions(map.usesKind(GestureKind.WINK)) {
                    Column {
                        EnhancedSlider(
                            label = stringResource(R.string.wink_hold),
                            icon = Icons.Outlined.HourglassBottom,
                            value = settings.winkHoldSec,
                            range = 0.1f..1f,
                            defaultValue = 0.25f,
                            step = 0.05f,
                            format = { String.format(Locale.US, "%.2f s", it) },
                            onValueChange = { v -> update { it.copy(winkHoldSec = v) } },
                        )
                        SettingSwitchRow(
                            icon = Icons.Outlined.SwapHoriz,
                            title = stringResource(R.string.swap_eyes),
                            subtitle = stringResource(R.string.swap_eyes_desc),
                            checked = settings.swapEyes,
                            onChange = { v -> update { it.copy(swapEyes = v) } },
                        )
                    }
                }
            }
        }
    }
}

/** Skupina čipov pre jednu akciu (ďalej / späť). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionGroup(
    title: String,
    icon: ImageVector,
    action: PageAction,
    map: Map<GestureTrigger, PageAction>,
    onChange: (Map<GestureTrigger, PageAction>) -> Unit,
) {
    val count = map.count { it.value == action }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        null,
                        Modifier.size(16.dp),
                        tint = if (count > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GestureTrigger.entries.forEach { trig ->
                    val current = map[trig] ?: PageAction.NONE
                    val selected = current == action
                    // gesto priradené k druhej akcii – zobrazíme ho jemnejšie
                    val elsewhere = current != PageAction.NONE && !selected
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val m = map.toMutableMap()
                            if (selected) m.remove(trig) else m[trig] = action
                            onChange(m)
                        },
                        label = { Text(stringResource(trig.labelRes)) },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                if (selected) Icons.Outlined.Check else trig.kind.icon(),
                                null,
                                Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        colors = if (elsewhere) FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            containerColor = Color.Transparent,
                        ) else FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KindOptions(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
    ) {
        Box(Modifier.padding(start = 32.dp)) { content() }
    }
}
