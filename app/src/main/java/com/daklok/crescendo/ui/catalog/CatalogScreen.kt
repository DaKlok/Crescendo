package com.daklok.crescendo.ui.catalog

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nightlife
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.CatalogItem
import com.daklok.crescendo.data.CatalogRepository
import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.ScreenTopBar
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.WavyLinearProgress
import com.daklok.crescendo.ui.common.WavyRing
import com.daklok.crescendo.ui.common.bleed
import com.daklok.crescendo.ui.common.decoShape
import androidx.compose.ui.unit.DpOffset
import com.daklok.crescendo.ui.common.colors
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.reader.AudioPlayer
import com.daklok.crescendo.ui.reader.PanelCloseButton
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

/**
 * Možnosť filtra: kľúč v catalog.json, názov, ikona, voliteľný podnadpis (roky obdobia)
 * a vlastný tvar a farba. Každý nástroj má svoj tvar, rovnaký v dlaždiciach, na kartách aj vo filtri.
 */
private class FilterOption(
    val key: String,
    val label: Int,
    val icon: ImageVector,
    val shape: Shape,
    val accent: Accent,
    val subtitle: Int? = null,
)

private val CATEGORIES = listOf(
    FilterOption("piano", R.string.cat_piano, Icons.Outlined.Piano, ExpressiveShapes.Cookie12, Accent.Primary),
    FilterOption("keyboard", R.string.cat_keyboard, Icons.Outlined.AccountBalance, ExpressiveShapes.Squircle, Accent.Secondary),
    FilterOption("guitar", R.string.cat_guitar, Icons.Outlined.MusicNote, ExpressiveShapes.Clover, Accent.Tertiary),
    FilterOption("strings", R.string.cat_strings, Icons.Outlined.GraphicEq, ExpressiveShapes.Cookie, Accent.Primary),
    FilterOption("winds", R.string.cat_winds, Icons.Outlined.Air, ExpressiveShapes.Cookie, Accent.Secondary),
    FilterOption("voice", R.string.cat_voice, Icons.Outlined.Mic, ExpressiveShapes.Cookie12, Accent.Tertiary),
    FilterOption("ensemble", R.string.cat_ensemble, Icons.Outlined.Groups, ExpressiveShapes.Squircle, Accent.Primary),
    FilterOption("other", R.string.cat_other, Icons.Outlined.LibraryMusic, ExpressiveShapes.Clover, Accent.Secondary),
)

private val STYLES = listOf(
    FilterOption("renaissance", R.string.style_renaissance, Icons.Outlined.HistoryEdu, ExpressiveShapes.Clover, Accent.Tertiary, R.string.style_renaissance_years),
    FilterOption("baroque", R.string.style_baroque, Icons.Outlined.Castle, ExpressiveShapes.Cookie12, Accent.Primary, R.string.style_baroque_years),
    FilterOption("classical", R.string.style_classical, Icons.Outlined.AccountBalance, ExpressiveShapes.Squircle, Accent.Secondary, R.string.style_classical_years),
    FilterOption("romantic", R.string.style_romantic, Icons.Outlined.LocalFlorist, ExpressiveShapes.Cookie, Accent.Tertiary, R.string.style_romantic_years),
    FilterOption("modern", R.string.style_modern, Icons.Outlined.Bolt, ExpressiveShapes.Clover, Accent.Primary, R.string.style_modern_years),
    FilterOption("jazz", R.string.style_jazz, Icons.Outlined.Nightlife, ExpressiveShapes.Cookie12, Accent.Secondary),
    FilterOption("folk", R.string.style_folk, Icons.Outlined.Groups, ExpressiveShapes.Squircle, Accent.Tertiary),
    FilterOption("hymn", R.string.style_hymn, Icons.Outlined.Church, ExpressiveShapes.Cookie, Accent.Primary),
    FilterOption("technique", R.string.style_technique, Icons.Outlined.FitnessCenter, ExpressiveShapes.Clover, Accent.Secondary),
)

private fun categoryOf(key: String): FilterOption = CATEGORIES.firstOrNull { it.key == key } ?: CATEGORIES.last()

/** Farby kariet v pásiku odporúčaných (striedajú sa). */
private val FEATURED_ACCENTS = listOf(Accent.Primary, Accent.Tertiary, Accent.Secondary)

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

