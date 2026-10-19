package com.daklok.crescendo.ui.settings

import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.DialogIcon
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.WavyLinearProgress
import androidx.compose.material.icons.outlined.Sync
import com.daklok.crescendo.ui.common.RemoteAvatar
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.ui.common.OverlayKind
import com.daklok.crescendo.ui.common.AppOverlay
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.backup.BackupManager
import com.daklok.crescendo.data.backup.BackupScheduler
import com.daklok.crescendo.data.backup.BackupState
import com.daklok.crescendo.data.backup.RemoteBackup
import com.google.android.gms.auth.api.identity.Identity
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SettingSwitchRow
import com.daklok.crescendo.ui.common.SettingsCard
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Karta „Záloha na Google Drive“ v nastaveniach. */
@Composable
fun BackupSection(
    settings: AppSettings,
    repo: SettingsRepository,
    backup: BackupManager,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val state by backup.state.collectAsStateWithLifecycle()
    val authClient = remember { Identity.getAuthorizationClient(context) }
    val proActive by (context.applicationContext as CrescendoApp).pro.isPro.collectAsStateWithLifecycle()

    var remote by remember { mutableStateOf<RemoteBackup?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    // čo urobiť, keď používateľ dokončí prihlásenie v systémovom okne
    val pending = remember { arrayOfNulls<(String) -> Unit>(1) }
    var lastToken by remember { mutableStateOf<String?>(null) }
    // stav (priebeh/výsledok) zobrazujeme len pri operáciách spustených z tejto karty
    var activeHere by remember { mutableStateOf(false) }

    val connected = settings.backupEmail.isNotBlank()
    val busy = state is BackupState.Working

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pending[0]
        pending[0] = null
        if (res.resultCode != Activity.RESULT_OK || action == null) return@rememberLauncherForActivityResult
        runCatching { authClient.getAuthorizationResultFromIntent(res.data) }
            .getOrNull()?.accessToken?.let { token ->
                lastToken = token
                action(token)
            }
    }

    /** Získa token (ak treba, zobrazí prihlásenie Google) a spustí [action]. */
    fun withToken(action: (String) -> Unit) {
        activeHere = true
        authClient.authorize(backup.authorizationRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent ?: return@addOnSuccessListener
                    pending[0] = action
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    result.accessToken?.let { token ->
                        lastToken = token
                        action(token)
                    }
                }
            }
            .addOnFailureListener { /* napr. bez Google Play služieb – nič nerobíme */ }
    }

    fun refreshRemote() {
        scope.launch {
            val token = backup.silentToken() ?: return@launch
            lastToken = token
            remote = backup.remoteInfo(token)
        }
    }

    LaunchedEffect(connected) {
        backup.resetState()
        if (connected) refreshRemote()
    }

    SettingsCard {
        AnimatedContent(
            targetState = connected,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
            label = "backupConnected",
        ) { isConnected ->
            if (!isConnected) {
                // ── Nepripojené ──
                Column {
                    Text(
                        stringResource(R.string.backup_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lock,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.backup_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            withToken { token -> scope.launch { backup.connect(token); refreshRemote() } }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Login, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_in_google), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ── Pripojené ──
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // profilová fotka Google účtu (doplní sa aj pre staršie pripojenia)
                        LaunchedEffect(settings.backupEmail) {
                            if (settings.backupPhoto.isBlank()) backup.refreshProfile()
                        }
                        RemoteAvatar(
                            url = settings.backupPhoto.ifBlank { null },
                            fallback = settings.backupName.ifBlank { settings.backupEmail },
                            size = 44.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                settings.backupName.ifBlank { settings.backupEmail },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                if (settings.backupName.isNotBlank()) settings.backupEmail else "Google Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { confirmDisconnect = true }, enabled = !busy) { Text(stringResource(R.string.disconnect)) }
                    }

                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )

                    InfoRow(
                        icon = Icons.Outlined.Schedule,
                        text = if (settings.lastBackupAt > 0) stringResource(R.string.last_backup, formatTime(settings.lastBackupAt))
                        else stringResource(R.string.no_backup_yet),
                    )
                    remote?.let { r ->
                        Spacer(Modifier.height(4.dp))
                        InfoRow(
                            icon = Icons.Outlined.CloudDone,
                            text = stringResource(R.string.on_drive, formatSize(r.sizeBytes), formatTime(r.modifiedAt)),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.LongPress)
                                withToken { token ->
                                    scope.launch {
                                        if (backup.backup(token)) remote = backup.remoteInfo(token)
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(Icons.Outlined.CloudUpload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_now), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(Icons.Outlined.CloudDownload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.restore))
                        }
                    }

                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )

                    SettingSwitchRow(
                        icon = Icons.Outlined.Wifi,
                        title = stringResource(R.string.auto_backup),
                        subtitle = stringResource(R.string.auto_backup_desc),
                        checked = settings.autoBackup && proActive,
                        pro = !proActive,
                        onChange = { on ->
                            val pro = (context.applicationContext as CrescendoApp).pro
                            if (!on || pro.allow(com.daklok.crescendo.data.pro.ProFeature.SYNC)) {
                                scope.launch { repo.update { it.copy(autoBackup = on) } }
                                BackupScheduler.setEnabled(context, on)
                            }
                        },
                    )
                    SettingSwitchRow(
                        icon = Icons.Outlined.Sync,
                        title = stringResource(R.string.sync_title),
                        subtitle = stringResource(R.string.sync_desc),
                        checked = settings.syncEnabled && proActive,
                        pro = !proActive,
                        onChange = { on ->
                            val pro = (context.applicationContext as CrescendoApp).pro
                            if (on && !pro.allow(com.daklok.crescendo.data.pro.ProFeature.SYNC)) return@SettingSwitchRow
                            scope.launch {
                                repo.update { it.copy(syncEnabled = on) }
                                if (on) {
                                    val app = context.applicationContext as CrescendoApp
                                    com.daklok.crescendo.data.backup.SyncManager.checkRemote(app)
                                    com.daklok.crescendo.data.backup.SyncManager.scheduleUploadIfChanged(app)
                                }
                            }
                        },
                    )
                }
            }
        }

        // ── Priebeh / výsledok ──
        AnimatedVisibility(
            visible = activeHere && state !is BackupState.Idle,
            enter = expandVertically(Motion.bouncy()) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.padding(top = 14.dp)) {
                when (val s = state) {
                    is BackupState.Working -> {
                        Text(s.label, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        val p = s.progress
                        if (p != null) {
                            WavyLinearProgress(progress = p, moving = true)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                            )
                        }
                    }
                    is BackupState.Done -> InfoRow(Icons.Outlined.CloudDone, s.message, highlight = true)
                    is BackupState.Error -> InfoRow(Icons.Outlined.ErrorOutline, s.message, error = true)
                    BackupState.Idle -> Unit
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            shape = RoundedCornerShape(28.dp),
            icon = { DialogIcon(Icons.Outlined.CloudDownload, ExpressiveShapes.Cookie12, Accent.Secondary) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Text(
                    stringResource(R.string.restore_drive_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    haptic(HapticFeedbackType.LongPress)
                    withToken { token ->
                        // nie v rozsahu panela – ten sa počas prechodu skryje a obnova by sa zrušila
                        (context.applicationContext as CrescendoApp).appScope.launch {
                            AppOverlay.show(OverlayKind.RESTORE)
                            if (!backup.restore(token)) AppOverlay.hide()
                        }
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.disconnect_title)) },
            text = { Text(stringResource(R.string.disconnect_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    remote = null
                    scope.launch { backup.disconnect(lastToken) }
                }) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    highlight: Boolean = false,
    error: Boolean = false,
) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

internal fun formatTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(ms))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%.0f kB", bytes / 1e3)
}

/** Offline záloha do ZIP súboru (rovnako ako v Bible Lock Screen). */
@Composable
fun LocalBackupSection(backup: BackupManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val state by backup.state.collectAsStateWithLifecycle()
    var activeHere by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val busy = state is BackupState.Working

    LaunchedEffect(state) {
        if (state !is BackupState.Working) {
            exporting = false
            importing = false
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            activeHere = true
            exporting = true
            scope.launch { backup.exportToUri(uri) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    SettingsCard {
        FileRow(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.export_file),
            subtitle = stringResource(R.string.export_file_desc),
            buttonLabel = stringResource(R.string.save),
            working = exporting,
            enabled = !busy,
            onClick = {
                haptic(HapticFeedbackType.LongPress)
                val date = java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                saver.launch(context.getString(R.string.backup_file_prefix) + "_$date.zip")
            },
        )
        HorizontalDivider(
            Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        FileRow(
            icon = Icons.Outlined.FileDownload,
            title = stringResource(R.string.import_file),
            subtitle = stringResource(R.string.import_file_desc),
            buttonLabel = stringResource(R.string.choose),
            working = importing,
            enabled = !busy,
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
        )

        AnimatedVisibility(
            visible = activeHere && (state is BackupState.Done || state is BackupState.Error),
            enter = expandVertically(Motion.bouncy()) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                when (val s = state) {
                    is BackupState.Done -> InfoRow(Icons.Outlined.CloudDone, s.message, highlight = true)
                    is BackupState.Error -> InfoRow(Icons.Outlined.ErrorOutline, s.message, error = true)
                    else -> Unit
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            shape = RoundedCornerShape(28.dp),
            icon = { DialogIcon(Icons.Outlined.FileDownload, ExpressiveShapes.Cookie12, Accent.Secondary) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Text(
                    stringResource(R.string.restore_file_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    haptic(HapticFeedbackType.LongPress)
                    activeHere = true
                    importing = true
                    (context.applicationContext as CrescendoApp).appScope.launch {
                        AppOverlay.show(OverlayKind.RESTORE)
                        if (!backup.importFromUri(uri)) AppOverlay.hide()
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    working: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedContent(
            targetState = working,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "fileRowWorking",
        ) { isWorking ->
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
