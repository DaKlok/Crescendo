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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.launch

private enum class DownloadState { IDLE, LOADING, DONE }

/** Katalóg voľných nôt – ťuk a skladba je v knižnici. */
@Composable
fun CatalogScreen(onBack: () -> Unit, onOpenPiece: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val states = remember { mutableStateMapOf<String, DownloadState>() }
    val imported = remember { mutableStateMapOf<String, Long>() }
    var error by remember { mutableStateOf(false) }

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
    val shown = items.filter {
        query.isBlank() || listOf(it.title, it.titleSk, it.composer).any { f -> f.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    Text(stringResource(R.string.catalog), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
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
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 280.dp else 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.catalog_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    if (error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.catalog_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id + it.url }) { item ->
                        val state = states[item.id] ?: DownloadState.IDLE
                        CatalogCard(
                            item = item,
                            state = state,
                            onDownload = {
                                if (state == DownloadState.LOADING) return@CatalogCard
                                val newId = imported[item.id]
                                if (state == DownloadState.DONE && newId != null) {
                                    onOpenPiece(newId)
                                    return@CatalogCard
                                }
                                haptic(HapticFeedbackType.LongPress)
                                error = false
                                states[item.id] = DownloadState.LOADING
                                scope.launch {
                                    app.catalog.download(item, app.library, null)
                                        .onSuccess { id ->
                                            imported[item.id] = id
                                            states[item.id] = DownloadState.DONE
                                        }
                                        .onFailure {
                                            states[item.id] = DownloadState.IDLE
                                            error = true
                                        }
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
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
private fun CatalogCard(
    item: CatalogItem,
    state: DownloadState,
    onDownload: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(Icons.Outlined.LibraryMusic, active = state == DownloadState.DONE, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    item.localizedTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.composer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.difficulty > 0) {
                    Row(Modifier.padding(top = 4.dp)) {
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