/**
 * Katalóg voľných nôt v štýle Material 3 Expressive: hľadanie v pilulke, dlaždice nástrojov (každý nástroj
 * má svoj tvar), pásik odporúčaných skladieb, rozbaliteľné karty a plávajúci prehrávač ukážky s vlnkou.
 */
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
    // detail odporúčanej skladby (spodný panel)
    var detail by remember { mutableStateOf<CatalogItem?>(null) }

    val states = remember { mutableStateMapOf<String, DownloadState>() }
    val imported = remember { mutableStateMapOf<String, Long>() }
    val dlProgress = remember { mutableStateMapOf<String, Float>() }
    val referenceJobs by app.catalog.referenceJobs.collectAsStateWithLifecycle()

    // ukážka (MIDI na zvuk), jedna naraz
    val player = remember { AudioPlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var previewItem by remember { mutableStateOf<CatalogItem?>(null) }
    var previewLoading by remember { mutableStateOf<String?>(null) }
    var previewProgress by remember { mutableFloatStateOf(-1f) }
    val previewUnavailable = stringResource(R.string.catalog_preview_unavailable)
    val previewOffline = stringResource(R.string.catalog_preview_offline)
    // skladby bez MIDI (zistené pri pokuse): tlačidlo ukážky sa pri nich skryje
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

    // filtrovanie na pozadí (s krátkym oneskorením pri písaní), zoznam sa nezasekáva
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
        // verzia zadarmo: najviac 10 skladieb v knižnici
        if (!app.pro.allowNewPieces()) return
        haptic(HapticFeedbackType.LongPress)
        states[item.id] = DownloadState.LOADING
        dlProgress[item.id] = 0f
        scope.launch {
            app.catalog.download(item, app.library, null) { p -> dlProgress[item.id] = p }
                .onSuccess { id ->
                    imported[item.id] = id
                    states[item.id] = DownloadState.DONE
                    // referenčná nahrávka z MIDI: na pozadí, aj keď sa katalóg zatvorí
                    app.appScope.launch { runCatching { app.catalog.attachReference(item, id, app.pieceAudio) } }
                }
                .onFailure {
                    states[item.id] = DownloadState.IDLE
                    snackbar.showSnackbar(downloadFailed)
                }
        }
    }

    fun hasAudio(item: CatalogItem) = item.midi.isNotBlank() && item.id !in noMidi

    val columns = if (compact) 1 else 2
    val tilesPerRow = if (compact) 2 else 4

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.catalog),
                subtitle = if (entries.isNotEmpty()) pluralStringResource(R.plurals.pieces_n, entries.size, entries.size) else null,
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Column(Modifier.fillMaxSize()) {
                val pills: @Composable (Modifier, Boolean) -> Unit = { m, weighted ->
                    FilterPills(
                        category = category,
                        style = style,
                        weighted = weighted,
                        onOpen = { sheet = it },
                        onClearCategory = { category = null },
                        onClearStyle = { style = null },
                        modifier = m,
                    )
                }
                if (compact) {
                    SearchField(
                        query = query,
                        onQuery = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    // filtre a zoradenie v jednom riadku
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    ) {
                        pills(Modifier.weight(1f), true)
                        Spacer(Modifier.width(8.dp))
                        SortButton(sort) { sort = it }
                    }
                } else {
                    // na širokej obrazovke hľadanie, filtre a zoradenie v jednom riadku (šetrí výšku)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                    ) {
                        SearchField(
                            query = query,
                            onQuery = { query = it },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        pills(Modifier, false)
                        Spacer(Modifier.width(8.dp))
                        SortButton(sort) { sort = it }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!filtering) {
                            // úvod: nástroje, odporúčané
                            item(key = "h_cat", span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(Icons.Outlined.Piano, stringResource(R.string.catalog_instruments), top = 0.dp)
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
                                item(key = "featured", span = { GridItemSpan(maxLineSpan) }) {
                                    // pásik až po okraje panela
                                    LazyRow(
                                        modifier = Modifier.bleed(16.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        itemsIndexed(featured, key = { _, it -> "f_" + it.id + it.url }) { i, item ->
                                            FeaturedCard(
                                                item = item,
                                                accent = FEATURED_ACCENTS[i % FEATURED_ACCENTS.size],
                                                hasAudio = hasAudio(item),
                                                state = states[item.id] ?: DownloadState.IDLE,
                                                previewPlaying = previewItem?.id == item.id && player.playing,
                                                previewLoading = previewLoading == item.id,
                                                previewProgress = previewProgress,
                                                downloadProgress = dlProgress[item.id] ?: -1f,
                                                onOpen = {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    detail = item
                                                },
                                                onPreview = { preview(item) },
                                                onDownload = { download(item) },
                                                modifier = Modifier
                                                    .width(if (compact) 268.dp else 304.dp)
                                                    .height(212.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "h_all", span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(Icons.Outlined.LibraryMusic, stringResource(R.string.catalog_all_title), count = entries.size)
                            }
                        } else {
                            item(key = "h_found", span = { GridItemSpan(maxLineSpan) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.catalog_found, result.list.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        query = ""
                                        category = null
                                        style = null
                                    }) {
                                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.catalog_clear_filters))
                                    }
                                }
                            }
                            if (result.list.isEmpty() && entries.isNotEmpty()) {
                                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyResult() }
                            }
                        }
                        items(result.list, key = { it.id + it.url }) { item ->
                            PieceCard(
                                item = item,
                                hasAudio = hasAudio(item),
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

            // plávajúci prehrávač ukážky
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

    detail?.let { item ->
        val state = states[item.id] ?: DownloadState.IDLE
        DetailSheet(
            item = item,
            hasAudio = hasAudio(item),
            state = state,
            previewPlaying = previewItem?.id == item.id && player.playing,
            previewLoading = previewLoading == item.id,
            downloadProgress = dlProgress[item.id] ?: -1f,
            referenceProgress = imported[item.id]?.let { referenceJobs[it] },
            onPreview = { preview(item) },
            onDownload = {
                // otvorenie skladby odíde z katalógu, panel sa zavrie
                if (state == DownloadState.DONE) detail = null
                download(item)
            },
            onComposer = {
                detail = null
                query = item.composer
            },
            onDismiss = { detail = null },
        )
    }
}

// ───────────────────────── Hľadanie a filtre ─────────────────────────

/** Hľadanie ako pilulka (bez okraja, pri písaní sa ukáže okraj vo farbe témy). */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.catalog_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant) },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.close)) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = scheme.primary,
        ),
    )
}

