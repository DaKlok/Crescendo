package com.daklok.crescendo.ui.library

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlin.math.max
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import com.daklok.crescendo.data.LibraryLayout
import com.daklok.crescendo.ui.common.FabMenuItem
import com.daklok.crescendo.ui.common.FabMenu
import com.daklok.crescendo.data.PracticeGoal
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.DocumentScanner
import com.daklok.crescendo.ui.common.isCompactWidth
import com.daklok.crescendo.ui.setlist.AddToSetlistDialog
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.FolderEntity
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.ui.common.AppMark
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
    onOpenPractice: () -> Unit = {},
    onOpenCatalog: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val notifier = rememberNotifier()
    var searchOpen by remember { mutableStateOf(false) }
    /** Rozbalená ponuka pridania nôt (plávajúce tlačidlo). */
    var fabOpen by remember { mutableStateOf(false) }
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
    val today by vm.today.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // denný cieľ sa mení len v denníku cvičenia; po návrate sa knižnica skladá nanovo
    val goalMin = remember { PracticeGoal.get(context).coerceAtLeast(1) }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            notifier.show(m.text, if (m.error) NotificationType.ERROR else NotificationType.SUCCESS, m.actionLabel, m.onAction)
        }
    }

    // Späť: najprv zavrie ponuku pridania, potom zruší výber, potom o priečinok vyššie
    BackHandler(enabled = fabOpen) { fabOpen = false }
    BackHandler(enabled = selection.active && !fabOpen) { vm.clearSelection() }
    BackHandler(enabled = state.currentFolder != null && !selection.active && !fabOpen) {
        haptic(HapticFeedbackType.TextHandleMove)
        vm.goUp()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importPdfs(uris)
    }
    val pro = (LocalContext.current.applicationContext as com.daklok.crescendo.CrescendoApp).pro
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    val pieceCount by pro.pieceCount.collectAsStateWithLifecycle()
    val launchImport = {
        haptic(HapticFeedbackType.LongPress)
        // PDF alebo zdieľaná skladba z Crescenda (.crescendo = ZIP); bez Pro najviac 10 skladieb
        if (pro.allowNewPieces()) picker.launch(arrayOf("application/pdf", "application/zip", "application/octet-stream"))
    }

    // doplnenie strán do existujúcej skladby (súbor alebo sken)
    var appendTarget by remember { mutableStateOf<PieceEntity?>(null) }
    var editingDetails by remember { mutableStateOf<PieceEntity?>(null) }
    var editingPages by remember { mutableStateOf<PieceEntity?>(null) }
    var splitting by remember { mutableStateOf<PieceEntity?>(null) }
    var pendingAppend by remember { mutableStateOf<Pair<PieceEntity, Boolean>?>(null) }
    val appendPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = pendingAppend
        if (uri != null && p != null) vm.appendToPiece(p.first, uri, p.second)
        pendingAppend = null
    }

    // skenovanie fotoaparátom -> PDF -> dialóg s názvom -> import (alebo doplnenie strán)
    var scannedPdf by remember { mutableStateOf<android.net.Uri?>(null) }
    val startScan = rememberDocumentScanner(
        onScanned = { uri, _ ->
            val p = pendingAppend
            if (p != null) {
                vm.appendToPiece(p.first, uri, p.second)
                pendingAppend = null
            } else {
                scannedPdf = uri
            }
        },
        onError = { android.widget.Toast.makeText(context, R.string.scan_failed, android.widget.Toast.LENGTH_LONG).show() },
    )
    val launchScan = {
        haptic(HapticFeedbackType.LongPress)
        // nový sken (nie doplnenie): zrušíme prípadné nedokončené doplnenie
        pendingAppend = null
        if (pro.allowNewPieces()) startScan()
    }

    val current = state.currentFolder
    val home = HomeInfo(
        todaySec = today.todaySec,
        streak = today.streak,
        goalMin = goalMin,
        setlistCount = setlists.size,
        isPro = isPro,
    )

    // výber skladieb: ponuka pridania sa zavrie
    LaunchedEffect(selection.active) { if (selection.active) fabOpen = false }

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
                        LibraryTopBar(
                            title = current?.name ?: stringResource(R.string.library),
                            atRoot = current == null,
                            searchOpen = searchOpen,
                            onSearch = {
                                searchOpen = !searchOpen
                                if (!searchOpen) vm.setQuery("")
                            },
                            onBack = { vm.goUp() },
                            onNewFolder = { newFolderOpen = true },
                            onOpenSettings = onOpenSettings,
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
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

                Spacer(Modifier.height(6.dp))

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
                            home = home,
                            onColorFolder = { coloringFolder = it },
                            onAddToSetlist = { piece -> addingToSetlist = listOf(piece.id) },
                            onAppend = { piece -> appendTarget = piece },
                            onEditDetails = { piece -> editingDetails = piece },
                            onEditPages = { piece -> editingPages = piece },
                            onSplit = { piece -> splitting = piece },
                            onExport = { piece ->
                                exporting = ExportRequest(
                                    piece = piece,
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
                            onOpenCatalog = onOpenCatalog,
                            onOpenPractice = onOpenPractice,
                            onOpenSetlists = onOpenSetlists,
                        )
                    }
                }
            }
        }

        // ── Ponuka pridania nôt: stmavené pozadie a plávajúce tlačidlo ──
        val scrim by animateFloatAsState(if (fabOpen) 1f else 0f, tween(220), label = "scrim")
        val scrimInteraction = remember { MutableInteractionSource() }
        if (scrim > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = scrimInteraction, indication = null) { fabOpen = false },
            )
        }
        AnimatedVisibility(
            visible = !selection.active,
            enter = scaleIn(Motion.bouncy(), transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
            exit = scaleOut(tween(160), transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = if (compact) 16.dp else 24.dp, bottom = if (compact) 16.dp else 24.dp),
        ) {
            FabMenu(
                expanded = fabOpen,
                onExpandedChange = { fabOpen = it },
                items = listOf(
                    FabMenuItem(
                        icon = Icons.Outlined.FileOpen,
                        label = if (current != null) stringResource(R.string.import_here) else stringResource(R.string.import_pdf),
                        onClick = { launchImport() },
                    ),
                    FabMenuItem(
                        icon = Icons.Outlined.DocumentScanner,
                        label = stringResource(R.string.scan_music),
                        onClick = { launchScan() },
                    ),
                ),
                contentDescription = if (fabOpen) stringResource(R.string.close) else stringResource(R.string.fab_add_music),
                large = !compact,
                above = {
                    // verzia zadarmo: koľko skladieb ešte zostáva (ťuk: Pro)
                    if (!isPro) {
                        com.daklok.crescendo.ui.pro.LibraryQuota(
                            count = pieceCount,
                            limit = com.daklok.crescendo.data.pro.ProManager.FREE_PIECES,
                            onClick = { pro.showPaywall(com.daklok.crescendo.data.pro.ProFeature.LIBRARY) },
                        )
                    }
                },
            )
        }

        NotificationHost(
            notifier = notifier,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (compact) 96.dp else 112.dp),
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
            icon = Icons.Outlined.CreateNewFolder,
        )
    }

    renamingFolder?.let { folder ->
        NameDialog(
            title = stringResource(R.string.rename_folder),
            initial = folder.name,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { vm.renameFolder(folder, it); renamingFolder = null },
            onDismiss = { renamingFolder = null },
            icon = Icons.Outlined.Folder,
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            shape = RoundedCornerShape(28.dp),
            icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Folder, com.daklok.crescendo.ui.common.ExpressiveShapes.Squircle, com.daklok.crescendo.ui.common.Accent.Secondary) },
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

    editingPages?.let { piece ->
        PageEditorDialog(
            piece = piece,
            pdf = vm.pdfFile(piece),
            onSave = { pages ->
                haptic(HapticFeedbackType.LongPress)
                vm.editPages(piece, pages)
                editingPages = null
            },
            onDismiss = { editingPages = null },
        )
    }

    splitting?.let { piece ->
        SplitDialog(
            piece = piece,
            onSplit = { parts, keep ->
                haptic(HapticFeedbackType.LongPress)
                // nové skladby z rozdelenia sa rátajú do limitu verzie zadarmo
                if (pro.allowNewPieces(parts.size - if (keep) 0 else 1)) vm.splitPiece(piece, parts, keep)
                splitting = null
            },
            onDismiss = { splitting = null },
        )
    }

    editingDetails?.let { piece ->
        PieceDetailsDialog(
            piece = piece,
            allTags = state.allTags,
            onRecognize = { vm.recognize(piece) },
            onSave = {
                haptic(HapticFeedbackType.LongPress)
                vm.updateDetails(it)
                editingDetails = null
            },
            onDismiss = { editingDetails = null },
        )
    }

    appendTarget?.let { piece ->
        AppendPagesDialog(
            piece = piece,
            onPickFile = { atStart ->
                pendingAppend = piece to atStart
                appendTarget = null
                appendPicker.launch(arrayOf("application/pdf"))
            },
            onScan = { atStart ->
                pendingAppend = piece to atStart
                appendTarget = null
                startScan()
            },
            onDismiss = { appendTarget = null },
        )
    }

    scannedPdf?.let { uri ->
        val defaultName = remember(uri) {
            context.getString(
                R.string.scan_default_name,
                java.text.SimpleDateFormat("d. M. yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            )
        }
        // názov a skladateľ z prvej strany skenu (kým píšeš, prečíta sa na pozadí)
        val recognizeOn = vm.settings.collectAsStateWithLifecycle().value.recognizeTitles
        var scanGuess by remember(uri) { mutableStateOf<com.daklok.crescendo.data.TitleRecognizer.Result?>(null) }
        LaunchedEffect(uri, recognizeOn) {
            if (recognizeOn) scanGuess = com.daklok.crescendo.data.TitleRecognizer.recognize(context, uri)
        }
        NameDialog(
            title = stringResource(R.string.scan_name_title),
            initial = defaultName,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                haptic(HapticFeedbackType.LongPress)
                vm.importScan(uri, name, scanGuess?.composer)
                scannedPdf = null
            },
            onDismiss = { scannedPdf = null },
            icon = Icons.Outlined.DocumentScanner,
            suggestion = scanGuess?.title,
            supporting = scanGuess?.composer?.let { stringResource(R.string.scan_recognized_composer, it) },
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
            icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Delete, destructive = true) },
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

/**
 * Horná lišta knižnice v jednom riadku: logo (v priečinku späť), veľký výrazný nadpis
 * a tri akcie (hľadať, nový priečinok, nastavenia). Ostatné ciele sú v dlaždiciach.
 * Jeden riadok šetrí výšku, čo je na tablete na šírku dôležité.
 */
@Composable
private fun LibraryTopBar(
    title: String,
    atRoot: Boolean,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val compact = isCompactWidth()
    TopAppBar(
        expandedHeight = TopBarHeight,
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally { it / 6 }) togetherWith fadeOut(tween(150))
                },
                label = "title",
            ) { t ->
                Text(
                    t,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            AnimatedContent(
                targetState = atRoot,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "markOrBack",
            ) { root ->
                if (root) {
                    AppMark(40.dp, Modifier.padding(start = 12.dp, end = 10.dp))
                } else {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp, end = 10.dp),
                    )
                }
            }
        },
        actions = {
            TonalIconButton(
                icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search),
                highlighted = searchOpen,
                onClick = onSearch,
            )
            Spacer(Modifier.width(8.dp))
            TonalIconButton(
                icon = Icons.Outlined.CreateNewFolder,
                contentDescription = stringResource(R.string.new_folder),
                onClick = onNewFolder,
            )
            Spacer(Modifier.width(8.dp))
            TonalIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
                onClick = onOpenSettings,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** Výška hornej lišty knižnice (rovnaká aj v režime výberu, aby obsah neposkočil). */
