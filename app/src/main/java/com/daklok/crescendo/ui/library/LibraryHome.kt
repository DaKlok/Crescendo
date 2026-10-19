package com.daklok.crescendo.ui.library

import com.daklok.crescendo.ui.common.ConnectedChoice
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import com.daklok.crescendo.data.LibraryLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.LibrarySort
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.WavyRing
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.pro.ProDot
import java.io.File

/** Údaje pre dlaždice na úvode knižnice. */
data class HomeInfo(
    val todaySec: Long = 0L,
    val streak: Int = 0,
    val goalMin: Int = 30,
    val setlistCount: Int = 0,
    val isPro: Boolean = false,
)

/**
 * Úvod knižnice: veľká karta „Pokračuj“ a dlaždice Voľné noty, Cvičenie a Setlisty.
 * Na širokej obrazovke sú dlaždice v stĺpci vedľa karty, na mobile v riadku pod ňou.
 */
@Composable
fun LibraryHomeTop(
    hero: PieceEntity?,
    heroThumb: File?,
    info: HomeInfo,
    onOpenHero: () -> Unit,
    onCatalog: () -> Unit,
    onPractice: () -> Unit,
    onSetlists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = isCompactWidth()
    when {
        hero == null -> DestinationRow(info, vertical = compact, onCatalog, onPractice, onSetlists, modifier)
        compact -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ContinueCard(piece = hero, thumb = heroThumb, onOpen = onOpenHero)
            DestinationRow(info, vertical = true, onCatalog, onPractice, onSetlists)
        }
        else -> Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ContinueCard(
                piece = hero,
                thumb = heroThumb,
                onOpen = onOpenHero,
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tile = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                CatalogTile(vertical = false, onClick = onCatalog, modifier = tile)
                PracticeTile(info, vertical = false, onClick = onPractice, modifier = tile)
                SetlistsTile(info, vertical = false, onClick = onSetlists, modifier = tile)
            }
        }
    }
}

/** Tri dlaždice vedľa seba (rovnako vysoké). */
@Composable
private fun DestinationRow(
    info: HomeInfo,
    vertical: Boolean,
    onCatalog: () -> Unit,
    onPractice: () -> Unit,
    onSetlists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(if (vertical) 10.dp else 16.dp),
    ) {
        val tile = Modifier
            .weight(1f)
            .fillMaxHeight()
        CatalogTile(vertical = vertical, onClick = onCatalog, modifier = tile)
        PracticeTile(info, vertical = vertical, onClick = onPractice, modifier = tile)
        SetlistsTile(info, vertical = vertical, onClick = onSetlists, modifier = tile)
    }
}

@Composable
private fun CatalogTile(vertical: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    DestinationTile(
        title = stringResource(R.string.catalog),
        subtitle = stringResource(R.string.lib_tile_catalog),
        vertical = vertical,
        onClick = onClick,
        modifier = modifier,
    ) { size, turn ->
        ShapeIcon(
            icon = Icons.Outlined.TravelExplore,
            shape = ExpressiveShapes.Clover,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            size = size,
            turn = turn,
        )
    }
}

@Composable
private fun PracticeTile(info: HomeInfo, vertical: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val todayMin = (info.todaySec / 60).toInt()
    val goal = info.goalMin.coerceAtLeast(1)
    val done = todayMin >= goal
    val subtitle = buildString {
        append(
            if (done) stringResource(R.string.lib_tile_goal_done)
            else stringResource(R.string.lib_tile_practice, todayMin, goal),
        )
        // séria dní: na širokej dlaždici je na ňu miesto
        if (!vertical && info.streak > 0) {
            append('\n')
            append(stringResource(R.string.practice_streak))
            append(": ")
            append(pluralStringResource(R.plurals.days, info.streak, info.streak))
        }
    }
    DestinationTile(
        title = stringResource(R.string.practice),
        subtitle = subtitle,
        vertical = vertical,
        onClick = onClick,
        modifier = modifier,
    ) { size, turn ->
        PracticeArt(todayMin = todayMin, goalMin = goal, size = size, turn = turn)
    }
}

@Composable
private fun SetlistsTile(info: HomeInfo, vertical: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    DestinationTile(
        title = stringResource(R.string.setlists),
        subtitle = if (info.setlistCount > 0) pluralStringResource(R.plurals.setlists_n, info.setlistCount, info.setlistCount)
        else stringResource(R.string.lib_tile_setlists_empty),
        vertical = vertical,
        onClick = onClick,
        modifier = modifier,
    ) { size, turn ->
        Box {
            ShapeIcon(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                shape = ExpressiveShapes.Squircle,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                size = size,
                turn = turn,
            )
            // setlisty sú v Pro
            if (!info.isPro) {
                ProDot(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                )
            }
        }
    }
}

/** Denný cieľ: vlnitý krúžok okolo vlnitého kruhu s dnešnými minútami (po splnení fajka). */
@Composable
private fun PracticeArt(todayMin: Int, goalMin: Int, size: Dp, turn: Float) {
    val scheme = MaterialTheme.colorScheme
    val done = todayMin >= goalMin
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .padding(size * 0.15f)
                .graphicsLayer { rotationZ = turn }
                .background(if (done) scheme.primary else scheme.primaryContainer, ExpressiveShapes.Cookie),
        )
        WavyRing(
            progress = todayMin.toFloat() / goalMin,
            modifier = Modifier.matchParentSize(),
            color = scheme.primary,
            trackColor = scheme.primary.copy(alpha = 0.18f),
            stroke = 3.5.dp,
            amplitude = 1.1.dp,
            wavelength = 9.dp,
        )
        if (done) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.34f))
        } else {
            Text(
                todayMin.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
            )
        }
    }
}

