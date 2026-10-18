package com.daklok.crescendo

import com.daklok.crescendo.ui.catalog.CatalogScreen
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.CloudSync
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import com.daklok.crescendo.ui.onboarding.OnboardingScreen
import com.daklok.crescendo.data.backup.SyncPrompt
import com.daklok.crescendo.data.backup.SyncManager
import com.daklok.crescendo.ui.stats.PracticeScreen
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import com.daklok.crescendo.ui.setlist.SetlistsScreen
import com.daklok.crescendo.ui.setlist.SetlistViewModel
import kotlinx.coroutines.delay
import com.daklok.crescendo.ui.common.OverlayKind
import com.daklok.crescendo.ui.common.AppOverlayHost
import com.daklok.crescendo.ui.common.AppOverlay
import com.daklok.crescendo.data.LanguageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.ui.library.LibraryScreen
import com.daklok.crescendo.ui.library.LibraryViewModel
import com.daklok.crescendo.ui.reader.ReaderScreen
import com.daklok.crescendo.ui.reader.ReaderViewModel
import com.daklok.crescendo.ui.settings.SettingsSheet
import com.daklok.crescendo.ui.common.LocalHapticsEnabled
import com.daklok.crescendo.ui.common.Motion
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.daklok.crescendo.ui.theme.CrescendoTheme

private sealed interface Route {
    data object Library : Route
    /** [nonce] zaručí novú inštanciu ViewModelu pri každom otvorení (čerstvá posledná strana). */
    data class Reader(
        val pieceId: Long,
        val nonce: Long = System.nanoTime(),
        /** Ak je skladba otvorená zo setlistu: ID setlistu a poradie v ňom. */
        val setlistId: Long? = null,
        val setlistIndex: Int = 0,
    ) : Route
    data object Setlists : Route
    data object Practice : Route
    data object Catalog : Route
}

class MainActivity : ComponentActivity() {