/** Nadpis sekcie: ikona vo farbe témy, výrazný názov a voliteľný počet v bublinke. */
@Composable
private fun SectionTitle(icon: ImageVector, text: String, count: Int? = null, top: Dp = 14.dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = top, bottom = 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Dva filtre (nástroj, obdobie); na úzkej obrazovke sa delia o šírku a dlhý názov sa skráti. */
@Composable
private fun FilterPills(
    category: String?,
    style: String?,
    weighted: Boolean,
    onOpen: (Int) -> Unit,
    onClearCategory: () -> Unit,
    onClearStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cat = CATEGORIES.firstOrNull { it.key == category }
    val st = STYLES.firstOrNull { it.key == style }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill(
            icon = cat?.icon ?: Icons.Outlined.Piano,
            label = cat?.let { stringResource(it.label) } ?: stringResource(R.string.catalog_filter_instrument),
            active = cat != null,
            onClick = { onOpen(0) },
            onClear = onClearCategory,
            modifier = if (weighted) Modifier.weight(1f, fill = false) else Modifier,
        )
        FilterPill(
            icon = st?.icon ?: Icons.Outlined.History,
            label = st?.let { stringResource(it.label) } ?: stringResource(R.string.catalog_filter_period),
            active = st != null,
            onClick = { onOpen(1) },
            onClear = onClearStyle,
            modifier = if (weighted) Modifier.weight(1f, fill = false) else Modifier,
        )
    }
}

/** Filter ako pilulka: neaktívny ukazuje názov filtra, aktívny vybranú hodnotu a krížik. */
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SortButton(sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    val haptic = rememberHaptic()
    Box {
        var open by remember { mutableStateOf(false) }
        TonalIconButton(
            icon = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = stringResource(R.string.catalog_sort),
            highlighted = open,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            CatalogSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.label)) },
                    leadingIcon = {
                        if (s == sort) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        open = false
                        onSort(s)
                    },
                )
            }
        }
    }
}

