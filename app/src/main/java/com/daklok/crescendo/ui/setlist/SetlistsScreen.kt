package com.daklok.crescendo.ui.setlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.SetlistEntity
import com.daklok.crescendo.ui.common.DialogIcon
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.Accent
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.ScreenTopBar
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.library.NameDialog
import com.daklok.crescendo.ui.library.SheetThumb
import com.daklok.crescendo.ui.reader.ToolButton
import com.daklok.crescendo.ui.reader.ToolGroup
import java.io.File

/**
 * Setlisty: zoznam setlistov a detail jedného setlistu (poradie skladieb, pridávanie, „Hrať“).
 * [onPlay] otvorí čítačku na danej položke setlistu.
 */
@Composable
fun SetlistsScreen(
    vm: SetlistViewModel,
    onBack: () -> Unit,
    onPlay: (setlistId: Long, index: Int, pieceId: Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newOpen by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()

    val open = state.setlists.firstOrNull { it.setlist.id == openId }
    BackHandler { if (openId != null) openId = null else onBack() }

    AnimatedContent(
        targetState = open?.setlist?.id,
        transitionSpec = {
            val forward = targetState != null
            val dir = if (forward) 1 else -1
            (fadeIn(tween(240, delayMillis = 40)) + slideInHorizontally(Motion.gentle()) { dir * it / 4 }) togetherWith
                (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -dir * it / 6 })
        },
        label = "setlist",
    ) { id ->
        val detail = state.setlists.firstOrNull { it.setlist.id == id }
        if (detail == null) {
            SetlistList(
                state = state,
                onBack = onBack,
                onOpen = { openId = it },
                onNew = { newOpen = true },
                onPlay = { sl -> sl.rows.firstOrNull()?.let { onPlay(sl.setlist.id, 0, it.piece.id) } },
                vm = vm,
            )
        } else {
            SetlistDetail(
                detail = detail,
                allPieces = state.pieces,
                vm = vm,
                onBack = { openId = null },
                onPlay = { index -> onPlay(detail.setlist.id, index, detail.rows[index].piece.id) },
            )
        }
    }

    if (newOpen) {
        NameDialog(
            title = stringResource(R.string.setlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                haptic(HapticFeedbackType.LongPress)
                vm.create(it) { id -> openId = id }
                newOpen = false
            },
            onDismiss = { newOpen = false },
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
        )
    }
}

