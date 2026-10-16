package com.daklok.crescendo.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.FolderEntity
import com.daklok.crescendo.data.LibrarySort
import com.daklok.crescendo.data.PieceEntity

/** Čo sa práve presúva (skladba alebo priečinok). */
private sealed interface MoveTarget {
    data class Piece(val piece: PieceEntity) : MoveTarget
    data class Folder(val folder: FolderEntity) : MoveTarget
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenPiece: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    var newFolderOpen by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var moving by remember { mutableStateOf<MoveTarget?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Späť = o priečinok vyššie
    BackHandler(enabled = state.currentFolder != null) { vm.goUp() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importPdfs(uris)
    }
    val launchImport = { picker.launch(arrayOf("application/pdf")) }

    val current = state.currentFolder

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        current?.name ?: "Knižnica",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { vm.goUp() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Späť")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.setQuery("")
                    }) {
                        Icon(
                            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Hľadať",
                        )
                    }
                    IconButton(onClick = { newFolderOpen = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Nový priečinok")
                    }
                    Column {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Zoradiť")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (option == state.sort) Icon(Icons.Rounded.Check, null)
                                        else Spacer(Modifier.size(24.dp))
                                    },
                                    onClick = {
                                        vm.setSort(option)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Nastavenia")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { launchImport() }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(if (current != null) "Importovať sem" else "Importovať PDF")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedVisibility(visible = searchOpen) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Hľadať v celej knižnici…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(32.dp),
                    )
                }
            }

            // cesta: Knižnica › A › B
            if (state.path.isNotEmpty() && !state.globalView) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Breadcrumbs(path = state.path, onNavigate = vm::openFolder)
                }
            }

            if (state.importing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Importujem…", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            val hero = state.continueReading
            if (hero != null && !state.globalView) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(
                        piece = hero,
                        thumb = vm.thumbFile(hero),
                        onOpen = { onOpenPiece(hero.id) },
                    )
                }
            }

            if (state.totalCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = !state.favoritesOnly,
                            onClick = { vm.setFavoritesOnly(false) },
                            label = { Text(if (current != null) "Tento priečinok" else "Všetko (${state.totalCount})") },
                        )
                        FilterChip(
                            selected = state.favoritesOnly,
                            onClick = { vm.setFavoritesOnly(true) },
                            label = { Text("Obľúbené") },
                            leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            // ---- priečinky ----
            if (state.folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Priečinky")
                }
                items(state.folders, key = { "f${it.id}" }) { folder ->
                    FolderCard(
                        folder = folder,
                        itemCount = state.folderCounts[folder.id] ?: 0,
                        onOpen = { vm.openFolder(folder.id) },
                        onRename = { renamingFolder = folder },
                        onMove = { moving = MoveTarget.Folder(folder) },
                        onDelete = { deletingFolder = folder },
                    )
                }
            }

            // ---- skladby ----
            if (state.loaded && state.totalCount == 0 && state.folders.isEmpty() && current == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary(onImport = { launchImport() })
                }
            } else if (state.pieces.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when {
                            state.globalView -> "Nič sa nenašlo."
                            state.folders.isNotEmpty() -> ""
                            else -> "Priečinok je prázdny. Importuj PDF alebo sem presuň skladby cez ponuku ⋮ na karte."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                if (state.folders.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Skladby")
                    }
                }
                items(state.pieces, key = { "p${it.id}" }) { piece ->
                    PieceCard(
                        piece = piece,
                        thumb = vm.thumbFile(piece),
                        onOpen = { onOpenPiece(piece.id) },
                        onToggleFavorite = { vm.toggleFavorite(piece) },
                        onRename = { vm.rename(piece, it) },
                        onDelete = { vm.delete(piece) },
                        onMove = { moving = MoveTarget.Piece(piece) },
                    )
                }
            }
        }
    }

    // ---- dialógy ----

    if (newFolderOpen) {
        NameDialog(
            title = if (current != null) "Nový priečinok v „${current.name}“" else "Nový priečinok",
            initial = "",
            confirmLabel = "Vytvoriť",
            onConfirm = { vm.createFolder(it); newFolderOpen = false },
            onDismiss = { newFolderOpen = false },
        )
    }

    renamingFolder?.let { folder ->
        NameDialog(
            title = "Premenovať priečinok",
            initial = folder.name,
            confirmLabel = "Uložiť",
            onConfirm = { vm.renameFolder(folder, it); renamingFolder = null },
            onDismiss = { renamingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Zrušiť priečinok?") },
            text = {
                Text("Priečinok „${folder.name}“ sa zruší. Skladby a podpriečinky v ňom sa nezmažú – presunú sa o úroveň vyššie.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteFolder(folder); deletingFolder = null }) { Text("Zrušiť priečinok") }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Späť") } },
        )
    }

    when (val m = moving) {
        is MoveTarget.Piece -> MoveToFolderDialog(
            folders = state.allFolders,
            currentLocation = m.piece.folderId,
            onSelect = { vm.movePiece(m.piece, it); moving = null },
            onDismiss = { moving = null },
        )
        is MoveTarget.Folder -> MoveToFolderDialog(
            folders = state.allFolders,
            currentLocation = m.folder.parentId,
            excluded = vm.descendantsOf(m.folder.id),
            onSelect = { vm.moveFolder(m.folder, it); moving = null },
            onDismiss = { moving = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Breadcrumbs(path: List<FolderEntity>, onNavigate: (Long?) -> Unit) {
    LazyRow(verticalAlignment = Alignment.CenterVertically) {
        item {
            TextButton(onClick = { onNavigate(null) }) {
                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Knižnica")
            }
        }
        itemsIndexed(path) { i, folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val last = i == path.lastIndex
                TextButton(onClick = { onNavigate(folder.id) }, enabled = !last) {
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(28.dp)
                        .size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Knižnica je zatiaľ prázdna",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Importuj PDF s notami a budú tu po ruke. Súbory zostanú len v tvojom zariadení.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onImport) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Importovať PDF")
            }
        }
    }
}
