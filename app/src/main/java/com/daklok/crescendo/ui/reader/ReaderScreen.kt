package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.MoreVert
import com.daklok.crescendo.ui.common.isCompactWidth
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import com.daklok.crescendo.ui.common.ExportRequest
import com.daklok.crescendo.ui.common.ExportPdfDialog
import androidx.compose.material.icons.outlined.IosShare
import com.daklok.crescendo.ui.settings.GestureSettingsSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import com.daklok.crescendo.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.res.Configuration
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.ToolbarPosition
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.rememberHaptic
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import com.daklok.crescendo.tracking.TurnDirection
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    onBack: () -> Unit,
    /** Prechod na inú skladbu v setliste (index v setliste, ID skladby). */
    onOpenSetlistEntry: (Int, Long) -> Unit = { _, _ -> },
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val doc = ui.document
    val piece = ui.piece

    when {
        ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        doc == null || piece == null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(ui.errorRes ?: R.string.error_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.padding(8.dp))
            FilledTonalButton(onClick = onBack) { Text(stringResource(R.string.back_to_library)) }
        }
        else -> ReaderContent(
            vm = vm,
            title = piece.title,
            pageCount = doc.pageCount,
            startPage = if (vm.fromStart) 0 else piece.lastPage,
            document = doc,
            onBack = onBack,
            settings = settings,
            onOpenSetlistEntry = onOpenSetlistEntry,
        )
    }
}

