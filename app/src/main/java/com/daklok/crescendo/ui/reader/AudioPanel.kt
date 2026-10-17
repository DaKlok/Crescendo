package com.daklok.crescendo.ui.reader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.data.MidiInstrument
import com.daklok.crescendo.data.midi.MidiCapture
import com.daklok.crescendo.data.midi.MidiFile
import com.daklok.crescendo.data.midi.MidiRenderer
import com.daklok.crescendo.ui.settings.BleMidiScanDialog
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Formát času m:ss. */
fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/**
 * Prehrávač pre referenčnú nahrávku aj vlastné nahrávky: spomalenie bez zmeny výšky tónu
 * (PlaybackParams) a slučka A–B.
 */
class AudioPlayer(private val scope: CoroutineScope) {
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    var file by mutableStateOf<File?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var loopA by mutableStateOf<Long?>(null)
    var loopB by mutableStateOf<Long?>(null)

    fun load(f: File?) {
        if (f?.path == file?.path && player != null) return
        release()
        file = f
        loopA = null
        loopB = null
        if (f == null || !f.exists()) return
        val p = runCatching {
            MediaPlayer().apply {
                setDataSource(f.path)
                prepare()
                setOnCompletionListener {
                    this@AudioPlayer.playing = false
                    this@AudioPlayer.position = 0
                }
            }
        }.getOrNull() ?: return
        player = p
        duration = p.duration.toLong()
        position = 0
    }

    fun toggle() = if (playing) pause() else play()

    fun play() {
        val p = player ?: return
        runCatching {
            p.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
            p.start()
        }
        playing = true
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && playing) {
                val pos = runCatching { p.currentPosition.toLong() }.getOrDefault(position)
                val a = loopA
                val b = loopB
                if (a != null && b != null && b > a && pos >= b) {
                    runCatching { p.seekTo(a.toInt()) }
                    position = a
                } else {
                    position = pos
                }
                delay(40)
            }
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        playing = false
        ticker?.cancel()
    }

    fun seek(ms: Long) {
        position = ms
        runCatching { player?.seekTo(ms.toInt()) }
    }

    fun setPlaybackSpeed(s: Float) {
        speed = s
        val p = player ?: return
        if (playing) runCatching { p.playbackParams = PlaybackParams().setSpeed(s).setPitch(1f) }
    }

    fun release() {
        ticker?.cancel()
        runCatching { player?.release() }
        player = null
        playing = false
        position = 0
        duration = 0
    }
}

/**
 * Nahrávanie z mikrofónu do .m4a (AAC) alebo hry z digitálneho klavíra do .mid (MIDI cez USB / Bluetooth).
 * Z MIDI nahrávky sa potom vyrobí zvuk klavíra ([MidiRenderer]).
 */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var recordingFile by mutableStateOf<File?>(null)
        private set
    var startedAt by mutableLongStateOf(0L)
        private set

    /** Nahráva sa z klavíra (MIDI)? */
    var midiMode by mutableStateOf(false)
        private set

    /** Počet zahraných nôt počas MIDI nahrávania. */
    var midiNotes by mutableIntStateOf(0)
        private set

    private var capture: MidiCapture? = null
    private var midiListener: ((ByteArray, Int, Int, Long) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private val midi get() = (context.applicationContext as CrescendoApp).midi

    /** Začne nahrávať hru z pripojeného klavíra. */
    fun startMidi(target: File): Boolean {
        stop()
        val m = midi
        if (!m.supported) return false
        val cap = MidiCapture(System.nanoTime())
        var posted = 0
        val listener: (ByteArray, Int, Int, Long) -> Unit = { msg, offset, count, ts ->
            cap.onBytes(msg, offset, count, ts)
            val n = cap.notes
            if (n != posted) {
                posted = n
                main.post { if (capture === cap) midiNotes = n }
            }
        }
        m.start()
        m.addListener(listener)
        capture = cap
        midiListener = listener
        recordingFile = target
        startedAt = System.currentTimeMillis()
        midiNotes = 0
        midiMode = true
        return true
    }

    private fun stopMidi(): File? {
        val cap = capture ?: return null
        capture = null
        midiListener?.let { midi.removeListener(it) }
        midiListener = null
        midi.stop()
        val f = recordingFile
        recordingFile = null
        midiMode = false
        val events = cap.finish(System.nanoTime())
        if (f == null || events == null) return null // nič sa nezahralo
        val ok = runCatching { MidiFile.write(f, events) }.isSuccess
        if (!ok) {
            f.delete()
            return null
        }
        MidiRenderer.request(context, f)
        return f
    }

    fun start(target: File): Boolean {
        stop()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.path)
            r.prepare()
            r.start()
            recorder = r
            recordingFile = target
            startedAt = System.currentTimeMillis()
            true
        }.getOrElse {
            runCatching { r.release() }
            target.delete()
            false
        }
    }

    /** Zastaví nahrávanie a vráti hotový súbor (alebo null). */
    fun stop(): File? {
        if (capture != null) return stopMidi()
        val r = recorder ?: return null
        val f = recordingFile
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        recorder = null
        recordingFile = null
        if (!ok) f?.delete()
        return if (ok) f else null
    }
}

fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Plávajúci panel „Nahrávky“: referenčná nahrávka skladby (spomalenie, slučka A–B)
 * a vlastné nahrávky z mikrofónu.
 */
@Composable
fun AudioPanel(
    pieceId: Long,
    /** Prehrávač a nahrávač patria čítačke – bežia ďalej aj po zatvorení panela (napr. v režime hrania). */
    player: AudioPlayer,
    recorder: Recorder,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audio = (context.applicationContext as CrescendoApp).pieceAudio
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) } // zmena zoznamu súborov
    var micDenied by remember { mutableStateOf(false) }
    // potvrdenie pred zmazaním / nahradením – nahrávky sa nedajú zmazať omylom
    var confirmDeleteRecording by remember { mutableStateOf<File?>(null) }
    var confirmDeleteReference by remember { mutableStateOf(false) }
    var confirmReplaceReference by remember { mutableStateOf(false) }

    // nahrávanie z digitálneho klavíra (MIDI)
    val app = context.applicationContext as CrescendoApp
    val midi = app.midi
    val midiSupported = midi.supported
    val midiConnected by midi.connected.collectAsStateWithLifecycle()
    val midiInstruments by midi.instruments.collectAsStateWithLifecycle()
    val midiActivity by midi.activity.collectAsStateWithLifecycle()
    val midiManualOff by midi.manualOff.collectAsStateWithLifecycle()
    val rendering by MidiRenderer.rendering.collectAsStateWithLifecycle()
    val renderFailed by MidiRenderer.failed.collectAsStateWithLifecycle()
    val btAddress by remember { app.settings.settings.map { it.midiBtAddress } }.collectAsStateWithLifecycle("")
    var source by rememberSaveable { mutableIntStateOf(-1) } // -1 automaticky, 0 mikrofón, 1 klavír
    var scanOpen by remember { mutableStateOf(false) }

    val reference = remember(pieceId, version) { audio.reference(pieceId) }
    val recordings = remember(pieceId, version) { audio.recordings(pieceId) }

    val copyScope = rememberCoroutineScope()
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..4 } ?: "mp3"
            player.release()
            // kopírovanie (aj veľkého súboru) mimo hlavného vlákna
            copyScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { audio.setReference(pieceId, uri, ext) }
                version++
            }
        }
    }
    var pendingRecord by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted && pendingRecord) {
            player.pause()
            recorder.start(audio.newRecordingFile(pieceId))
        }
        pendingRecord = false
    }

    Surface(
        modifier = modifier.widthIn(max = 380.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Text(
                    stringResource(R.string.audio_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            SecondaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0; player.pause() }, text = { Text(stringResource(R.string.audio_reference)) })
                Tab(selected = tab == 1, onClick = { tab = 1; player.pause() }, text = { Text(stringResource(R.string.audio_my_recordings)) })
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "audioTab",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) { t ->
                if (t == 0) {
                    // ── referenčná nahrávka ──
                    if (reference == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.audio_reference_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }, shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.audio_add_reference))
                            }
                        }
                    } else {
                        // version: nový súbor môže mať rovnakú cestu ako starý (File.equals porovnáva cestu)
                        LaunchedEffect(reference, version) { player.load(reference) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerControls(player)
                            // rýchlosť
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(0.5f, 0.75f, 1f).forEach { s ->
                                    FilterChip(
                                        selected = player.speed == s,
                                        onClick = { player.setPlaybackSpeed(s) },
                                        label = { Text("${(s * 100).toInt()} %") },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                            Slider(
                                value = player.speed,
                                onValueChange = { player.setPlaybackSpeed((it * 20).toInt() / 20f) },
                                valueRange = 0.4f..1.5f,
                            )
                            // slučka A–B
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                FilterChip(
                                    selected = player.loopA != null,
                                    onClick = { player.loopA = if (player.loopA == null) player.position else null },
                                    label = { Text("A" + (player.loopA?.let { " " + formatClock(it) } ?: "")) },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                FilterChip(
                                    selected = player.loopB != null,
                                    enabled = player.loopA != null,
                                    onClick = {
                                        player.loopB = if (player.loopB == null) player.position.takeIf { p -> p > (player.loopA ?: 0L) } else null
                                        player.loopA?.let { a -> if (player.loopB != null) player.seek(a) }
                                    },
                                    label = { Text("B" + (player.loopB?.let { " " + formatClock(it) } ?: "")) },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { confirmReplaceReference = true }) {
                                    Icon(Icons.Outlined.SwapHoriz, stringResource(R.string.audio_replace))
                                }
                                IconButton(onClick = { confirmDeleteReference = true }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                                }
                            }
                            Text(
                                stringResource(R.string.audio_loop_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // ── vlastné nahrávky ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val recording = recorder.recordingFile != null
                        val useMidi = midiSupported && when (source) {
                            1 -> true
                            0 -> false
                            else -> midiConnected.isNotEmpty() // automaticky: klavír, ak je pripojený
                        }
                        if (midiSupported) {
                            // počas zobrazenia počúvame, aby bolo vidno, či je klavír pripojený
                            DisposableEffect(Unit) {
                                midi.start()
                                onDispose { midi.stop() }
                            }
                            LaunchedEffect(btAddress) { if (btAddress.isNotBlank()) midi.connectBluetoothAddress(btAddress) }
                            if (!recording) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !useMidi,
                                        onClick = { source = 0 },
                                        label = { Text(stringResource(R.string.rec_source_mic)) },
                                        leadingIcon = { Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    FilterChip(
                                        selected = useMidi,
                                        onClick = { source = 1 },
                                        label = { Text(stringResource(R.string.rec_source_piano)) },
                                        leadingIcon = { Icon(Icons.Outlined.Piano, null, Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                            // výrazné potvrdenie: či je klavír pripojený a ako sa volá
                            if ((useMidi && !recording) || recorder.midiMode) {
                                PianoStatusCard(
                                    instruments = midiInstruments,
                                    activity = midiActivity,
                                    recording = recorder.midiMode,
                                    manualOff = midiManualOff,
                                    onBluetooth = { scanOpen = true },
                                    onDisconnect = { midi.disconnect() },
                                    onReconnect = { midi.reconnect(btAddress) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RecordButton(recording, enabled = !useMidi || midiConnected.isNotEmpty()) {
                                if (recording) {
                                    recorder.stop()
                                    version++
                                } else if (useMidi) {
                                    player.pause()
                                    recorder.startMidi(audio.newMidiFile(pieceId))
                                } else if (hasMicPermission(context)) {
                                    player.pause()
                                    recorder.start(audio.newRecordingFile(pieceId))
                                } else {
                                    pendingRecord = true
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            if (recording) {
                                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        now = System.currentTimeMillis()
                                        delay(250)
                                    }
                                }
                                Column {
                                    Text(
                                        formatClock(now - recorder.startedAt),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    if (recorder.midiMode) {
                                        Text(
                                            stringResource(R.string.rec_notes_count, recorder.midiNotes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    when {
                                        useMidi -> stringResource(R.string.rec_piano_hint)
                                        micDenied -> stringResource(R.string.audio_mic_denied)
                                        else -> stringResource(R.string.audio_record_hint)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (micDenied && !useMidi) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // súbor na prehranie pre každú nahrávku (pri MIDI vyrobený zvuk klavíra)
                        val playables = remember(recordings, rendering, version) {
                            recordings.associateWith { MidiRenderer.playable(context, it) }
                        }
                        if (player.file != null && playables.values.any { it?.path == player.file?.path }) PlayerControls(player)
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(recordings, key = { it.path }) { f ->
                                val isMidi = MidiRenderer.isMidi(f)
                                val play = playables[f]
                                val progress = rendering[f.path]
                                val active = play != null && player.file?.path == play.path
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    when {
                                        progress != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { progress.coerceAtLeast(0.02f) },
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp,
                                            )
                                        }
                                        play == null -> IconButton(onClick = { MidiRenderer.request(context, f) }) {
                                            Icon(
                                                if (f.path in renderFailed) Icons.Outlined.ErrorOutline else Icons.Outlined.PlayArrow,
                                                stringResource(R.string.rec_make_audio),
                                                tint = if (f.path in renderFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        else -> IconButton(onClick = {
                                            if (active) player.toggle() else {
                                                player.load(play)
                                                player.play()
                                            }
                                        }) {
                                            Icon(if (active && player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                                        }
                                    }
                                    if (isMidi) {
                                        Icon(
                                            Icons.Outlined.Piano,
                                            stringResource(R.string.rec_source_piano),
                                            Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            f.nameWithoutExtension.toLongOrNull()?.let {
                                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                                            } ?: f.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (progress != null) {
                                            Text(
                                                stringResource(R.string.rec_rendering),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else if (f.path in renderFailed) {
                                            Text(
                                                stringResource(R.string.rec_render_failed),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { shareRecording(context, f) }) {
                                        Icon(Icons.Outlined.Share, stringResource(R.string.rec_share))
                                    }
                                    IconButton(onClick = { confirmDeleteRecording = f }) {
                                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteRecording?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.recording_delete_title),
            text = stringResource(R.string.recording_delete_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                val playing = MidiRenderer.playable(context, f)
                if (player.file?.path == (playing ?: f).path) player.release()
                f.delete()
                if (MidiRenderer.isMidi(f)) MidiRenderer.delete(context, f)
                version++
                confirmDeleteRecording = null
            },
            onDismiss = { confirmDeleteRecording = null },
        )
    }
    if (scanOpen) {
        BleMidiScanDialog(
            midi = midi,
            onConnected = { address, name ->
                copyScope.launch { app.settings.update { it.copy(midiBtAddress = address, midiBtName = name) } }
                scanOpen = false
            },
            onDismiss = { scanOpen = false },
        )
    }
    if (confirmDeleteReference) {
        ConfirmDialog(
            title = stringResource(R.string.reference_delete_title),
            text = stringResource(R.string.reference_delete_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                player.release()
                audio.removeReference(pieceId)
                version++
                confirmDeleteReference = false
            },
            onDismiss = { confirmDeleteReference = false },
        )
    }
    if (confirmReplaceReference) {
        ConfirmDialog(
            title = stringResource(R.string.reference_replace_title),
            text = stringResource(R.string.reference_replace_text),
            confirm = stringResource(R.string.audio_replace),
            onConfirm = {
                confirmReplaceReference = false
                pickAudio.launch(arrayOf("audio/*"))
            },
            onDismiss = { confirmReplaceReference = false },
        )
    }
}

@Composable
private fun PlayerControls(player: AudioPlayer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { player.toggle() }, modifier = Modifier.size(48.dp)) {
            Icon(if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Slider(
                value = player.position.toFloat().coerceIn(0f, player.duration.coerceAtLeast(1).toFloat()),
                onValueChange = { player.seek(it.toLong()) },
                valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(),
            )
            Row {
                Text(formatClock(player.position), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatClock(player.duration), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 0.4f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recAlpha",
    )
    FilledIconButton(
        onClick = onClick,
        enabled = enabled || recording,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        if (recording) {
            Icon(Icons.Outlined.Stop, stringResource(R.string.audio_stop_recording), Modifier.size(28.dp))
        } else {
            Box(
                Modifier
                    .size(22.dp)
                    .alpha(alpha)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
        }
    }
}


@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.Delete, null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Zdieľanie nahrávky (.m4a alebo .mid – MIDI sa dá otvoriť napr. v MuseScore). */
private fun shareRecording(context: Context, f: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val type = if (MidiRenderer.isMidi(f)) "audio/midi" else "audio/mp4"
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Výrazná karta stavu klavíra: pripojený (názov, USB / Bluetooth) alebo nie.
 * Kontrolka vpravo blikne pri každej zahranej note – vidno, že klavír naozaj posiela dáta.
 */
@Composable
private fun PianoStatusCard(
    instruments: List<MidiInstrument>,
    activity: Long,
    recording: Boolean,
    manualOff: Boolean,
    onBluetooth: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
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
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = onContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (connected) scheme.primary else scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Piano,
                    null,
                    Modifier.size(22.dp),
                    tint = if (connected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = scheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rec_piano_connected), style = MaterialTheme.typography.labelMedium)
                    }
                    instruments.forEach { inst ->
                        Text(
                            inst.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                when {
                                    inst.bluetooth -> R.string.rec_via_bluetooth
                                    inst.virtual -> R.string.rec_via_virtual
                                    else -> R.string.rec_via_usb
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!recording) {
                        Text(
                            stringResource(R.string.rec_play_to_test),
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.75f),
                        )
                        TextButton(
                            onClick = onDisconnect,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Outlined.LinkOff, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.midi_disconnect))
                        }
                    }
                } else {
                    Text(
                        stringResource(
                            when {
                                recording -> R.string.rec_piano_lost
                                manualOff -> R.string.midi_disconnected
                                else -> R.string.rec_piano_none
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            when {
                                recording -> R.string.rec_piano_lost_hint
                                manualOff -> R.string.midi_disconnected_hint
                                else -> R.string.rec_piano_none_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (connected) {
                val led by animateFloatAsState(if (lit) 1f else 0.22f, tween(if (lit) 40 else 320), label = "led")
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(14.dp)
                        .alpha(led)
                        .background(scheme.primary, CircleShape),
                )
            } else if (!recording && manualOff) {
                FilledTonalButton(
                    onClick = onReconnect,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.midi_reconnect))
                }
            } else if (!recording) {
                FilledTonalButton(
                    onClick = onBluetooth,
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