private val TopBarHeight = 72.dp

@Composable
private fun LibraryGrid(
    s: LibraryUiState,
    vm: LibraryViewModel,
    selection: Selection,
    home: HomeInfo,
    onColorFolder: (FolderEntity) -> Unit,
    onAddToSetlist: (PieceEntity) -> Unit,
    onAppend: (PieceEntity) -> Unit,
    onEditDetails: (PieceEntity) -> Unit,
    onEditPages: (PieceEntity) -> Unit,
    onSplit: (PieceEntity) -> Unit,
    onExport: (PieceEntity) -> Unit,
    onOpenPiece: (Long) -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onMove: (MoveTarget) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenSetlists: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val current = s.currentFolder
    // mobil: 2 stĺpce listov, užšie okraje
    val compact = isCompactWidth()
    val side = if (compact) 14.dp else 24.dp
    val gap = if (compact) 12.dp else 18.dp
    val atRoot = current == null && !s.globalView
    val libraryEmpty = s.loaded && s.totalCount == 0 && s.folders.isEmpty() && current == null

    // ── rozloženie: priečinky majú vlastný počet stĺpcov, skladby podľa nastavenia (mriežka / zoznam) ──
    val settings by vm.settings.collectAsStateWithLifecycle()
    val avail = LocalConfiguration.current.screenWidthDp.dp - side * 2
    fun fit(min: Dp): Int = max(1, ((avail + gap) / (min + gap)).toInt())
    val list = settings.libraryLayout == LibraryLayout.LIST
    val folderFit = if (compact) 1 else fit(184.dp)
    // najviac toľko v riadku, aby bol list aspoň 96 dp široký (a nie viac ako 7)
    val maxCols = fit(96.dp).coerceIn(2, 7)
    val pieceCols = when {
        list -> 1
        settings.libraryColumns <= 0 -> fit(if (compact) 148.dp else 184.dp)
        else -> settings.libraryColumns.coerceAtMost(maxCols)
    }
    // spoločná mriežka: počet stĺpcov deliteľný oboma, každá položka zaberie svoj diel
    // (keby bolo stĺpcov priveľa a medzery by sa nezmestili, priečinky sa prispôsobia skladbám)
    val folderCols = if ((lcm(folderFit, pieceCols) - 1) * gap.value > avail.value * 0.6f) {
        (folderFit downTo 1).first { pieceCols % it == 0 }
    } else {
        folderFit
    }
    val totalCols = lcm(folderCols, pieceCols)
    val cell = (avail - gap * (pieceCols - 1)) / pieceCols
    val dense = !list && cell < 150.dp
    val columnOptions = listOf(0) + (2..maxCols).toList()
    val weekly by vm.weekly.collectAsStateWithLifecycle()

    // štipnutie ako v Galérii: roztiahnutie = väčšie listy (až po zoznam), stiahnutie = viac v riadku
    val gridHaptic = rememberHaptic()
    var pinchScale by remember { mutableFloatStateOf(1f) }
    val shownScale by animateFloatAsState(pinchScale, label = "pinchScale")
    var gridHint by remember { mutableStateOf<String?>(null) }
    var gridHintTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(gridHintTick) {
        if (gridHint != null) {
            kotlinx.coroutines.delay(1100)
            gridHint = null
        }
    }
    val listLabel = stringResource(R.string.lib_view_list)
    val perRowLabel = stringResource(R.string.lib_pinch_per_row)
    val stepGrid by rememberUpdatedState { dir: Int ->
        val cur = if (list) 1 else pieceCols
        val next = (cur + dir).coerceIn(1, maxCols)
        if (next != cur) {
            if (next == 1) vm.setView(LibraryLayout.LIST, settings.libraryColumns) else vm.setView(LibraryLayout.GRID, next)
            gridHint = if (next == 1) listLabel else perRowLabel.format(next)
            gridHintTick++
            gridHaptic(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(totalCols),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var acc = 1f
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            acc *= event.calculateZoom()
                            pinchScale = acc.coerceIn(0.88f, 1.12f)
                            if (acc > 1.28f) {
                                stepGrid(-1)
                                acc = 1f
                            } else if (acc < 0.78f) {
                                stepGrid(1)
                                acc = 1f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinching) pinchScale = 1f
                }
            }
            .graphicsLayer {
                scaleX = shownScale
                scaleY = shownScale
            },
        contentPadding = PaddingValues(start = side, end = side, top = if (compact) 18.dp else 24.dp, bottom = 136.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(if (list) 10.dp else if (compact) 16.dp else 20.dp),
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

        // prázdna knižnica: najprv výzva na import, pod ňou dlaždice (napr. voľné noty)
        if (libraryEmpty) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyLibrary(onImport = onImport, onScan = onScan, modifier = Modifier.animateItem())
            }
        }

        // ── Úvod: Pokračuj a dlaždice ──
        if (atRoot) {
            val hero = s.continueReading
            item(span = { GridItemSpan(maxLineSpan) }, key = "home") {
                LibraryHomeTop(
                    hero = hero,
                    heroThumb = hero?.let { vm.thumbFile(it) },
                    info = home,
                    onOpenHero = { hero?.let { onOpenPiece(it.id) } },
                    onCatalog = onOpenCatalog,
                    onPractice = onOpenPractice,
                    onSetlists = onOpenSetlists,
                    modifier = Modifier.animateItem(),
                )
            }
            // ďalšie naposledy otvorené skladby: rýchly návrat jedným ťuknutím
            if (hero != null && s.recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    LazyRow(
                        modifier = Modifier.animateItem(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.recent.size, key = { s.recent[it].id }) { i ->
                            val p = s.recent[i]
                            RecentChip(piece = p, thumb = vm.thumbFile(p), onOpen = { onOpenPiece(p.id) })
                        }
                    }
                }
            }
            // týždeň v skratke (raz za týždeň, kým ho nezavrieš)
            weekly?.let { w ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "weekly") {
                    com.daklok.crescendo.ui.stats.WeeklyCard(
                        summary = w.summary,
                        topTitle = w.topTitle,
                        title = stringResource(R.string.weekly_title_your),
                        goalSec = w.goalSec,
                        onClick = onOpenPractice,
                        onDismiss = { vm.dismissWeekly() },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (s.totalCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                FilterPills(
                    s = s,
                    onAll = {
                        vm.setFavoritesOnly(false)
                        vm.setTagFilter(null)
                    },
                    onFavorites = { vm.setFavoritesOnly(!s.favoritesOnly) },
                    onTag = { tag -> vm.setTagFilter(if (s.tagFilter == tag) null else tag) },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 4.dp),
                )
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
                span = { if (compact) GridItemSpan(maxLineSpan) else GridItemSpan(totalCols / folderCols) },
            ) { folder ->
                FolderCard(
                    folder = folder,
                    itemCount = s.folderCounts[folder.id] ?: 0,
                    thumbs = s.folderThumbs[folder.id].orEmpty().map { vm.thumbFile(it) },
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
        if (!libraryEmpty && s.pieces.isEmpty() && (s.globalView || s.folders.isEmpty())) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                EmptyHint(
                    text = if (s.globalView) stringResource(R.string.nothing_found)
                    else stringResource(R.string.folder_is_empty),
                    modifier = Modifier.animateItem(),
                )
            }
        } else if (s.pieces.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-pieces") {
                PiecesHeader(
                    title = if (s.globalView) stringResource(R.string.results_count, s.pieces.size) else stringResource(R.string.pieces),
                    sort = s.sort,
                    onSort = vm::setSort,
                    layout = settings.libraryLayout,
                    columns = settings.libraryColumns,
                    columnOptions = columnOptions,
                    onLayout = vm::setLayout,
                    onColumns = vm::setColumns,
                    modifier = Modifier.animateItem(),
                )
            }
            items(s.pieces, key = { "p${it.id}" }, span = { GridItemSpan(totalCols / pieceCols) }) { piece ->
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
                    onAppend = { onAppend(piece) },
                    onEditDetails = { onEditDetails(piece) },
                    onEditPages = { onEditPages(piece) },
                    onSplit = { onSplit(piece) },
                    modifier = Modifier.animateItem(),
                    selectionMode = selection.active,
                    selected = piece.id in selection.pieces,
                    onSelect = { vm.togglePiece(piece.id) },
                    list = list,
                    dense = dense,
                )
            }
        }
    }
    // po štipnutí krátko ukáže nové rozloženie
    androidx.compose.animation.AnimatedVisibility(
        visible = gridHint != null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp),
        enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Text(
                gridHint.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    }
}

/** Najmenší spoločný násobok (počet stĺpcov spoločnej mriežky priečinkov a skladieb). */
private fun lcm(a: Int, b: Int): Int {
    tailrec fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)
    return a / gcd(a, b) * b
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
        expandedHeight = TopBarHeight,
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
            TextButton(onClick = { onNavigate(null) }, shape = RoundedCornerShape(50)) {
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
                    shape = RoundedCornerShape(50),
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
