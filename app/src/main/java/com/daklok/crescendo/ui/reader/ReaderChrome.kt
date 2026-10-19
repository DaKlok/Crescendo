package com.daklok.crescendo.ui.reader

import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.pro.ProDot

/*
 * Spoločné prvky čítačky v štýle Material 3 Expressive: plávajúce lišty (pilulky), skupiny nástrojov,
 * nástrojové tlačidlá s meniacim sa tvarom a jednotný vzhľad panelov (metronóm, ladička, nahrávky, vizualizér).
 */

/** Farba plávajúcich líšt nad notami (takmer nepriehľadná, aby noty pod ňou nerušili). */
@Composable
fun readerBarColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f)

/** Plávajúca pilulka (horná a dolná lišta čítačky). */
@Composable
fun FloatingBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = readerBarColor(),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        content = content,
    )
}

/**
 * Nástrojové tlačidlo v lište: bez pozadia, zapnuté má farebný podklad a tvar sa zmení z kruhu
 * na zaoblený štvorec (pri stlačení ešte o kúsok viac). [pro] = v rohu hviezdička Pro.
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    pro: Boolean = false,
    size: Dp = 40.dp,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) scheme.primaryContainer else Color.Transparent, tween(200), label = "toolBg")
    val fg by animateColorAsState(if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant, tween(200), label = "toolFg")
    val corner by animateIntAsState(
        when {
            pressed -> 24
            active -> 30
            else -> 50
        },
        Motion.bouncy(),
        label = "toolShape",
    )
    Box(modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .pressScale(interaction, 0.9f)
                .clip(RoundedCornerShape(percent = corner))
                .background(bg)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) fg else fg.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp),
            )
        }
        if (pro) {
            ProDot(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp),
            )
        }
    }
}

/** Skupina súvisiacich nástrojov na spoločnom jemnom podklade (spojené tlačidlá). */
@Composable
fun ToolGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Okrúhle tlačidlo zavrieť v hlavičke panela. */
@Composable
fun PanelCloseButton(onClose: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .pressScale(interaction, 0.88f)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button) {
                haptic(HapticFeedbackType.TextHandleMove)
                onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(R.string.close),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Plávajúci panel nástroja: výrazný tvar s ikonou, veľký názov, voliteľné akcie a zavrieť.
 * Každý nástroj má vlastný tvar a farbu (metronóm vlnitý kruh, ladička štvorlístok...).
 */
@Composable
fun ToolPanel(
    title: String,
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bodyPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
    spacing: Dp = 10.dp,
    /** Drobný text pod názvom (napr. „Pamätá sa pre túto skladbu“). */
    subtitle: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeIcon(icon = icon, shape = shape, container = container, content = content, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
                Spacer(Modifier.width(4.dp))
                PanelCloseButton(onClose)
            }
            Column(
                Modifier.padding(bodyPadding),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = body,
            )
        }
    }
}

/** Hlavné tlačidlo lišty: režim hrania (celá obrazovka) ako výrazný zaoblený štvorec. */
@Composable
fun StageButton(immersive: Boolean, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(if (pressed) 24 else 34, Motion.bouncy(), label = "stageShape")
    Surface(
        onClick = {
            haptic(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(percent = corner),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction,
        modifier = Modifier
            .size(48.dp)
            .pressScale(interaction, 0.92f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                contentDescription = stringResource(R.string.fullscreen),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Číslo strany v dolnej lište: veľké číslo a menší celkový počet; pri zmene sa číslo prevalí. */
@Composable
fun PageBadge(label: String, total: Int, description: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            Modifier
                .height(48.dp)
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically(Motion.bouncy()) { it / 2 } +
                        androidx.compose.animation.fadeIn(tween(160))) togetherWith
                        (androidx.compose.animation.slideOutVertically(tween(160)) { -it / 2 } +
                            androidx.compose.animation.fadeOut(tween(120)))
                },
                label = "pageLabel",
            ) { l ->
                Text(l, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            }
            Text(
                "/$total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            )
        }
    }
}