@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    title: String,
    pageCount: Int,
    startPage: Int,
    document: com.daklok.crescendo.pdf.PdfDocument,
    onBack: () -> Unit,
    settings: com.daklok.crescendo.data.AppSettings,
    onOpenSetlistEntry: (Int, Long) -> Unit,
) {
    val context = LocalContext.current
    val setlist by vm.setlist.collectAsStateWithLifecycle()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val keyFocus = remember { FocusRequester() }
    var gestureSheetOpen by rememberSaveable { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }

    // metronóm – tempo a takt sa pamätajú pre každú skladbu
    val metronome = remember { MetronomeEngine() }
    var metronomeOpen by remember { mutableStateOf(false) }
    var audioOpen by remember { mutableStateOf(false) }
    var tunerOpen by remember { mutableStateOf(false) }
    // prehrávanie a nahrávanie žijú v čítačke, nie v paneli – zatvorenie panela ich nepreruší
    val audioScope = rememberCoroutineScope()
    val audioPlayer = remember { AudioPlayer(audioScope) }
    val audioRecorder = remember { Recorder(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.stop()
            audioPlayer.release()
        }
    }
    val metronomePieceId = vm.state.collectAsStateWithLifecycle().value.piece?.id
    LaunchedEffect(metronomePieceId) {
        if (metronomePieceId != null) MetronomePrefs.load(context, metronomePieceId, metronome)
    }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }

    // Denník cvičenia: čas, keď je skladba otvorená a aplikácia v popredí (min. 30 s)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, metronomePieceId) {
        val pid = metronomePieceId ?: return@DisposableEffect onDispose { }
        val app = context.applicationContext as com.daklok.crescendo.CrescendoApp
        val startedAt = System.currentTimeMillis()
        var resumedAt = if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) startedAt else 0L
        var total = 0L
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> resumedAt = System.currentTimeMillis()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> if (resumedAt > 0) {
                    total += System.currentTimeMillis() - resumedAt
                    resumedAt = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumedAt > 0) total += System.currentTimeMillis() - resumedAt
            val sec = (total / 1000).toInt()
            if (sec >= 30) {
                app.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.practice.insert(com.daklok.crescendo.data.PracticeSessionEntity(pieceId = pid, startedAt = startedAt, durationSec = sec))
                }
            }
        }
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compact = isCompactWidth()
    // dvojstrana len na širokom displeji (tablet na šírku), na mobile by boli noty príliš malé
    val spread = settings.twoPageSpread && landscape && pageCount > 1 &&
        LocalConfiguration.current.screenWidthDp >= 900
    val perScreen = if (spread) 2 else 1
    val screenCount = (pageCount + perScreen - 1) / perScreen

    val pager = rememberPagerState(
        initialPage = (startPage / perScreen).coerceIn(0, screenCount - 1),
        pageCount = { screenCount },
    )

    var chromeVisible by remember { mutableStateOf(true) }
    var halfTurned by remember { mutableStateOf(false) }

    // priblíženie (štipnutím) – platí pre aktuálnu stranu, pri zmene strany sa resetuje
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val renderBoost by remember {
        derivedStateOf {
            when {
                zoom <= 1.3f -> 1f
                zoom <= 2.3f -> 2f
                else -> 3f
            }
        }
    }
    /** Režim hrania: nič neruší – bez líšt, bez systémových panelov, ťuk do stredu len ukáže malé „Ukončiť“. */
    var immersive by remember { mutableStateOf(false) }
    var stageHint by remember { mutableStateOf(false) }
    LaunchedEffect(stageHint) {
        if (stageHint) {
            kotlinx.coroutines.delay(3500)
            stageHint = false
        }
    }
    fun enterStage(on: Boolean) {
        immersive = on
        chromeVisible = false
        if (on) {
            vm.annotations.enabled = false
            metronomeOpen = false
            audioOpen = false
            tunerOpen = false
            zoom = 1f
            pan = Offset.Zero
            stageHint = true
        } else {
            chromeVisible = true
            stageHint = false
        }
    }
    var cameraPermission by remember { mutableStateOf(hasCameraPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermission = granted
        if (granted) vm.update { it.copy(headTrackingEnabled = true) }
    }

    // Pri zmene orientácie sa zachová strana (spread <-> single).
    var anchorPage by remember { mutableStateOf(startPage) }
    LaunchedEffect(perScreen) {
        pager.scrollToPage((anchorPage / perScreen).coerceIn(0, screenCount - 1))
    }
    LaunchedEffect(pager, perScreen) {
        snapshotFlow { pager.currentPage }.collect { screen ->
            anchorPage = (screen * perScreen).coerceIn(0, pageCount - 1)
            vm.saveProgress(anchorPage)
        }
    }

    LaunchedEffect(pager.currentPage, perScreen) {
        zoom = 1f
        pan = Offset.Zero
    }

    // Obrazovka zostane svietiť
    DisposableEffect(settings.keepScreenOn, immersive) {
        view.keepScreenOn = settings.keepScreenOn || immersive
        onDispose { view.keepScreenOn = false }
    }

    // Imerzívny režim (skryté systémové lišty)
    DisposableEffect(immersive) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // aktuálna hodnota aj pre gestá / pedále, ktoré si zapamätali starší next()
    val halfTurnAllowed by androidx.compose.runtime.rememberUpdatedState(settings.halfPageTurn && !spread)
    fun next() = scope.launch {
        val canHalf = halfTurnAllowed && zoom <= 1.01f
        if (canHalf && !halfTurned && pager.currentPage < screenCount - 1) {
            // polovičné otáčanie: najprv len horná polovica ďalšej strany
            halfTurned = true
        } else if (halfTurned) {
            halfTurned = false
            pager.scrollToPage((pager.currentPage + 1).coerceAtMost(screenCount - 1))
        } else if (pager.currentPage < screenCount - 1) {
            pager.animateScrollToPage(pager.currentPage + 1)
        } else {
            // koniec skladby v setliste -> ďalšia skladba
            val sl = setlist
            val nxt = sl?.next
            if (sl != null && nxt != null) onOpenSetlistEntry(sl.index + 1, nxt.pieceId)
        }
    }
    fun previous() = scope.launch {
        if (halfTurned) {
            halfTurned = false
        } else if (pager.currentPage > 0) {
            pager.animateScrollToPage(pager.currentPage - 1)
        } else {
            val sl = setlist
            val prev = sl?.previous
            if (sl != null && prev != null) onOpenSetlistEntry(sl.index - 1, prev.pieceId)
        }
    }

    // Pedál klavíra (MIDI): stlačenie (alebo dvojité stlačenie) otočí stranu
    if (settings.midiEnabled) {
        val midi = (context.applicationContext as com.daklok.crescendo.CrescendoApp).midi
        DisposableEffect(Unit) {
            midi.start()
            if (settings.midiBtAddress.isNotBlank()) midi.connectBluetoothAddress(settings.midiBtAddress)
            onDispose { midi.stop() }
        }
        LaunchedEffect(settings.midiNextCc, settings.midiPrevCc, settings.midiDoublePress, settings.turnHaptic) {
            val lastPress = HashMap<Int, Long>()
            midi.events.collect { e ->
                if (!e.pressed) return@collect
                val forward = when (e.cc) {
                    settings.midiNextCc -> true
                    settings.midiPrevCc -> false
                    else -> return@collect
                }
                if (settings.midiDoublePress) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val prev = lastPress[e.cc]
                    if (prev == null || now - prev > 450) {
                        lastPress[e.cc] = now
                        return@collect
                    }
                    lastPress.remove(e.cc)
                }
                if (settings.turnHaptic) haptic(HapticFeedbackType.LongPress)
                if (forward) next() else previous()
            }
        }
    }

    BackHandler {
        if (immersive) enterStage(false) else onBack()
    }

    val trackingActive = settings.headTrackingEnabled && cameraPermission

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when {
                    settings.nightMode -> Color(0xFF0E0E10)
                    immersive -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            // klávesnica a Bluetooth pedály (posielajú šípky / PageUp / PageDown)
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                // naučené klávesy Bluetooth pedála majú prednosť
                val code = e.nativeKeyEvent.keyCode
                if (settings.pedalNextKey != 0 && code == settings.pedalNextKey) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) next()
                    return@onPreviewKeyEvent true
                }
                if (settings.pedalPrevKey != 0 && code == settings.pedalPrevKey) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) previous()
                    return@onPreviewKeyEvent true
                }
                val volume = e.key == Key.VolumeDown || e.key == Key.VolumeUp
                if (volume && !settings.volumeKeysTurn) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent volume
                when (e.key) {
                    Key.DirectionRight, Key.DirectionDown, Key.PageDown, Key.Spacebar, Key.Enter, Key.VolumeDown -> { next(); true }
                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.VolumeUp -> { previous(); true }
                    else -> false
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .zoomPanGestures(
                    zoom = { zoom },
                    pan = { pan },
                    // prst posúva priblížený obsah, len ak ním práve nekreslíš
                    // pri kreslení prstom jeden prst kreslí, posúva sa dvoma prstami
                    singleFingerPan = { !(vm.annotations.enabled && settings.fingerDrawing) },
                    onChange = { z, p -> zoom = z; pan = p },
                ),
        ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom <= 1.01f,
            modifier = Modifier
                .fillMaxSize()
                // ťuk: ľavá/pravá tretina otočí stranu, stred zobrazí/skryje ovládanie
                .pointerInput(settings.tapToTurn, screenCount) {
                    detectTapGestures { offset ->
                        val third = size.width / 3f
                        when {
                            settings.tapToTurn && offset.x < third -> previous()
                            settings.tapToTurn && offset.x > size.width - third -> next()
                            // v režime hrania sa lišty neukážu – len malé tlačidlo na ukončenie
                            immersive -> stageHint = !stageHint
                            else -> chromeVisible = !chromeVisible
                        }
                    }
                },
            beyondViewportPageCount = 1,
            key = { it },
        ) { screen ->
            val isCurrent = screen == pager.currentPage
            val boost = if (isCurrent) renderBoost else 1f
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isCurrent) {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(if (spread) 2.dp else 0.dp),
            ) {
                val first = screen * perScreen
                Box(Modifier.weight(1f).fillMaxSize()) {
                    PageView(
                        document, first, vm.annotations, settings.fingerDrawing, renderBoost = boost,
                        cropMargins = settings.cropMargins, night = settings.nightMode,
                    )
                    // Polovičné otáčanie: horná polovica už ukazuje ďalšiu stranu, dolná ešte túto
                    if (!spread && isCurrent && halfTurned && !vm.annotations.enabled && first + 1 < pageCount) {
                        val divider = MaterialTheme.colorScheme.primary
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(bottom = size.height / 2f) { this@drawWithContent.drawContent() }
                                    drawLine(
                                        divider,
                                        Offset(0f, size.height / 2f),
                                        Offset(size.width, size.height / 2f),
                                        strokeWidth = 3.dp.toPx(),
                                    )
                                },
                        ) {
                            PageView(
                                document, first + 1, vm.annotations, settings.fingerDrawing,
                                cropMargins = settings.cropMargins, night = settings.nightMode,
                            )
                        }
                    }
                }
                if (spread) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        PageView(
                            document, first + 1, vm.annotations, settings.fingerDrawing, renderBoost = boost,
                            cropMargins = settings.cropMargins, night = settings.nightMode,
                        )
                    }
                }
            }
        }
        }

        // pri ručnom prelistovaní sa polovičné otočenie zruší
        LaunchedEffect(pager.currentPage) { halfTurned = false }

        val tbPos = settings.toolbarPosition
        val barTop = if (chromeVisible) 88.dp else 12.dp
        val barBottom = if (chromeVisible && screenCount > 1) 88.dp else 12.dp
        val toolbarRight = vm.annotations.enabled && tbPos == ToolbarPosition.RIGHT

        // Ukazovateľ priblíženia (ťuk = reset) – na strane oproti panelu nástrojov
        AnimatedVisibility(
            visible = zoom > 1.01f,
            modifier = Modifier
                .align(if (toolbarRight) Alignment.TopStart else Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = barTop, start = 16.dp, end = 16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FilledTonalButton(
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    zoom = 1f
                    pan = Offset.Zero
                },
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.zoom_reset, (zoom * 100).toInt()))
            }
        }

        // Horná lišta – plávajúca „pilulka“ s tonálnymi tlačidlami
        AnimatedVisibility(
            visible = chromeVisible && !immersive,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { -it } +
                scaleIn(initialScale = 0.94f, animationSpec = Motion.bouncy()),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
        ) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                ) {
                    TonalIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .widthIn(max = if (compact) (if (setlist != null) 96.dp else 140.dp) else 420.dp)
                            .padding(horizontal = if (compact) 4.dp else 8.dp),
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    setlist?.let { sl ->
                        SetlistChip(sl) { idx -> if (idx != sl.index) onOpenSetlistEntry(idx, sl.entries[idx].pieceId) }
                    }
                    TonalIconButton(
                        icon = Icons.Outlined.Face,
                        contentDescription = stringResource(R.string.head_tracking),
                        highlighted = trackingActive,
                        onClick = {
                            val on = !trackingActive
                            if (on && !cameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                vm.update { it.copy(headTrackingEnabled = on) }
                            }
                        },
                    )
                    if (compact) {
                        // mobil: kreslenie ostáva, zvyšok v ponuke ⋯
                        TonalIconButton(
                            icon = Icons.Outlined.Draw,
                            contentDescription = stringResource(R.string.annotations),
                            highlighted = vm.annotations.enabled,
                            onClick = { vm.annotations.enabled = !vm.annotations.enabled },
                        )
                        Box {
                            var more by remember { mutableStateOf(false) }
                            TonalIconButton(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                onClick = { more = true },
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = more,
                                onDismissRequest = { more = false },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.metronome)) },
                                    leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                                    onClick = { more = false; metronomeOpen = true; audioOpen = false; tunerOpen = false },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tuner)) },
                                    leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) },
                                    onClick = { more = false; tunerOpen = true; metronomeOpen = false; audioOpen = false },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.audio_title)) },
                                    leadingIcon = { Icon(Icons.Outlined.Headphones, null) },
                                    onClick = { more = false; audioOpen = true; metronomeOpen = false; tunerOpen = false },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.gesture_settings)) },
                                    leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                    onClick = { more = false; gestureSheetOpen = true },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_pdf)) },
                                    leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                                    onClick = { more = false; exportOpen = true },
                                )
                                DisplayToggles(settings) { t -> vm.update(t) }
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.fullscreen)) },
                                    leadingIcon = {
                                        Icon(if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, null)
                                    },
                                    onClick = { more = false; enterStage(!immersive) },
                                )
                            }
                        }
                    } else {
                        TonalIconButton(
                            icon = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.gesture_settings),
                            onClick = { gestureSheetOpen = true },
                        )
                        TonalIconButton(
                            icon = Icons.Outlined.Draw,
                            contentDescription = stringResource(R.string.annotations),
                            highlighted = vm.annotations.enabled,
                            onClick = { vm.annotations.enabled = !vm.annotations.enabled },
                        )
                        TonalIconButton(
                            icon = Icons.Outlined.Timer,
                            contentDescription = stringResource(R.string.metronome),
                            highlighted = metronome.playing || metronomeOpen,
                            onClick = { metronomeOpen = !metronomeOpen; audioOpen = false; tunerOpen = false },
                        )
                        TonalIconButton(
                            icon = Icons.Outlined.GraphicEq,
                            contentDescription = stringResource(R.string.tuner),
                            highlighted = tunerOpen,
                            onClick = { tunerOpen = !tunerOpen; metronomeOpen = false; audioOpen = false },
                        )
                        TonalIconButton(
                            icon = Icons.Outlined.Headphones,
                            contentDescription = stringResource(R.string.audio_title),
                            highlighted = audioOpen,
                            onClick = { audioOpen = !audioOpen; metronomeOpen = false; tunerOpen = false },
                        )
                        Box {
                            var displayMenu by remember { mutableStateOf(false) }
                            TonalIconButton(
                                icon = Icons.Outlined.DisplaySettings,
                                contentDescription = stringResource(R.string.display_options),
                                highlighted = settings.nightMode || settings.cropMargins || settings.halfPageTurn,
                                onClick = { displayMenu = true },
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = displayMenu,
                                onDismissRequest = { displayMenu = false },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                DisplayToggles(settings) { t -> vm.update(t) }
                            }
                        }
                        TonalIconButton(
                            icon = Icons.Outlined.IosShare,
                            contentDescription = stringResource(R.string.export_pdf),
                            enabled = ui.piece != null && ui.document != null,
                            onClick = { exportOpen = true },
                        )
                        TonalIconButton(
                            icon = if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = stringResource(R.string.fullscreen),
                            onClick = { enterStage(!immersive) },
                        )
                    }
                }
            }
        }

        // Dolná lišta s posuvníkom strán
        AnimatedVisibility(
            visible = chromeVisible && !immersive && screenCount > 1,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { it } +
                scaleIn(initialScale = 0.94f, animationSpec = Motion.bouncy()),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it },
        ) {
            Surface(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = pager.currentPage * perScreen + 1
                    val label = if (spread && first + 1 <= pageCount) "$first–${first + 1}" else "$first"
                    // číslo strany v pilulke; pri zmene sa prevalí
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                (slideInVertically(Motion.bouncy()) { it / 2 } + fadeIn(tween(160))) togetherWith
                                    (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                            },
                            label = "pageLabel",
                        ) { l ->
                            Text(
                                l,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = pager.currentPage.toFloat(),
                        onValueChange = {
                            val target = it.toInt()
                            if (target != pager.currentPage) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                scope.launch { pager.scrollToPage(target) }
                            }
                        },
                        valueRange = 0f..(screenCount - 1).coerceAtLeast(1).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$pageCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        // Panel nástrojov pre poznámky – poloha podľa nastavení (vľavo / vpravo / hore / dole)
        val tbAlign = when (tbPos) {
            ToolbarPosition.LEFT -> Alignment.CenterStart
            ToolbarPosition.RIGHT -> Alignment.CenterEnd
            ToolbarPosition.TOP -> Alignment.TopCenter
            ToolbarPosition.BOTTOM -> Alignment.BottomCenter
        }
        val tbPadding = when (tbPos) {
            ToolbarPosition.LEFT -> Modifier.padding(start = 12.dp, top = barTop, bottom = barBottom)
            ToolbarPosition.RIGHT -> Modifier.padding(end = 12.dp, top = barTop, bottom = barBottom)
            ToolbarPosition.TOP -> Modifier.padding(top = barTop, start = 12.dp, end = 12.dp)
            ToolbarPosition.BOTTOM -> Modifier.padding(bottom = barBottom, start = 12.dp, end = 12.dp)
        }
        AnimatedVisibility(
            visible = vm.annotations.enabled,
            modifier = Modifier
                .align(tbAlign)
                .statusBarsPadding()
                .navigationBarsPadding()
                .then(tbPadding),
            enter = fadeIn() + when (tbPos) {
                ToolbarPosition.LEFT -> slideInHorizontally { -it }
                ToolbarPosition.RIGHT -> slideInHorizontally { it }
                ToolbarPosition.TOP -> slideInVertically { -it }
                ToolbarPosition.BOTTOM -> slideInVertically { it }
            },
            exit = fadeOut() + when (tbPos) {
                ToolbarPosition.LEFT -> slideOutHorizontally { -it }
                ToolbarPosition.RIGHT -> slideOutHorizontally { it }
                ToolbarPosition.TOP -> slideOutVertically { -it }
                ToolbarPosition.BOTTOM -> slideOutVertically { it }
            },
        ) {
            AnnotationToolbar(
                controller = vm.annotations,
                fingerDrawing = settings.fingerDrawing,
                onFingerDrawingChange = { on -> vm.update { it.copy(fingerDrawing = on) } },
                pages = (0 until perScreen).map { pager.currentPage * perScreen + it },
                position = tbPos,
                onPositionChange = { p -> vm.update { it.copy(toolbarPosition = p) } },
                onClose = { vm.annotations.enabled = false },
            )
        }

        // Sledovanie hlavy
        if (trackingActive) {
            HeadTrackingOverlay(
                settings = settings,
                visible = !immersive,
                onTurn = { dir ->
                    if (settings.turnHaptic) haptic(HapticFeedbackType.LongPress)
                    if (dir == TurnDirection.NEXT) next() else previous()
                },
                onCalibrate = { yaw, roll, pitch ->
                    vm.update { it.copy(yawOffset = yaw, rollOffset = roll, pitchOffset = pitch) }
                },
                modifier = Modifier
                    .align(if (toolbarRight) Alignment.BottomStart else Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = if (chromeVisible) 88.dp else 16.dp, end = 16.dp, start = 16.dp),
            )
        }

        val exportPiece = ui.piece
        if (exportOpen && exportPiece != null) {
            ExportPdfDialog(
                request = ExportRequest(
                    title = exportPiece.title,
                    pdf = vm.pdfFile(exportPiece),
                    notes = { vm.annotations.snapshot() },
                ),
                onDismiss = { exportOpen = false },
            )
        }

        // Režim hrania: nenápadné číslo strany a ťukom do stredu tlačidlo na ukončenie
        if (immersive) {
            val first = pager.currentPage * perScreen + 1
            Text(
                "$first / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }
        AnimatedVisibility(
            visible = immersive && stageHint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(stringResource(R.string.stage_mode_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = { enterStage(false) },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inversePrimary,
                        ),
                    ) {
                        Icon(Icons.Outlined.FullscreenExit, null, Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.stage_mode_exit))
                    }
                }
            }
        }

        // Metronóm
        AnimatedVisibility(
            visible = metronomeOpen,
            modifier = Modifier
                .align(if (tbPos == ToolbarPosition.LEFT && vm.annotations.enabled) Alignment.BottomEnd else Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            MetronomePanel(
                engine = metronome,
                scope = scope,
                onChanged = { metronomePieceId?.let { MetronomePrefs.save(context, it, metronome) } },
                onClose = { metronomeOpen = false },
            )
        }

        // Ladička a akordy
        AnimatedVisibility(
            visible = tunerOpen,
            modifier = Modifier
                .align(if (tbPos == ToolbarPosition.LEFT && vm.annotations.enabled) Alignment.BottomEnd else Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            TunerPanel(onClose = { tunerOpen = false })
        }

        // Nahrávky (referenčná nahrávka + vlastné nahrávky)
        AnimatedVisibility(
            visible = audioOpen && metronomePieceId != null,
            modifier = Modifier
                .align(if (tbPos == ToolbarPosition.LEFT && vm.annotations.enabled) Alignment.BottomEnd else Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            metronomePieceId?.let { pid ->
                AudioPanel(pieceId = pid, player = audioPlayer, recorder = audioRecorder, onClose = { audioOpen = false })
            }
        }

        // Prebieha nahrávanie a panel je zatvorený – malý červený ukazovateľ (ťuk = zastaviť)
        AnimatedVisibility(
            visible = audioRecorder.recordingFile != null && !audioOpen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = if (immersive) 16.dp else barTop + 8.dp, end = 16.dp),
            enter = fadeIn() + scaleIn(Motion.bouncy()),
            exit = fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = System.currentTimeMillis()
                    kotlinx.coroutines.delay(500)
                }
            }
            Surface(
                onClick = { audioRecorder.stop() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                    )
                    if (audioRecorder.midiMode) {
                        // nahrávanie z klavíra – názov klavíra, alebo varovanie, že sa odpojil
                        val pianos by (context.applicationContext as com.daklok.crescendo.CrescendoApp).midi.instruments
                            .collectAsStateWithLifecycle()
                        Icon(Icons.Outlined.Piano, null, Modifier.padding(end = 6.dp).size(18.dp))
                        Text(
                            pianos.firstOrNull()?.name ?: stringResource(R.string.rec_piano_lost),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp).widthIn(max = 160.dp),
                        )
                    }
                    Text(
                        "REC " + formatClock(now - audioRecorder.startedAt),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(Icons.Outlined.Stop, stringResource(R.string.recording_stop), Modifier.padding(start = 8.dp).size(18.dp))
                }
            }
        }

        // Koniec skladby v setliste – ďalšia skladba
        val nextEntry = setlist?.next
        AnimatedVisibility(
            visible = nextEntry != null && pager.currentPage == screenCount - 1 && !vm.annotations.enabled,
            modifier = Modifier
                .align(if (toolbarRight) Alignment.BottomStart else Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp + if (trackingActive) 150.dp else 0.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val sl = setlist
            androidx.compose.material3.Button(
                onClick = { if (sl != null && sl.next != null) onOpenSetlistEntry(sl.index + 1, sl.next!!.pieceId) },
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    stringResource(R.string.setlist_next, nextEntry?.title ?: ""),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp))
            }
        }

        // Nastavenia gest priamo v čítačke (rovnaké ako v Nastaveniach, zosynchronizované)
        if (gestureSheetOpen) {
            GestureSettingsSheet(
                settings = settings,
                update = { t -> vm.update(t) },
                onDismiss = { gestureSheetOpen = false },
            )
        }
    }
}


