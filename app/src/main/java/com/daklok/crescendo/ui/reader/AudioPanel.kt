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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Headphones
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.verticalScroll
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
import com.daklok.crescendo.ui.common.PianoConnectionCard
import com.daklok.crescendo.data.midi.MidiCapture
import com.daklok.crescendo.data.midi.MidiFile
import com.daklok.crescendo.data.midi.MidiRenderer
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

    /** Kedy bol načítaný súbor naposledy zmenený – ak sa súbor na tej istej ceste prepíše (nová nahrávka), načíta sa znova. */
    private var loadedStamp = 0L

    fun load(f: File?) {
        val stamp = f?.let { it.lastModified() xor it.length() } ?: 0L
        if (f?.path == file?.path && player != null && stamp == loadedStamp) return
        release()
        loadedStamp = stamp
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
    var version by remember { mutableIntStateOf(0) } // zmena zoznamu súborov
    var micDenied by remember { mutableStateOf(false) }
    // potvrdenie pred zmazaním / nahradením – nahrávky sa nedajú zmazať omylom
    var confirmDeleteRecording by remember { mutableStateOf<File?>(null) }
    var confirmDeleteReference by remember { mutableStateOf<com.daklok.crescendo.data.ReferenceAudio?>(null) }
    var renameReference by remember { mutableStateOf<com.daklok.crescendo.data.ReferenceAudio?>(null) }

    // nahrávanie z digitálneho klavíra (MIDI)
    val app = context.applicationContext as CrescendoApp
    val midi = app.midi
    val midiSupported = midi.supported
    val midiConnected by midi.connected.collectAsStateWithLifecycle()
    val rendering by MidiRenderer.rendering.collectAsStateWithLifecycle()
    val renderFailed by MidiRenderer.failed.collectAsStateWithLifecycle()
    val btAddress by remember { app.settings.settings.map { it.midiBtAddress } }.collectAsStateWithLifecycle("")
    var source by rememberSaveable { mutableIntStateOf(-1) } // -1 automaticky, 0 mikrofón, 1 klavír

    // referenčná nahrávka z katalógu (vyrába sa z MIDI) – priebeh, a po dokončení obnoviť zoznam
    val refJobs by app.catalog.referenceJobs.collectAsStateWithLifecycle()
    val refJob = refJobs[pieceId]
    LaunchedEffect(refJob == null) { if (refJob == null) version++ }
    // zastaraná oficiálna nahrávka z katalógu (staršia verzia aplikácie) – vyrobí sa znova
    LaunchedEffect(pieceId) { app.appScope.launch { runCatching { app.catalog.refreshOfficialIfOutdated(pieceId, audio) } } }
    val references = remember(pieceId, version) { audio.references(pieceId) }
    val reference = remember(pieceId, version) { audio.reference(pieceId) }
    val recordings = remember(pieceId, version) { audio.recordings(pieceId) }

    val copyScope = rememberCoroutineScope()
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..4 } ?: "mp3"
            player.release()
            // kopírovanie (aj veľkého súboru) mimo hlavného vlákna – pridá sa k ostatným, nič sa neprepíše
            copyScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { audio.addReference(pieceId, uri, name, ext) }
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

    // prehrávač: ak nič nehrá, pripraví sa vybraná referenčná nahrávka (bez spustenia)
    LaunchedEffect(reference, version) {
        if (player.file == null || (player.file?.exists() != true)) reference?.let { player.load(it) }
    }
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
    }
    // súbor na prehranie pre každú vlastnú nahrávku (pri MIDI vyrobený zvuk klavíra)
    val playables = remember(recordings, rendering, version) { recordings.associateWith { MidiRenderer.playable(context, it) } }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    fun recordingTitle(f: File) = f.nameWithoutExtension.toLongOrNull()?.let { dateFormat.format(Date(it)) } ?: f.name
    val officialLabel = stringResource(R.string.reference_official)

    // čo práve hrá (názov do prehrávača)
    val nowFile = player.file
    val nowRef = references.firstOrNull { it.file.path == nowFile?.path }
    val nowRec = recordings.firstOrNull { playables[it]?.path == nowFile?.path }
    val nowTitle = when {
        nowRef != null -> if (nowRef.official) officialLabel else nowRef.label
        nowRec != null -> recordingTitle(nowRec)
        else -> null
    }

    fun playReference(ref: com.daklok.crescendo.data.ReferenceAudio) {
        if (player.file?.path == ref.file.path) {
            player.toggle()
            return
        }
        audio.select(pieceId, ref.file)
        player.load(ref.file)
        player.play()
        version++
    }

    fun playRecording(f: File) {
        val play = playables[f]
        if (play == null) {
            MidiRenderer.request(context, f) // MIDI – najprv sa vyrobí zvuk
            return
        }
        if (player.file?.path == play.path) player.toggle() else {
            player.load(play)
            player.play()
        }
    }

    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.78f).dp
    Surface(
        modifier = modifier
            .widthIn(max = 420.dp)
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            // hlavička
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 4.dp)) {
                Icon(Icons.Outlined.Headphones, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.audio_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            Column(
                Modifier
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── 1. prehrávač ──
                NowPlayingCard(player = player, title = nowTitle)

                // ── 2. na počúvanie (referenčné nahrávky) ──
                SectionLabel(Icons.Outlined.LibraryMusic, stringResource(R.string.audio_listen_section))
                if (refJob != null) {
                    JobCard(
                        text = if (refJob < 0) stringResource(R.string.ref_job_downloading)
                        else stringResource(R.string.ref_job_rendering, (refJob * 100).toInt()),
                        progress = refJob,
                    )
                }
                if (references.isEmpty() && refJob == null) {
                    Text(
                        stringResource(R.string.audio_reference_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                references.forEach { ref ->
                    val active = player.file?.path == ref.file.path
                    TrackRow(
                        icon = if (ref.official) Icons.Outlined.AutoAwesome else Icons.Outlined.LibraryMusic,
                        title = if (ref.official) officialLabel else ref.label,
                        subtitle = if (ref.official) stringResource(R.string.reference_official_desc)
                        else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ref.added)),
                        active = active,
                        playing = active && player.playing,
                        onClick = { playReference(ref) },
                        menu = buildList {
                            if (!ref.official) add(TrackAction(Icons.Outlined.Edit, R.string.reference_rename) { renameReference = ref })
                            add(TrackAction(Icons.Outlined.Delete, R.string.delete) { confirmDeleteReference = ref })
                        },
                    )
                }
                TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.audio_add_from_file))
                }

                // ── 3. moje nahrávky ──
                SectionLabel(Icons.Outlined.Mic, stringResource(R.string.audio_my_recordings))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (midiSupported && !recording) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !useMidi,
                                    onClick = { source = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    icon = { Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)) },
                                ) { Text(stringResource(R.string.rec_source_mic), maxLines = 1) }
                                SegmentedButton(
                                    selected = useMidi,
                                    onClick = { source = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    icon = { Icon(Icons.Outlined.Piano, null, Modifier.size(18.dp)) },
                                ) { Text(stringResource(R.string.rec_source_piano), maxLines = 1) }
                            }
                        }
                        if ((useMidi && !recording) || recorder.midiMode) {
                            PianoConnectionCard(compact = true, recording = recorder.midiMode)
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
                                    Text(
                                        if (recorder.midiMode) stringResource(R.string.rec_notes_count, recorder.midiNotes)
                                        else stringResource(R.string.audio_tap_to_stop),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Column {
                                    Text(
                                        stringResource(R.string.audio_record_me),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
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
                        }
                    }
                }
                recordings.forEach { f ->
                    val isMidi = MidiRenderer.isMidi(f)
                    val play = playables[f]
                    val progress = rendering[f.path]
                    val failed = f.path in renderFailed
                    val active = play != null && player.file?.path == play.path
                    TrackRow(
                        icon = if (isMidi) Icons.Outlined.Piano else Icons.Outlined.Mic,
                        title = recordingTitle(f),
                        subtitle = when {
                            progress != null -> stringResource(R.string.rec_rendering)
                            failed -> stringResource(R.string.rec_render_failed)
                            isMidi -> stringResource(R.string.rec_source_piano)
                            else -> stringResource(R.string.rec_source_mic)
                        },
                        subtitleError = failed,
                        active = active,
                        playing = active && player.playing,
                        progress = progress,
                        onClick = { playRecording(f) },
                        menu = listOf(
                            TrackAction(Icons.Outlined.Share, R.string.rec_share) { shareRecording(context, f) },
                            TrackAction(Icons.Outlined.Delete, R.string.delete) { confirmDeleteRecording = f },
                        ),
                    )
                }
                Spacer(Modifier.size(4.dp))
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
    confirmDeleteReference?.let { ref ->
        ConfirmDialog(
            title = stringResource(R.string.reference_delete_title),
            text = stringResource(if (ref.official) R.string.reference_delete_official_text else R.string.reference_delete_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                if (player.file?.path == ref.file.path) player.release()
                audio.removeReference(pieceId, ref.file)
                version++
                confirmDeleteReference = null
            },
            onDismiss = { confirmDeleteReference = null },
        )
    }
    renameReference?.let { ref ->
        var text by remember(ref) { mutableStateOf(ref.label) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameReference = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Edit, null) },
            title = { Text(stringResource(R.string.reference_rename)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (player.file?.path == ref.file.path) player.release()
                    audio.rename(pieceId, ref, text)
                    version++
                    renameReference = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameReference = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Akcia v ponuke ⋮ pri nahrávke. */
private class TrackAction(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int, val onClick: () -> Unit)

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Jedna nahrávka v zozname: ťuk = prehrať / pauza, ⋮ = ďalšie akcie. Práve vybraná je zvýraznená.
 */
@Composable
private fun TrackRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    menu: List<TrackAction>,
    progress: Float? = null,
    subtitleError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container by androidx.compose.animation.animateColorAsState(
        if (active) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        label = "trackRow",
    )
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (active) scheme.primary else scheme.surfaceContainerLow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress.coerceAtLeast(0.02f) }, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(
                        when {
                            playing -> Icons.Outlined.Pause
                            active -> Icons.Outlined.PlayArrow
                            else -> icon
                        },
                        null,
                        Modifier.size(20.dp),
                        tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (subtitleError) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
                    menu.forEach { action ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(action.label)) },
                            leadingIcon = { Icon(action.icon, null) },
                            onClick = {
                                open = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Priebeh výroby nahrávky z katalógu. */
@Composable
private fun JobCard(text: String, progress: Float) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (progress < 0) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            else androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.ref_job_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Prehrávač hore v paneli: názov, prehrať / pauza, posuvník s časom, rýchlosť a slučka A–B –
 * platí pre čokoľvek, čo práve počúvaš (referenčnú aj vlastnú nahrávku).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingCard(player: AudioPlayer, title: String?) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title == null || player.file == null) {
                Text(
                    stringResource(R.string.audio_nothing_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { player.toggle() }, modifier = Modifier.size(52.dp)) {
                    Icon(if (player.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatClock(player.position) + " / " + formatClock(player.duration),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Slider(
                value = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { v -> player.seek((v * player.duration).toLong()) },
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(0.5f, 0.75f, 1f).forEach { sp ->
                    FilterChip(
                        selected = player.speed == sp,
                        onClick = { player.setPlaybackSpeed(sp) },
                        label = { Text("${(sp * 100).toInt()} %") },
                        leadingIcon = if (sp == 0.5f) ({ Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp)) }) else null,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                FilterChip(
                    selected = player.loopA != null,
                    onClick = { player.loopA = if (player.loopA == null) player.position else null },
                    label = { Text("A" + (player.loopA?.let { " " + formatClock(it) } ?: "")) },
                    leadingIcon = { Icon(Icons.Outlined.Repeat, null, Modifier.size(16.dp)) },
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
