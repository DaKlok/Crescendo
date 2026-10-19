package com.daklok.crescendo.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Stop
import com.daklok.crescendo.data.addRep
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.NotificationsOff
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
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
            composer = piece.composer,
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
    composer: String,
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
    var visOpen by remember { mutableStateOf(false) }
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
    /** Tempo skladby je načítané (automatické posúvanie sa k nemu viaže). */
    var metronomeReady by remember { mutableStateOf(false) }
    LaunchedEffect(metronomePieceId) {
        metronomeReady = false
        if (metronomePieceId != null) MetronomePrefs.load(context, metronomePieceId, metronome)
        metronomeReady = true
    }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }
    val crescendo = context.applicationContext as com.daklok.crescendo.CrescendoApp

    // cvičebné úseky (ťažké miesta v notách s počítadlom)
    var spotsOpen by remember { mutableStateOf(false) }
    var spotMarking by remember { mutableStateOf(false) }
    var spotDraft by remember { mutableStateOf<com.daklok.crescendo.data.PracticeSpotEntity?>(null) }
    var spotRename by remember { mutableStateOf<com.daklok.crescendo.data.PracticeSpotEntity?>(null) }
    var spotFocus by remember { mutableStateOf<Long?>(null) }
    var spotFlash by remember { mutableStateOf<Long?>(null) }
    /** Úsek, ktorému sa pri zrýchľovaní metronómu pripočítava každé kolo. */
    var ladderSpotId by remember { mutableStateOf<Long?>(null) }
    val spotsFlow = remember(metronomePieceId) {
        metronomePieceId?.let { crescendo.spots.observeForPiece(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val spots by spotsFlow.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(spotFlash) {
        if (spotFlash != null) {
            kotlinx.coroutines.delay(2200)
            spotFlash = null
        }
    }
    LaunchedEffect(ladderSpotId) {
        val id = ladderSpotId
        val onRound: () -> Unit = {
            val reached = metronome.liveBpm
            crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val s = crescendo.spots.addRep(id ?: return@launch)
                if (s != null && reached > s.bestBpm) crescendo.spots.update(s.copy(bestBpm = reached))
            }
        }
        metronome.onRound = if (id == null) null else onRound
    }
    // úsek s metronómom: pri zastavení si zapamätá začiatočné tempo
    LaunchedEffect(metronome.playing) {
        val id = ladderSpotId ?: return@LaunchedEffect
        if (!metronome.playing) {
            val from = metronome.ladderStart
            crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                crescendo.spots.get(id)?.let { crescendo.spots.update(it.copy(fromBpm = from)) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { metronome.onRound = null } }
    // iná skladba (setlist): úsek z predošlej skladby sa odpojí
    LaunchedEffect(metronomePieceId) {
        ladderSpotId = null
        spotFocus = null
        spotMarking = false
    }

    // otáčanie podľa zvuku
    val follow = remember { FollowController(crescendo, scope) }
    var followOpen by remember { mutableStateOf(false) }
    LaunchedEffect(metronomePieceId) { metronomePieceId?.let { follow.load(it) } }
    DisposableEffect(Unit) { onDispose { follow.stop() } }
    val midiConnected by crescendo.midi.connected.collectAsStateWithLifecycle()
    var followMidiChoice by remember { mutableStateOf<Boolean?>(null) }
    val followMidi = crescendo.midi.supported && (followMidiChoice ?: midiConnected.isNotEmpty())

    // plynulé posúvanie (strany pod sebou) a automatické posúvanie
    val autoScroll = remember { AutoScroll() }
    /** Posúvanie zaplo strany pod sebou samo (pred tým boli vedľa seba) – po zavretí sa vráti späť. */
    var autoSwitched by remember { mutableStateOf(false) }
    val autoSwitchedNow by androidx.compose.runtime.rememberUpdatedState(autoSwitched)
    DisposableEffect(Unit) {
        onDispose { if (autoSwitchedNow) vm.update { it.copy(continuousScroll = false) } }
    }
    fun closeAutoScroll() {
        autoScroll.running = false
        autoScroll.shown = false
        if (autoSwitched) {
            autoSwitched = false
            vm.update { it.copy(continuousScroll = false) }
        }
    }
    // vizualizér: noty z MIDI sa pripravia vopred, aby sa panel otvoril hneď
    LaunchedEffect(metronomePieceId) {
        val pid = metronomePieceId ?: return@LaunchedEffect
        val app = context.applicationContext as com.daklok.crescendo.CrescendoApp
        // skladba z katalógu s opraveným tempom / výberom partitúry – MIDI aj nahrávka sa obnovia na pozadí
        app.appScope.launch { runCatching { app.catalog.refreshOfficialIfOutdated(pid, app.pieceAudio) } }
        // vizualizér je v Pro – bez neho sa noty vopred nepripravujú
        if (app.pro.isPro.value) prewarmVisualizer(app, pid)
    }

    // Denník cvičenia: čas, keď je skladba otvorená a aplikácia v popredí (min. 30 s)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, metronomePieceId) {
        val pid = metronomePieceId ?: return@DisposableEffect onDispose { }
        val app = context.applicationContext as com.daklok.crescendo.CrescendoApp
        // čas cvičenia – ukladá sa priebežne, takže sa nestratí ani pri zabití aplikácie
        val tracker = com.daklok.crescendo.data.PracticeTracker(app.practice, app.appScope, pid)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) tracker.resume()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> tracker.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    tracker.pause()
                    tracker.flush()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val ticker = app.appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                tracker.flush()
            }
        }
        onDispose {
            ticker.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.pause()
            tracker.flush()
        }
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    // skratky aplikácie (dlhé podržanie ikony) a widget – naposledy otvorená skladba
    val openedPiece = ui.piece
    LaunchedEffect(openedPiece?.id) {
        openedPiece?.let { com.daklok.crescendo.widget.AppShortcuts.pieceOpened(context.applicationContext, it) }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compact = isCompactWidth()
    // dvojstrana len na širokom displeji (tablet na šírku), na mobile by boli noty príliš malé
    val continuous = settings.continuousScroll
    val spread = settings.twoPageSpread && landscape && pageCount > 1 && !continuous &&
        LocalConfiguration.current.screenWidthDp >= 900
    val perScreen = if (spread) 2 else 1
    val screenCount = (pageCount + perScreen - 1) / perScreen

    val pager = rememberPagerState(
        initialPage = (startPage / perScreen).coerceIn(0, screenCount - 1),
        pageCount = { screenCount },
    )

    var chromeVisible by remember { mutableStateOf(true) }

    // záložky skladby (pomenované miesta – „Repríza“, „Coda“…)
    val bookmarkStore = remember { com.daklok.crescendo.data.BookmarkStore(context.applicationContext) }
    var bookmarks by remember(metronomePieceId) {
        mutableStateOf(metronomePieceId?.let { bookmarkStore.list(it) }.orEmpty())
    }
    fun saveBookmarks(list: List<com.daklok.crescendo.data.Bookmark>) {
        bookmarks = list.sortedBy { it.page }
        metronomePieceId?.let { scope.launch(kotlinx.coroutines.Dispatchers.IO) { bookmarkStore.save(it, list) } }
    }
    var bookmarkAddPage by remember { mutableStateOf<Int?>(null) }
    var bookmarkEdit by remember { mutableStateOf<com.daklok.crescendo.data.Bookmark?>(null) }
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
    val proManager = (context.applicationContext as com.daklok.crescendo.CrescendoApp).pro
    val isPro by proManager.isPro.collectAsStateWithLifecycle()
    /** Režim hrania: nič neruší – bez líšt, bez systémových panelov, ťuk do stredu len ukáže malé „Ukončiť“. */
    var immersive by remember { mutableStateOf(false) }
    var stageHint by remember { mutableStateOf(false) }
    var stageHintTick by remember { mutableIntStateOf(0) } // dotyk tlačidla predĺži zobrazenie
    LaunchedEffect(stageHint, stageHintTick) {
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
            visOpen = false
            spotsOpen = false
            spotMarking = false
            followOpen = false
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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
    )
    // Pri zmene orientácie (spread <-> single) aj pri prepnutí na posúvanie sa zachová strana.
    var anchorPage by remember { mutableStateOf(startPage) }
    LaunchedEffect(perScreen, continuous) {
        if (continuous) listState.scrollToItem(anchorPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        else pager.scrollToPage((anchorPage / perScreen).coerceIn(0, screenCount - 1))
    }
    LaunchedEffect(pager, perScreen, continuous) {
        snapshotFlow { if (continuous) listState.pagePosition().toInt() else pager.currentPage * perScreen }.collect { p ->
            anchorPage = p.coerceIn(0, pageCount - 1)
            vm.saveProgress(anchorPage)
        }
    }
    /** Prvá zobrazená strana (pri posúvaní strana v hornej tretine obrazovky). */
    val currentPage by remember(continuous, perScreen, pageCount) {
        derivedStateOf {
            if (continuous) listState.pagePosition().toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            else pager.currentPage * perScreen
        }
    }
    val continuousNow by androidx.compose.runtime.rememberUpdatedState(continuous)
    val perScreenNow by androidx.compose.runtime.rememberUpdatedState(perScreen)
    val screenCountNow by androidx.compose.runtime.rememberUpdatedState(screenCount)
    /** Skok na stranu v oboch režimoch (listovanie aj posúvanie). */
    fun goToPage(page: Int, animate: Boolean = true) {
        val p = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (continuousNow) {
                if (animate) listState.animateScrollToItem(p) else listState.scrollToItem(p)
            } else {
                val target = (p / perScreenNow).coerceIn(0, screenCountNow - 1)
                if (animate) pager.animateScrollToPage(target) else pager.scrollToPage(target)
            }
        }
    }
    // pomery strán pre posúvanie (s orezaním okrajov, ak je zapnuté)
    var pageRatios by remember { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(document, continuous, settings.cropMargins) {
        if (!continuous) return@LaunchedEffect
        loadPageRatios(document, settings.cropMargins, anchorPage) { pageRatios = it }
    }
    // strany pod sebou vypnuté ručne: nič sa už nevracia
    LaunchedEffect(continuous) { if (!continuous) autoSwitched = false }
    // šírka strán pri posúvaní (štipnutím); uloží sa po skončení gesta
    var scrollWidth by remember(settings.scrollWidth) { mutableFloatStateOf(settings.scrollWidth) }
    // dĺžka automatického posúvania pre skladbu
    LaunchedEffect(metronomePieceId, continuous, metronomeReady) {
        val pid = metronomePieceId ?: return@LaunchedEffect
        if (!metronomeReady) return@LaunchedEffect
        if (!continuous) {
            autoScroll.running = false
            autoScroll.shown = false
            return@LaunchedEffect
        }
        val (sec, bpm) = AutoScrollPrefs.load(context, pid, pageCount, metronome.bpm)
        autoScroll.durationSec = sec
        autoScroll.baseBpm = bpm
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    AutoScrollEffect(autoScroll, listState) {
        val r = pageRatios
        val vp = listState.layoutInfo.viewportSize
        if (r == null || vp.width == 0) {
            0f
        } else {
            val itemW = vp.width * scrollWidth.coerceIn(0.4f, 1f)
            val gap = with(density) { PageGap.toPx() }
            var total = gap
            for (x in r) total += itemW / x.coerceIn(0.2f, 5f) + gap
            val scrollable = (total - vp.height).coerceAtLeast(1f)
            scrollable / autoScroll.effectiveSec(metronome.bpm).coerceAtLeast(10)
        }
    }
    fun adjustAutoScroll(factor: Float) {
        val pid = metronomePieceId ?: return
        val cur = autoScroll.effectiveSec(metronome.bpm).coerceAtLeast(10)
        autoScroll.baseBpm = metronome.bpm
        autoScroll.durationSec = (cur * factor).roundToInt().coerceIn(10, 7200)
        AutoScrollPrefs.save(context, pid, autoScroll.durationSec, autoScroll.baseBpm)
    }

    // poloha v notách pre otáčanie podľa zvuku (nácvik aj sledovanie)
    LaunchedEffect(continuous, perScreen) {
        snapshotFlow {
            if (continuous) {
                val pos = listState.pagePosition()
                pos to pos.toInt()
            } else {
                (pager.currentPage * perScreen).toFloat() to (pager.currentPage * perScreen + perScreen - 1)
            }
        }.collect { (pos, last) ->
            follow.position = pos
            follow.lastVisible = last
        }
    }
    // ručné otočenie počas sledovania: pokračuje sa od novej strany
    LaunchedEffect(continuous, perScreen) {
        snapshotFlow { currentPage }.collect { p ->
            if (follow.mode == FollowController.Mode.FOLLOWING && System.currentTimeMillis() - follow.lastTurnAt > 1500) {
                follow.reanchor(p.toFloat())
            }
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
    val halfTurnAllowed by androidx.compose.runtime.rememberUpdatedState(settings.halfPageTurn && !spread && !continuous)
    fun next() = scope.launch {
        if (continuousNow) {
            // posúvanie: presne na začiatok ďalšej strany; vysoká strana najprv po obrazovkách až po jej spodok
            if (listState.canScrollForward) {
                val vpH = listState.layoutInfo.viewportSize.height.toFloat()
                val idx = listState.firstVisibleItemIndex
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                val remaining = (item?.size ?: 0) - listState.firstVisibleItemScrollOffset
                if (item != null && remaining - vpH > 8f) {
                    listState.animateScrollBy(minOf(vpH * 0.85f, remaining - vpH))
                } else {
                    listState.animateScrollToItem((idx + 1).coerceAtMost(pageCount - 1))
                }
            } else {
                val sl = setlist
                val nxt = sl?.next
                if (sl != null && nxt != null) onOpenSetlistEntry(sl.index + 1, nxt.pieceId)
            }
            return@launch
        }
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
        if (continuousNow) {
            if (listState.canScrollBackward) {
                val info = listState.layoutInfo
                val vpH = info.viewportSize.height.toFloat()
                val idx = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                if (offset > 8) {
                    // späť v rámci strany, presne po jej začiatok
                    if (offset <= vpH * 0.85f) listState.animateScrollToItem(idx) else listState.animateScrollBy(-vpH * 0.85f)
                } else if (idx > 0) {
                    // predošlá strana: celá, alebo jej spodok, ak je vyššia ako obrazovka
                    val r = pageRatios?.getOrNull(idx - 1) ?: 0.7071f
                    val h = info.viewportSize.width * scrollWidth.coerceIn(0.4f, 1f) / r.coerceIn(0.2f, 5f)
                    if (h <= vpH + 8f) listState.animateScrollToItem(idx - 1)
                    else listState.animateScrollToItem(idx - 1, (h - vpH).toInt())
                } else {
                    listState.animateScrollToItem(0)
                }
            } else {
                val sl = setlist
                val prev = sl?.previous
                if (sl != null && prev != null) onOpenSetlistEntry(sl.index - 1, prev.pieceId)
            }
            return@launch
        }
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

    /** Otvorí jeden nástroj (ostatné panely sa zatvoria). */
    fun openTool(
        metro: Boolean = false,
        tuner: Boolean = false,
        audio: Boolean = false,
        vis: Boolean = false,
        spotsPanel: Boolean = false,
        listen: Boolean = false,
    ) {
        metronomeOpen = metro
        tunerOpen = tuner
        audioOpen = audio
        visOpen = vis
        spotsOpen = spotsPanel
        followOpen = listen
        if (!spotsPanel) spotMarking = false
    }

    // otáčanie podľa zvuku: mikrofón treba povoliť (klavír cez MIDI nie)
    var pendingFollow by remember { mutableStateOf<(() -> Unit)?>(null) }
    val followMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingFollow?.invoke() else follow.error = R.string.follow_mic_denied
        pendingFollow = null
    }
    fun withListening(action: () -> Unit) {
        if (followMidi || hasMicPermission(context)) {
            action()
        } else {
            pendingFollow = action
            followMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun finishLearning() {
        follow.finishLearning { ok ->
            if (ok) {
                android.widget.Toast.makeText(context, R.string.follow_saved, android.widget.Toast.LENGTH_LONG).show()
            } else {
                followOpen = true
            }
        }
    }
    fun startLearning() = withListening {
        goToPage(0, animate = false)
        followOpen = false
        follow.startLearning(followMidi, lastPage = pageCount - 1, onAutoFinish = { finishLearning() })
    }
    fun startFollowing() = withListening {
        followOpen = false
        follow.startFollowing(followMidi, startPage = currentPage.toFloat()) { target ->
            if (settings.turnHaptic) haptic(HapticFeedbackType.LongPress)
            goToPage(target)
        }
    }
    /** Úsek: skok na jeho stranu a krátke zablikanie. */
    fun jumpToSpot(s: com.daklok.crescendo.data.PracticeSpotEntity) {
        halfTurned = false
        goToPage(s.page)
        spotFlash = s.id
        spotFocus = s.id
    }

    // v režime hrania gesto späť nič neukončí – len ukáže tlačidlo (podržaním sa režim ukončí)
    BackHandler(enabled = spotMarking) { spotMarking = false; spotsOpen = true }
    BackHandler(enabled = !spotMarking) {
        if (immersive) stageHint = true else onBack()
    }

    // režim hrania: stíšiť upozornenia (Nerušiť – povolia sa len budíky), ak to používateľ povolil
    val notificationManager = remember { context.getSystemService(android.app.NotificationManager::class.java) }
    var dndAllowed by remember { mutableStateOf(notificationManager?.isNotificationPolicyAccessGranted == true) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        dndAllowed = notificationManager?.isNotificationPolicyAccessGranted == true
        onPauseOrDispose { }
    }
    DisposableEffect(immersive, dndAllowed) {
        val nm = notificationManager
        var previous: Int? = null
        if (immersive && dndAllowed && nm != null) {
            runCatching {
                previous = nm.currentInterruptionFilter
                if (previous == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS)
                } else {
                    previous = null // Nerušiť už je zapnuté – nemeníme a ani nevraciame
                }
            }
        }
        onDispose { previous?.let { p -> runCatching { nm?.setInterruptionFilter(p) } } }
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
        // cvičebné úseky nad každou stranou (v režime hrania sa neukazujú)
        val spotOverlay: @Composable (Int) -> Unit = { pg ->
            PracticeSpotsLayer(
                page = pg,
                spots = spots,
                marking = spotMarking,
                visible = settings.showSpots && !immersive,
                flashId = spotFlash,
                onCreate = { p, x1, y1, x2, y2 ->
                    val pid = metronomePieceId
                    spotMarking = false
                    if (pid != null) {
                        spotDraft = com.daklok.crescendo.data.PracticeSpotEntity(pieceId = pid, page = p, x1 = x1, y1 = y1, x2 = x2, y2 = y2)
                    }
                },
                onRep = { s -> crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { crescendo.spots.addRep(s.id) } },
                onOpen = { s ->
                    spotFocus = s.id
                    openTool(spotsPanel = true)
                },
            )
        }
        // ťuk: ľavá/pravá tretina otočí stranu (pri posúvaní posunie o obrazovku), stred zobrazí/skryje ovládanie
        val tapModifier = Modifier.pointerInput(settings.tapToTurn, screenCount, continuous) {
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
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    when {
                        // v režime hrania sa nič nepribližuje ani neposúva omylom
                        immersive -> Modifier
                        // pri posúvaní štipnutie mení šírku strán
                        continuous -> Modifier.pinchToWidth(
                            width = { scrollWidth },
                            onChange = { scrollWidth = it },
                            onEnd = {
                                val w = scrollWidth
                                vm.update { it.copy(scrollWidth = w) }
                            },
                        )
                        else -> Modifier.zoomPanGestures(
                            zoom = { zoom },
                            pan = { pan },
                            // prst posúva priblížený obsah, len ak ním práve nekreslíš (ani neoznačuješ úsek)
                            // pri kreslení prstom jeden prst kreslí, posúva sa dvoma prstami
                            singleFingerPan = { !(vm.annotations.enabled && settings.fingerDrawing) && !spotMarking },
                            onChange = { z, p -> zoom = z; pan = p },
                        )
                    },
                ),
        ) {
        if (continuous) {
            ContinuousPages(
                document = document,
                ratios = pageRatios,
                listState = listState,
                annotations = vm.annotations,
                fingerDrawing = settings.fingerDrawing,
                cropMargins = settings.cropMargins,
                night = settings.nightMode,
                widthFraction = scrollWidth,
                // kreslenie prstom a označovanie úseku posúvanie vypnú; v režime hrania sa neposúva prstom
                userScrollEnabled = !immersive && !spotMarking && !(vm.annotations.enabled && settings.fingerDrawing),
                overlay = spotOverlay,
                modifier = tapModifier,
            )
        } else {
        HorizontalPager(
            state = pager,
            // v režime hrania strany neotočí náhodné potiahnutie – len pedál, hlava, klávesy alebo ťuk na okraj
            userScrollEnabled = zoom <= 1.01f && !immersive && !spotMarking,
            modifier = Modifier
                .fillMaxSize()
                .then(tapModifier),
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
                        cropMargins = settings.cropMargins, night = settings.nightMode, overlay = spotOverlay,
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
                                cropMargins = settings.cropMargins, night = settings.nightMode, overlay = spotOverlay,
                            )
                        }
                    }
                }
                if (spread) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        PageView(
                            document, first + 1, vm.annotations, settings.fingerDrawing, renderBoost = boost,
                            cropMargins = settings.cropMargins, night = settings.nightMode, overlay = spotOverlay,
                        )
                    }
                }
            }
        }
        }
        }

        // pri ručnom prelistovaní sa polovičné otočenie zruší
        LaunchedEffect(pager.currentPage) { halfTurned = false }

        val tbPos = settings.toolbarPosition
        val barTop = if (chromeVisible) 88.dp else 12.dp
        val bottomBar = screenCount > 1 || continuous
        val barBottom = if (chromeVisible && bottomBar) 96.dp else 12.dp
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
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.zoom_reset, (zoom * 100).toInt()))
            }
        }

        // Horná lišta: vľavo názov skladby, vpravo nástroje v skupinách (stred ostáva voľný, nezakrýva nadpis nôt)
        AnimatedVisibility(
            visible = chromeVisible && !immersive,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { -it } +
                scaleIn(initialScale = 0.94f, animationSpec = Motion.bouncy()),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
        ) {
            val toggleDraw = { vm.annotations.enabled = !vm.annotations.enabled }
            val toggleTracking = {
                val on = !trackingActive
                if (on && !cameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    vm.update { it.copy(headTrackingEnabled = on) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // názov a skladateľ
                FloatingBar(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(end = 12.dp),
                ) {
                    Row(
                        Modifier.padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TonalIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBack,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (composer.isNotBlank()) {
                                Text(
                                    composer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        setlist?.let { sl ->
                            Spacer(Modifier.width(12.dp))
                            SetlistChip(sl) { idx -> if (idx != sl.index) onOpenSetlistEntry(idx, sl.entries[idx].pieceId) }
                        }
                    }
                }
                // nástroje
                FloatingBar {
                    Row(
                        // na tablete je na konci väčšie tlačidlo režimu hrania (48 dp), lišta ostáva vysoká 56 dp
                        Modifier.padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
                    ) {
                        if (compact) {
                            // mobil: kreslenie a sledovanie hlavy, zvyšok v ponuke ⋮
                            ToolButton(
                                icon = Icons.Outlined.Draw,
                                description = stringResource(R.string.annotations),
                                active = vm.annotations.enabled,
                                onClick = toggleDraw,
                            )
                            ToolButton(
                                icon = Icons.Outlined.Face,
                                description = stringResource(R.string.head_tracking),
                                active = trackingActive,
                                onClick = toggleTracking,
                            )
                            Box {
                                var more by remember { mutableStateOf(false) }
                                ToolButton(
                                    icon = Icons.Outlined.MoreVert,
                                    description = stringResource(R.string.more),
                                    onClick = { more = true },
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = more,
                                    onDismissRequest = { more = false },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.metronome)) },
                                        leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                                        onClick = { more = false; openTool(metro = true) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.spots_title)) },
                                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                                        onClick = { more = false; openTool(spotsPanel = true) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tuner)) },
                                        leadingIcon = { Icon(Icons.Outlined.GraphicEq, null) },
                                        onClick = { more = false; openTool(tuner = true) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.audio_title)) },
                                        leadingIcon = { Icon(Icons.Outlined.Headphones, null) },
                                        trailingIcon = { if (!isPro) com.daklok.crescendo.ui.pro.ProBadge() },
                                        onClick = {
                                            more = false
                                            if (proManager.allow(com.daklok.crescendo.data.pro.ProFeature.AUDIO)) openTool(audio = true)
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.visualizer)) },
                                        leadingIcon = { Icon(Icons.Outlined.Piano, null) },
                                        trailingIcon = { if (!isPro) com.daklok.crescendo.ui.pro.ProBadge() },
                                        onClick = {
                                            more = false
                                            if (proManager.allow(com.daklok.crescendo.data.pro.ProFeature.VISUALIZER)) openTool(vis = true)
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.follow_title)) },
                                        leadingIcon = { Icon(Icons.Outlined.Hearing, null) },
                                        onClick = { more = false; openTool(listen = true) },
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
                            // cvičenie: metronóm, ladička, nahrávky, klavír
                            ToolGroup {
                                ToolButton(
                                    icon = Icons.Outlined.Timer,
                                    description = stringResource(R.string.metronome),
                                    active = metronome.playing || metronomeOpen,
                                    onClick = { openTool(metro = !metronomeOpen) },
                                )
                                ToolButton(
                                    icon = Icons.Outlined.GraphicEq,
                                    description = stringResource(R.string.tuner),
                                    active = tunerOpen,
                                    onClick = { openTool(tuner = !tunerOpen) },
                                )
                                ToolButton(
                                    icon = Icons.Outlined.Headphones,
                                    description = stringResource(R.string.audio_title),
                                    active = audioOpen,
                                    pro = !isPro,
                                    onClick = {
                                        if (audioOpen || proManager.allow(com.daklok.crescendo.data.pro.ProFeature.AUDIO)) openTool(audio = !audioOpen)
                                    },
                                )
                                ToolButton(
                                    icon = Icons.Outlined.Piano,
                                    description = stringResource(R.string.visualizer),
                                    active = visOpen,
                                    pro = !isPro,
                                    onClick = {
                                        if (visOpen || proManager.allow(com.daklok.crescendo.data.pro.ProFeature.VISUALIZER)) openTool(vis = !visOpen)
                                    },
                                )
                                ToolButton(
                                    icon = Icons.Outlined.Flag,
                                    description = stringResource(R.string.spots_title),
                                    active = spotsOpen || spotMarking,
                                    onClick = { openTool(spotsPanel = !spotsOpen) },
                                )
                            }
                            // noty: kreslenie, zobrazenie, sledovanie hlavy
                            ToolGroup {
                                ToolButton(
                                    icon = Icons.Outlined.Draw,
                                    description = stringResource(R.string.annotations),
                                    active = vm.annotations.enabled,
                                    onClick = toggleDraw,
                                )
                                Box {
                                    var displayMenu by remember { mutableStateOf(false) }
                                    ToolButton(
                                        icon = Icons.Outlined.DisplaySettings,
                                        description = stringResource(R.string.display_options),
                                        active = settings.nightMode || settings.cropMargins || settings.halfPageTurn || settings.continuousScroll,
                                        onClick = { displayMenu = true },
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = displayMenu,
                                        onDismissRequest = { displayMenu = false },
                                        shape = RoundedCornerShape(20.dp),
                                    ) {
                                        DisplayToggles(settings) { t -> vm.update(t) }
                                    }
                                }
                                ToolButton(
                                    icon = Icons.Outlined.Face,
                                    description = stringResource(R.string.head_tracking),
                                    active = trackingActive,
                                    onClick = toggleTracking,
                                )
                                ToolButton(
                                    icon = Icons.Outlined.Hearing,
                                    description = stringResource(R.string.follow_title),
                                    active = followOpen || follow.mode != FollowController.Mode.IDLE,
                                    onClick = { openTool(listen = !followOpen) },
                                )
                            }
                            // menej časté: gestá a export
                            Box {
                                var more by remember { mutableStateOf(false) }
                                ToolButton(
                                    icon = Icons.Outlined.MoreVert,
                                    description = stringResource(R.string.more),
                                    onClick = { more = true },
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = more,
                                    onDismissRequest = { more = false },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.gesture_settings)) },
                                        leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                        onClick = { more = false; gestureSheetOpen = true },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_pdf)) },
                                        leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                                        enabled = ui.piece != null && ui.document != null,
                                        onClick = { more = false; exportOpen = true },
                                    )
                                }
                            }
                            // režim hrania: hlavné tlačidlo
                            StageButton(immersive = immersive, onClick = { enterStage(!immersive) })
                        }
                    }
                }
            }
        }

        // Dolná lišta: číslo strany, posuvník so záložkami, záložky
        AnimatedVisibility(
            visible = chromeVisible && !immersive && bottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { it } +
                scaleIn(initialScale = 0.94f, animationSpec = Motion.bouncy()),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it },
        ) {
            FloatingBar(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = currentPage + 1
                    val label = if (spread && first + 1 <= pageCount) "$first-${first + 1}" else "$first"
                    PageBadge(
                        label = label,
                        total = pageCount,
                        description = stringResource(R.string.page_x_of_y, first, pageCount),
                    )
                    Spacer(Modifier.width(16.dp))
                    // záložky ako zvislé čiary priamo v dráhe posuvníka
                    val sliderInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val markColor = MaterialTheme.colorScheme.tertiary
                    val sliderPos = if (continuous) currentPage else pager.currentPage
                    Slider(
                        value = sliderPos.toFloat(),
                        onValueChange = {
                            val target = it.toInt()
                            if (target != sliderPos) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                scope.launch { if (continuous) listState.scrollToItem(target) else pager.scrollToPage(target) }
                            }
                        },
                        valueRange = 0f..(screenCount - 1).coerceAtLeast(1).toFloat(),
                        interactionSource = sliderInteraction,
                        thumb = { androidx.compose.material3.SliderDefaults.Thumb(interactionSource = sliderInteraction) },
                        track = { sliderState ->
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.material3.SliderDefaults.Track(sliderState = sliderState)
                                if (bookmarks.isNotEmpty()) {
                                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                        val max = (screenCount - 1).coerceAtLeast(1)
                                        val w = 3.dp.toPx()
                                        bookmarks.forEach { b ->
                                            val f = (b.page / perScreen).coerceAtMost(max).toFloat() / max
                                            val x = (w / 2 + (size.width - w) * f)
                                            drawLine(
                                                markColor,
                                                Offset(x, 0f),
                                                Offset(x, size.height),
                                                strokeWidth = w,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // automatické posúvanie (prepne na strany pod sebou)
                    ToolButton(
                        icon = Icons.Outlined.SwipeVertical,
                        description = stringResource(R.string.autoscroll_title),
                        active = autoScroll.shown || autoScroll.running,
                        onClick = {
                            if (!continuous) {
                                autoSwitched = true
                                vm.update { it.copy(continuousScroll = true) }
                                autoScroll.shown = true
                            } else if (autoScroll.shown) {
                                closeAutoScroll()
                            } else {
                                autoScroll.shown = true
                            }
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    // záložky: zoznam, skok, pridanie na aktuálnu stranu
                    Box {
                        var open by remember { mutableStateOf(false) }
                        val firstIdx = currentPage
                        val here = bookmarks.any { it.page in firstIdx until firstIdx + perScreen }
                        ToolButton(
                            icon = if (here) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                            description = stringResource(R.string.bookmarks),
                            active = here,
                            onClick = { open = true },
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = open,
                            onDismissRequest = { open = false },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                stringResource(R.string.bookmarks),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            if (bookmarks.isEmpty()) {
                                Text(
                                    stringResource(R.string.bookmarks_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .widthIn(max = 260.dp),
                                )
                            }
                            bookmarks.forEach { b ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(b.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            Text(
                                                stringResource(R.string.bookmark_page, b.page + 1),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.tertiary) },
                                    trailingIcon = {
                                        IconButton(onClick = { open = false; bookmarkEdit = b }) {
                                            Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                                        }
                                    },
                                    onClick = {
                                        open = false
                                        halfTurned = false
                                        goToPage(b.page)
                                    },
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmark_add_here, firstIdx + 1)) },
                                leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) },
                                onClick = { open = false; bookmarkAddPage = firstIdx },
                            )
                        }
                    }
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
                pages = (0 until perScreen).map { currentPage + it },
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
                    piece = exportPiece,
                    pdf = vm.pdfFile(exportPiece),
                    notes = { vm.annotations.snapshot() },
                ),
                onDismiss = { exportOpen = false },
            )
        }

        // záložky – pridanie / úprava
        bookmarkAddPage?.let { page ->
            BookmarkDialog(
                initialName = "",
                page = page,
                onSave = { name ->
                    saveBookmarks(bookmarks + com.daklok.crescendo.data.Bookmark(page, name))
                    bookmarkAddPage = null
                },
                onDelete = null,
                onDismiss = { bookmarkAddPage = null },
            )
        }
        bookmarkEdit?.let { b ->
            BookmarkDialog(
                initialName = b.name,
                page = b.page,
                onSave = { name ->
                    saveBookmarks(bookmarks.map { if (it == b) it.copy(name = name) else it })
                    bookmarkEdit = null
                },
                onDelete = {
                    saveBookmarks(bookmarks - b)
                    bookmarkEdit = null
                },
                onDismiss = { bookmarkEdit = null },
            )
        }

        // Režim hrania: nenápadné číslo strany a ťukom do stredu tlačidlo na ukončenie
        if (immersive) {
            val first = currentPage + 1
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
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Column {
                        Text(stringResource(R.string.stage_mode_hint), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (dndAllowed) R.string.stage_mode_dnd_on else R.string.stage_mode_locked),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (!dndAllowed && notificationManager != null) {
                        // povolenie stíšiť upozornenia (otvorí systémové nastavenia)
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        }) {
                            Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.stage_mode_dnd_allow), tint = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                    HoldToExitButton(
                        label = stringResource(R.string.stage_mode_hold_exit),
                        onExit = { enterStage(false) },
                        onPress = {
                            stageHint = true
                            stageHintTick++
                        },
                    )
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
            val linked = spots.firstOrNull { it.id == ladderSpotId }
            MetronomePanel(
                engine = metronome,
                scope = scope,
                onChanged = { metronomePieceId?.let { MetronomePrefs.save(context, it, metronome) } },
                onClose = { metronomeOpen = false },
                spot = linked?.let { spotTitle(it, spots.indexOf(it)) },
                spotReps = linked?.reps ?: 0,
                onUnlinkSpot = { ladderSpotId = null },
            )
        }

        // Cvičebné úseky („na precvičenie“)
        AnimatedVisibility(
            visible = spotsOpen && !spotMarking,
            modifier = Modifier
                .align(if (tbPos == ToolbarPosition.LEFT && vm.annotations.enabled) Alignment.BottomEnd else Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            PracticeSpotsPanel(
                spots = spots,
                visibleOnPage = settings.showSpots,
                focusId = spotFocus,
                onToggleVisible = { vm.update { it.copy(showSpots = !it.showSpots) } },
                onMark = {
                    vm.annotations.enabled = false
                    zoom = 1f
                    pan = Offset.Zero
                    halfTurned = false
                    spotMarking = true
                },
                onJump = { s ->
                    jumpToSpot(s)
                    // na mobile panel zakrýva noty
                    if (compact) spotsOpen = false
                },
                onRep = { s, d -> crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { crescendo.spots.addRep(s.id, d) } },
                onLadder = { s ->
                    ladderSpotId = s.id
                    if (s.fromBpm > 0) metronome.ladderFrom = s.fromBpm
                    metronome.setLadder(true, scope)
                    metronomePieceId?.let { MetronomePrefs.save(context, it, metronome) }
                    jumpToSpot(s)
                    openTool(metro = true)
                },
                onRename = { s -> spotRename = s },
                onDone = { s, done ->
                    crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        crescendo.spots.get(s.id)?.let { crescendo.spots.update(it.copy(done = done)) }
                    }
                    if (done && ladderSpotId == s.id) ladderSpotId = null
                },
                onDelete = { s ->
                    if (ladderSpotId == s.id) ladderSpotId = null
                    crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { crescendo.spots.delete(s.id) }
                },
                onClose = { spotsOpen = false },
            )
        }

        // označovanie úseku: nápoveda hore
        AnimatedVisibility(
            visible = spotMarking,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = barTop + 8.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            SpotMarkingHint(onCancel = {
                spotMarking = false
                spotsOpen = true
            })
        }

        // Otáčanie podľa zvuku
        AnimatedVisibility(
            visible = followOpen,
            modifier = Modifier
                .align(if (tbPos == ToolbarPosition.LEFT && vm.annotations.enabled) Alignment.BottomEnd else Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            FollowPanel(
                follow = follow,
                midiAvailable = crescendo.midi.supported && midiConnected.isNotEmpty(),
                useMidi = followMidi,
                onUseMidi = { followMidiChoice = it },
                onLearn = { startLearning() },
                onToggleFollow = { on -> if (on) startFollowing() else follow.stop() },
                onClose = { followOpen = false },
            )
        }
        // nácvik / sledovanie beží a panel je zatvorený: malý ukazovateľ (ťuk = panel)
        AnimatedVisibility(
            visible = follow.mode != FollowController.Mode.IDLE && !followOpen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    top = (if (immersive) 16.dp else barTop + 8.dp) + if (audioRecorder.recordingFile != null && !audioOpen) 52.dp else 0.dp,
                    end = 16.dp,
                ),
            enter = fadeIn() + scaleIn(Motion.bouncy()),
            exit = fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            FollowStatusPill(
                follow = follow,
                page = currentPage,
                pageCount = pageCount,
                onOpen = { if (!immersive) openTool(listen = true) },
                onDone = { finishLearning() },
                onStop = { follow.stop() },
                modifier = Modifier.graphicsLayer { alpha = if (immersive) 0.75f else 1f },
            )
        }

        // Automatické posúvanie: ovládanie nad dolnou lištou, počas hrania len malá pauza
        AnimatedVisibility(
            visible = continuous && autoScroll.shown && chromeVisible && !immersive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            AutoScrollBar(
                auto = autoScroll,
                bpm = metronome.bpm,
                onToggle = { autoScroll.running = !autoScroll.running },
                onSlower = { adjustAutoScroll(1.08f) },
                onFaster = { adjustAutoScroll(0.93f) },
                onClose = { closeAutoScroll() },
            )
        }
        AnimatedVisibility(
            visible = continuous && autoScroll.running && (!chromeVisible || immersive),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            enter = fadeIn() + scaleIn(Motion.bouncy()),
            exit = fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            Surface(
                onClick = { autoScroll.running = false },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Pause, stringResource(R.string.pause), Modifier.size(22.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = autoScroll.countdown > 0,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(Motion.bouncy()),
            exit = fadeOut(tween(200)),
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = autoScroll.countdown.coerceAtLeast(1),
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = Motion.bouncy())) togetherWith
                        fadeOut(tween(120))
                },
                label = "countdown",
            ) { v -> AutoScrollCountdown(v) }
        }

        // nový úsek: pomenovanie
        spotDraft?.let { draft ->
            SpotNameDialog(
                title = stringResource(R.string.spot_new_title),
                initial = "",
                onSave = { name ->
                    val d = draft.copy(name = name)
                    spotDraft = null
                    spotsOpen = true
                    crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val id = crescendo.spots.insert(d)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            spotFocus = id
                            spotFlash = id
                        }
                    }
                },
                onDismiss = {
                    spotDraft = null
                    spotsOpen = true
                },
            )
        }
        spotRename?.let { sp ->
            SpotNameDialog(
                title = stringResource(R.string.spot_rename_title),
                initial = sp.name,
                onSave = { name ->
                    spotRename = null
                    crescendo.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        crescendo.spots.get(sp.id)?.let { crescendo.spots.update(it.copy(name = name)) }
                    }
                },
                onDismiss = { spotRename = null },
            )
        }

        // Klavírny vizualizér – široký panel dole (noty ostávajú viditeľné hore)
        AnimatedVisibility(
            visible = visOpen && metronomePieceId != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = barBottom + 8.dp, start = if (compact) 8.dp else 16.dp, end = if (compact) 8.dp else 16.dp),
            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            metronomePieceId?.let { pid ->
                VisualizerPanel(
                    pieceId = pid,
                    onClose = { visOpen = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1100.dp),
                )
            }
        }

        // Vizualizér sa pripravuje na pozadí (panel je zatvorený) – plávajúca ikonka s priebehom; ťuk ho otvorí
        metronomePieceId?.let { pid ->
            val crescendoApp = context.applicationContext as com.daklok.crescendo.CrescendoApp
            val renderMap by com.daklok.crescendo.data.midi.MidiRenderer.rendering.collectAsStateWithLifecycle()
            val midiVer by crescendoApp.pieceMidi.version.collectAsStateWithLifecycle()
            val visKey = remember(pid, midiVer) { crescendoApp.pieceMidi.renderKey(pid) }
            val visProgress = visKey?.let { renderMap[it] }
            var wasLoading by remember { mutableStateOf(false) }
            var justReady by remember { mutableStateOf(false) }
            LaunchedEffect(visProgress == null) {
                if (visProgress != null) {
                    wasLoading = true
                } else if (wasLoading) {
                    wasLoading = false
                    justReady = true
                    kotlinx.coroutines.delay(4000)
                    justReady = false
                }
            }
            AnimatedVisibility(
                visible = isPro && !visOpen && (visProgress != null || justReady),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = barBottom + 12.dp, end = 16.dp),
                enter = fadeIn() + scaleIn(Motion.bouncy()),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
            ) {
                Surface(
                    onClick = {
                        justReady = false
                        openTool(vis = true)
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            val p = visProgress
                            if (p == null) {
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            } else if (p < 0f) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            } else {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { p.coerceAtLeast(0.02f) },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                )
                                Icon(Icons.Outlined.Piano, null, Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (visProgress == null) stringResource(R.string.vis_ready)
                            else if (visProgress < 0f) stringResource(R.string.visualizer)
                            else stringResource(R.string.vis_preparing_short, (visProgress * 100).toInt()),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
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
                shape = RoundedCornerShape(50),
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
            visible = nextEntry != null && !vm.annotations.enabled &&
                (if (continuous) !listState.canScrollForward else pager.currentPage == screenCount - 1),
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
                shape = RoundedCornerShape(50),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
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
            shape = RoundedCornerShape(50),
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
            shape = RoundedCornerShape(20.dp),
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
    // strany pod sebou: plynulé posúvanie (aj automatické)
    androidx.compose.material3.DropdownMenuItem(
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.continuous_scroll))
                Text(
                    stringResource(R.string.continuous_scroll_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = { Icon(Icons.Outlined.SwipeVertical, null) },
        trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = settings.continuousScroll, onCheckedChange = null) },
        onClick = { update { it.copy(continuousScroll = !it.continuousScroll) } },
    )
    // dve strany vedľa seba (pri orientácii na šírku)
    androidx.compose.material3.DropdownMenuItem(
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.two_pages))
                Text(
                    stringResource(R.string.two_pages_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = { Icon(Icons.Outlined.AutoStories, null) },
        trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = settings.twoPageSpread, onCheckedChange = null) },
        onClick = { update { it.copy(twoPageSpread = !it.twoPageSpread) } },
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.night_mode)) },
        leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
        trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = settings.nightMode, onCheckedChange = null) },
        onClick = { update { it.copy(nightMode = !it.nightMode) } },
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.crop_margins)) },
        leadingIcon = { Icon(Icons.Outlined.Crop, null) },
        trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = settings.cropMargins, onCheckedChange = null) },
        onClick = { update { it.copy(cropMargins = !it.cropMargins) } },
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.half_page_turn)) },
        leadingIcon = { Icon(Icons.Outlined.VerticalSplit, null) },
        trailingIcon = { com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = settings.halfPageTurn, onCheckedChange = null) },
        onClick = { update { it.copy(halfPageTurn = !it.halfPageTurn) } },
    )
}

