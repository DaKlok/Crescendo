package com.daklok.crescendo.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.AppSettings
import com.daklok.crescendo.data.MidiPedals
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.PianoConnectionCard
import com.daklok.crescendo.ui.common.SectionHeader
import com.daklok.crescendo.ui.common.SettingSwitchRow
import com.daklok.crescendo.ui.common.SettingsCard
import kotlinx.coroutines.delay

/** Čitateľný názov klávesy (napr. „PAGE DOWN“). */
fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')

@Composable
fun pedalName(cc: Int): String =
    if (cc < 0) stringResource(R.string.pedal_none)
    else MidiPedals.pedalName(
        cc,
        stringResource(R.string.pedal_sustain),
        stringResource(R.string.pedal_sostenuto),
        stringResource(R.string.pedal_soft),
    )

/**
 * Nastavenia „Klavír a pedále“ v troch jasných častiach:
 * 1. Digitálny klavír – stav pripojenia a ako ho pripojiť,
 * 2. Otáčanie strán pedálom klavíra,
 * 3. Bluetooth otáčač strán (samostatné zariadenie).
 * Rovnaký obsah je v Nastaveniach aj v čítačke.
 */
@Composable
fun PedalSettingsContent(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val midi = (context.applicationContext as CrescendoApp).midi
    var learnKey by remember { mutableStateOf<Boolean?>(null) } // true = ďalej, false = späť
    var learnMidi by remember { mutableStateOf<Boolean?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ── 1. Digitálny klavír ──
        SectionHeader(Icons.Outlined.Piano, stringResource(R.string.piano_section))
        SettingsCard {
            Text(
                stringResource(R.string.piano_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PianoConnectionCard()
            Spacer(Modifier.size(14.dp))
            Text(
                stringResource(R.string.piano_how_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            HowToRow(Icons.Outlined.Cable, stringResource(R.string.piano_how_usb), stringResource(R.string.midi_cable_hint))
            HowToRow(Icons.Outlined.Bluetooth, stringResource(R.string.piano_how_bt), stringResource(R.string.piano_how_bt_desc))
            if (settings.midiBtName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 34.dp),
                ) {
                    Text(
                        stringResource(R.string.piano_saved, settings.midiBtName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { update { it.copy(midiBtAddress = "", midiBtName = "") } }) {
                        Text(stringResource(R.string.piano_forget))
                    }
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        // ── 2. Otáčanie strán pedálom klavíra ──
        SectionHeader(Icons.Outlined.SwapHoriz, stringResource(R.string.piano_turn_section))
        SettingsCard {
            SettingSwitchRow(
                icon = Icons.Outlined.Piano,
                title = stringResource(R.string.pedal_midi_title),
                subtitle = stringResource(if (midi.supported) R.string.pedal_midi_desc else R.string.pedal_midi_unsupported),
                checked = settings.midiEnabled && midi.supported,
                enabled = midi.supported,
                onChange = { v -> update { it.copy(midiEnabled = v) } },
            )
            AnimatedVisibility(
                visible = settings.midiEnabled && midi.supported,
                enter = expandVertically(Motion.bouncy()) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                Column {
                    Divider()
                    LearnRow(
                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                        label = stringResource(R.string.next_page),
                        value = pedalName(settings.midiNextCc),
                        onLearn = { learnMidi = true },
                        onReset = null,
                    )
                    LearnRow(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        label = stringResource(R.string.prev_page),
                        value = pedalName(settings.midiPrevCc),
                        onLearn = { learnMidi = false },
                        onReset = null,
                    )
                    Divider()
                    SettingSwitchRow(
                        icon = Icons.Outlined.TouchApp,
                        title = stringResource(R.string.midi_double),
                        subtitle = stringResource(R.string.midi_double_desc),
                        checked = settings.midiDoublePress,
                        onChange = { v -> update { it.copy(midiDoublePress = v) } },
                    )
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        // ── 3. Bluetooth otáčač strán ──
        SectionHeader(Icons.Outlined.SettingsRemote, stringResource(R.string.pedal_bt_title))
        SettingsCard {
            Text(
                stringResource(R.string.pedal_bt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LearnRow(
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                label = stringResource(R.string.next_page),
                value = if (settings.pedalNextKey != 0) keyName(settings.pedalNextKey) else stringResource(R.string.pedal_default_keys),
                onLearn = { learnKey = true },
                onReset = if (settings.pedalNextKey != 0) ({ update { it.copy(pedalNextKey = 0) } }) else null,
            )
            LearnRow(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                label = stringResource(R.string.prev_page),
                value = if (settings.pedalPrevKey != 0) keyName(settings.pedalPrevKey) else stringResource(R.string.pedal_default_keys),
                onLearn = { learnKey = false },
                onReset = if (settings.pedalPrevKey != 0) ({ update { it.copy(pedalPrevKey = 0) } }) else null,
            )
        }
    }

    learnKey?.let { next ->
        KeyLearnDialog(
            title = stringResource(if (next) R.string.next_page else R.string.prev_page),
            onKey = { code ->
                update { if (next) it.copy(pedalNextKey = code) else it.copy(pedalPrevKey = code) }
                learnKey = null
            },
            onDismiss = { learnKey = null },
        )
    }
    learnMidi?.let { next ->
        MidiLearnDialog(
            midi = midi,
            title = stringResource(if (next) R.string.next_page else R.string.prev_page),
            onCc = { cc ->
                update { if (next) it.copy(midiNextCc = cc) else it.copy(midiPrevCc = cc) }
                learnMidi = null
            },
            onDismiss = { learnMidi = null },
        )
    }
}

/** Návod: ikona, nadpis a krátky popis. */
@Composable
private fun HowToRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LearnRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onLearn: () -> Unit,
    onReset: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (onReset != null) TextButton(onClick = onReset) { Text(stringResource(R.string.calibration_reset)) }
        FilledTonalButton(onClick = onLearn) { Text(stringResource(R.string.pedal_learn)) }
    }
}

/** „Stlač pedál…“ – zachytí kláves, ktorý posiela Bluetooth pedál. */
@Composable
private fun KeyLearnDialog(title: String, onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    var captured by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.SettingsRemote) },
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Back) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyDown) captured = e.nativeKeyEvent.keyCode
                        true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    captured?.let { stringResource(R.string.pedal_captured, keyName(it)) } ?: stringResource(R.string.pedal_press_now),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = captured != null, onClick = { captured?.let(onKey) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** „Stlač pedál na klavíri…“ – zachytí číslo pedála (MIDI Control Change). */
@Composable
private fun MidiLearnDialog(midi: MidiPedals, title: String, onCc: (Int) -> Unit, onDismiss: () -> Unit) {
    var captured by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        midi.start()
        onDispose { midi.stop() }
    }
    LaunchedEffect(Unit) {
        midi.events.collect { e -> if (e.pressed) captured = e.cc }
    }
    val connected by midi.connected.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Piano, com.daklok.crescendo.ui.common.ExpressiveShapes.Squircle, com.daklok.crescendo.ui.common.Accent.Tertiary) },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    captured?.let { stringResource(R.string.pedal_captured, pedalName(it)) }
                        ?: stringResource(R.string.pedal_press_piano),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                if (connected.isEmpty()) {
                    Text(
                        stringResource(R.string.midi_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = captured != null, onClick = { captured?.let(onCc) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onCc(-1) }) { Text(stringResource(R.string.pedal_none)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Vyhľadanie Bluetooth MIDI klavírov v okolí a pripojenie. */
@SuppressLint("MissingPermission")
@Composable
internal fun BleMidiScanDialog(
    midi: MidiPedals,
    onConnected: (address: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val found = remember { mutableStateListOf<BluetoothDevice>() }
    var permitted by remember { mutableStateOf(midi.hasBluetoothPermission()) }
    var connecting by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permitted = res.values.all { it }
        if (!permitted) error = R.string.midi_bt_permission
    }
    LaunchedEffect(Unit) { if (!permitted) launcher.launch(permissions) }

    // skenovanie, kým je dialóg otvorený
    DisposableEffect(permitted) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = if (permitted && adapter?.isEnabled == true) adapter.bluetoothLeScanner else null
        if (permitted && adapter?.isEnabled != true) error = R.string.midi_bt_off
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                if (found.none { it.address == d.address }) found.add(d)
            }
        }
        runCatching {
            scanner?.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(MidiPedals.BLE_MIDI_SERVICE).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
        }
        onDispose { runCatching { scanner?.stopScan(callback) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { com.daklok.crescendo.ui.common.DialogIcon(Icons.Outlined.Bluetooth, com.daklok.crescendo.ui.common.ExpressiveShapes.Cookie12, com.daklok.crescendo.ui.common.Accent.Secondary) },
        title = { Text(stringResource(R.string.midi_bt_connect)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.midi_bt_scan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (found.isEmpty() && error == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.midi_bt_searching))
                    }
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(found, key = { it.address }) { d ->
                        val name = runCatching { d.name }.getOrNull() ?: d.address
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = connecting == null) {
                                    connecting = d.address
                                    error = null
                                    midi.connectBluetooth(d) { ok ->
                                        connecting = null
                                        if (ok) onConnected(d.address, name) else error = R.string.midi_bt_failed
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.Piano, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(name, modifier = Modifier.weight(1f))
                            if (connecting == d.address) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
