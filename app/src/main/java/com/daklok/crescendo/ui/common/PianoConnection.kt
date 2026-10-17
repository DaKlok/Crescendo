package com.daklok.crescendo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.MidiInstrument
import com.daklok.crescendo.ui.settings.BleMidiScanDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Karta stavu digitálneho klavíra – rovnaká v nastaveniach, nahrávkach aj v akordoch:
 * či je pripojený, jeho meno a spôsob pripojenia, kontrolka aktivity (blikne pri každej note)
 * a tlačidlá Odpojiť / Pripojiť / Bluetooth.
 *
 * [compact] – jeden riadok (do malých panelov); [connectedHint] / [disconnectedHint] – vlastný popis v kompaktnom režime.
 */
@Composable
fun PianoConnectionCard(
    modifier: Modifier = Modifier,
    recording: Boolean = false,
    compact: Boolean = false,
    connectedHint: String? = null,
    disconnectedHint: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val midi = app.midi
    val scope = rememberCoroutineScope()
    val instruments by midi.instruments.collectAsStateWithLifecycle()
    val activity by midi.activity.collectAsStateWithLifecycle()
    val manualOff by midi.manualOff.collectAsStateWithLifecycle()
    val btAddress by remember { app.settings.settings.map { it.midiBtAddress } }.collectAsStateWithLifecycle("")
    var scanOpen by remember { mutableStateOf(false) }

    if (!midi.supported) {
        Text(
            stringResource(R.string.pedal_midi_unsupported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    // kým je karta na obrazovke, počúvame (aby bolo vidno skutočný stav) a skúsime uložený Bluetooth klavír
    DisposableEffect(Unit) {
        midi.start()
        onDispose { midi.stop() }
    }
    LaunchedEffect(btAddress) { if (btAddress.isNotBlank()) midi.connectBluetoothAddress(btAddress) }

    val connected = instruments.isNotEmpty()
    val scheme = MaterialTheme.colorScheme
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(activity) {
        if (activity > 0 && android.os.SystemClock.uptimeMillis() - activity < 1000) {
            lit = true
            delay(160)
            lit = false
        }
    }
    val container by animateColorAsState(
        when {
            connected -> scheme.primaryContainer
            recording -> scheme.errorContainer
            else -> scheme.surfaceContainerHighest
        },
        label = "pianoCard",
    )
    val onContainer = when {
        connected -> scheme.onPrimaryContainer
        recording -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    val title = when {
        connected -> null
        recording -> stringResource(R.string.rec_piano_lost)
        manualOff -> stringResource(R.string.midi_disconnected)
        else -> stringResource(R.string.rec_piano_none)
    }
    val hint = when {
        recording -> stringResource(R.string.rec_piano_lost_hint)
        manualOff -> stringResource(R.string.midi_disconnected_hint)
        else -> stringResource(R.string.rec_piano_none_hint)
    }

    Surface(
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        color = container,
        contentColor = onContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (compact) 32.dp else 40.dp)
                    .background(if (connected) scheme.primary else scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Piano,
                    null,
                    Modifier.size(if (compact) 18.dp else 22.dp),
                    tint = if (connected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                if (connected) {
                    if (!compact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = scheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.rec_piano_connected), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    instruments.forEach { inst ->
                        Text(
                            inst.name,
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact) Text(connectionLabel(inst), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (compact) connectedHint ?: connectionLabel(instruments.first())
                        else if (!recording) stringResource(R.string.rec_play_to_test) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                        maxLines = 2,
                    )
                    if (!compact && !recording) {
                        TextButton(
                            onClick = { midi.disconnect() },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Outlined.LinkOff, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.midi_disconnect))
                        }
                    }
                } else {
                    Text(title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (compact) disconnectedHint ?: hint else hint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                    )
                }
            }
            when {
                connected -> {
                    val led by animateFloatAsState(if (lit) 1f else 0.22f, tween(if (lit) 40 else 320), label = "led")
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(if (compact) 12.dp else 14.dp)
                            .alpha(led)
                            .background(scheme.primary, CircleShape),
                    )
                    if (compact && !recording) {
                        IconButton(onClick = { midi.disconnect() }) {
                            Icon(Icons.Outlined.LinkOff, stringResource(R.string.midi_disconnect))
                        }
                    }
                }
                recording -> Unit
                manualOff -> if (compact) {
                    IconButton(onClick = { midi.reconnect(btAddress) }) {
                        Icon(Icons.Outlined.Link, stringResource(R.string.midi_reconnect))
                    }
                } else {
                    FilledTonalButton(
                        onClick = { midi.reconnect(btAddress) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.midi_reconnect))
                    }
                }
                else -> if (compact) {
                    IconButton(onClick = { scanOpen = true }) {
                        Icon(Icons.Outlined.Bluetooth, stringResource(R.string.midi_bt_connect))
                    }
                } else {
                    FilledTonalButton(
                        onClick = { scanOpen = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Bluetooth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rec_bluetooth))
                    }
                }
            }
        }
    }

    if (scanOpen) {
        BleMidiScanDialog(
            midi = midi,
            onConnected = { address, name ->
                scope.launch { app.settings.update { it.copy(midiBtAddress = address, midiBtName = name) } }
                scanOpen = false
            },
            onDismiss = { scanOpen = false },
        )
    }
}

@Composable
private fun connectionLabel(inst: MidiInstrument): String = stringResource(
    when {
        inst.bluetooth -> R.string.rec_via_bluetooth
        inst.virtual -> R.string.rec_via_virtual
        else -> R.string.rec_via_usb
    },
)
