package com.daklok.crescendo.ui.settings

import com.daklok.crescendo.ui.common.AUTHOR_AVATAR_URL
import com.daklok.crescendo.ui.common.RemoteAvatar
import com.daklok.crescendo.ui.common.IconBadge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.Piano
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.ui.common.OverlayKind
import com.daklok.crescendo.ui.common.AppOverlay
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

/** Nastavenia ako spodný panel (ako v Bible Lock Screen). */
@Composable
fun SettingsSheet(
    repo: SettingsRepository,
    backup: BackupManager,
    onDismiss: () -> Unit,
) {
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    fun update(t: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(t) }
    }
    val close: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

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
                        .padding(start = if (page == SettingsPage.HOME) 24.dp else 8.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // nadpis: „Nastavenia“ alebo šípka späť + názov kategórie
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "settingsTitle",
                        modifier = Modifier.weight(1f),
                    ) { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p != SettingsPage.HOME) {
                                IconButton(onClick = { page = SettingsPage.HOME }, modifier = Modifier.padding(end = 4.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            }
                            Text(
                                stringResource(p.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(onClick = close) {
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
        // Späť v podstránke = návrat na zoznam kategórií
        BackHandler(enabled = page != SettingsPage.HOME) { page = SettingsPage.HOME }
        // panel siaha len do ~90 % výšky obrazovky (ako v Bible Lock Screen)
        Box(Modifier.fillMaxHeight(0.9f)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != SettingsPage.HOME
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(Motion.gentle()) { dir * it / 4 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { -dir * it / 6 })
                },
                label = "settingsPage",
            ) { p ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (isCompactWidth()) 12.dp else 24.dp)
                        .padding(bottom = 40.dp)
                        .navigationBarsPadding(),
                ) {
                    when (p) {
                        SettingsPage.HOME -> SettingsHome(settings = settings, onOpen = { page = it })
                        SettingsPage.APPEARANCE -> {
                            // ── Jazyk ──
                            SectionHeader(Icons.Outlined.Language, stringResource(R.string.language))
                            SettingsCard {
                                val ctx = LocalContext.current
                                val activity = remember(ctx) {
                                    var c: android.content.Context? = ctx
                                    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                                    c as? android.app.Activity
                                }
                                val currentLanguage = remember { activity?.let { LanguageManager.current(it) } ?: AppLanguage.SYSTEM }
                                Segmented(
                                    options = AppLanguage.entries,
                                    selected = currentLanguage,
                                    label = { it.labelRes },
                                    onSelect = { lang ->
                                        if (lang != currentLanguage && activity != null) {
                                            // najprv ukážeme prechodovú obrazovku, až potom sa aplikácia prestaví
                                            AppOverlay.show(OverlayKind.LANGUAGE, owner = activity)
                                            AppOverlay.markPending(activity, OverlayKind.LANGUAGE)
                                            // nie v rozsahu panela – ten sa počas prechodu skryje a jeho práce by sa zrušili
                                            (activity.application as CrescendoApp).appScope.launch {
                                                kotlinx.coroutines.delay(450)
                                                LanguageManager.set(activity, lang)
                                            }
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            // ── Vzhľad ──
                            SectionHeader(Icons.Outlined.Palette, stringResource(R.string.appearance))
                            SettingsCard {
                                Segmented(
                                    options = ThemeMode.entries,
                                    selected = settings.themeMode,
                                    label = { it.labelRes },
                                    onSelect = { m -> update { it.copy(themeMode = m) } },
                                )
                                Text(
                                    stringResource(R.string.paper_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.AutoAwesome,
                                    title = stringResource(R.string.dynamic_colors),
                                    subtitle = stringResource(R.string.dynamic_colors_desc),
                                    checked = settings.useDynamicColor,
                                    enabled = settings.themeMode != ThemeMode.PAPER,
                                    onChange = { v -> update { it.copy(useDynamicColor = v) } },
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            // ── Všeobecné ──
                            SectionHeader(Icons.Outlined.Tune, stringResource(R.string.general))
                            SettingsCard {
                                SettingSwitchRow(
                                    icon = Icons.Outlined.Vibration,
                                    title = stringResource(R.string.haptics),
                                    subtitle = stringResource(R.string.haptics_desc),
                                    checked = settings.haptics,
                                    onChange = { v -> update { it.copy(haptics = v) } },
                                )
                            }
                        }
                        SettingsPage.READER -> {
                            // ── Čítačka ──
                            SettingsCard {
                                SettingSwitchRow(
                                    icon = Icons.Outlined.AutoStories,
                                    title = stringResource(R.string.two_pages),
                                    subtitle = stringResource(R.string.two_pages_desc),
                                    checked = settings.twoPageSpread,
                                    onChange = { v -> update { it.copy(twoPageSpread = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.TouchApp,
                                    title = stringResource(R.string.tap_to_turn),
                                    subtitle = stringResource(R.string.tap_to_turn_desc),
                                    checked = settings.tapToTurn,
                                    onChange = { v -> update { it.copy(tapToTurn = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                                    title = stringResource(R.string.volume_keys),
                                    subtitle = stringResource(R.string.volume_keys_desc),
                                    checked = settings.volumeKeysTurn,
                                    onChange = { v -> update { it.copy(volumeKeysTurn = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.LightMode,
                                    title = stringResource(R.string.keep_screen_on),
                                    subtitle = stringResource(R.string.keep_screen_on_desc),
                                    checked = settings.keepScreenOn,
                                    onChange = { v -> update { it.copy(keepScreenOn = v) } },
                                )
                            }
                        }
                        SettingsPage.ANNOTATIONS -> {
                            // ── Poznámky ──
                            SettingsCard {
                                SettingSwitchRow(
                                    icon = Icons.Outlined.TouchApp,
                                    title = stringResource(R.string.draw_with_finger),
                                    subtitle = stringResource(R.string.draw_with_finger_desc),
                                    checked = settings.fingerDrawing,
                                    onChange = { v -> update { it.copy(fingerDrawing = v) } },
                                )
                                Divider()
                                RowLabel(Icons.Outlined.ViewSidebar, stringResource(R.string.toolbar_position_long))
                                Segmented(
                                    options = ToolbarPosition.entries,
                                    selected = settings.toolbarPosition,
                                    label = { it.labelRes },
                                    onSelect = { p -> update { it.copy(toolbarPosition = p) } },
                                )
                            }
                        }
                        SettingsPage.HEAD -> {
                            // ── Otáčanie hlavou ──
                            SettingsCard {
                                HeadTrackingSettingsContent(settings = settings, update = { t -> update(t) })
                            }
                        }
                        SettingsPage.PEDALS -> {
                            // ── Pedále ──
                            SettingsCard {
                                PedalSettingsContent(settings = settings, update = { t -> update(t) })
                            }
                        }
                        SettingsPage.BACKUP -> {
                            // ── Záloha ──
                            SectionHeader(Icons.Outlined.CloudUpload, stringResource(R.string.backup_drive))
                            BackupSection(settings = settings, repo = repo, backup = backup)
                            Spacer(Modifier.height(20.dp))
                            // ── Offline záloha ──
                            SectionHeader(Icons.Outlined.FolderZip, stringResource(R.string.backup_file))
                            LocalBackupSection(backup = backup)
                        }
                        SettingsPage.ABOUT -> {
                            // ── Podpora ──
                            SectionHeader(Icons.Outlined.FavoriteBorder, stringResource(R.string.support))
                            SupportCard()
                            Spacer(Modifier.height(20.dp))
                            // ── O aplikácii ──
                            SectionHeader(Icons.Outlined.Info, stringResource(R.string.about))
                            AboutCard()
                        }
                    }
                }
            }
        }
    }
}

/** Kategórie nastavení – hlavná stránka je krátky zoznam, detail sa otvorí ťuknutím. */
internal enum class SettingsPage(@androidx.annotation.StringRes val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.settings, Icons.Outlined.Settings),
    APPEARANCE(R.string.appearance_language, Icons.Outlined.Palette),
    READER(R.string.reader, Icons.AutoMirrored.Outlined.MenuBook),
    ANNOTATIONS(R.string.annotations, Icons.Outlined.Draw),
    HEAD(R.string.head_tracking, Icons.Outlined.Face),
    PEDALS(R.string.pedals, Icons.Outlined.Piano),
    BACKUP(R.string.backup_title, Icons.Outlined.CloudUpload),
    ABOUT(R.string.about_support, Icons.Outlined.Info),
}

/** Hlavná stránka: kategórie v troch kartách, pri každej krátky stav. */
@Composable
private fun SettingsHome(settings: AppSettings, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val language = remember {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.let { LanguageManager.current(it) } ?: AppLanguage.SYSTEM
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val dot = " · "

    val readerSummary = listOfNotNull(
        stringResource(R.string.two_pages).takeIf { settings.twoPageSpread },
        stringResource(R.string.tap_to_turn).takeIf { settings.tapToTurn },
        stringResource(R.string.keep_screen_on).takeIf { settings.keepScreenOn },
        stringResource(R.string.volume_keys).takeIf { settings.volumeKeysTurn },
    ).joinToString(dot).ifEmpty { stringResource(R.string.state_off) }

    val annotationsSummary = listOfNotNull(
        stringResource(R.string.toolbar_on_side, stringResource(settings.toolbarPosition.labelRes)),
        stringResource(R.string.draw_with_finger).takeIf { settings.fingerDrawing },
    ).joinToString(dot)

    val preset = com.daklok.crescendo.data.GesturePreset.entries.firstOrNull { it.map == settings.gestureMap }
    val headSummary = if (settings.headTrackingEnabled) {
        stringResource(R.string.state_on) + dot + stringResource(preset?.labelRes ?: R.string.preset_custom)
    } else stringResource(R.string.state_off)

    val pedalSummary = if (settings.midiEnabled) {
        stringResource(R.string.pedals_summary_midi, settings.midiBtName.ifBlank { "MIDI" })
    } else stringResource(R.string.pedals_summary)

    val backupSummary = when {
        settings.backupEmail.isNotBlank() && settings.lastBackupAt > 0 ->
            stringResource(R.string.last_backup, formatTime(settings.lastBackupAt))
        settings.backupEmail.isNotBlank() -> stringResource(R.string.bk_connected, settings.backupEmail)
        else -> stringResource(R.string.backup_summary_off)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            CategoryRow(SettingsPage.APPEARANCE, stringResource(settings.themeMode.labelRes) + dot + stringResource(language.labelRes), onOpen)
            Divider()
            CategoryRow(SettingsPage.READER, readerSummary, onOpen)
            Divider()
            CategoryRow(SettingsPage.ANNOTATIONS, annotationsSummary, onOpen)
        }
        SettingsCard {
            CategoryRow(SettingsPage.HEAD, headSummary, onOpen)
            Divider()
            CategoryRow(SettingsPage.PEDALS, pedalSummary, onOpen)
        }
        SettingsCard {
            CategoryRow(SettingsPage.BACKUP, backupSummary, onOpen)
            Divider()
            CategoryRow(SettingsPage.ABOUT, stringResource(R.string.version_line, version), onOpen)
        }
    }
}

/** Riadok kategórie: farebná ikona, názov, stav a šípka. */
@Composable
private fun CategoryRow(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                haptic(HapticFeedbackType.TextHandleMove)
                onOpen(page)
            }
            .padding(vertical = 4.dp),
    ) {
        IconBadge(page.icon, active = true, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    val haptic = rememberHaptic()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSelect(option)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(label(option)), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun RowLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp, top = 2.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppMark(48.dp)
            Column {
                Text("Crescendo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.version_line, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Divider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DaKlok/crescendo-sheet-music-reader"))
                        )
                    }
                }
                .padding(vertical = 8.dp),
        ) {
            // profilová fotka autora z GitHubu
            RemoteAvatar(url = AUTHOR_AVATAR_URL, fallback = "D", size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("DaKlok", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.author_line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Prispieť na vývoj (rovnaký Ko-fi odkaz a tlačidlo ako v Bible Lock Screen). */
@Composable
private fun SupportCard() {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    SettingsCard {
        Text(
            stringResource(R.string.support_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                haptic(HapticFeedbackType.LongPress)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/daklok")))
                }
            },
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction, 0.97f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5E5B),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Outlined.Favorite, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.donate), fontWeight = FontWeight.Bold)
        }
    }
}

/** Doplnkové nastavenia gesta – pružne sa rozbalia, keď je gesto zapnuté. */
@Composable
private fun GestureOptions(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
    ) {
        Box(Modifier.padding(start = 32.dp, bottom = 4.dp)) { content() }
    }
}