@Composable
private fun SetlistList(
    state: SetlistsUiState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onPlay: (SetlistWithItems) -> Unit,
    vm: SetlistViewModel,
) {
    var renaming by remember { mutableStateOf<SetlistEntity?>(null) }
    var deleting by remember { mutableStateOf<SetlistEntity?>(null) }
    val compact = isCompactWidth()
    val count = state.setlists.size
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.setlists),
                subtitle = if (count > 0) pluralStringResource(R.plurals.setlists_n, count, count) else null,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            // prázdny zoznam má vlastné veľké tlačidlo
            if (count > 0) BigFab(Icons.Outlined.Add, stringResource(R.string.setlist_new), onClick = onNew, large = !compact)
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding() + 4.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            if (state.loaded && state.setlists.isEmpty()) {
                SetlistsEmpty(onNew = onNew)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 400.dp),
                    contentPadding = PaddingValues(
                        start = if (compact) 16.dp else 24.dp,
                        end = if (compact) 16.dp else 24.dp,
                        top = 20.dp,
                        bottom = 132.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.setlists, key = { it.setlist.id }) { sl ->
                        SetlistCard(
                            sl = sl,
                            thumbs = sl.rows.take(3).map { vm.thumbFile(it.piece) },
                            onOpen = { onOpen(sl.setlist.id) },
                            onPlay = { onPlay(sl) },
                            onRename = { renaming = sl.setlist },
                            onDelete = { deleting = sl.setlist },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    renaming?.let { sl ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = sl.name,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { vm.rename(sl.id, it); renaming = null },
            onDismiss = { renaming = null },
            icon = Icons.Outlined.Edit,
        )
    }
    deleting?.let { sl ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(28.dp),
            icon = { DialogIcon(Icons.Outlined.Delete, destructive = true) },
            title = { Text(stringResource(R.string.setlist_delete_title)) },
            text = { Text(stringResource(R.string.setlist_delete_text, sl.name)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(sl.id); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Veľké plávajúce tlačidlo s popisom (rovnaký tvar a veľkosť ako v knižnici). */
@Composable
private fun BigFab(icon: ImageVector, label: String, onClick: () -> Unit, large: Boolean) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            haptic(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(percent = 30),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        interactionSource = interaction,
        modifier = Modifier
            .padding(end = if (large) 8.dp else 0.dp, bottom = if (large) 8.dp else 0.dp)
            .height(if (large) 80.dp else 64.dp)
            .pressScale(interaction, 0.94f),
    ) {
        Row(
            Modifier.padding(start = if (large) 24.dp else 20.dp, end = if (large) 28.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 30.dp else 26.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Žiadne setlisty: zložené výrazné tvary, vysvetlenie a veľké tlačidlo. */
@Composable
private fun SetlistsEmpty(onNew: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(140.dp)
                    .graphicsLayer { rotationZ = -8f }
                    .background(scheme.secondaryContainer, ExpressiveShapes.Squircle),
            )
            Box(
                Modifier
                    .size(98.dp)
                    .background(scheme.primary, ExpressiveShapes.Cookie12),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.setlists_empty_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setlists_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onNew, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.setlist_new), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Karta setlistu: vejár prvých strán, názov, počet a prvé skladby, vlnité tlačidlo Hrať. */
@Composable
private fun SetlistCard(
    sl: SetlistWithItems,
    thumbs: List<File?>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val n = sl.rows.size
    Surface(
        onClick = onOpen,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetFan(files = thumbs, empty = n == 0)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sl.setlist.name,
                    style = if (isCompactWidth()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (n == 0) stringResource(R.string.folder_empty) else pluralStringResource(R.plurals.pieces_n, n, n),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    maxLines = 1,
                )
                if (n > 0) {
                    Text(
                        sl.rows.joinToString(" · ") { it.piece.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (n > 0) {
                Spacer(Modifier.width(8.dp))
                PlayCookie(
                    size = 52.dp,
                    contentDescription = stringResource(R.string.setlist_play),
                    onClick = onPlay,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Prvé (najviac tri) strany skladieb ako vejár papierov; prázdny setlist ukáže ikonu vo výraznom tvare. */
@Composable
private fun SheetFan(files: List<File?>, empty: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(width = 72.dp, height = 80.dp), contentAlignment = Alignment.CenterStart) {
        if (empty || files.isEmpty()) {
            ShapeIcon(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                shape = ExpressiveShapes.Squircle,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                size = 56.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val tilts = listOf(-3f, 6f, -9f)
            // zadné listy prvé, predný (prvá skladba) navrchu
            files.indices.reversed().forEach { i ->
                SheetThumb(
                    file = files[i],
                    elevation = if (i == 0) 3.dp else 1.5.dp,
                    modifier = Modifier
                        .offset(x = (i * 9).dp)
                        .width(50.dp)
                        .aspectRatio(0.74f)
                        .graphicsLayer { rotationZ = tilts[i] },
                )
            }
        }
    }
}

@Composable
private fun SetlistDetail(
    detail: SetlistWithItems,
    allPieces: List<PieceEntity>,
    vm: SetlistViewModel,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val compact = isCompactWidth()
    val n = detail.rows.size
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = detail.setlist.name,
                subtitle = pluralStringResource(R.plurals.pieces_n, n, n),
                onBack = onBack,
                actions = {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = stringResource(R.string.setlist_add_pieces),
                        onClick = { adding = true },
                    )
                },
            )
        },
        floatingActionButton = {
            // hrať od začiatku: veľké tlačidlo na dosah palca
            if (n > 0) BigFab(Icons.Outlined.PlayArrow, stringResource(R.string.setlist_play), onClick = { onPlay(0) }, large = !compact)
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding() + 4.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            if (n == 0) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShapeIcon(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        shape = ExpressiveShapes.Squircle,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 96.dp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.setlist_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { adding = true }, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setlist_add_pieces), style = MaterialTheme.typography.titleSmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 920.dp),
                    contentPadding = PaddingValues(
                        start = if (compact) 12.dp else 24.dp,
                        end = if (compact) 12.dp else 24.dp,
                        top = 20.dp,
                        bottom = 132.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(detail.rows, key = { _, r -> r.item.id }) { i, row ->
                        SetlistRowCard(
                            index = i,
                            piece = row.piece,
                            thumb = if (compact) null else vm.thumbFile(row.piece),
                            showThumb = !compact,
                            canUp = i > 0,
                            canDown = i < detail.rows.lastIndex,
                            onPlay = { onPlay(i) },
                            onUp = { haptic(HapticFeedbackType.TextHandleMove); vm.move(detail.rows, i, -1) },
                            onDown = { haptic(HapticFeedbackType.TextHandleMove); vm.move(detail.rows, i, 1) },
                            onRemove = { vm.remove(row) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        PiecePickerDialog(
            pieces = allPieces,
            onConfirm = { ids ->
                haptic(HapticFeedbackType.LongPress)
                vm.addPieces(detail.setlist.id, ids)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** Riadok setlistu: poradie vo vlnitom kruhu, (na tablete) prvá strana, názov a posun / odobratie. */
@Composable
private fun SetlistRowCard(
    index: Int,
    piece: PieceEntity,
    thumb: File?,
    showThumb: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onPlay: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onPlay,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // prvá skladba (tu sa začína hrať) je výraznejšia
            Box(
                Modifier
                    .size(38.dp)
                    .background(if (index == 0) scheme.primary else scheme.primaryContainer, ExpressiveShapes.Cookie),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (index == 0) scheme.onPrimary else scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (showThumb) {
                SheetThumb(
                    file = thumb,
                    elevation = 2.dp,
                    modifier = Modifier
                        .width(46.dp)
                        .aspectRatio(0.74f),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    piece.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (piece.composer.isNotBlank()) {
                    Text(
                        piece.composer,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            ToolGroup {
                ToolButton(
                    icon = Icons.Outlined.KeyboardArrowUp,
                    description = stringResource(R.string.move_up),
                    onClick = onUp,
                    enabled = canUp,
                    size = 36.dp,
                )
                ToolButton(
                    icon = Icons.Outlined.KeyboardArrowDown,
                    description = stringResource(R.string.move_down),
                    onClick = onDown,
                    enabled = canDown,
                    size = 36.dp,
                )
            }
            ToolButton(
                icon = Icons.Outlined.Close,
                description = stringResource(R.string.setlist_remove_item),
                onClick = onRemove,
                size = 40.dp,
            )
        }
    }
}

/** Výber skladieb (s hľadaním) na pridanie do setlistu. Poradie = poradie označenia (číslo vo vlnitom kruhu). */
@Composable
private fun PiecePickerDialog(
    pieces: List<PieceEntity>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<Long>()) }
    val shown = pieces.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { DialogIcon(Icons.AutoMirrored.Outlined.PlaylistAdd, ExpressiveShapes.Squircle, Accent.Secondary) },
        title = { Text(stringResource(R.string.setlist_add_pieces)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = scheme.surfaceContainerHighest,
                        focusedContainerColor = scheme.surfaceContainerHighest,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = scheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.id }) { p ->
                        val idx = picked.indexOf(p.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .toggleable(value = idx >= 0, role = Role.Checkbox) {
                                    picked = if (idx >= 0) picked - p.id else picked + p.id
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PickMark(order = idx + 1)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.composer.isNotBlank()) {
                                    Text(
                                        p.composer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = { onConfirm(picked) }) {
                Text(stringResource(R.string.setlist_add_n, picked.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Značka výberu: prázdny krúžok, po výbere vlnitý kruh s poradím (pružne „vyskočí“). */
@Composable
private fun PickMark(order: Int) {
    val scheme = MaterialTheme.colorScheme
    val picked = order > 0
    val scale by animateFloatAsState(if (picked) 1f else 0.86f, Motion.bouncy(), label = "pickMark")
    Box(
        Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (picked) Modifier.background(scheme.primary, ExpressiveShapes.Cookie)
                else Modifier.border(2.dp, scheme.outline, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Text(order.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
        }
    }
}

/** Výber setlistu pri pridávaní skladieb z knižnice (+ nový setlist). */
@Composable
fun AddToSetlistDialog(
    setlists: List<SetlistEntity>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.setlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { onCreate(it) },
            onDismiss = { creating = false },
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { DialogIcon(Icons.AutoMirrored.Outlined.QueueMusic, ExpressiveShapes.Squircle, Accent.Secondary) },
        title = { Text(stringResource(R.string.add_to_setlist)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(setlists, key = { it.id }) { sl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(sl.id) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapeIcon(
                            icon = Icons.AutoMirrored.Outlined.QueueMusic,
                            shape = ExpressiveShapes.Squircle,
                            container = scheme.secondaryContainer,
                            content = scheme.onSecondaryContainer,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(sl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { creating = true }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapeIcon(
                            icon = Icons.Outlined.Add,
                            shape = ExpressiveShapes.Cookie,
                            container = scheme.primaryContainer,
                            content = scheme.onPrimaryContainer,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.setlist_new),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