    private lateinit var libraryVm: LibraryViewModel
    private var pendingOpen = mutableStateOf<Long?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CrescendoApp
        libraryVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(app, app.library, app.settings) as T
        })[LibraryViewModel::class.java]

        handleIntent(intent)

        // dokončenie prechodu po zmene jazyka / obnovení zálohy
        val pendingOverlay = AppOverlay.consumePending(this)
            // pri studenom štarte úvodná obrazovka, kým sa nenačíta knižnica (inak by noty na chvíľu chýbali)
            ?: OverlayKind.STARTUP.takeIf { savedInstanceState == null && !AppOverlay.contentReady }
        if (pendingOverlay != null) {
            AppOverlay.show(pendingOverlay)
            // pozadie okna v rovnakej farbe ako prechodová obrazovka – žiadne bliknutie pred prvým snímkom
            AppOverlay.savedBackground(this)?.let { color ->
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
            }
        }

        setContent {
            val settings by libraryVm.settings.collectAsStateWithLifecycle()
            // knižnica načítaná → o chvíľu (náhľady) môže úvodná obrazovka zmiznúť
            val libraryLoaded by remember { libraryVm.state.map { it.loaded } }.collectAsStateWithLifecycle(false)
            LaunchedEffect(libraryLoaded) {
                if (libraryLoaded && !AppOverlay.contentReady) {
                    kotlinx.coroutines.delay(350)
                    AppOverlay.contentReady = true
                }
            }
            CrescendoTheme(mode = settings.themeMode, dynamicColor = settings.useDynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        CrescendoNav(app = app, libraryVm = libraryVm, openRequest = pendingOpen.value) {
                            pendingOpen.value = null
                        }
                        // úvodný sprievodca (len pri prvom spustení; čakáme na skutočné nastavenia, nech nebliká)
                        val loaded by remember { app.settings.settings.map<AppSettings, AppSettings?> { it } }
                            .collectAsStateWithLifecycle(initialValue = null)
                        val current = loaded
                        if (current != null && !current.onboardingDone) {
                            OnboardingScreen(onDone = {
                                app.appScope.launch { app.settings.update { it.copy(onboardingDone = true) } }
                            })
                        }
                        SyncPromptHost(app)
                        AppOverlayHost()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // synchronizácia: má iné zariadenie novší stav?
        val app = application as CrescendoApp
        app.appScope.launch { runCatching { SyncManager.checkRemote(app) } }
    }

    override fun onStop() {
        super.onStop()
        // synchronizácia: nahrať zmeny, keď bude internet
        val app = application as CrescendoApp
        if (!isChangingConfigurations) app.appScope.launch { runCatching { SyncManager.scheduleUploadIfChanged(app) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** PDF otvorené z iných aplikácií sa naimportuje do knižnice a hneď otvorí. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        libraryVm.importPdfs(listOf(uri)) { id -> pendingOpen.value = id }
    }
}

@Composable
private fun CrescendoNav(
    app: CrescendoApp,
    libraryVm: LibraryViewModel,
    openRequest: Long?,
    onOpenRequestHandled: () -> Unit,
) {
    var route by remember { mutableStateOf<Route>(Route.Library) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            route = Route.Reader(openRequest)
            onOpenRequestHandled()
        }
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val from = initialState
            val to = targetState
            if (from is Route.Reader && to is Route.Reader && to.setlistId != null) {
                // ďalšia / predchádzajúca skladba v setliste: posun do strany
                val dir = if (to.setlistIndex >= from.setlistIndex) 1 else -1
                (fadeIn(tween(260)) + slideInHorizontally(Motion.gentle()) { dir * it / 3 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { -dir * it / 4 })
            } else if (targetState is Route.Reader ||
                (targetState is Route.Setlists || targetState is Route.Practice || targetState is Route.Catalog) &&
                    initialState is Route.Library
            ) {
                // otvorenie skladby: čítačka pružne „vyrastie“, knižnica ustúpi do pozadia
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.92f, animationSpec = Motion.gentle())) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f, animationSpec = tween(260)))
            } else {
                // návrat do knižnice
                (fadeIn(tween(280)) + scaleIn(initialScale = 1.04f, animationSpec = Motion.gentle())) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(240)))
            }
        },
        label = "route",
    ) { r ->
        when (r) {
            Route.Library -> LibraryScreen(
                vm = libraryVm,
                onOpenPiece = { route = Route.Reader(it) },
                onOpenSettings = { showSettings = true },
                onOpenSetlists = { route = Route.Setlists },
                onOpenPractice = { route = Route.Practice },
                onOpenCatalog = { route = Route.Catalog },
            )
            Route.Catalog -> CatalogScreen(
                onBack = { route = Route.Library },
                onOpenPiece = { route = Route.Reader(it) },
            )
            Route.Practice -> PracticeScreen(onBack = { route = Route.Library }, onOpenPiece = { route = Route.Reader(it) })
            Route.Setlists -> {
                val vm: SetlistViewModel = viewModel(
                    key = "setlists",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            SetlistViewModel(app.setlists, app.library) as T
                    },
                )
                SetlistsScreen(
                    vm = vm,
                    onBack = { route = Route.Library },
                    onPlay = { setlistId, index, pieceId ->
                        route = Route.Reader(pieceId, setlistId = setlistId, setlistIndex = index)
                    },
                )
            }
            is Route.Reader -> {
                val vm: ReaderViewModel = viewModel(
                    key = "reader-${r.pieceId}-${r.nonce}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ReaderViewModel(
                                library = app.library,
                                settingsRepo = app.settings,
                                annotationStore = app.annotations,
                                pieceId = r.pieceId,
                                setlists = app.setlists,
                                setlistId = r.setlistId,
                                setlistIndex = r.setlistIndex,
                                fromStart = r.setlistId != null,
                            ) as T
                    },
                )
                DisposableEffect(vm) { onDispose { vm.release() } }
                ReaderScreen(
                    vm = vm,
                    // zo setlistu sa vraciame do setlistov, inak do knižnice
                    onBack = { route = if (r.setlistId != null) Route.Setlists else Route.Library },
                    onOpenSetlistEntry = { index, pieceId ->
                        route = Route.Reader(pieceId, setlistId = r.setlistId, setlistIndex = index)
                    },
                )
            }
        }
    }

    if (showSettings && AppOverlay.kind == null) {
        SettingsSheet(repo = app.settings, backup = app.backup, onDismiss = { showSettings = false })
    }
}


/** Otázka synchronizácie: načítať novší stav z iného zariadenia / ponechať tento. */
@Composable
private fun SyncPromptHost(app: CrescendoApp) {
    val prompt by SyncManager.prompt.collectAsStateWithLifecycle()
    val p = prompt ?: return
    if (AppOverlay.kind != null) return
    val (device, at) = when (p) {
        is SyncPrompt.RemoteNewer -> p.deviceName to p.at
        is SyncPrompt.Conflict -> p.deviceName to p.at
    }
    val time = remember(at) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))
    }
    val conflict = p is SyncPrompt.Conflict
    fun loadRemote() {
        SyncManager.clearPrompt()
        app.appScope.launch {
            val token = app.backup.silentToken() ?: return@launch
            AppOverlay.show(OverlayKind.RESTORE)
            if (!app.backup.restore(token)) AppOverlay.hide()
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { SyncManager.later(app) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.CloudSync, null) },
        title = {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(if (conflict) R.string.sync_conflict_title else R.string.sync_newer_title),
            )
        },
        text = {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(
                    if (conflict) R.string.sync_conflict_text else R.string.sync_newer_text,
                    device,
                    time,
                ),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { loadRemote() }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.sync_load, device))
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (conflict) {
                    androidx.compose.material3.TextButton(onClick = { app.appScope.launch { SyncManager.keepLocal(app) } }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.sync_keep_local))
                    }
                }
                androidx.compose.material3.TextButton(onClick = { SyncManager.later(app) }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.sync_later))
                }
            }
        },
    )
}
