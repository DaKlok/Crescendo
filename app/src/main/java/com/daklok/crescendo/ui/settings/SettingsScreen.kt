package com.daklok.crescendo.ui.settings

import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.colors
import com.daklok.crescendo.ui.common.decoShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.VerticalSplit
import com.daklok.crescendo.ui.common.AUTHOR_AVATAR_URL
import com.daklok.crescendo.ui.common.RemoteAvatar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.ThemeMode
import com.daklok.crescendo.data.ToolbarPosition
import com.daklok.crescendo.data.backup.BackupManager
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderZip
import com.daklok.crescendo.ui.common.AppMark
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.SettingSwitchRow
import com.daklok.crescendo.ui.common.SettingsCard
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.launch

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
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // nadpis: „Nastavenia“ alebo tlačidlo späť a názov kategórie
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "settingsTitle",
                        modifier = Modifier.weight(1f),
                    ) { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p != SettingsPage.HOME) {
                                TonalIconButton(
                                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    onClick = { page = SettingsPage.HOME },
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            } else {
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                stringResource(p.titleRes),
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TonalIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        onClick = close,
                    )
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
                                Divider()
                                // prvý deň v týždni (denník cvičenia, týždenný súhrn)
                                var weekStart by remember { mutableStateOf(com.daklok.crescendo.data.WeekStart.get(ctx)) }
                                RowLabel(Icons.Outlined.CalendarMonth, stringResource(R.string.week_start))
                                Segmented(
                                    options = com.daklok.crescendo.data.WeekStartChoice.entries,
                                    selected = weekStart,
                                    label = { it.labelRes },
                                    onSelect = { choice ->
                                        weekStart = choice
                                        com.daklok.crescendo.data.WeekStart.set(ctx, choice)
                                    },
                                )
                                if (weekStart == com.daklok.crescendo.data.WeekStartChoice.AUTO) {
                                    Text(
                                        stringResource(
                                            R.string.week_start_device,
                                            com.daklok.crescendo.data.WeekStart.detect()
                                                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    )
                                }
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
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.AutoAwesome,
                                    title = stringResource(R.string.recognize_titles),
                                    subtitle = stringResource(R.string.recognize_titles_desc),
                                    checked = settings.recognizeTitles,
                                    onChange = { v -> update { it.copy(recognizeTitles = v) } },
                                )
                            }
                        }
                        SettingsPage.READER -> {
                            // ── Čítačka ──
                            SettingsCard {
                                SettingSwitchRow(
                                    icon = Icons.Outlined.SwipeVertical,
                                    title = stringResource(R.string.continuous_scroll),
                                    subtitle = stringResource(R.string.continuous_scroll_setting_desc),
                                    checked = settings.continuousScroll,
                                    onChange = { v -> update { it.copy(continuousScroll = v) } },
                                )
                                Divider()
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
                                    icon = Icons.Outlined.VerticalSplit,
                                    title = stringResource(R.string.half_page_turn),
                                    subtitle = stringResource(R.string.half_page_turn_desc),
                                    checked = settings.halfPageTurn,
                                    onChange = { v -> update { it.copy(halfPageTurn = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.Crop,
                                    title = stringResource(R.string.crop_margins),
                                    subtitle = stringResource(R.string.crop_margins_desc),
                                    checked = settings.cropMargins,
                                    onChange = { v -> update { it.copy(cropMargins = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.DarkMode,
                                    title = stringResource(R.string.night_mode),
                                    subtitle = stringResource(R.string.night_mode_desc),
                                    checked = settings.nightMode,
                                    onChange = { v -> update { it.copy(nightMode = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.LightMode,
                                    title = stringResource(R.string.keep_screen_on),
                                    subtitle = stringResource(R.string.keep_screen_on_desc),
                                    checked = settings.keepScreenOn,
                                    onChange = { v -> update { it.copy(keepScreenOn = v) } },
                                )
                                Divider()
                                SettingSwitchRow(
                                    icon = Icons.Outlined.Flag,
                                    title = stringResource(R.string.spots_show_on_page),
                                    subtitle = stringResource(R.string.spots_show_on_page_desc),
                                    checked = settings.showSpots,
                                    onChange = { v -> update { it.copy(showSpots = v) } },
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
                                RowLabel(Icons.AutoMirrored.Outlined.ViewSidebar, stringResource(R.string.toolbar_position_long))
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
                            // ── Klavír a pedále (vlastné karty po sekciách) ──
                            PedalSettingsContent(settings = settings, update = { t -> update(t) })
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
                        SettingsPage.STORAGE -> StorageSettingsContent()
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

/**
 * Kategórie nastavení: hlavná stránka je krátky zoznam, detail sa otvorí ťuknutím.
 * Každá kategória má vlastný výrazný tvar a farbu ikony.
 */
internal enum class SettingsPage(
    @androidx.annotation.StringRes val titleRes: Int,
    val icon: ImageVector,
    val shape: Shape,
    val accent: Accent,
) {
    HOME(R.string.settings, Icons.Outlined.Settings, ExpressiveShapes.Cookie, Accent.Primary),
    APPEARANCE(R.string.appearance_language, Icons.Outlined.Palette, ExpressiveShapes.Cookie12, Accent.Primary),
    READER(R.string.reader, Icons.AutoMirrored.Outlined.MenuBook, ExpressiveShapes.Squircle, Accent.Secondary),
    ANNOTATIONS(R.string.annotations, Icons.Outlined.Draw, ExpressiveShapes.Clover, Accent.Tertiary),
    HEAD(R.string.head_tracking, Icons.Outlined.Face, ExpressiveShapes.Cookie, Accent.Primary),
    PEDALS(R.string.pedals, Icons.Outlined.Piano, ExpressiveShapes.Squircle, Accent.Tertiary),
    BACKUP(R.string.backup_title, Icons.Outlined.CloudUpload, ExpressiveShapes.Cookie12, Accent.Secondary),
    STORAGE(R.string.storage_title, Icons.Outlined.Storage, ExpressiveShapes.Clover, Accent.Primary),
    ABOUT(R.string.about_support, Icons.Outlined.Info, ExpressiveShapes.Cookie, Accent.Tertiary),
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
        ProCard()
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
            // celková veľkosť dát aplikácie (zistí sa na pozadí)
            val storageTotal by androidx.compose.runtime.produceState<Long?>(null) {
                value = runCatching {
                    com.daklok.crescendo.data.StorageManager(context.applicationContext).scan().sumOf { it.bytes }
                }.getOrNull()
            }
            CategoryRow(
                SettingsPage.STORAGE,
                storageTotal?.let { stringResource(R.string.storage_summary, formatBytes(it)) } ?: stringResource(R.string.storage_summary_loading),
                onOpen,
            )
            Divider()
            CategoryRow(SettingsPage.ABOUT, stringResource(R.string.version_line, version), onOpen)
        }
    }
}

/** Karta Crescendo Pro hore v nastaveniach: výrazná pozvánka (zadarmo) alebo stav (Pro). */
@Composable
private fun ProCard() {
    val app = LocalContext.current.applicationContext as com.daklok.crescendo.CrescendoApp
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val white = Color.White
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(32.dp))
            .then(
                if (isPro) Modifier.background(scheme.surfaceContainerHigh)
                else Modifier.background(com.daklok.crescendo.ui.pro.proBrush()),
            )
            // veľký vlnitý kruh v rohu (ozdoba)
            .decoShape(
                shape = ExpressiveShapes.Cookie12,
                color = if (isPro) scheme.primary.copy(alpha = 0.07f) else white.copy(alpha = 0.10f),
                diameter = 150.dp,
                alignment = Alignment.CenterEnd,
                offset = DpOffset(54.dp, 0.dp),
                turn = { 12f },
            )
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) { app.pro.showPaywall() },
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(
                icon = Icons.Filled.Star,
                shape = ExpressiveShapes.Cookie12,
                container = if (isPro) scheme.primaryContainer else white.copy(alpha = 0.22f),
                content = if (isPro) scheme.onPrimaryContainer else white,
                size = 52.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Crescendo Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isPro) scheme.onSurface else white,
                        maxLines = 1,
                    )
                    if (isPro) {
                        Spacer(Modifier.width(8.dp))
                        com.daklok.crescendo.ui.pro.ProBadge()
                    }
                }
                Text(
                    stringResource(if (isPro) R.string.pro_active else R.string.pro_settings_pitch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPro) scheme.onSurfaceVariant else white.copy(alpha = 0.9f),
                )
            }
            if (!isPro) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Surface(shape = RoundedCornerShape(50), color = white) {
                    Text(
                        stringResource(R.string.pro_unlock),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = com.daklok.crescendo.ui.pro.proOnWhite(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Riadok kategórie: ikona vo výraznom tvare (pri stlačení sa pootočí), názov, stav a šípka. */
@Composable
private fun CategoryRow(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, Motion.bouncy(), label = "categoryTurn")
    val c = page.accent.colors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                haptic(HapticFeedbackType.TextHandleMove)
                onOpen(page)
            }
            .padding(vertical = 4.dp),
    ) {
        ShapeIcon(
            icon = page.icon,
            shape = page.shape,
            container = c.container,
            content = c.onContainer,
            size = 44.dp,
            turn = turn,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Výber jednej možnosti ako spojená skupina tlačidiel (Material 3 Expressive). */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    val labels = options.map { stringResource(label(it)) }
    ConnectedChoice(
        options = options,
        selected = selected,
        label = { labels[options.indexOf(it)] },
        onSelect = onSelect,
        height = 44.dp,
    )
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
            AppMark(56.dp)
            Column {
                Text("Crescendo", style = MaterialTheme.typography.headlineSmall)
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
        // licencia zvuku klavíra (CC BY 3.0 vyžaduje uvedenie autora)
        Text(
            stringResource(R.string.piano_samples_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            Icon(Icons.Outlined.Favorite, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.donate), style = MaterialTheme.typography.titleSmall)
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
