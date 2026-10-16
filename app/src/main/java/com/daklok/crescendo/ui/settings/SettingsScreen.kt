package com.daklok.crescendo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.CameraChoice
import com.daklok.crescendo.data.SettingsRepository
import com.daklok.crescendo.data.ThemeMode
import com.daklok.crescendo.data.ToolbarPosition
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    onBack: () -> Unit,
) {
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    fun update(t: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(t) }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Nastavenia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Späť")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Vzhľad") {
                Text("Téma", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { update { it.copy(themeMode = mode) } },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    "Režim „Papier“ používa teplé, matné farby a vysoký kontrast – vhodné pre displej NXTPAPER.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "Dynamické farby",
                    subtitle = "Farby z tapety (Android 12+, nepoužije sa v režime Papier)",
                    checked = settings.useDynamicColor,
                    onChange = { v -> update { it.copy(useDynamicColor = v) } },
                )
            }

            Section("Čítačka") {
                SwitchRow(
                    title = "Dve strany vedľa seba",
                    subtitle = "Pri orientácii na šírku",
                    checked = settings.twoPageSpread,
                    onChange = { v -> update { it.copy(twoPageSpread = v) } },
                )
                SwitchRow(
                    title = "Otáčanie ťukom",
                    subtitle = "Ľavá/pravá tretina obrazovky otočí stranu",
                    checked = settings.tapToTurn,
                    onChange = { v -> update { it.copy(tapToTurn = v) } },
                )
                SwitchRow(
                    title = "Nechať displej zapnutý",
                    subtitle = "Počas čítania sa displej nevypne",
                    checked = settings.keepScreenOn,
                    onChange = { v -> update { it.copy(keepScreenOn = v) } },
                )
            }

            Section("Poznámky") {
                SwitchRow(
                    title = "Kresliť prstom",
                    subtitle = "So stylusom sa kreslí vždy. Ak stylus nemáš, zapni kreslenie prstom. Priblíženie a posun: dva prsty.",
                    checked = settings.fingerDrawing,
                    onChange = { v -> update { it.copy(fingerDrawing = v) } },
                )
                Text("Poloha panela nástrojov", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolbarPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = settings.toolbarPosition == pos,
                            onClick = { update { it.copy(toolbarPosition = pos) } },
                            label = { Text(pos.label) },
                        )
                    }
                }
                Text(
                    "Dá sa zmeniť aj priamo v paneli tlačidlom so šípkami.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Otáčanie hlavou") {
                SwitchRow(
                    title = "Zapnúť sledovanie hlavy",
                    subtitle = "Kamera sa spracúva len v zariadení, nič sa neukladá ani neodosiela",
                    checked = settings.headTrackingEnabled,
                    onChange = { v -> update { it.copy(headTrackingEnabled = v) } },
                )
                Text("Kamera", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraChoice.entries.forEach { c ->
                        FilterChip(
                            selected = settings.camera == c,
                            onClick = { update { it.copy(camera = c) } },
                            label = { Text(c.label) },
                        )
                    }
                }
                SliderRow(
                    title = "Citlivosť (uhol natočenia)",
                    valueLabel = "${settings.yawThresholdDeg.toInt()}°",
                    value = settings.yawThresholdDeg,
                    range = 10f..50f,
                    onChange = { v -> update { it.copy(yawThresholdDeg = v) } },
                    hint = "Menej stupňov = citlivejšie",
                )
                SliderRow(
                    title = "Čas podržania",
                    valueLabel = String.format(Locale.US, "%.1f s", settings.holdTimeSec),
                    value = settings.holdTimeSec,
                    range = 0f..1.5f,
                    onChange = { v -> update { it.copy(holdTimeSec = (v * 10).toInt() / 10f) } },
                    hint = "Ako dlho treba držať hlavu natočenú",
                )
                SliderRow(
                    title = "Pauza po otočení",
                    valueLabel = String.format(Locale.US, "%.1f s", settings.cooldownSec),
                    value = settings.cooldownSec,
                    range = 0.5f..3f,
                    onChange = { v -> update { it.copy(cooldownSec = (v * 10).toInt() / 10f) } },
                    hint = "Zabraňuje dvojitému otočeniu",
                )
                SwitchRow(
                    title = "Obrátiť smer",
                    subtitle = "Vľavo = ďalej, vpravo = späť",
                    checked = settings.reverseDirection,
                    onChange = { v -> update { it.copy(reverseDirection = v) } },
                )
                SwitchRow(
                    title = "Zobraziť náhľad kamery",
                    subtitle = "Malé okno s ukazovateľom natočenia",
                    checked = settings.showCameraPreview,
                    onChange = { v -> update { it.copy(showCameraPreview = v) } },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    hint: String,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