/**
 * Ukončenie režimu hrania podržaním (~0,8 s) – krúžok ukazuje priebeh; náhodný dotyk nič neukončí.
 */
@Composable
private fun HoldToExitButton(label: String, onExit: () -> Unit, onPress: () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.inversePrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(800, easing = androidx.compose.animation.core.LinearEasing))
                            onExit()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(22.dp),
                color = accent,
                strokeWidth = 2.5.dp,
                trackColor = accent.copy(alpha = 0.25f),
            )
            Icon(Icons.Outlined.FullscreenExit, null, Modifier.size(12.dp), tint = accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

/** Pridanie / premenovanie záložky – s rýchlymi návrhmi názvov. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BookmarkDialog(
    initialName: String,
    page: Int,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val suggestions = listOf(
        R.string.bm_start, R.string.bm_repeat, R.string.bm_coda, R.string.bm_trio,
        R.string.bm_chorus, R.string.bm_verse, R.string.bm_hard,
    ).map { stringResource(it) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Bookmark, com.daklok.crescendo.ui.common.ExpressiveShapes.Clover, com.daklok.crescendo.ui.common.Accent.Tertiary) },
        title = { Text(stringResource(R.string.bookmark_title, page + 1)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bookmark_name)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    suggestions.forEach { sug ->
                        androidx.compose.material3.SuggestionChip(
                            onClick = { name = sug },
                            label = { Text(sug) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    androidx.compose.material3.TextButton(
                        onClick = onDelete,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(name.trim().ifBlank { suggestions.first() }) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
