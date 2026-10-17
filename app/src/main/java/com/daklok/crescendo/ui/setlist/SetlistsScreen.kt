package com.daklok.crescendo.ui.setlist

import androidx.compose.material3.FilledIconButton
import com.daklok.crescendo.ui.common.isCompactWidth
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.SetlistEntity
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.library.NameDialog
import com.daklok.crescendo.ui.library.ThumbImage

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
                title = { Text(stringResource(R.string.setlists), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.setlist_new)) },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding() + 8.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            if (state.loaded && state.setlists.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(Icons.AutoMirrored.Outlined.QueueMusic, active = true, size = 88.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.setlists_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.setlists_empty_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.setlists, key = { it.setlist.id }) { sl ->
                        SetlistCard(
                            sl = sl,
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
        )
    }
    deleting?.let { sl ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.setlist_delete_title)) },
            text = { Text(stringResource(R.string.setlist_delete_text, sl.name)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(sl.id); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SetlistCard(
    sl: SetlistWithItems,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(Icons.AutoMirrored.Outlined.QueueMusic, active = sl.rows.isNotEmpty())
            Column(Modifier.weight(1f)) {
                Text(sl.setlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (sl.rows.isEmpty()) stringResource(R.string.folder_empty)
                    else sl.rows.joinToString(" · ") { it.piece.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sl.rows.isNotEmpty()) {
                if (isCompactWidth()) {
                    FilledIconButton(onClick = onPlay) {
                        Icon(Icons.Outlined.PlayArrow, stringResource(R.string.setlist_play))
                    }
                } else {
                    Button(onClick = onPlay, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.setlist_play))
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
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
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(detail.setlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(R.plurals.items, detail.rows.size, detail.rows.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = stringResource(R.string.setlist_add_pieces),
                        onClick = { adding = true },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (detail.rows.isNotEmpty()) {
                        Button(onClick = { onPlay(0) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(end = 12.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.setlist_play))
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
                .padding(top = padding.calculateTopPadding() + 8.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            if (detail.rows.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.setlist_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { adding = true }, shape = RoundedCornerShape(20.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setlist_add_pieces))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(detail.rows, key = { _, r -> r.item.id }) { i, row ->
                        Card(
                            onClick = { onPlay(i) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                if (!isCompactWidth()) {
                                    ThumbImage(
                                        file = vm.thumbFile(row.piece),
                                        modifier = Modifier
                                            .width(44.dp)
                                            .aspectRatio(0.74f)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                Text(
                                    row.piece.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.move(detail.rows, i, -1) },
                                    enabled = i > 0,
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up)) }
                                IconButton(
                                    onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.move(detail.rows, i, 1) },
                                    enabled = i < detail.rows.lastIndex,
                                ) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down)) }
                                IconButton(onClick = { vm.remove(row) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.setlist_remove_item))
                                }
                            }
                        }
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

/** Výber skladieb (s hľadaním) na pridanie do setlistu. Poradie = poradie označenia. */
@Composable
private fun PiecePickerDialog(
    pieces: List<PieceEntity>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<Long>()) }
    val shown = pieces.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.setlist_add_pieces)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.id }) { p ->
                        val idx = picked.indexOf(p.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { picked = if (idx >= 0) picked - p.id else picked + p.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = idx >= 0, onCheckedChange = { picked = if (idx >= 0) picked - p.id else picked + p.id })
                            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (idx >= 0) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
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

/** Výber setlistu pri pridávaní skladieb z knižnice (+ nový setlist). */
@Composable
fun AddToSetlistDialog(
    setlists: List<SetlistEntity>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.setlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { onCreate(it) },
            onDismiss = { creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
        title = { Text(stringResource(R.string.add_to_setlist)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(setlists, key = { it.id }) { sl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(sl.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(sl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { creating = true }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.setlist_new), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