/**
 * Dlaždica cieľa (Voľné noty, Cvičenie, Setlisty). Pri stlačení sa tvar ikony pružne pootočí.
 * [vertical]: ikona hore a text pod ňou (úzke dlaždice na mobile), inak ikona vľavo.
 */
@Composable
private fun DestinationTile(
    title: String,
    subtitle: String,
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable (size: Dp, turn: Float) -> Unit,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, Motion.bouncy(), label = "tileTurn")
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.pressScale(interaction, 0.96f),
        shape = RoundedCornerShape(if (vertical) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interaction,
    ) {
        if (vertical) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)) {
                art(44.dp, turn)
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                art(48.dp, turn)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Nadpis zoznamu skladieb: vpravo zobrazenie (mriežka alebo zoznam, počet v riadku) a zoradenie.
 * [columnOptions] = počty v riadku, ktoré sa na tejto obrazovke zmestia (0 = automaticky).
 */
@Composable
fun PiecesHeader(
    title: String,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    layout: LibraryLayout,
    columns: Int,
    columnOptions: List<Int>,
    onLayout: (LibraryLayout) -> Unit,
    onColumns: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    var open by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // ── zobrazenie ──
        Box {
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    viewOpen = true
                },
                shape = RoundedCornerShape(50),
                color = if (viewOpen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (layout == LibraryLayout.LIST) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                        contentDescription = stringResource(R.string.lib_view),
                        modifier = Modifier.size(18.dp),
                    )
                    // počet v riadku vedľa ikony (pri automatickom len ikona)
                    if (layout == LibraryLayout.GRID && columns > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("$columns", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            DropdownMenu(
                expanded = viewOpen,
                onDismissRequest = { viewOpen = false },
                shape = RoundedCornerShape(24.dp),
            ) {
                ViewOptions(
                    layout = layout,
                    columns = columns,
                    columnOptions = columnOptions,
                    onLayout = onLayout,
                    onColumns = onColumns,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // ── zoradenie ──
        Box {
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    open = true
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(R.string.sort),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(sort.labelRes), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(20.dp),
            ) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        leadingIcon = {
                            if (option == sort) Icon(Icons.Outlined.Check, null)
                            else Spacer(Modifier.size(24.dp))
                        },
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onSort(option)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** Obsah ponuky zobrazenia: mriežka alebo zoznam a pri mriežke počet skladieb v riadku. */
@Composable
private fun ViewOptions(
    layout: LibraryLayout,
    columns: Int,
    columnOptions: List<Int>,
    onLayout: (LibraryLayout) -> Unit,
    onColumns: (Int) -> Unit,
) {
    val auto = stringResource(R.string.lib_columns_auto)
    Column(
        Modifier
            .width(300.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.lib_view), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        ConnectedChoice(
            options = listOf(LibraryLayout.GRID, LibraryLayout.LIST),
            selected = layout,
            onSelect = onLayout,
            height = 44.dp,
        ) { option, _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (option == LibraryLayout.LIST) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (option == LibraryLayout.LIST) R.string.lib_view_list else R.string.lib_view_grid),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        if (layout == LibraryLayout.GRID) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.lib_per_row),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ConnectedChoice(
                options = columnOptions,
                // uložený počet sa na menšej obrazovke zúži na najväčší možný
                selected = if (columns <= 0) 0 else columns.coerceAtMost(columnOptions.max()),
                label = { if (it == 0) auto else it.toString() },
                onSelect = onColumns,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Filtre knižnice ako pilulky: všetko (s počtom), obľúbené, štítky. */
@Composable
fun FilterPills(
    s: LibraryUiState,
    onAll: () -> Unit,
    onFavorites: () -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val allSelected = !s.favoritesOnly && s.tagFilter == null
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            Pill(
                selected = allSelected,
                label = if (s.currentFolder != null) stringResource(R.string.this_folder) else stringResource(R.string.filter_all),
                icon = if (allSelected) Icons.Outlined.Check else null,
                count = if (s.currentFolder == null) s.totalCount else null,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onAll()
                },
            )
        }
        item(key = "fav") {
            Pill(
                selected = s.favoritesOnly,
                label = stringResource(R.string.favorites),
                icon = if (s.favoritesOnly) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onFavorites()
                },
            )
        }
        items(s.allTags.size, key = { "t" + s.allTags[it] }) { i ->
            val tag = s.allTags[i]
            Pill(
                selected = s.tagFilter == tag,
                label = tag,
                icon = if (s.tagFilter == tag) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.Label,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onTag(tag)
                },
            )
        }
    }
}

@Composable
private fun Pill(selected: Boolean, label: String, icon: ImageVector?, onClick: () -> Unit, count: Int? = null) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, maxLines = 1)
                if (count != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(
                                if (selected) scheme.onSecondaryContainer.copy(alpha = 0.12f) else scheme.surfaceContainerHighest,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        leadingIcon = if (icon != null) {
            { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(36.dp),
    )
}

/** Prázdna knižnica: zložené výrazné tvary a dve cesty, ako začať. */
@Composable
fun EmptyLibrary(onImport: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer { rotationZ = 14f }
                    .background(scheme.tertiaryContainer, ExpressiveShapes.Clover),
            )
            Box(
                Modifier
                    .size(98.dp)
                    .background(scheme.primary, ExpressiveShapes.Cookie12),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.library_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Icon(Icons.Outlined.FileOpen, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.import_pdf), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onScan,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_music))
        }
    }
}

/** Krátka správa namiesto obsahu (prázdny priečinok, nič sa nenašlo). */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 24.dp, horizontal = 4.dp),
    )
}
