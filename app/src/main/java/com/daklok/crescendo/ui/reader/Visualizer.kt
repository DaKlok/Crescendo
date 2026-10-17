package com.daklok.crescendo.ui.reader

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.midi.MidiFile
import com.daklok.crescendo.data.midi.VisSong
import com.daklok.crescendo.data.music.HeldNotes
import com.daklok.crescendo.ui.common.isCompactWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BLACK_KEYS = setOf(1, 3, 6, 8, 10)
private fun isBlack(p: Int) = (p % 12) in BLACK_KEYS

/** Koľko sekúnd skladby vidno nad klaviatúrou. */
private const val WINDOW_SECONDS = 3.0

/**
 * Klavírny vizualizér: padajúce noty nad klaviatúrou (pravá a ľavá ruka inou farbou),
 * zvuk z MIDI so spomalením a režim „čakaj na mňa“ – s pripojeným klavírom počká, kým stlačíš správne klávesy.
 */
@Composable
fun VisualizerPanel(
    pieceId: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val scope = rememberCoroutineScope()
    val compact = isCompactWidth()
    val midiVersion by app.pieceMidi.version.collectAsStateWithLifecycle()
    val refJobs by app.catalog.referenceJobs.collectAsStateWithLifecycle()

    // skladba (noty) z MIDI
    var song by remember { mutableStateOf<VisSong?>(null) }
    var hasMidi by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    LaunchedEffect(pieceId, midiVersion) {
        val f = app.pieceMidi.file(pieceId)
        hasMidi = f != null
        song = f?.let { withContext(Dispatchers.Default) { runCatching { VisSong.from(MidiFile.read(it)) }.getOrNull() } }
    }

    // zvuk
    val player = remember { AudioPlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // zvuk sa vyrába na pozadí (pokračuje aj po zatvorení panela) – priebeh z MidiRenderer
    val renderMap by com.daklok.crescendo.data.midi.MidiRenderer.rendering.collectAsStateWithLifecycle()
    val renderKey = remember(pieceId, midiVersion) { app.pieceMidi.renderKey(pieceId) }
    val renderProgress = renderKey?.let { renderMap[it] }
    val readyFile = remember(pieceId, midiVersion, renderProgress == null) { app.pieceMidi.soundReady(pieceId) }
    var pendingPlay by remember { mutableStateOf(false) }
    // −2 = nič, −1 = pripravuje sa, 0..1
    val soundProgress = when {
        renderProgress != null -> renderProgress
        pendingPlay && readyFile == null -> -1f
        else -> -2f
    }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }

    // čas skladby (s)
    var clock by remember { mutableDoubleStateOf(0.0) }
    var running by remember { mutableStateOf(false) }

    // režim „čakaj na mňa“; zbalený panel (len klaviatúra)
    var waitMode by rememberSaveable { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var seekVersion by remember { mutableIntStateOf(0) }
    val instruments by app.midi.instruments.collectAsStateWithLifecycle()
    val pianoConnected = instruments.isNotEmpty()
    var held by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pressedWhileWaiting by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var waitingFor by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // klavír – stlačené klávesy
    if (app.midi.supported) {
        DisposableEffect(Unit) {
            val midi = app.midi
            midi.start()
            val tracker = HeldNotes()
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val listener: (ByteArray, Int, Int, Long) -> Unit = { msg, offset, count, _ ->
                if (tracker.onBytes(msg, offset, count)) {
                    val now = tracker.notes().toSet()
                    main.post {
                        val newly = now - held
                        held = now
                        if (waitingFor.isNotEmpty()) pressedWhileWaiting = pressedWhileWaiting + newly
                    }
                }
            }
            midi.addListener(listener)
            onDispose {
                midi.removeListener(listener)
                midi.stop()
            }
        }
    }

    val chords = remember(song) { song?.chords(null).orEmpty() }

    fun startPlayback() {
        val s = song ?: return
        if (clock >= s.duration) clock = 0.0
        if (waitMode && pianoConnected) {
            player.pause()
            running = true
            return
        }
        val sound = app.pieceMidi.soundReady(pieceId)
        if (sound == null) {
            // ešte sa vyrába – spustí sa samo, keď bude hotový
            pendingPlay = true
            app.pieceMidi.prepare(pieceId)
            return
        }
        player.load(sound)
        player.setPlaybackSpeed(speed)
        player.seek((clock * 1000).toLong())
        player.play()
        running = true
    }

    // zvuk začne vyrábať hneď po otvorení (na pozadí); po dokončení spustí čakajúce prehrávanie
    LaunchedEffect(pieceId, hasMidi, midiVersion) { if (hasMidi) app.pieceMidi.prepare(pieceId) }
    LaunchedEffect(readyFile, pendingPlay) {
        if (pendingPlay && readyFile != null) {
            pendingPlay = false
            startPlayback()
        }
    }

    /** Posun na čas [t] (s) – aj počas prehrávania. */
    fun seekTo(t: Double) {
        val d = song?.duration ?: return
        clock = t.coerceIn(0.0, d)
        player.seek((clock * 1000).toLong())
        waitingFor = emptySet()
        pressedWhileWaiting = emptySet()
        seekVersion++
    }

    fun stopPlayback() {
        pendingPlay = false
        running = false
        player.pause()
        waitingFor = emptySet()
    }

    // hodiny: pri počúvaní podľa zvuku (plynulo dopočítané), v režime čakania podľa reálneho času
    LaunchedEffect(running, waitMode, speed, chords, seekVersion) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        var lastPos = -1L
        var lastPosAt = last
        var chordIndex = chords.indexOfFirst { it.time >= clock - 0.01 }.let { if (it < 0) chords.size else it }
        while (running) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9
            last = now
            val s = song ?: break
            if (waitMode && pianoConnected) {
                val target = clock + dt * speed
                val chord = chords.getOrNull(chordIndex)
                if (chord != null && target >= chord.time) {
                    clock = chord.time
                    if (waitingFor != chord.pitches) {
                        waitingFor = chord.pitches
                        pressedWhileWaiting = emptySet()
                    }
                    val ok = chord.pitches.all { it in held || it in pressedWhileWaiting }
                    if (ok) {
                        chordIndex++
                        waitingFor = emptySet()
                        pressedWhileWaiting = emptySet()
                    }
                } else {
                    clock = target
                }
                if (clock >= s.duration + 0.5) running = false
            } else {
                val pos = player.position
                if (pos != lastPos) {
                    lastPos = pos
                    lastPosAt = now
                }
                val extra = if (player.playing) ((now - lastPosAt) / 1e9 * speed).coerceAtMost(0.12) else 0.0
                clock = pos / 1000.0 + extra
                if (!player.playing && clock >= s.duration - 0.05) running = false
            }
        }
    }

    val pickMidi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        stopPlayback()
        player.release()
        scope.launch {
            val f = app.pieceMidi.import(pieceId, uri)
            importError = f == null
            if (f != null) {
                clock = 0.0
                // zvuk z MIDI aj ako referenčná nahrávka (na pozadí)
                val label = "MIDI – " + (name?.substringBeforeLast('.') ?: "import")
                app.appScope.launch { runCatching { app.catalog.referenceFromPieceMidi(pieceId, label, app.pieceAudio) } }
            }
        }
    }
    val importMidi = { pickMidi.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "*/*")) }
    // skladba z katalógu stiahnutá pred touto verziou – MIDI sa dá doplniť jedným ťuknutím
    var catalogItem by remember { mutableStateOf<com.daklok.crescendo.data.CatalogItem?>(null) }
    LaunchedEffect(pieceId, hasMidi) { if (!hasMidi) catalogItem = app.catalog.itemForPiece(pieceId) }
    val fromCatalog: (() -> Unit)? = catalogItem?.let { item ->
        { app.appScope.launch { runCatching { app.catalog.attachReference(item, pieceId, app.pieceAudio) } }; Unit }
    }
    val latestRecording = remember(pieceId, midiVersion) {
        app.pieceAudio.recordings(pieceId).firstOrNull { it.extension.equals("mid", true) }
    }
    val useRecording: (() -> Unit)? = latestRecording?.let { rec ->
        {
            stopPlayback()
            player.release()
            scope.launch {
                app.pieceMidi.setFrom(pieceId, rec, "")
                clock = 0.0
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            // hlavička
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Icon(Icons.Outlined.Piano, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.visualizer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                refJobs[pieceId]?.let { p ->
                    // vyrába sa zvuk / referenčná nahrávka
                    Text(
                        if (p < 0) stringResource(R.string.ref_job_downloading) else stringResource(R.string.vis_preparing, (p * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                if (hasMidi && song != null) {
                    IconButton(onClick = { collapsed = !collapsed }) {
                        Icon(
                            if (collapsed) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            stringResource(if (collapsed) R.string.vis_show_falling else R.string.vis_hide_falling),
                        )
                    }
                }
                if (hasMidi) {
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vis_import)) },
                                leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                                onClick = {
                                    menu = false
                                    importMidi()
                                },
                            )
                            if (useRecording != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.vis_use_recording)) },
                                    leadingIcon = { Icon(Icons.Outlined.Piano, null) },
                                    onClick = {
                                        menu = false
                                        useRecording()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vis_remove)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menu = false
                                    stopPlayback()
                                    player.release()
                                    app.pieceMidi.delete(pieceId)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }

            val s = song
            if (!hasMidi || s == null) {
                EmptyVisualizer(
                    error = importError,
                    onFromCatalog = if (refJobs[pieceId] == null) fromCatalog else null,
                    onImport = importMidi,
                    onUseRecording = useRecording,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            // padajúce noty (dajú sa skryť šípkou v hlavičke) + klaviatúra
            val rollHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = when {
                    collapsed -> if (compact) 64.dp else 84.dp
                    compact -> 240.dp
                    else -> 340.dp
                },
                animationSpec = com.daklok.crescendo.ui.common.Motion.snappy(),
                label = "rollHeight",
            )
            PianoRoll(
                song = s,
                clock = { clock },
                held = held,
                waitingFor = waitingFor,
                showFalling = !collapsed,
                keyboardHeight = if (compact) 56.dp else 76.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rollHeight)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            // ovládanie
            run {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    // čas a posuvník priamo pod klavírom
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeAndSeek(
                            clock = { clock },
                            duration = s.duration,
                            onSeek = { t -> seekTo(t) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val options: @Composable () -> Unit = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // ovládanie ako vo videu: prehrať / stop / 10 s späť / 10 s dopredu
                            PlayButton(
                                progress = soundProgress,
                                running = running,
                                onClick = { if (running) stopPlayback() else startPlayback() },
                            )
                            androidx.compose.material3.FilledTonalIconButton(onClick = {
                                stopPlayback()
                                seekTo(0.0)
                            }) { Icon(Icons.Outlined.Stop, stringResource(R.string.vis_stop)) }
                            androidx.compose.material3.FilledTonalIconButton(onClick = { seekTo(clock - 10) }) {
                                Icon(Icons.Outlined.Replay10, stringResource(R.string.vis_back10))
                            }
                            androidx.compose.material3.FilledTonalIconButton(onClick = { seekTo(clock + 10) }) {
                                Icon(Icons.Outlined.Forward10, stringResource(R.string.vis_fwd10))
                            }
                            Spacer(Modifier.width(6.dp))
                            listOf(0.5f, 0.75f, 1f).forEach { sp ->
                                FilterChip(
                                    selected = speed == sp,
                                    onClick = {
                                        speed = sp
                                        player.setPlaybackSpeed(sp)
                                    },
                                    label = { Text("${(sp * 100).toInt()} %") },
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                            FilterChip(
                                selected = waitMode && pianoConnected,
                                enabled = pianoConnected,
                                onClick = {
                                    val was = running
                                    stopPlayback()
                                    waitMode = !waitMode
                                    if (was) startPlayback()
                                },
                                label = { Text(stringResource(R.string.vis_wait_mode)) },
                                leadingIcon = { Icon(Icons.Outlined.HourglassTop, null, Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    if (compact) {
                        androidx.compose.foundation.lazy.LazyRow { item { options() } }
                    } else {
                        options()
                    }
                    val hint = when {
                        waitMode && pianoConnected && waitingFor.isNotEmpty() ->
                            stringResource(R.string.vis_waiting_for, waitingFor.sorted().joinToString(" · ") { noteLabel(it) })
                        waitMode && !pianoConnected -> stringResource(R.string.vis_wait_needs_piano)
                        !pianoConnected -> stringResource(R.string.vis_connect_hint)
                        else -> null
                    }
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (waitingFor.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (waitingFor.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Prehrať / pauza; kým sa zvuk vyrába, krúžok s priebehom. */
@Composable
private fun PlayButton(progress: Float, running: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            progress > -2f && progress < 0f -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
            progress >= 0f -> CircularProgressIndicator(
                progress = { progress.coerceAtLeast(0.02f) },
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
            )
            else -> FilledIconButton(onClick = onClick) {
                Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
            }
        }
    }
}

/** Čas a posuvník – vlastný „recompose scope“, aby sa pri každom snímku neprestavoval celý panel. */
@Composable
private fun TimeAndSeek(clock: () -> Double, duration: Double, onSeek: (Double) -> Unit, modifier: Modifier) {
    // posuvník a čas stačí obnoviť štyrikrát za sekundu (nie pri každom snímku)
    val shown by remember(clock) { androidx.compose.runtime.derivedStateOf { (clock() * 4).toLong() / 4.0 } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatClock((shown * 1000).toLong()) + " / " + formatClock((duration * 1000).toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = (shown / duration.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f),
            onValueChange = { v -> onSeek(v * duration) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
    }
}

private fun noteLabel(p: Int): String = NOTE_NAMES[p % 12] + (p / 12 - 1)

@Composable
private fun EmptyVisualizer(
    error: Boolean,
    onFromCatalog: (() -> Unit)?,
    onImport: () -> Unit,
    onUseRecording: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.vis_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.vis_empty_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error) {
            Text(
                stringResource(R.string.vis_import_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (onFromCatalog != null) {
            androidx.compose.material3.Button(onClick = onFromCatalog, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.Piano, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vis_from_catalog))
            }
        }
        FilledTonalButton(onClick = onImport, shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vis_import))
        }
        if (onUseRecording != null) {
            TextButton(onClick = onUseRecording) {
                Icon(Icons.Outlined.Piano, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vis_use_recording))
            }
        }
    }
}

/**
 * Padajúce noty (jedna farba) nad klaviatúrou. Na klaviatúre sú plne zvýraznené klávesy, ktoré treba práve stlačiť,
 * jemne tie, ktoré prídu o chvíľu, a inou farbou tie, ktoré práve stláčaš na pripojenom klavíri.
 * [showFalling] = false → len klaviatúra.
 */
@Composable
private fun PianoRoll(
    song: VisSong,
    /** Čítané až pri kreslení – každý snímok sa len prekreslí, nič sa neprestavuje. */
    clock: () -> Double,
    held: Set<Int>,
    waitingFor: Set<Int>,
    showFalling: Boolean,
    keyboardHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val now = scheme.primary
    val pressedColor = scheme.tertiary
    val laneColor = scheme.surfaceContainerLow
    val lineColor = scheme.outlineVariant
    val whiteKey = Color(0xFFF7F7F5)
    val blackKey = Color(0xFF1E1E22)
    val keyEdge = Color(0x33000000)

    Canvas(modifier) {
        val t = clock()
        val low = song.low
        val high = song.high
        val whites = (low..high).filter { !isBlack(it) }
        val whiteW = size.width / whites.size
        val whiteIndex = HashMap<Int, Int>().apply { whites.forEachIndexed { i, p -> put(p, i) } }
        fun keyX(p: Int): Float = if (!isBlack(p)) whiteIndex[p]!! * whiteW else (whiteIndex[p - 1] ?: 0) * whiteW + whiteW * 0.68f
        fun keyW(p: Int): Float = if (!isBlack(p)) whiteW else whiteW * 0.64f

        val kbH = keyboardHeight.toPx().coerceAtMost(size.height)
        val rollH = (size.height - kbH - 4f).coerceAtLeast(0f)
        val kbTop = size.height - kbH
        val falling = showFalling && rollH > 24f

        if (falling) {
            drawRoundRect(laneColor, Offset.Zero, Size(size.width, rollH), CornerRadius(18f, 18f))
            for (p in low..high) if (p % 12 == 0) {
                val x = keyX(p)
                drawLine(lineColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, rollH), strokeWidth = 1f)
            }
        }

        val active = HashSet<Int>()
        val notes = song.notes
        var i = song.firstVisible(t)
        val ahead = if (falling) WINDOW_SECONDS else 0.6
        while (i < notes.size) {
            val n = notes[i]
            if (n.start > t + ahead) break
            i++
            if (n.end < t) continue
            if (n.start <= t + 0.001 && n.end > t) active += n.pitch
            if (!falling) continue
            val yBottom = (rollH - ((n.start - t) / WINDOW_SECONDS * rollH)).toFloat()
            val yTop = (rollH - ((n.end - t) / WINDOW_SECONDS * rollH)).toFloat()
            val top = yTop.coerceAtLeast(0f)
            val bottom = yBottom.coerceAtMost(rollH)
            if (bottom - top < 1f) continue
            val color = if (isBlack(n.pitch)) lerpDark(now) else now
            val x = keyX(n.pitch) + 1.5f
            val w = keyW(n.pitch) - 3f
            drawRoundRect(color.copy(alpha = 0.92f), Offset(x, top), Size(w, bottom - top), CornerRadius(w * 0.25f, w * 0.25f))
        }
        active += waitingFor
        if (falling) drawLine(now.copy(alpha = 0.7f), Offset(0f, rollH + 2f), Offset(size.width, rollH + 2f), strokeWidth = 3f)

        for (p in whites) {
            val x = keyX(p)
            val fill = when {
                p in active -> now
                p in held -> pressedColor.copy(alpha = 0.75f)
                else -> whiteKey
            }
            drawRoundRect(fill, Offset(x + 1f, kbTop), Size(whiteW - 2f, kbH), CornerRadius(7f, 7f))
            drawRoundRect(keyEdge, Offset(x + 1f, kbTop), Size(whiteW - 2f, kbH), CornerRadius(7f, 7f), style = Stroke(1f))
            if (p in held && p in active) {
                drawRoundRect(pressedColor, Offset(x + 3f, size.height - 12f), Size(whiteW - 6f, 7f), CornerRadius(4f, 4f))
            }
        }
        for (p in low..high) {
            if (!isBlack(p) || whiteIndex[p - 1] == null) continue
            val x = keyX(p)
            val w = keyW(p)
            val fill = when {
                p in active -> lerpDark(now)
                p in held -> pressedColor
                else -> blackKey
            }
            drawRoundRect(fill, Offset(x, kbTop), Size(w, kbH * 0.62f), CornerRadius(4f, 4f))
        }
    }
}

/** Čierny kláves, ktorý príde o chvíľu – jemne zafarbený. */
private fun lerpBlackSoon(black: Color, accent: Color): Color =
    Color(black.red * 0.6f + accent.red * 0.4f, black.green * 0.6f + accent.green * 0.4f, black.blue * 0.6f + accent.blue * 0.4f, 1f)

/** Tmavší odtieň farby (noty na čiernych klávesoch). */
private fun lerpDark(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)
