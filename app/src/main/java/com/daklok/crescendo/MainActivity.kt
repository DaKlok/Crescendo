package com.daklok.crescendo

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
import com.daklok.crescendo.ui.settings.SettingsScreen
import com.daklok.crescendo.ui.theme.CrescendoTheme

private sealed interface Route {
    data object Library : Route
    data object Settings : Route
    /** [nonce] zaručí novú inštanciu ViewModelu pri každom otvorení (čerstvá posledná strana). */
    data class Reader(val pieceId: Long, val nonce: Long = System.nanoTime()) : Route
}

class MainActivity : ComponentActivity() {

    private lateinit var libraryVm: LibraryViewModel
    private var pendingOpen = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CrescendoApp
        libraryVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(app.library, app.settings) as T
        })[LibraryViewModel::class.java]

        handleIntent(intent)

        setContent {
            val settings by libraryVm.settings.collectAsStateWithLifecycle()
            CrescendoTheme(mode = settings.themeMode, dynamicColor = settings.useDynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CrescendoNav(app = app, libraryVm = libraryVm, openRequest = pendingOpen.value) {
                        pendingOpen.value = null
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

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            route = Route.Reader(openRequest)
            onOpenRequestHandled()
        }
    }

    BackHandler(enabled = route is Route.Settings) { route = Route.Library }

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "route",
    ) { r ->
        when (r) {
            Route.Library -> LibraryScreen(
                vm = libraryVm,
                onOpenPiece = { route = Route.Reader(it) },
                onOpenSettings = { route = Route.Settings },
            )
            Route.Settings -> SettingsScreen(
                repo = app.settings,
                onBack = { route = Route.Library },
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
}
