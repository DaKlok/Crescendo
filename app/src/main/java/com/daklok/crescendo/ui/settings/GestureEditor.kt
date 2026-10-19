package com.daklok.crescendo.ui.settings

import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.ExpressiveShapes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import java.util.Locale

private fun GestureKind.icon(): ImageVector = when (this) {
    GestureKind.TURN -> Icons.Outlined.SwapHoriz
    GestureKind.TILT -> Icons.Outlined.Rotate90DegreesCw
    GestureKind.NOD -> Icons.Outlined.SwapVert
    GestureKind.WINK -> Icons.Outlined.Visibility
    GestureKind.SMILE -> Icons.Outlined.SentimentSatisfied
}

private fun GestureKind.labelRes(): Int = when (this) {
    GestureKind.TURN -> R.string.g_turn
    GestureKind.TILT -> R.string.g_tilt
    GestureKind.NOD -> R.string.g_nod
    GestureKind.WINK -> R.string.g_wink
    GestureKind.SMILE -> R.string.g_smile
}

private fun GesturePreset.icon(): ImageVector = when (this) {
    GesturePreset.TURN -> Icons.Outlined.SwapHoriz
    GesturePreset.TILT -> Icons.Outlined.Rotate90DegreesCw
    GesturePreset.WINK -> Icons.Outlined.Visibility
}

/** „Ďalej: … · Späť: …“ – krátky popis toho, čo mapa robí. */
@Composable
private fun summaryOf(map: Map<GestureTrigger, PageAction>): String {
    val none = stringResource(R.string.gesture_none)
    val next = map.filterValues { it == PageAction.NEXT }.keys.map { stringResource(it.labelRes) }
    val prev = map.filterValues { it == PageAction.PREVIOUS }.keys.map { stringResource(it.labelRes) }
    return stringResource(
        R.string.gesture_summary,
        next.joinToString(", ").ifEmpty { none },
        prev.joinToString(", ").ifEmpty { none },
    )
}

/**
 * Výber spôsobu otáčania strán:
 * 1. zoznam režimov (predvoľby + „Vlastné“) – jedno ťuknutie, pod každým je napísané, čo robí,
 * 2. pri „Vlastné“ sa rozbalí tabuľka gest – pri každom geste sa zvolí Nič / Späť / Ďalej,
 * 3. citlivosť len pre gestá, ktoré sa naozaj používajú.
 */
@Composable
fun GestureMappingEditor(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    val haptic = rememberHaptic()
    val map = settings.gestureMap
    val matchingPreset = GesturePreset.entries.firstOrNull { it.map == map }
    var custom by rememberSaveable { mutableStateOf(matchingPreset == null) }

    fun setMap(newMap: Map<GestureTrigger, PageAction>) {
        haptic(HapticFeedbackType.TextHandleMove)
        update { it.copy(gestureMap = newMap) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowLabel(Icons.Outlined.SwapHoriz, stringResource(R.string.how_to_turn))

        GesturePreset.entries.forEach { preset ->
            ModeTile(
                icon = preset.icon(),
                title = stringResource(preset.labelRes),
                subtitle = summaryOf(preset.map),
                selected = !custom && matchingPreset == preset,
                onClick = {
                    custom = false
                    setMap(preset.map)
                },
            )
        }
        ModeTile(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.preset_custom),
            subtitle = if (custom) summaryOf(map) else stringResource(R.string.preset_custom_desc),
            selected = custom,
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                custom = true
            },
        )

        // ── vlastné priradenie ──
        AnimatedVisibility(
            visible = custom,
            enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
        ) {
            CustomMapping(map = map, onChange = ::setMap)
        }

        // ── citlivosť len pre použité gestá ──
        val usesAngles = map.usesKind(GestureKind.TURN) || map.usesKind(GestureKind.TILT) ||
            map.usesKind(GestureKind.NOD) || map.usesKind(GestureKind.WINK)
        AnimatedVisibility(
            visible = usesAngles,
            enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                RowLabel(Icons.Outlined.Straighten, stringResource(R.string.sensitivity_header))
                KindOptions(map.usesKind(GestureKind.TURN)) {
                    EnhancedSlider(
                        label = stringResource(R.string.sensitivity_turn),
                        icon = Icons.Outlined.SwapHoriz,
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
                        icon = Icons.Outlined.Rotate90DegreesCw,
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
                        icon = Icons.Outlined.SwapVert,
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
                            icon = Icons.Outlined.Visibility,
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

/** Jedna voľba režimu – karta s ikonou, názvom, popisom a značkou pri vybranej. */
@Composable
private fun ModeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.5f),
        tween(220),
        label = "tile",
    )
    val iconBg by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        tween(220),
        label = "tileIcon",
    )
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(), label = "check")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // vybraná predvoľba má ikonu vo vlnitom kruhu
            Box(
                Modifier
                    .size(38.dp)
                    .background(iconBg, if (selected) ExpressiveShapes.Cookie else CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.size(20.dp),
                    tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.CheckCircle,
                null,
                Modifier
                    .size(22.dp)
                    .scale(checkScale),
                tint = scheme.primary,
            )
        }
    }
}

/** Tabuľka gest: pri každom geste prepínač Nič / Späť / Ďalej, zoskupené podľa druhu pohybu. */
@Composable
private fun CustomMapping(
    map: Map<GestureTrigger, PageAction>,
    onChange: (Map<GestureTrigger, PageAction>) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.custom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            GestureKind.entries.forEach { kind ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                ) {
                    Icon(kind.icon(), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(kind.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                GestureTrigger.entries.filter { it.kind == kind }.forEach { trig ->
                    TriggerRow(
                        label = stringResource(trig.labelRes),
                        action = map[trig] ?: PageAction.NONE,
                        onAction = { a ->
                            val m = map.toMutableMap()
                            if (a == PageAction.NONE) m.remove(trig) else m[trig] = a
                            onChange(m)
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = map.values.none { it != PageAction.NONE },
                enter = expandVertically(Motion.bouncy()) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_gesture_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerRow(
    label: String,
    action: PageAction,
    onAction: (PageAction) -> Unit,
) {
    val options = listOf(
        Triple(PageAction.NONE, Icons.Outlined.Block, R.string.gesture_none),
        Triple(PageAction.PREVIOUS, Icons.AutoMirrored.Outlined.ArrowBack, R.string.prev_page),
        Triple(PageAction.NEXT, Icons.AutoMirrored.Outlined.ArrowForward, R.string.next_page),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (action == PageAction.NONE) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ConnectedChoice(
            options = options,
            selected = options.first { it.first == action },
            onSelect = { (a, _, _) -> if (action != a) onAction(a) },
            modifier = Modifier.width(156.dp),
            height = 36.dp,
        ) { (_, icon, desc), _ ->
            Icon(icon, contentDescription = stringResource(desc), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun KindOptions(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)) + scaleIn(initialScale = 0.98f),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)) + scaleOut(targetScale = 0.98f),
    ) {
        content()
    }
}
