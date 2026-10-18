package com.daklok.crescendo.ui.common

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────── Pohyb ─────────────────────────

/** Spoločné pružiny, aby všetky animácie v aplikácii pôsobili jednotne. */
object Motion {
    /** Hravá pružina s jemným dokmitom (rozbalenia, odznaky, bodky). */
    fun <T> bouncy() = spring<T>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)

    /** Pomalšia, mäkká pružina (veľké plochy, prechody obrazoviek). */
    fun <T> gentle() = spring<T>(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)

    /** Rýchla pružina bez dokmitu (posúvanie, priblíženie). */
    fun <T> snappy() = spring<T>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}

/** Pri stlačení sa prvok jemne „vtlačí“ a pružne vráti. */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.bouncy(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ───────────────────────── Haptika ─────────────────────────

/** Či má aplikácia vibrovať (nastavenie „Haptická odozva“). */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Vráti funkciu na krátku vibráciu, ktorá rešpektuje nastavenie haptiky. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptic, enabled) {
        { type -> if (enabled) haptic.performHapticFeedback(type) }
    }
}

// ───────────────────────── Logo ─────────────────────────

/** Malé logo aplikácie (nota na tmavomodrom zaoblenom štvorci). */
@Composable
fun AppMark(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Color(0xFF212681)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

// ───────────────────────── Tlačidlá a odznaky ─────────────────────────

/** Okrúhle tonálne tlačidlo s ikonou (ako ozubené koliesko v Bible Lock Screen). */
@Composable
fun TonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    /** Funkcia je v Pro – v rohu sa ukáže hviezdička. */
    pro: Boolean = false,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
        animationSpec = tween(220),
        label = "tonalContainer",
    )
    val content by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(220),
        label = "tonalContent",
    )
    Box(modifier) {
        FilledIconButton(
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier
                .size(40.dp)
                .pressScale(interaction, 0.88f),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container,
                contentColor = content,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
        if (pro) {
            com.daklok.crescendo.ui.pro.ProDot(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp),
            )
        }
    }
}

/** Ikona v zaoblenom štvorčeku; farebná keď je [active]. */
@Composable
fun IconBadge(
    icon: ImageVector,
    active: Boolean = true,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        animationSpec = tween(260),
        label = "badgeBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(260),
        label = "badgeFg",
    )
    Box(
        modifier = modifier
            .size(size)
            .background(bg, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

// ───────────────────────── Nastavenia ─────────────────────────

@Composable
fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 8.dp, top = 4.dp, start = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Riadok s ikonou, názvom, popisom a prepínačom; ťuk kdekoľvek prepne. */
@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** Nastavenie je v Pro – pri názve sa ukáže štítok PRO. */
    pro: Boolean = false,
) {
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                haptic(HapticFeedbackType.LongPress)
                onChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (pro) {
                    Spacer(Modifier.width(8.dp))
                    com.daklok.crescendo.ui.pro.ProBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                haptic(HapticFeedbackType.LongPress)
                onChange(it)
            },
        )
    }
}

/** Posuvník s ikonou, hodnotou a tlačidlom na návrat k predvolenej hodnote. */
@Composable
fun EnhancedSlider(
    label: String,
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    step: Float = 0.1f,
) {
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    format(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(
                    visible = kotlin.math.abs(value - defaultValue) > step / 2,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    IconButton(
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            onValueChange(defaultValue)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.reset_default),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = {
                val rounded = (Math.round(it / step) * step)
                if (kotlin.math.abs(rounded - value) >= step / 2) {
                    onValueChange(rounded)
                    haptic(HapticFeedbackType.TextHandleMove)
                }
            },
            valueRange = range,
        )
    }
}

// ───────────────────────── Plávajúce notifikácie ─────────────────────────

enum class NotificationType { SUCCESS, INFO, ERROR }

data class AppNotification(val message: String, val type: NotificationType, val id: Long = System.nanoTime())

/** Krátke plávajúce hlásenie dole uprostred (namiesto snackbaru). */
@Stable
class Notifier(private val scope: CoroutineScope) {
    var current by mutableStateOf<AppNotification?>(null)
        private set
    private var job: Job? = null

    fun show(message: String, type: NotificationType = NotificationType.INFO) {
        job?.cancel()
        current = AppNotification(message, type)
        job = scope.launch {
            delay(2600)
            current = null
        }
    }
}

@Composable
fun rememberNotifier(): Notifier {
    val scope = rememberCoroutineScope()
    return remember(scope) { Notifier(scope) }
}

@Composable
fun NotificationHost(notifier: Notifier, modifier: Modifier = Modifier) {
    // posledné hlásenie si pamätáme, aby sa pri odchode animovalo s textom
    val last = remember { mutableStateOf<AppNotification?>(null) }
    notifier.current?.let { last.value = it }

    AnimatedVisibility(
        visible = notifier.current != null,
        modifier = modifier,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = Motion.bouncy(),
        ) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
        exit = fadeOut(tween(300)) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(300, easing = FastOutLinearInEasing),
        ) + scaleOut(targetScale = 0.9f, animationSpec = tween(300)),
    ) {
        last.value?.let { n ->
            val container = when (n.type) {
                NotificationType.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val content = when (n.type) {
                NotificationType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
            val icon = when (n.type) {
                NotificationType.SUCCESS -> Icons.Outlined.Check
                NotificationType.INFO -> Icons.Outlined.Info
                NotificationType.ERROR -> Icons.Outlined.ErrorOutline
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = container,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    Text(n.message, style = MaterialTheme.typography.labelLarge, color = content)
                }
            }
        }
    }
}


/**
 * Úzka obrazovka (mobil na výšku, šírka pod 600 dp). Tablet ostáva bez zmeny –
 * kompaktné rozloženie sa použije len tam, kde sa bežné nezmestí.
 */
@Composable
fun isCompactWidth(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
