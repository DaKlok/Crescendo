package com.daklok.crescendo.ui.library

import com.daklok.crescendo.ui.common.contentColorFor
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import com.daklok.crescendo.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.data.FolderEntity

@Composable
fun FolderCard(
    folder: FolderEntity,
    itemCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val haptic = rememberHaptic()
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val tint = folder.color?.let { Color(it) }
    val container by animateColorAsState(
        when {
            selected -> scheme.secondaryContainer
            tint != null -> lerp(scheme.surfaceContainerHigh, tint, 0.16f)
            else -> scheme.surfaceContainerHigh
        },
        tween(250),
        label = "folderBg",
    )
    Card(
        modifier = modifier
            .pressScale(interaction)
            .selectedLook(selected)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    if (selectionMode) onSelect() else onOpen()
                },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onSelect()
                },
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 76.dp)
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FolderBadge(color = folder.color, active = itemCount > 0)
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (itemCount == 0) stringResource(R.string.folder_empty)
                    else pluralStringResource(R.plurals.items, itemCount, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                SelectionCheck(visible = selectionMode, checked = selected)
                // plný názov: v Box vnútri Row by sa inak vybralo RowScope.AnimatedVisibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = !selectionMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Box {
                        IconButton(onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            menuOpen = true
                        }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menuOpen = false; onRename() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder_color)) },
                                leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                onClick = { menuOpen = false; onColor() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move)) },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = { menuOpen = false; onMove() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_folder)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; onDelete() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ikona priečinka vo farbe priečinka (alebo vo farbe témy). */
@Composable
fun FolderBadge(color: Int?, active: Boolean, size: Dp = 46.dp) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            color != null -> Color(color)
            active -> scheme.primary
            else -> scheme.outline.copy(alpha = 0.22f)
        },
        tween(260),
        label = "folderBadge",
    )
    val fg = when {
        color != null -> contentColorFor(color)
        active -> scheme.onPrimary
        else -> scheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(bg, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Dialóg s jedným textovým poľom (nový priečinok / premenovanie). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.name)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Výber cieľa pri presúvaní: knižnica alebo ľubovoľný priečinok (stromovo odsadené).
 * [excluded] = priečinky, ktoré sa nedajú vybrať (napr. presúvaný priečinok a jeho podpriečinky).
 */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    currentLocation: Long?,
    excluded: Set<Long> = emptySet(),
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tree = remember(folders, excluded) { flattenTree(folders, excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.move_to)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    DestinationRow(
                        name = stringResource(R.string.library),
                        depth = 0,
                        isRoot = true,
                        selected = currentLocation == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(tree, key = { it.first.id }) { (folder, depth) ->
                    DestinationRow(
                        name = folder.name,
                        depth = depth + 1,
                        isRoot = false,
                        color = folder.color,
                        selected = currentLocation == folder.id,
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DestinationRow(
    name: String,
    depth: Int,
    isRoot: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    color: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selected, onClick = onClick)
            .padding(start = (depth * 20).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isRoot) Icons.Outlined.LibraryMusic else Icons.Outlined.Folder,
            contentDescription = null,
            tint = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
        )
        Box(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.current_location))
    }
}

/** Strom priečinkov -> zoznam (priečinok, hĺbka) v poradí na zobrazenie. */
private fun flattenTree(folders: List<FolderEntity>, excluded: Set<Long>): List<Pair<FolderEntity, Int>> {
    val byParent = folders.filter { it.id !in excluded }.groupBy { it.parentId }
    val out = ArrayList<Pair<FolderEntity, Int>>()
    fun walk(parent: Long?, depth: Int) {
        if (depth > 32) return
        byParent[parent].orEmpty().sortedBy { it.name.lowercase() }.forEach { f ->
            out.add(f to depth)
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}
