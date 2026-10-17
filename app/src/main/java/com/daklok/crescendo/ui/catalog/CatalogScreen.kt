package com.daklok.crescendo.ui.catalog

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.CatalogItem
import com.daklok.crescendo.data.CatalogRepository
import com.daklok.crescendo.data.midi.MidiRenderer
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.reader.AudioPlayer
import kotlinx.coroutines.launch

private enum class DownloadState { IDLE, LOADING, DONE }

/** Skupiny nástrojov (kľúč v catalog.json → text). */
private val CATEGORIES = listOf(
    "piano" to R.string.cat_piano,
    "keyboard" to R.string.cat_keyboard,
    "guitar" to R.string.cat_guitar,
    "strings" to R.string.cat_strings,
    "winds" to R.string.cat_winds,
    "voice" to R.string.cat_voice,
    "ensemble" to R.string.cat_ensemble,
    "other" to R.string.cat_other,
)

/** Obdobia / štýly. */
private val STYLES = listOf(
    "baroque" to R.string.style_baroque,
    "classical" to R.string.style_classical,
    "romantic" to R.string.style_romantic,
    "renaissance" to R.string.style_renaissance,
    "modern" to R.string.style_modern,
    "jazz" to R.string.style_jazz,
    "folk" to R.string.style_folk,
    "hymn" to R.string.style_hymn,
    "technique" to R.string.style_technique,
)

/** Katalóg voľných nôt – vyhľadávanie, filtre, vypočutie ukážky a stiahnutie do knižnice. */
@Composable
fun CatalogScreen(onBack: () -> Unit, onOpenPiece: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var style by rememberSaveable { mutableStateOf<String?>(null) }
    val states = remember { mutableStateMapOf<String, DownloadState>() }
    val imported = remember { mutableStateMapOf<String, Long>() }

    // ukážka (MIDI → zvuk klavíra)
    val player = remember { AudioPlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var previewId by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf<String?>(null) }
    val previewUnavailable = stringResource(R.string.catalog_preview_unavailable)
    val downloadFailed = stringResource(R.string.catalog_error)

    LaunchedEffect(Unit) {
        items = app.catalog.local()
        // len tie, ktoré sú ešte v knižnici (a vieme ich otvoriť) – inak by ťuk stiahol duplikát
        app.catalog.downloaded(app.library).forEach { (id, pieceId) ->
            imported[id] = pieceId
            states[id] = DownloadState.DONE
        }
        app.catalog.refresh()?.let { items = it }
    }
    BackHandler(onBack = onBack)

    val compact = isCompactWidth()
    val q = query.trim()
    val filtering = q.isNotEmpty() || category != null || style != null
    val shown = remember(items, q, category, style) {
        items.filter { item ->
            (category == null || item.category == category) &&
                (style == null || item.style == style) &&
                (q.isEmpty() || listOf(item.title, item.titleSk, item.composer, item.opus, item.instrument)
                    .any { it.contains(q, ignoreCase = true) })
        }
    }
    val featured = remember(items) { items.filter { it.featured } }

    fun preview(item: CatalogItem) {
        if (previewId == item.id && player.file != null) {
            player.toggle()
            return
        }
        if (previewLoading != null) return
        haptic(HapticFeedbackType.TextHandleMove)
        player.release()
        previewId = null
        previewLoading = item.id
        scope.launch {
            val mid = app.catalog.midiFile(item)
            val sound = mid?.let { MidiRenderer.renderNow(context, it) }
            previewLoading = null
            if (sound == null) {
                snackbar.showSnackbar(previewUnavailable)
            } else {
                player.load(sound)
                player.play()
                previewId = item.id
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
        scope.launch {
            app.catalog.download(item, app.library, null)
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

    @Composable
    fun card(item: CatalogItem, modifier: Modifier) {
        CatalogCard(
            item = item,
            state = states[item.id] ?: DownloadState.IDLE,
            previewPlaying = previewId == item.id && player.playing,
            previewLoading = previewLoading == item.id,
            onPreview = { preview(item) },
            onDownload = { download(item) },
            onComposer = { query = item.composer },
            modifier = modifier,
        )
    }

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
                        if (items.isNotEmpty()) {
                            Text(
                                stringResource(R.string.catalog_count, items.size),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.catalog_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.close)) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            // nástroje
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = { Text(stringResource(R.string.cat_all)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                items(CATEGORIES, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = category == key,
                        onClick = { category = if (category == key) null else key },
                        label = { Text(stringResource(label)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            // obdobie
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(STYLES, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = style == key,
                        onClick = { style = if (style == key) null else key },
                        label = { Text(stringResource(label)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 300.dp else 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!filtering) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.catalog_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                        if (featured.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(Icons.Outlined.AutoAwesome, stringResource(R.string.catalog_featured))
                            }
                            items(featured, key = { "f_" + it.id + it.url }) { item -> card(item, Modifier.animateItem()) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitle(Icons.Outlined.LibraryMusic, stringResource(R.string.catalog_all, items.size))
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.catalog_found, shown.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                )
                                TextButton(onClick = {
                                    query = ""
                                    category = null
                                    style = null
                                }) { Text(stringResource(R.string.catalog_clear_filters)) }
                            }
                        }
                    }
                    items(shown, key = { it.id + it.url }) { item -> card(item, Modifier.animateItem()) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(
                                stringResource(R.string.catalog_source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CatalogRepository.SOURCE_URL)))
                                }
                            }) {
                                Text(stringResource(R.string.catalog_more))
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun categoryLabel(key: String): String =
    stringResource(CATEGORIES.firstOrNull { it.first == key }?.second ?: R.string.cat_other)

@Composable
private fun CatalogCard(
    item: CatalogItem,
    state: DownloadState,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onComposer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onDownload,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Icons.Outlined.LibraryMusic, active = state == DownloadState.DONE, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    item.localizedTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // ťuk na skladateľa = všetky jeho skladby
                Text(
                    item.composer + (if (item.opus.isNotBlank() && item.opus.length <= 24) " · " + item.opus else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onComposer),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        categoryLabel(item.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.midi.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Headphones,
                            null,
                            Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.difficulty > 0) {
                        Spacer(Modifier.width(8.dp))
                        repeat(5) { i ->
                            Icon(
                                Icons.Outlined.Star,
                                null,
                                Modifier.size(12.dp),
                                tint = if (i < item.difficulty) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            if (item.midi.isNotBlank()) {
                if (previewLoading) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                } else {
                    IconButton(onClick = onPreview) {
                        Icon(
                            if (previewPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            stringResource(R.string.catalog_listen),
                            tint = if (previewPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = state,
                transitionSpec = { (fadeIn() + scaleIn(Motion.bouncy())) togetherWith fadeOut() },
                label = "dl",
            ) { s ->
                when (s) {
                    DownloadState.LOADING -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                    DownloadState.DONE -> FilledTonalIconButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Check, stringResource(R.string.catalog_in_library))
                    }
                    DownloadState.IDLE -> FilledTonalIconButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, stringResource(R.string.catalog_download))
                    }
                }
            }
        }
    }
}
