package com.daklok.crescendo.ui.catalog

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.CatalogItem
import com.daklok.crescendo.data.CatalogRepository
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.reader.AudioPlayer
import com.daklok.crescendo.ui.reader.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DownloadState { IDLE, LOADING, DONE }

private enum class CatalogSort(val label: Int) {
    COMPOSER(R.string.catalog_sort_composer),
    TITLE(R.string.catalog_sort_title),
    NEWEST(R.string.catalog_sort_newest),
}

/** Možnosť filtra: kľúč v catalog.json, názov, ikona a voliteľný podnadpis (napr. roky obdobia). */
private class FilterOption(val key: String, val label: Int, val icon: ImageVector, val subtitle: Int? = null)

private val CATEGORIES = listOf(
    FilterOption("piano", R.string.cat_piano, Icons.Outlined.Piano),
    FilterOption("keyboard", R.string.cat_keyboard, Icons.Outlined.AccountBalance),
    FilterOption("guitar", R.string.cat_guitar, Icons.Outlined.MusicNote),
    FilterOption("strings", R.string.cat_strings, Icons.Outlined.GraphicEq),
    FilterOption("winds", R.string.cat_winds, Icons.Outlined.Air),
    FilterOption("voice", R.string.cat_voice, Icons.Outlined.Mic),
    FilterOption("ensemble", R.string.cat_ensemble, Icons.Outlined.Groups),
    FilterOption("other", R.string.cat_other, Icons.Outlined.LibraryMusic),
)

private val STYLES = listOf(
    FilterOption("renaissance", R.string.style_renaissance, Icons.Outlined.History, R.string.style_renaissance_years),
    FilterOption("baroque", R.string.style_baroque, Icons.Outlined.History, R.string.style_baroque_years),
    FilterOption("classical", R.string.style_classical, Icons.Outlined.History, R.string.style_classical_years),
    FilterOption("romantic", R.string.style_romantic, Icons.Outlined.History, R.string.style_romantic_years),
    FilterOption("modern", R.string.style_modern, Icons.Outlined.History, R.string.style_modern_years),
    FilterOption("jazz", R.string.style_jazz, Icons.Outlined.MusicNote),
    FilterOption("folk", R.string.style_folk, Icons.Outlined.Groups),
    FilterOption("hymn", R.string.style_hymn, Icons.Outlined.AccountBalance),
    FilterOption("technique", R.string.style_technique, Icons.Outlined.Piano),
)

private fun categoryIcon(key: String): ImageVector = CATEGORIES.firstOrNull { it.key == key }?.icon ?: Icons.Outlined.LibraryMusic

/** Položka s pripraveným textom na hľadanie (počíta sa raz, nie pri každom písmene). */
@Immutable
private class Entry(val item: CatalogItem) {
    val search: String = listOf(item.title, item.titleSk, item.composer, item.opus, item.instrument)
        .joinToString(" ").lowercase()
    val composerSort: String = item.composer.substringAfterLast(' ').lowercase()
}

@Immutable
private class Result(
    val list: List<CatalogItem>,
    val categoryCounts: Map<String, Int>,
    val styleCounts: Map<String, Int>,
)