// ───────────────────────── Úvod: nástroje a odporúčané ─────────────────────────

/** Dlaždice nástrojov na úvode: každý nástroj má svoj tvar a farbu, po riadkoch bez posúvania do strán. */
@Composable
private fun CategoryTiles(counts: Map<String, Int>, perRow: Int, onPick: (String) -> Unit) {
    val options = CATEGORIES.filter { (counts[it.key] ?: 0) > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(perRow).forEach { row ->
            // dlaždice v riadku rovnako vysoké (dlhší názov má dva riadky)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { opt ->
                    CategoryTile(
                        option = opt,
                        count = counts[opt.key] ?: 0,
                        onClick = { onPick(opt.key) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(option: FilterOption, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = option.accent.colors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, Motion.bouncy(), label = "tileTurn")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeIcon(
                icon = option.icon,
                shape = option.shape,
                container = c.container,
                content = c.onContainer,
                size = 44.dp,
                turn = turn,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(option.label),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.pieces_n, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Karta v pásiku odporúčaných: farebná plocha s veľkým tvarom nástroja v rohu, názov, skladateľ,
 * obtiažnosť a dve tlačidlá (ukážka, pridať). Ťuk na kartu otvorí detail.
 */
@Composable
private fun FeaturedCard(
    item: CatalogItem,
    accent: Accent,
    hasAudio: Boolean,
    state: DownloadState,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    previewProgress: Float,
    downloadProgress: Float,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = accent.colors()
    val cat = categoryOf(item.category)
    val period = STYLES.firstOrNull { it.key == item.style }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, Motion.bouncy(), label = "featuredTurn")
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = c.container,
        contentColor = c.onContainer,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, 0.97f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // veľký tvar nástroja v rohu (len ozdoba)
                .decoShape(
                    shape = cat.shape,
                    color = c.accent.copy(alpha = 0.10f),
                    diameter = 172.dp,
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(46.dp, 54.dp),
                    turn = { 12f + turn / 2f },
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    icon = cat.icon,
                    shape = cat.shape,
                    container = c.accent,
                    content = c.onAccent,
                    size = 44.dp,
                    turn = turn,
                )
                Spacer(Modifier.weight(1f))
                if (item.difficulty > 0) {
                    DifficultyMeter(item.difficulty, color = c.accent, track = c.onContainer.copy(alpha = 0.18f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                item.localizedTitle(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.composer,
                style = MaterialTheme.typography.bodyMedium,
                color = c.onContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        state == DownloadState.DONE -> stringResource(R.string.catalog_in_library_label)
                        period != null -> stringResource(period.label)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasAudio) {
                    PreviewButton(
                        playing = previewPlaying,
                        loading = previewLoading,
                        progress = previewProgress,
                        onClick = onPreview,
                        size = 48.dp,
                        idle = c.accent,
                        idleContent = c.onAccent,
                        active = c.accent,
                        activeContent = c.onAccent,
                        progressColor = c.accent,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                DownloadButton(
                    state = state,
                    progress = downloadProgress,
                    onClick = onDownload,
                    size = 48.dp,
                    idleContainer = c.onContainer.copy(alpha = 0.10f),
                    idleContent = c.onContainer,
                    doneContainer = c.onContainer,
                    doneContent = c.container,
                    progressColor = c.accent,
                )
            }
        }
    }
}

/** Obtiažnosť ako päť stúpajúcich stĺpikov (ako crescendo): plné stĺpiky ukazujú úroveň 1 až 5. */
@Composable
private fun DifficultyMeter(
    level: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    large: Boolean = false,
) {
    val description = stringResource(R.string.catalog_difficulty, level)
    Canvas(
        modifier
            .size(width = if (large) 38.dp else 26.dp, height = if (large) 18.dp else 12.dp)
            .semantics { contentDescription = description },
    ) {
        val n = 5
        val gap = (if (large) 3.dp else 2.dp).toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = size.height * (0.36f + 0.64f * i / (n - 1))
            drawRoundRect(
                color = if (i < level) color else track,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

// ───────────────────────── Karty skladieb ─────────────────────────

/** Karta skladby: ťuk rozbalí detail (nástroj, obdobie, licencia, akcie); tvar nástroja sa pootočí. */
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
    val cat = categoryOf(item.category)
    val c = cat.accent.colors()
    // skladby, ktoré už sú v knižnici, majú inú farbu: na prvý pohľad vidno, čo už máš
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(
        when {
            pressed -> 30f
            expanded -> 15f
            else -> 0f
        },
        Motion.bouncy(),
        label = "cardTurn",
    )
    val corner by animateDpAsState(if (expanded) 32.dp else 24.dp, Motion.bouncy(), label = "cardCorner")
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = if (inLibrary) scheme.onSecondaryContainer else scheme.onSurface,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(Motion.snappy()),
    ) {
        Column {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ShapeIcon(
                        icon = cat.icon,
                        shape = cat.shape,
                        container = c.container,
                        content = c.onContainer,
                        size = 48.dp,
                        turn = turn,
                    )
                    if (inLibrary) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(20.dp)
                                .border(2.dp, container, CircleShape)
                                .background(scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.localizedTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            item.composer,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.difficulty > 0) {
                            Spacer(Modifier.width(10.dp))
                            DifficultyMeter(item.difficulty)
                        }
                    }
                }
                if (hasAudio) {
                    PreviewButton(
                        playing = previewPlaying,
                        loading = previewLoading,
                        progress = previewProgress,
                        onClick = onPreview,
                        size = 44.dp,
                    )
                }
                DownloadButton(
                    state = state,
                    progress = downloadProgress,
                    onClick = onDownload,
                    size = 44.dp,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }

            // po stiahnutí: vyrába sa referenčná nahrávka
            AnimatedVisibility(
                visible = referenceProgress != null,
                enter = expandVertically(Motion.snappy()) + fadeIn(),
                exit = shrinkVertically(tween(200)) + fadeOut(),
            ) {
                ReferenceProgress(
                    progress = referenceProgress ?: -1f,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                )
            }

            // detail
            if (expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    PieceDetails(
                        item = item,
                        hasAudio = hasAudio,
                        state = state,
                        previewPlaying = previewPlaying,
                        previewLoading = previewLoading,
                        onPreview = onPreview,
                        onDownload = onDownload,
                        onComposer = onComposer,
                        chipColor = scheme.surfaceContainerLow,
                    )
                }
            }
        }
    }
}

/** Priebeh výroby referenčnej nahrávky po pridaní skladby (vlnka, kým sa zvuk vyrába). */
@Composable
private fun ReferenceProgress(progress: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            if (progress < 0) stringResource(R.string.ref_job_downloading)
            else stringResource(R.string.ref_job_rendering, (progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (progress < 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            WavyLinearProgress(progress = progress, moving = true)
        }
    }
}

/** Štítky a akcie skladby (v rozbalenej karte aj v detaile odporúčanej skladby). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceDetails(
    item: CatalogItem,
    hasAudio: Boolean,
    state: DownloadState,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onComposer: () -> Unit,
    chipColor: Color,
) {
    val cat = categoryOf(item.category)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(cat.icon, item.instrument.ifBlank { stringResource(R.string.cat_other) }, chipColor)
            STYLES.firstOrNull { it.key == item.style }?.let { InfoChip(it.icon, stringResource(it.label), chipColor) }
            if (item.opus.isNotBlank() && item.opus.length <= 40) InfoChip(Icons.Outlined.MusicNote, item.opus, chipColor)
            if (item.license.isNotBlank()) InfoChip(Icons.Outlined.AutoAwesome, licenseShort(item.license), chipColor)
            if (hasAudio) InfoChip(Icons.Outlined.Headphones, stringResource(R.string.catalog_has_audio), chipColor)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDownload,
                enabled = state != DownloadState.LOADING,
                contentPadding = PaddingValues(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
            ) {
                when (state) {
                    DownloadState.LOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    DownloadState.DONE -> Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    DownloadState.IDLE -> Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (state) {
                            DownloadState.LOADING -> R.string.catalog_adding
                            DownloadState.DONE -> R.string.catalog_open
                            DownloadState.IDLE -> R.string.catalog_add
                        },
                    ),
                )
            }
            if (hasAudio) {
                FilledTonalButton(
                    onClick = onPreview,
                    enabled = !previewLoading,
                    contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Icon(if (previewPlaying) Icons.Outlined.Pause else Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.catalog_listen_preview))
                }
            }
            TextButton(onClick = onComposer, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.catalog_by_composer))
            }
        }
    }
}

/** Ukážka: vlnitý kruh s ▶ (pri prehrávaní pauza vo výraznej farbe), pri načítaní priebeh. */
@Composable
private fun PreviewButton(
    playing: Boolean,
    loading: Boolean,
    progress: Float,
    onClick: () -> Unit,
    size: Dp,
    idle: Color = MaterialTheme.colorScheme.surfaceBright,
    idleContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    active: Color = MaterialTheme.colorScheme.primary,
    activeContent: Color = MaterialTheme.colorScheme.onPrimary,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(Modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            ProgressCircle(progress, size - 14.dp, progressColor)
        } else {
            val bg by animateColorAsState(if (playing) active else idle, tween(200), label = "previewBg")
            val fg by animateColorAsState(if (playing) activeContent else idleContent, tween(200), label = "previewFg")
            PlayCookie(
                size = size,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.catalog_listen),
                onClick = onClick,
                playing = playing,
                color = bg,
                contentColor = fg,
            )
        }
    }
}

/** Pridať do knižnice: kruh s ikonou; po pridaní plný zaoblený štvorec s fajkou (ťuk otvorí skladbu). */
@Composable
private fun DownloadButton(
    state: DownloadState,
    progress: Float,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    idleContainer: Color = MaterialTheme.colorScheme.surfaceBright,
    idleContent: Color = MaterialTheme.colorScheme.primary,
    doneContainer: Color = MaterialTheme.colorScheme.primary,
    doneContent: Color = MaterialTheme.colorScheme.onPrimary,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        if (state == DownloadState.LOADING) {
            ProgressCircle(progress, size - 14.dp, progressColor)
        } else {
            val done = state == DownloadState.DONE
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val corner by animateIntAsState(
                when {
                    pressed -> 24
                    done -> 32
                    else -> 50
                },
                Motion.bouncy(),
                label = "dlShape",
            )
            val bg by animateColorAsState(if (done) doneContainer else idleContainer, tween(220), label = "dlBg")
            val fg by animateColorAsState(if (done) doneContent else idleContent, tween(220), label = "dlFg")
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(percent = corner),
                color = bg,
                contentColor = fg,
                interactionSource = interaction,
                modifier = Modifier
                    .size(size)
                    .pressScale(interaction, 0.9f),
            ) {
                // Crossfade nevyplní tlačidlo sám, preto ikona v Boxe cez celú plochu (inak by bola vľavo hore)
                Crossfade(targetState = done, animationSpec = tween(200), modifier = Modifier.fillMaxSize(), label = "dlIcon") { d ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (d) Icons.Outlined.Check else Icons.Outlined.LibraryAdd,
                            contentDescription = stringResource(if (d) R.string.catalog_in_library else R.string.catalog_download),
                            modifier = Modifier.size(22.dp),
                        )
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
private fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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

/** Kruhový priebeh s percentami vo vlnitom krúžku; pri neznámom priebehu (−1) sa len točí. */
@Composable
private fun ProgressCircle(progress: Float, size: Dp, color: Color = MaterialTheme.colorScheme.primary) {
    Box(contentAlignment = Alignment.Center) {
        if (progress < 0f) {
            CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 3.dp)
        } else {
            WavyRing(
                progress = progress.coerceAtLeast(0.02f),
                modifier = Modifier.size(size + 8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                stroke = 3.dp,
                amplitude = 1.dp,
                wavelength = 7.dp,
            )
            Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

// ───────────────────────── Spodné panely ─────────────────────────

/** Výber hodnoty filtra zospodu: mriežka dlaždíc s tvarom a počtom (bez posúvania do strán). */
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
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            // „Všetko“ cez celú šírku
            SheetTile(
                icon = Icons.Outlined.LibraryMusic,
                shape = ExpressiveShapes.Cookie12,
                accent = Accent.Primary,
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
                            shape = opt.shape,
                            accent = opt.accent,
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
    shape: Shape,
    accent: Accent,
    label: String,
    subtitle: String?,
    count: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val c = accent.colors()
    val container by animateColorAsState(if (selected) c.container else scheme.surfaceContainerHigh, label = "tile")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, Motion.bouncy(), label = "sheetTileTurn")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = if (selected) c.onContainer else scheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeIcon(
                icon = if (selected) Icons.Outlined.Check else icon,
                shape = shape,
                container = when {
                    selected -> c.accent
                    enabled -> c.container
                    else -> scheme.surfaceContainerHighest
                },
                content = when {
                    selected -> c.onAccent
                    enabled -> c.onContainer
                    else -> scheme.outline
                },
                size = 40.dp,
                turn = turn,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (enabled) Color.Unspecified else scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(subtitle, count?.let { pluralStringResource(R.plurals.pieces_n, it, it) }).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) c.onContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Detail odporúčanej skladby zospodu: veľký tvar nástroja, názov, obtiažnosť, štítky a akcie. */
@Composable
private fun DetailSheet(
    item: CatalogItem,
    hasAudio: Boolean,
    state: DownloadState,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    downloadProgress: Float,
    referenceProgress: Float?,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onComposer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cat = categoryOf(item.category)
    val c = cat.accent.colors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    icon = cat.icon,
                    shape = cat.shape,
                    container = c.container,
                    content = c.onContainer,
                    size = 68.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.localizedTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.composer,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (item.difficulty > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.field_difficulty),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    DifficultyMeter(item.difficulty, large = true)
                }
            }
            AnimatedVisibility(visible = state == DownloadState.LOADING && downloadProgress >= 0f) {
                WavyLinearProgress(progress = downloadProgress, moving = true)
            }
            AnimatedVisibility(visible = referenceProgress != null) {
                ReferenceProgress(progress = referenceProgress ?: -1f)
            }
            PieceDetails(
                item = item,
                hasAudio = hasAudio,
                state = state,
                previewPlaying = previewPlaying,
                previewLoading = previewLoading,
                onPreview = onPreview,
                onDownload = onDownload,
                onComposer = onComposer,
                chipColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

// ───────────────────────── Ostatné ─────────────────────────

@Composable
private fun EmptyResult() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(116.dp)
                    .graphicsLayer { rotationZ = 14f }
                    .background(scheme.tertiaryContainer, ExpressiveShapes.Clover),
            )
            Box(
                Modifier
                    .size(72.dp)
                    .background(scheme.surface, ExpressiveShapes.Cookie12),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.catalog_empty), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.catalog_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SourceFooter() {
    val context = LocalContext.current
    Column(Modifier.padding(top = 20.dp)) {
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
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Plávajúci prehrávač ukážky: tvar nástroja sa pri prehrávaní pomaly otáča ako platňa,
 * pod ním vlnitý priebeh, ktorý sa pri pauze vyrovná.
 */
@Composable
private fun MiniPlayer(item: CatalogItem, player: AudioPlayer, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val cat = categoryOf(item.category)
    val spin = remember { Animatable(0f) }
    LaunchedEffect(player.playing) {
        if (!player.playing) return@LaunchedEffect
        while (true) {
            spin.snapTo(spin.value % 360f)
            spin.animateTo(spin.value + 360f, tween(9000, easing = LinearEasing))
        }
    }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    icon = Icons.Outlined.Headphones,
                    shape = cat.shape,
                    container = scheme.primary,
                    content = scheme.onPrimary,
                    size = 44.dp,
                    turn = spin.value,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.localizedTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.catalog_now_playing, formatClock(player.position), formatClock(player.duration)),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
                PlayCookie(
                    size = 48.dp,
                    contentDescription = stringResource(if (player.playing) R.string.pause else R.string.play),
                    onClick = { player.toggle() },
                    playing = player.playing,
                )
                Spacer(Modifier.width(6.dp))
                PanelCloseButton(onClose)
            }
            Spacer(Modifier.height(12.dp))
            WavyLinearProgress(
                progress = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f,
                moving = player.playing,
                wavy = player.playing,
                color = scheme.primary,
                trackColor = scheme.onPrimaryContainer.copy(alpha = 0.16f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
