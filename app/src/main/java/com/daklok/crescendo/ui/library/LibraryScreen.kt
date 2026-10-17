package com.daklok.crescendo.ui.library

import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.MoreVert
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.setlist.AddToSetlistDialog
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import com.daklok.crescendo.ui.common.ExportRequest
import com.daklok.crescendo.ui.common.ExportPdfDialog
import com.daklok.crescendo.ui.common.FOLDER_COLORS
import com.daklok.crescendo.ui.common.ColorPickerDialog
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.FolderEntity
import com.daklok.crescendo.data.LibrarySort
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.ui.common.AppMark
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.NotificationHost
import com.daklok.crescendo.ui.common.NotificationType
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.rememberHaptic
import com.daklok.crescendo.ui.common.rememberNotifier

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
    onOpenSetlists: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val notifier = rememberNotifier()
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var compactMenuOpen by remember { mutableStateOf(false) }
    val compact = isCompactWidth()

    var newFolderOpen by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var moving by remember { mutableStateOf<MoveTarget?>(null) }
    var coloringFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var exporting by remember { mutableStateOf<ExportRequest?>(null) }
    var bulkDeleteOpen by remember { mutableStateOf(false) }
    var bulkMoveOpen by remember { mutableStateOf(false) }
    /** Skladby, ktoré sa práve pridávajú do setlistu (dialóg). */
    var addingToSetlist by remember { mutableStateOf<List<Long>?>(null) }
    val setlists by vm.setlists.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            notifier.show(m.text, if (m.error) NotificationType.ERROR else NotificationType.SUCCESS)
        }
    }

    // Späť = najprv zruší výber, potom o priečinok vyššie
    BackHandler(enabled = selection.active) { vm.clearSelection() }
    BackHandler(enabled = state.currentFolder != null && !selection.active) {
        haptic(HapticFeedbackType.TextHandleMove)
        vm.goUp()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importPdfs(uris)
    }
    val launchImport = {
        haptic(HapticFeedbackType.LongPress)
        picker.launch(arrayOf("application/pdf"))
    }

    // skenovanie fotoaparátom -> PDF -> dialóg s názvom -> import
    var scannedPdf by remember { mutableStateOf<android.net.Uri?>(null) }
    val startScan = rememberDocumentScanner(
        onScanned = { uri, _ -> scannedPdf = uri },
        onError = { android.widget.Toast.makeText(context, R.string.scan_failed, android.widget.Toast.LENGTH_LONG).show() },
    )
    val launchScan = {
        haptic(HapticFeedbackType.LongPress)
        startScan()
    }

    val current = state.currentFolder

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AnimatedContent(
                    targetState = selection.active,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { -it / 3 }) togetherWith fadeOut(tween(150))
                    },
                    label = "topBar",
                ) { selecting ->
                    if (selecting) {
                        SelectionTopBar(
                            count = selection.count,
                            onClose = { vm.clearSelection() },
                            onSelectAll = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                vm.selectAll()
                            },
                            onMove = { bulkMoveOpen = true },
                            onDelete = { bulkDeleteOpen = true },
                            onAddToSetlist = if (selection.pieces.isNotEmpty()) {
                                { addingToSetlist = state.pieces.filter { it.id in selection.pieces }.map { it.id } }
                            } else null,
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedContent(
                                        targetState = current == null,
                                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                                        label = "markOrBack",
                                    ) { atRoot ->
                                        if (atRoot) {
                                            AppMark(32.dp)
                                        } else {
                                            TonalIconButton(
                                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                                contentDescription = stringResource(R.string.back),
                                                onClick = { vm.goUp() },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    AnimatedContent(
                                        targetState = current?.name ?: "Crescendo",
                                        transitionSpec = {
                                            (fadeIn(tween(220)) + slideInHorizontally { it / 6 }) togetherWith fadeOut(tween(150))
                                        },
                                        label = "title",
                                    ) { title ->
                                        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            },
                            actions = {
                              if (compact) {
                                // mobil: hľadanie, ponuka ⋯ (setlisty, priečinok, zoradenie) a nastavenia
                                TonalIconButton(
                                    icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search),
                                    highlighted = searchOpen,
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) vm.setQuery("")
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    TonalIconButton(
                                        icon = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.more),
                                        onClick = { compactMenuOpen = true },
                                    )
                                    DropdownMenu(
                                        expanded = compactMenuOpen,
                                        onDismissRequest = { compactMenuOpen = false },
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.setlists)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
                                            onClick = { compactMenuOpen = false; onOpenSetlists() },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.new_folder)) },
                                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                                            onClick = { compactMenuOpen = false; newFolderOpen = true },
                                        )
                                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                        Text(
                                            stringResource(R.string.sort),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                        LibrarySort.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(option.labelRes)) },
                                                leadingIcon = {
                                                    if (option == state.sort) Icon(Icons.Outlined.Check, null)
                                                    else Spacer(Modifier.size(24.dp))
                                                },
                                                onClick = {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    vm.setSort(option)
                                                    compactMenuOpen = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                TonalIconButton(
                                    icon = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                    onClick = onOpenSettings,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                              } else {
                                TonalIconButton(
                                    icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search),
                                    highlighted = searchOpen,
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) vm.setQuery("")
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                TonalIconButton(
                                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                                    contentDescription = stringResource(R.string.setlists),
                                    onClick = onOpenSetlists,
                                )
                                Spacer(Modifier.width(8.dp))
                                TonalIconButton(
                                    icon = Icons.Outlined.CreateNewFolder,
                                    contentDescription = stringResource(R.string.new_folder),
                                    onClick = { newFolderOpen = true },
                                )
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    TonalIconButton(
                                        icon = Icons.AutoMirrored.Outlined.Sort,
                                        contentDescription = stringResource(R.string.sort),
                                        onClick = { sortMenuOpen = true },
                                    )
                                    DropdownMenu(
                                        expanded = sortMenuOpen,
                                        onDismissRequest = { sortMenuOpen = false },
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        LibrarySort.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(option.labelRes)) },
                                                leadingIcon = {
                                                    if (option == state.sort) Icon(Icons.Outlined.Check, null)
                                                    else Spacer(Modifier.size(24.dp))
                                                },
                                                onClick = {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    vm.setSort(option)
                                                    sortMenuOpen = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                TonalIconButton(
                                    icon = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                    onClick = onOpenSettings,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                              }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !selection.active,
                    enter = scaleIn(Motion.bouncy()) + fadeIn(),
                    exit = scaleOut(tween(160)) + fadeOut(tween(160)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // naskenovať noty fotoaparátom
                        SmallFloatingActionButton(
                            onClick = { launchScan() },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = stringResource(R.string.scan_music))
                        }
                        ExtendedFloatingActionButton(
                            onClick = { launchImport() },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text(if (current != null) stringResource(R.string.import_here) else stringResource(R.string.import_pdf)) },
                            expanded = !state.globalView,
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                // ── Hľadanie ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }

                // ── Cesta v priečinkoch ───────────────────────────────
                AnimatedVisibility(
                    visible = state.path.isNotEmpty() && !state.globalView,
                    enter = expandVertically(Motion.bouncy()) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Breadcrumbs(path = state.path, onNavigate = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        vm.openFolder(it)
                    })
                }

                Spacer(Modifier.height(8.dp))

                // ── Panel s obsahom (zaoblený ako v Bible Lock Screen) ─
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it.currentFolder?.id to it.globalView },
                        transitionSpec = {
                            val forward = targetState.path.size >= initialState.path.size
                            folderTransition(forward)
                        },
                        label = "folder",
                    ) { s ->
                        LibraryGrid(
                            s = s,
                            vm = vm,
                            selection = selection,
                            onColorFolder = { coloringFolder = it },
                            onAddToSetlist = { piece -> addingToSetlist = listOf(piece.id) },
                            onExport = { piece ->
                                exporting = ExportRequest(
                                    title = piece.title,
                                    pdf = vm.pdfFile(piece),
                                    notes = { vm.loadAnnotations(piece.id) },
                                )
                            },
                            onOpenPiece = onOpenPiece,
                            onImport = { launchImport() },
                            onScan = { launchScan() },
                            onRenameFolder = { renamingFolder = it },
                            onDeleteFolder = { deletingFolder = it },
                            onMove = { moving = it },
                        )
                    }
                }
            }
        }

        NotificationHost(
            notifier = notifier,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }

    // ── Dialógy ───────────────────────────────────────────────────────

    if (newFolderOpen) {
        NameDialog(
            title = if (current != null) stringResource(R.string.new_folder_in, current.name) else stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                haptic(HapticFeedbackType.LongPress)
                vm.createFolder(it)
                newFolderOpen = false
            },
            onDismiss = { newFolderOpen = false },
        )
    }

    renamingFolder?.let { folder ->
        NameDialog(
            title = stringResource(R.string.rename_folder),
            initial = folder.name,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { vm.renameFolder(folder, it); renamingFolder = null },
            onDismiss = { renamingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Folder, null) },
            title = { Text(stringResource(R.string.remove_folder_title)) },
            text = {
                Text(stringResource(R.string.remove_folder_text, folder.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    vm.deleteFolder(folder)
                    deletingFolder = null
                }) { Text(stringResource(R.string.remove_folder)) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.back)) } },
        )
    }

    scannedPdf?.let { uri ->
        val defaultName = remember(uri) {
            context.getString(
                R.string.scan_default_name,
                java.text.SimpleDateFormat("d. M. yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            )
        }
        NameDialog(
            title = stringResource(R.string.scan_name_title),
            initial = defaultName,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                haptic(HapticFeedbackType.LongPress)
                vm.importScan(uri, name)
                scannedPdf = null
            },
            onDismiss = { scannedPdf = null },
        )
    }

    coloringFolder?.let { folder ->
        ColorPickerDialog(
            title = stringResource(R.string.folder_color_title, folder.name),
            initial = folder.color ?: FOLDER_COLORS[7],
            presets = FOLDER_COLORS,
            onPick = {
                haptic(HapticFeedbackType.LongPress)
                vm.setFolderColor(folder, it)
                coloringFolder = null
            },
            onReset = {
                vm.setFolderColor(folder, null)
                coloringFolder = null
            },
            onDismiss = { coloringFolder = null },
        )
    }

    exporting?.let { req ->
        ExportPdfDialog(request = req, onDismiss = { exporting = null })
    }

    if (bulkDeleteOpen) {
        val pieceCount = selection.pieces.size
        val folderCount = selection.folders.size
        AlertDialog(
            onDismissRequest = { bulkDeleteOpen = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(pluralStringResource(R.plurals.delete_n_title, selection.count, selection.count)) },
            text = {
                Text(
                    buildString {
                        if (pieceCount > 0) append(context.getString(R.string.delete_n_pieces_text))
                        if (folderCount > 0) {
                            if (isNotEmpty()) append("\n\n")
                            append(context.getString(R.string.delete_n_folders_text))
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    vm.deleteSelected()
                    bulkDeleteOpen = false
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { bulkDeleteOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    addingToSetlist?.let { ids ->
        AddToSetlistDialog(
            setlists = setlists,
            onPick = { id ->
                haptic(HapticFeedbackType.LongPress)
                vm.addToSetlist(ids, id)
                addingToSetlist = null
                vm.clearSelection()
            },
            onCreate = { name ->
                vm.addToSetlist(ids, null, name)
                addingToSetlist = null
                vm.clearSelection()
            },
            onDismiss = { addingToSetlist = null },
        )
    }

    if (bulkMoveOpen) {
        MoveToFolderDialog(
            folders = state.allFolders,
            currentLocation = state.currentFolder?.id,
            excluded = vm.selectionExcluded(),
            onSelect = {
                haptic(HapticFeedbackType.LongPress)
                vm.moveSelected(it)
                bulkMoveOpen = false
            },
            onDismiss = { bulkMoveOpen = false },
        )
    }

    when (val m = moving) {
        is MoveTarget.Piece -> MoveToFolderDialog(
            folders = state.allFolders,
            currentLocation = m.piece.folderId,
            onSelect = {
                haptic(HapticFeedbackType.LongPress)
                vm.movePiece(m.piece, it)
                moving = null
            },
            onDismiss = { moving = null },
        )
        is MoveTarget.Folder -> MoveToFolderDialog(
            folders = state.allFolders,
            currentLocation = m.folder.parentId,
            excluded = vm.descendantsOf(m.folder.id),
            onSelect = {
                haptic(HapticFeedbackType.LongPress)
                vm.moveFolder(m.folder, it)
                moving = null
            },
            onDismiss = { moving = null },
        )
        null -> Unit
    }
}

/** Do priečinka: obsah prichádza sprava; späť: zľava. */
private fun folderTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return (fadeIn(tween(260, delayMillis = 60)) + slideInHorizontally(Motion.gentle()) { dir * it / 4 }) togetherWith
        (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -dir * it / 6 })
}

@Composable
private fun LibraryGrid(
    s: LibraryUiState,
    vm: LibraryViewModel,
    selection: Selection,
    onColorFolder: (FolderEntity) -> Unit,
    onAddToSetlist: (PieceEntity) -> Unit,
    onExport: (PieceEntity) -> Unit,
    onOpenPiece: (Long) -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onMove: (MoveTarget) -> Unit,
) {
    val haptic = rememberHaptic()
    val gridState = rememberLazyGridState()
    val current = s.currentFolder
    // mobil: menšie karty (2 stĺpce), užšie okraje
    val compact = isCompactWidth()
    val side = if (compact) 12.dp else 20.dp
    val gap = if (compact) 12.dp else 16.dp

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = if (compact) 150.dp else 200.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = side, end = side, top = if (compact) 20.dp else 28.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (s.importing) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "importing") {
                Row(
                    modifier = Modifier.animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.importing), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        val hero = s.continueReading
        if (hero != null && !s.globalView) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                ContinueCard(
                    piece = hero,
                    thumb = vm.thumbFile(hero),
                    onOpen = { onOpenPiece(hero.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (s.totalCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                Row(
                    modifier = Modifier.animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = !s.favoritesOnly,
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            vm.setFavoritesOnly(false)
                        },
                        label = { Text(if (current != null) stringResource(R.string.this_folder) else stringResource(R.string.all_count, s.totalCount)) },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = if (!s.favoritesOnly) {
                            { Icon(Icons.Outlined.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                    FilterChip(
                        selected = s.favoritesOnly,
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            vm.setFavoritesOnly(true)
                        },
                        label = { Text(stringResource(R.string.favorites)) },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                if (s.favoritesOnly) Icons.Outlined.Check else Icons.Outlined.StarOutline,
                                null,
                                Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }

        // ── Priečinky ──
        if (s.folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-folders") {
                SectionHeader(Icons.Outlined.Folder, stringResource(R.string.folders), Modifier.animateItem())
            }
            // na mobile sú priečinky cez celú šírku (v úzkej karte by sa názov nezmestil)
            items(
                s.folders,
                key = { "f${it.id}" },
                span = { if (compact) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            ) { folder ->
                FolderCard(
                    folder = folder,
                    itemCount = s.folderCounts[folder.id] ?: 0,
                    onOpen = { vm.openFolder(folder.id) },
                    onRename = { onRenameFolder(folder) },
                    onColor = { onColorFolder(folder) },
                    onMove = { onMove(MoveTarget.Folder(folder)) },
                    onDelete = { onDeleteFolder(folder) },
                    modifier = Modifier.animateItem(),
                    selectionMode = selection.active,
                    selected = folder.id in selection.folders,
                    onSelect = { vm.toggleFolder(folder.id) },
                )
            }
        }

        // ── Skladby ──
        if (s.loaded && s.totalCount == 0 && s.folders.isEmpty() && current == null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyLibrary(onImport = onImport, onScan = onScan, modifier = Modifier.animateItem())
            }
        } else if (s.pieces.isEmpty() && (s.globalView || s.folders.isEmpty())) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                EmptyHint(
                    text = if (s.globalView) stringResource(R.string.nothing_found)
                    else stringResource(R.string.folder_is_empty),
                    modifier = Modifier.animateItem(),
                )
            }
        } else if (s.pieces.isNotEmpty()) {
            if (s.folders.isNotEmpty() || s.globalView) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-pieces") {
                    SectionHeader(
                        Icons.Outlined.MusicNote,
                        if (s.globalView) stringResource(R.string.results_count, s.pieces.size) else stringResource(R.string.pieces),
                        Modifier.animateItem(),
                    )
                }
            }
            items(s.pieces, key = { "p${it.id}" }) { piece ->
                PieceCard(
                    piece = piece,
                    thumb = vm.thumbFile(piece),
                    onOpen = { onOpenPiece(piece.id) },
                    onToggleFavorite = { vm.toggleFavorite(piece) },
                    onRename = { vm.rename(piece, it) },
                    onDelete = { vm.delete(piece) },
                    onMove = { onMove(MoveTarget.Piece(piece)) },
                    onExport = { onExport(piece) },
                    onAddToSetlist = { onAddToSetlist(piece) },
                    modifier = Modifier.animateItem(),
                    selectionMode = selection.active,
                    selected = piece.id in selection.pieces,
                    onSelect = { vm.togglePiece(piece.id) },
                )
            }
        }
    }
}

/** Horná lišta v režime výberu: počet, vybrať všetko, presunúť, zmazať. */
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToSetlist: (() -> Unit)?,
) {
    TopAppBar(
        navigationIcon = {
            TonalIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cancel_selection),
                onClick = onClose,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
        title = {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "count",
            ) { n ->
                Text(
                    pluralStringResource(R.plurals.selected_n, n, n),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        },
        actions = {
            TonalIconButton(
                icon = Icons.Outlined.SelectAll,
                contentDescription = stringResource(R.string.select_all),
                onClick = onSelectAll,
            )
            Spacer(Modifier.width(8.dp))
            if (onAddToSetlist != null) {
                TonalIconButton(
                    icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = stringResource(R.string.add_to_setlist),
                    onClick = onAddToSetlist,
                )
                Spacer(Modifier.width(8.dp))
            }
            TonalIconButton(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                contentDescription = stringResource(R.string.move_to_folder),
                onClick = onMove,
            )
            Spacer(Modifier.width(8.dp))
            TonalIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.remove),
                onClick = onDelete,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun Breadcrumbs(path: List<FolderEntity>, onNavigate: (Long?) -> Unit) {
    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        item {
            TextButton(onClick = { onNavigate(null) }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library))
            }
        }
        itemsIndexed(path, key = { _, f -> f.id }) { i, folder ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                val last = i == path.lastIndex
                TextButton(
                    onClick = { onNavigate(folder.id) },
                    enabled = !last,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        folder.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 24.dp, horizontal = 4.dp),
    )
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Outlined.LibraryMusic, active = true, size = 88.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_pdf))
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onScan, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Outlined.DocumentScanner, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_music))
        }
    }
}