/** Katalóg voľných nôt – Material 3 Expressive: dlaždice nástrojov, filtre v jednom riadku, rozbaliteľné karty, prehrávač ukážky. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit, onOpenPiece: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val compact = isCompactWidth()
    val gridState = rememberLazyGridState()

    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var style by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(CatalogSort.COMPOSER) }
    var sheet by remember { mutableStateOf<Int?>(null) } // 0 = nástroj, 1 = obdobie
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val states = remember { mutableStateMapOf<String, DownloadState>() }
    val imported = remember { mutableStateMapOf<String, Long>() }
    val dlProgress = remember { mutableStateMapOf<String, Float>() }
    val referenceJobs by app.catalog.referenceJobs.collectAsStateWithLifecycle()

    // ukážka (MIDI → zvuk), jedna naraz
    val player = remember { AudioPlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var previewItem by remember { mutableStateOf<CatalogItem?>(null) }
    var previewLoading by remember { mutableStateOf<String?>(null) }
    var previewProgress by remember { mutableFloatStateOf(-1f) }
    val previewUnavailable = stringResource(R.string.catalog_preview_unavailable)
    val previewOffline = stringResource(R.string.catalog_preview_offline)
    // skladby bez MIDI (zistené pri pokuse) – tlačidlo ▶ sa pri nich skryje
    var noMidi by remember { mutableStateOf(app.catalog.noMidiIds()) }
    val downloadFailed = stringResource(R.string.catalog_error)

    LaunchedEffect(Unit) {
        fun prepare(list: List<CatalogItem>) = list.map { Entry(it) }
        entries = withContext(Dispatchers.Default) { prepare(app.catalog.local()) }
        app.catalog.downloaded(app.library).forEach { (id, pieceId) ->
            imported[id] = pieceId
            states[id] = DownloadState.DONE
        }
        app.catalog.refresh()?.let { fresh -> entries = withContext(Dispatchers.Default) { prepare(fresh) } }
    }
    BackHandler(onBack = onBack)

    val q = query.trim().lowercase()
    val filtering = q.isNotEmpty() || category != null || style != null

    // filtrovanie na pozadí (s krátkym oneskorením pri písaní) – zoznam sa nezasekáva
    val result by produceState(Result(emptyList(), emptyMap(), emptyMap()), entries, q, category, style, sort) {
        if (q.isNotEmpty()) delay(140)
        value = withContext(Dispatchers.Default) {
            val bySearch = if (q.isEmpty()) entries else entries.filter { it.search.contains(q) }
            val catCounts = bySearch.filter { style == null || it.item.style == style }.groupingBy { it.item.category }.eachCount()
            val styleCounts = bySearch.filter { category == null || it.item.category == category }.groupingBy { it.item.style }.eachCount()
            val list = bySearch.filter {
                (category == null || it.item.category == category) && (style == null || it.item.style == style)
            }
            val sorted = when (sort) {
                CatalogSort.COMPOSER -> list.sortedWith(compareBy({ it.composerSort }, { it.item.title.lowercase() }))
                CatalogSort.TITLE -> list.sortedBy { it.item.localizedTitle().lowercase() }
                CatalogSort.NEWEST -> list.sortedByDescending { it.item.added }
            }
            Result(sorted.map { it.item }, catCounts, styleCounts)
        }
    }
    val featured = remember(entries) { entries.filter { it.item.featured }.map { it.item } }

    // pri zmene filtra hore
    LaunchedEffect(category, style, sort) { if (gridState.firstVisibleItemIndex > 0) gridState.scrollToItem(0) }

    fun preview(item: CatalogItem) {
        if (previewItem?.id == item.id && player.file != null) {
            player.toggle()
            return
        }
        if (previewLoading != null) return
        haptic(HapticFeedbackType.TextHandleMove)
        player.release()
        previewItem = null
        previewLoading = item.id
        previewProgress = -1f
        scope.launch {
            val sound = app.catalog.preview(item) { p -> previewProgress = p }
            previewLoading = null
            if (sound == null) {
                noMidi = app.catalog.noMidiIds()
                snackbar.showSnackbar(if (item.id in noMidi) previewUnavailable else previewOffline)
            } else {
                player.load(sound)
                player.play()
                previewItem = item
            }
        }
    }

    fun download(item: CatalogItem) {
        val state = states[item.id] ?: DownloadState.IDLE
        if (state == DownloadState.LOADING) return
        val newId = imported[item.id]
        if (state == DownloadState.DONE && newId != null) {
            onOpenPiece(newId)
            return
        }
        haptic(HapticFeedbackType.LongPress)
        states[item.id] = DownloadState.LOADING
        dlProgress[item.id] = 0f
        scope.launch {
            app.catalog.download(item, app.library, null) { p -> dlProgress[item.id] = p }
                .onSuccess { id ->
                    imported[item.id] = id
                    states[item.id] = DownloadState.DONE
                    // referenčná nahrávka z MIDI – na pozadí, aj keď sa katalóg zatvorí
                    app.appScope.launch { runCatching { app.catalog.attachReference(item, id, app.pieceAudio) } }
                }
                .onFailure {
                    states[item.id] = DownloadState.IDLE
                    snackbar.showSnackbar(downloadFailed)
                }
        }
    }

    val columns = if (compact) 1 else 2
    val tilesPerRow = if (compact) 2 else 4

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                title = {
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.catalog), fontWeight = FontWeight.Bold)
                        AnimatedVisibility(visible = entries.isNotEmpty(), enter = fadeIn()) {
                            Text(
                                stringResource(R.string.catalog_count, entries.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── hľadanie ──
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.catalog_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.close)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                // ── filtre v jednom riadku ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                ) {
                    val cat = CATEGORIES.firstOrNull { it.key == category }
                    val st = STYLES.firstOrNull { it.key == style }
                    FilterPill(
                        icon = cat?.icon ?: Icons.Outlined.Piano,
                        label = cat?.let { stringResource(it.label) } ?: stringResource(R.string.catalog_filter_instrument),
                        active = cat != null,
                        onClick = { sheet = 0 },
                        onClear = { category = null },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    FilterPill(
                        icon = Icons.Outlined.History,
                        label = st?.let { stringResource(it.label) } ?: stringResource(R.string.catalog_filter_period),
                        active = st != null,
                        onClick = { sheet = 1 },
                        onClear = { style = null },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(0.01f))
                    SortButton(sort) { sort = it }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!filtering) {
                            // ── úvod: nástroje, odporúčané ──
                            item(key = "h_cat", span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(Icons.Outlined.Piano, stringResource(R.string.catalog_instruments))
                            }
                            item(key = "tiles", span = { GridItemSpan(maxLineSpan) }) {
                                CategoryTiles(
                                    counts = result.categoryCounts,
                                    perRow = tilesPerRow,
                                    onPick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        category = it
                                    },
                                )
                            }
                            if (featured.isNotEmpty()) {
                                item(key = "h_feat", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionTitle(Icons.Outlined.AutoAwesome, stringResource(R.string.catalog_featured))
                                }
                                items(featured, key = { "f_" + it.id + it.url }) { item ->
                                    PieceCard(
                                        item = item,
                                        hasAudio = item.midi.isNotBlank() && item.id !in noMidi,
                                        state = states[item.id] ?: DownloadState.IDLE,
                                        expanded = expanded == "f_" + item.id,
                                        previewPlaying = previewItem?.id == item.id && player.playing,
                                        previewLoading = previewLoading == item.id,
                                        previewProgress = previewProgress,
                                        downloadProgress = dlProgress[item.id] ?: -1f,
                                        referenceProgress = imported[item.id]?.let { referenceJobs[it] },
                                        onToggle = { expanded = if (expanded == "f_" + item.id) null else "f_" + item.id },
                                        onPreview = { preview(item) },
                                        onDownload = { download(item) },
                                        onComposer = { query = item.composer },
                                    )
                                }
                            }
                            item(key = "h_all", span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(Icons.Outlined.LibraryMusic, stringResource(R.string.catalog_all, entries.size))
                            }
                        } else {
                            item(key = "h_found", span = { GridItemSpan(maxLineSpan) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.catalog_found, result.list.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp),
                                    )
                                    TextButton(onClick = {
                                        query = ""
                                        category = null
                                        style = null
                                    }) { Text(stringResource(R.string.catalog_clear_filters)) }
                                }
                            }
                            if (result.list.isEmpty() && entries.isNotEmpty()) {
                                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyResult() }
                            }
                        }
                        items(result.list, key = { it.id + it.url }) { item ->
                            PieceCard(
                                item = item,
                                hasAudio = item.midi.isNotBlank() && item.id !in noMidi,
                                state = states[item.id] ?: DownloadState.IDLE,
                                expanded = expanded == item.id,
                                previewPlaying = previewItem?.id == item.id && player.playing,
                                previewLoading = previewLoading == item.id,
                                previewProgress = previewProgress,
                                downloadProgress = dlProgress[item.id] ?: -1f,
                                referenceProgress = imported[item.id]?.let { referenceJobs[it] },
                                onToggle = { expanded = if (expanded == item.id) null else item.id },
                                onPreview = { preview(item) },
                                onDownload = { download(item) },
                                onComposer = { query = item.composer },
                            )
                        }
                        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { SourceFooter() }
                    }
                }
            }

            // ── plávajúci prehrávač ukážky ──
            AnimatedVisibility(
                visible = previewItem != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                enter = slideInVertically(Motion.bouncy()) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(),
            ) {
                previewItem?.let { item ->
                    MiniPlayer(
                        item = item,
                        player = player,
                        onClose = {
                            player.release()
                            previewItem = null
                        },
                    )
                }
            }
        }
    }

    sheet?.let { which ->
        FilterSheet(
            title = stringResource(if (which == 0) R.string.catalog_filter_instrument else R.string.catalog_filter_period),
            options = if (which == 0) CATEGORIES else STYLES,
            counts = if (which == 0) result.categoryCounts else result.styleCounts,
            selected = if (which == 0) category else style,
            onSelect = { key ->
                haptic(HapticFeedbackType.TextHandleMove)
                if (which == 0) category = key else style = key
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }
}

// ───────────────────────── Časti obrazovky ─────────────────────────

@Composable
private fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/** Filter ako „pilulka“: neaktívny ukazuje názov filtra, aktívny vybranú hodnotu a krížik. */
@Composable
private fun FilterPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (active) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "pill")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = if (active) scheme.onSecondaryContainer else scheme.onSurface,
        interactionSource = interaction,
        modifier = modifier
            .height(40.dp)
            .pressScale(interaction),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = if (active) 4.dp else 10.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (active) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.close), Modifier.size(16.dp))
                }
            } else {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SortButton(sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.catalog_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            CatalogSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.label)) },
                    leadingIcon = {
                        if (s == sort) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onSort(s)
                    },
                )
            }
        }
    }
}

