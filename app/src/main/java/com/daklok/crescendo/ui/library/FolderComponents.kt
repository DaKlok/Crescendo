package com.daklok.crescendo.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
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
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(28.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (itemCount) {
                        0 -> "Prázdny"
                        1 -> "1 položka"
                        in 2..4 -> "$itemCount položky"
                        else -> "$itemCount položiek"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Viac")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Premenovať") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Presunúť") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Zrušiť priečinok") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
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
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Názov") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
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
        title = { Text("Presunúť do…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    DestinationRow(
                        name = "Knižnica",
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
                        selected = currentLocation == folder.id,
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
private fun DestinationRow(
    name: String,
    depth: Int,
    isRoot: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selected, onClick = onClick)
            .padding(start = (depth * 20).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isRoot) Icons.Rounded.LibraryMusic else Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
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
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Aktuálne umiestnenie")
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
