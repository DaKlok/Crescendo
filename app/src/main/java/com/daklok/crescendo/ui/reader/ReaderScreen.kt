package com.daklok.crescendo.ui.reader

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
            startPage = piece.lastPage,
            document = doc,
            onBack = onBack,
            settings = settings,
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
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val keyFocus = remember { FocusRequester() }
    var gestureSheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spread = settings.twoPageSpread && landscape && pageCount > 1
    val perScreen = if (spread) 2 else 1
    val screenCount = (pageCount + perScreen - 1) / perScreen

    val pager = rememberPagerState(
        initialPage = (startPage / perScreen).coerceIn(0, screenCount - 1),
        pageCount = { screenCount },
    )

    var chromeVisible by remember { mutableStateOf(true) }

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
    var immersive by remember { mutableStateOf(false) }
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
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
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

    fun next() = scope.launch {
        if (pager.currentPage < screenCount - 1) pager.animateScrollToPage(pager.currentPage + 1)
    }
    fun previous() = scope.launch {
        if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
    }

    BackHandler {
        if (immersive) immersive = false else onBack()
    }

    val trackingActive = settings.headTrackingEnabled && cameraPermission

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // klávesnica a Bluetooth pedály (posielajú šípky / PageUp / PageDown)
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
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
                    PageView(document, first, vm.annotations, settings.fingerDrawing, renderBoost = boost)
                }
                if (spread) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        PageView(document, first + 1, vm.annotations, settings.fingerDrawing, renderBoost = boost)
                    }
                }
            }
        }
        }

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
            visible = chromeVisible,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .widthIn(max = 420.dp)
                            .padding(horizontal = 8.dp),
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
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
                        icon = if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = stringResource(R.string.fullscreen),
                        onClick = { immersive = !immersive; chromeVisible = false },
                    )
                }
            }
        }

        // Dolná lišta s posuvníkom strán
        AnimatedVisibility(
            visible = chromeVisible && screenCount > 1,
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