/** Dlaždice nástrojov na úvode – veľké, s počtom skladieb; po riadkoch, bez posúvania do strán. */
@Composable
private fun CategoryTiles(counts: Map<String, Int>, perRow: Int, onPick: (String) -> Unit) {
    val options = CATEGORIES.filter { (counts[it.key] ?: 0) > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { opt ->
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { onPick(opt.key) },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        interactionSource = interaction,
                        modifier = Modifier
                            .weight(1f)
                            .pressScale(interaction),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(opt.icon, active = true, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(opt.label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.catalog_count, counts[opt.key] ?: 0),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Výber hodnoty filtra zospodu: mriežka dlaždíc s počtami (bez posúvania do strán). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    title: String,
    options: List<FilterOption>,
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val perRow = if (isCompactWidth()) 2 else 3
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            // „Všetko“ cez celú šírku
            SheetTile(
                icon = Icons.Outlined.LibraryMusic,
                label = stringResource(R.string.cat_all),
                subtitle = null,
                count = null,
                selected = selected == null,
                enabled = true,
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth(),
            )
            options.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { opt ->
                        val count = counts[opt.key] ?: 0
                        SheetTile(
                            icon = opt.icon,
                            label = stringResource(opt.label),
                            subtitle = opt.subtitle?.let { stringResource(it) },
                            count = count,
                            selected = selected == opt.key,
                            enabled = count > 0 || selected == opt.key,
                            onClick = { onSelect(opt.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SheetTile(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    count: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, label = "tile")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) Icons.Outlined.Check else icon,
                null,
                Modifier.size(20.dp),
                tint = if (selected) scheme.primary else if (enabled) scheme.onSurfaceVariant else scheme.outline,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (enabled) androidx.compose.ui.graphics.Color.Unspecified else scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(subtitle, count?.let { stringResource(R.string.catalog_count, it) }).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Karta skladby: ťuk rozbalí detail (nástroj, obdobie, licencia, akcie). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceCard(
    item: CatalogItem,
    hasAudio: Boolean,
    state: DownloadState,
    expanded: Boolean,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    previewProgress: Float,
    downloadProgress: Float,
    referenceProgress: Float?,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onComposer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // stiahnuté skladby (už sú v knižnici) majú inú farbu – na prvý pohľad vidno, čo už máš
    val inLibrary = state == DownloadState.DONE
    val container by animateColorAsState(
        when {
            inLibrary && expanded -> scheme.secondaryContainer
            inLibrary -> scheme.secondaryContainer.copy(alpha = 0.7f).compositeOver(scheme.surfaceContainerHigh)
            expanded -> scheme.surfaceContainerHighest
            else -> scheme.surfaceContainerHigh
        },
        label = "card",
    )
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(if (expanded) 28.dp else 22.dp),
        color = container,
        contentColor = if (inLibrary) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(Motion.snappy()),
    ) {
        Column {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(categoryIcon(item.category), active = state == DownloadState.DONE || item.featured, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.localizedTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.composer,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.difficulty > 0) {
                        Row(Modifier.padding(top = 3.dp)) {
                            repeat(5) { i ->
                                Icon(
                                    Icons.Outlined.Star,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = if (i < item.difficulty) scheme.tertiary else scheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
                if (hasAudio) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (previewLoading) {
                            ProgressCircle(previewProgress, 26.dp)
                        } else {
                            IconButton(onClick = onPreview) {
                                Icon(
                                    if (previewPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    stringResource(R.string.catalog_listen),
                                    tint = if (previewPlaying) scheme.primary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Crossfade(targetState = state, animationSpec = tween(200), label = "dl") { s ->
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        when (s) {
                            DownloadState.LOADING -> ProgressCircle(downloadProgress, 28.dp)
                            DownloadState.DONE -> androidx.compose.material3.FilledIconButton(onClick = onDownload) {
                                Icon(Icons.Outlined.Check, stringResource(R.string.catalog_in_library))
                            }
                            DownloadState.IDLE -> FilledTonalIconButton(onClick = onDownload) {
                                Icon(Icons.Outlined.Download, stringResource(R.string.catalog_download))
                            }
                        }
                    }
                }
            }

            // po stiahnutí: vyrába sa referenčná nahrávka
            AnimatedVisibility(
                visible = referenceProgress != null,
                enter = expandVertically(Motion.snappy()) + fadeIn(),
                exit = shrinkVertically(tween(200)) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    val p = referenceProgress ?: -1f
                    Text(
                        if (p < 0) stringResource(R.string.ref_job_downloading)
                        else stringResource(R.string.ref_job_rendering, (p * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    if (p < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
            }

            // detail
            if (expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip(categoryIcon(item.category), item.instrument.ifBlank { stringResource(R.string.cat_other) })
                        STYLES.firstOrNull { it.key == item.style }?.let { InfoChip(Icons.Outlined.History, stringResource(it.label)) }
                        if (item.opus.isNotBlank() && item.opus.length <= 40) InfoChip(Icons.Outlined.MusicNote, item.opus)
                        if (item.license.isNotBlank()) InfoChip(Icons.Outlined.AutoAwesome, licenseShort(item.license))
                        if (hasAudio) InfoChip(Icons.Outlined.Headphones, stringResource(R.string.catalog_has_audio))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload, shape = RoundedCornerShape(18.dp)) {
                            Icon(if (state == DownloadState.DONE) Icons.Outlined.Check else Icons.Outlined.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (state == DownloadState.DONE) R.string.catalog_open else R.string.catalog_add))
                        }
                        if (hasAudio) {
                            FilledTonalButton(onClick = onPreview, shape = RoundedCornerShape(18.dp)) {
                                Icon(if (previewPlaying) Icons.Outlined.Pause else Icons.Outlined.Headphones, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.catalog_listen_preview))
                            }
                        }
                        TextButton(onClick = onComposer) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.catalog_by_composer))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun licenseShort(license: String): String = when {
    license.startsWith("Public Domain") -> stringResource(R.string.license_pd)
    license.contains("ShareAlike") -> "CC BY-SA"
    license.contains("Attribution") -> "CC BY"
    else -> license
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

/** Kruhový priebeh s percentami; pri neznámom priebehu (−1) sa len točí. */
@Composable
private fun ProgressCircle(progress: Float, size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        if (progress < 0f) {
            CircularProgressIndicator(Modifier.size(size), strokeWidth = 2.5.dp)
        } else {
            CircularProgressIndicator(
                progress = { progress.coerceAtLeast(0.02f) },
                modifier = Modifier.size(size + 8.dp),
                strokeWidth = 3.dp,
            )
            Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyResult() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Outlined.SearchOff, active = false, size = 72.dp)
        Text(stringResource(R.string.catalog_empty), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.catalog_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceFooter() {
    val context = LocalContext.current
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(R.string.catalog_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CatalogRepository.SOURCE_URL))) }
        }) {
            Text(stringResource(R.string.catalog_more))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
        }
    }
}

/** Plávajúci prehrávač ukážky: názov, priebeh, pauza a zavretie. */
@Composable
private fun MiniPlayer(item: CatalogItem, player: AudioPlayer, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Headphones, null, Modifier.size(20.dp), tint = scheme.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.localizedTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.catalog_now_playing, formatClock(player.position), formatClock(player.duration)),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                FilledTonalIconButton(onClick = { player.toggle() }) {
                    Icon(if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(
                progress = { if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                color = scheme.primary,
                trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
        }
    }
}