/** Čip „setlist 2/5“ v hornej lište; ťuk ukáže skladby setlistu a dá sa preskočiť na inú. */
@Composable
private fun SetlistChip(setlist: SetlistNav, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, Modifier.padding(end = 6.dp))
                Text(
                    "${setlist.index + 1}/${setlist.entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                setlist.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            setlist.entries.forEachIndexed { i, e ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            "${i + 1}. ${e.title}",
                            fontWeight = if (i == setlist.index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = if (i == setlist.index) {
                        { Icon(Icons.Outlined.PlayArrow, null) }
                    } else null,
                    onClick = {
                        open = false
                        onSelect(i)
                    },
                )
            }
        }
    }
}


/** Prepínače zobrazenia nôt (v ponuke čítačky): nočný režim, orezanie okrajov, polovičné otáčanie. */
@Composable
private fun DisplayToggles(
    settings: com.daklok.crescendo.data.AppSettings,
    update: ((com.daklok.crescendo.data.AppSettings) -> com.daklok.crescendo.data.AppSettings) -> Unit,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.night_mode)) },
        leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
        trailingIcon = { androidx.compose.material3.Switch(checked = settings.nightMode, onCheckedChange = null) },
        onClick = { update { it.copy(nightMode = !it.nightMode) } },
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.crop_margins)) },
        leadingIcon = { Icon(Icons.Outlined.Crop, null) },
        trailingIcon = { androidx.compose.material3.Switch(checked = settings.cropMargins, onCheckedChange = null) },
        onClick = { update { it.copy(cropMargins = !it.cropMargins) } },
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.half_page_turn)) },
        leadingIcon = { Icon(Icons.Outlined.VerticalSplit, null) },
        trailingIcon = { androidx.compose.material3.Switch(checked = settings.halfPageTurn, onCheckedChange = null) },
        onClick = { update { it.copy(halfPageTurn = !it.halfPageTurn) } },
    )
}
