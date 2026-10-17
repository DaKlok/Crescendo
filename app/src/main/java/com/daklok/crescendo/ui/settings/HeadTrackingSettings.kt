package com.daklok.crescendo.ui.settings

import androidx.compose.material.icons.outlined.Piano
import com.daklok.crescendo.ui.common.isCompactWidth
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Gesture
import com.daklok.crescendo.data.LanguageManager
import com.daklok.crescendo.data.AppLanguage
import androidx.compose.material.icons.outlined.Language
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import com.daklok.crescendo.ui.common.pressScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.CameraChoice
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.ThemeMode
import com.daklok.crescendo.data.ToolbarPosition
import com.daklok.crescendo.data.backup.BackupManager
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderZip
import com.daklok.crescendo.ui.common.AppMark
import com.daklok.crescendo.ui.common.EnhancedSlider
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.SettingSwitchRow
import com.daklok.crescendo.ui.common.SettingsCard
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.layout.ColumnScope

/**
 * Nastavenia otáčania hlavou (gestá, citlivosť, kamera…). Rovnaký obsah sa zobrazuje
 * v Nastaveniach aplikácie aj priamo v čítačke – oba miesta čítajú a zapisujú to isté nastavenie,
 * takže sú vždy zosynchronizované.
 */
@Composable
fun ColumnScope.HeadTrackingSettingsContent(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingSwitchRow(
        icon = Icons.Outlined.Face,
        title = stringResource(R.string.head_tracking_switch),
        subtitle = stringResource(R.string.head_tracking_desc),
        checked = settings.headTrackingEnabled,
        onChange = { v -> update { it.copy(headTrackingEnabled = v) } },
    )
    // pri vypnutom sledovaní len jemná poznámka – nastavenia ostávajú dostupné
    AnimatedVisibility(
        visible = !settings.headTrackingEnabled,
        enter = expandVertically(Motion.bouncy()) + fadeIn(tween(250)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(200)),
    ) {
        Text(
            stringResource(R.string.tracking_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 4.dp),
        )
    }
    Divider()
    GestureMappingEditor(settings = settings, update = { t -> update(t) })
    Divider()
    MoreSettings(settings = settings, update = update)
}

/** Zbaliteľná časť s menej používanými nastaveniami. */
@Composable
private fun MoreSettings(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.bouncy(), label = "chevron")
    val haptic = rememberHaptic()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptic(HapticFeedbackType.TextHandleMove)
                open = !open
            }
            .padding(vertical = 8.dp),
    ) {
        Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.more_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.more_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ExpandMore,
            null,
            Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            RowLabel(Icons.Outlined.PhotoCamera, stringResource(R.string.camera))
            Segmented(
                options = CameraChoice.entries,
                selected = settings.camera,
                label = { it.labelRes },
                onSelect = { c -> update { it.copy(camera = c) } },
            )
            Spacer(Modifier.height(12.dp))
            EnhancedSlider(
                label = stringResource(R.string.hold_time),
                icon = Icons.Outlined.HourglassBottom,
                value = settings.holdTimeSec,
                range = 0f..1.5f,
                defaultValue = 0.4f,
                format = { String.format(Locale.US, "%.1f s", it) },
                onValueChange = { v -> update { it.copy(holdTimeSec = v) } },
            )
            EnhancedSlider(
                label = stringResource(R.string.cooldown),
                icon = Icons.Outlined.Timer,
                value = settings.cooldownSec,
                range = 0.5f..3f,
                defaultValue = 1.2f,
                format = { String.format(Locale.US, "%.1f s", it) },
                onValueChange = { v -> update { it.copy(cooldownSec = v) } },
            )
            SettingSwitchRow(
                icon = Icons.Outlined.CameraFront,
                title = stringResource(R.string.camera_preview),
                subtitle = stringResource(R.string.camera_preview_desc),
                checked = settings.showCameraPreview,
                onChange = { v -> update { it.copy(showCameraPreview = v) } },
            )
            SettingSwitchRow(
                icon = Icons.Outlined.Vibration,
                title = stringResource(R.string.turn_haptic),
                subtitle = stringResource(R.string.turn_haptic_desc),
                checked = settings.turnHaptic,
                onChange = { v -> update { it.copy(turnHaptic = v) } },
            )
            Spacer(Modifier.height(8.dp))
            val calibrated = settings.yawOffset != 0f || settings.rollOffset != 0f || settings.pitchOffset != 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CenterFocusStrong,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.calibration),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(if (calibrated) R.string.calibration_set else R.string.calibration_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = calibrated) {
                    TextButton(onClick = {
                        update { it.copy(yawOffset = 0f, rollOffset = 0f, pitchOffset = 0f) }
                    }) { Text(stringResource(R.string.calibration_reset)) }
                }
            }
        }
    }
}

/** Spodný panel s nastaveniami gest, otváraný priamo z čítačky. */
@Composable
fun GestureSettingsSheet(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.head_tracking),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isCompactWidth()) 12.dp else 24.dp)
                .padding(bottom = 40.dp)
                .navigationBarsPadding(),
        ) {
            SettingsCard {
                HeadTrackingSettingsContent(settings = settings, update = update)
            }
            Spacer(Modifier.height(16.dp))
            // pedále – tiež otáčajú strany, preto sú aj tu
            PedalSettingsContent(settings = settings, update = update)
        }
    }
}
