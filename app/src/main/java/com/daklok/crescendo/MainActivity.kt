package com.daklok.crescendo

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
    data class Reader(val pieceId: Long, val nonce: Long = System.nanoTime()) : Route
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
        if (pendingOverlay != null) {
            AppOverlay.show(pendingOverlay)
            // pozadie okna v rovnakej farbe ako prechodová obrazovka – žiadne bliknutie pred prvým snímkom
            AppOverlay.savedBackground(this)?.let { color ->
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
            }
        }

        setContent {
            val settings by libraryVm.settings.collectAsStateWithLifecycle()
            CrescendoTheme(mode = settings.themeMode, dynamicColor = settings.useDynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        CrescendoNav(app = app, libraryVm = libraryVm, openRequest = pendingOpen.value) {
                            pendingOpen.value = null
                        }
                        AppOverlayHost()
                    }
                }
            }
        }
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
            if (targetState is Route.Reader) {
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
            )
            is Route.Reader -> {
                val vm: ReaderViewModel = viewModel(
                    key = "reader-${r.pieceId}-${r.nonce}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ReaderViewModel(app.library, app.settings, app.annotations, r.pieceId) as T
                    },
                )
                DisposableEffect(vm) { onDispose { vm.release() } }
                ReaderScreen(vm = vm, onBack = { route = Route.Library })
            }
        }
    }

    if (showSettings && AppOverlay.kind == null) {
        SettingsSheet(repo = app.settings, backup = app.backup, onDismiss = { showSettings = false })
    }
}
