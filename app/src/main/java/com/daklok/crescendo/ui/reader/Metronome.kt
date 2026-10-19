package com.daklok.crescendo.ui.reader

import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.height
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.ExpressiveShapes
import androidx.compose.material.icons.outlined.Timer
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

const val MIN_BPM = 30
const val MAX_BPM = 240

/**
 * Zvuk metronómu: kliky sa generujú a zapisujú do AudioTrack (presné časovanie podľa
 * počtu vzoriek, nie podľa časovačov). Prvá doba taktu je vyššia (akcent).
 *
 * Postupné zrýchľovanie ([ladder]): metronóm začne na [ladderFrom] a po každom kole
 * ([ladderBars] taktov) pridá [ladderStep] BPM, až kým nedosiahne [bpm] (cieľové tempo skladby).
 */
class MetronomeEngine {
    private val rate = 44_100
    private var job: Job? = null

    var playing by mutableStateOf(false)
        private set

    /** Práve znejúca doba (0 = prvá), −1 keď nehrá. */
    var beat by mutableIntStateOf(-1)
        private set

    /** Tempo skladby (pri postupnom zrýchľovaní je to cieľ). */
    var bpm by mutableIntStateOf(100)
    var beats by mutableIntStateOf(4)
    var sound by mutableStateOf(true)

    /** Postupné zrýchľovanie zapnuté. */
    var ladder by mutableStateOf(false)
    var ladderFrom by mutableIntStateOf(70)
    var ladderStep by mutableIntStateOf(2)
    var ladderBars by mutableIntStateOf(4)

    /** Tempo, ktoré práve znie (pri zrýchľovaní stúpa od [ladderFrom] po [bpm]). */
    var liveBpm by mutableIntStateOf(100)
        private set

    /** Dokončené kolá od spustenia (len pri zrýchľovaní). */
    var round by mutableIntStateOf(0)
        private set

    /** Po každom dokončenom kole (napr. počítadlo cvičebného úseku). Volá sa z vlákna zvuku. */
    @Volatile
    var onRound: (() -> Unit)? = null

    /** Začiatok zrýchľovania vždy pod cieľom. */
    val ladderStart: Int get() = ladderFrom.coerceIn(MIN_BPM, (bpm - 1).coerceAtLeast(MIN_BPM))

    /** Tempo na zobrazenie: počas zrýchľovania aktuálne, inak tempo skladby. */
    val shownBpm: Int get() = if (ladder && playing) liveBpm else bpm

    /** Priebeh zrýchľovania 0..1. */
    val ladderProgress: Float
        get() {
            val from = ladderStart
            return if (bpm > from) ((liveBpm - from).toFloat() / (bpm - from)).coerceIn(0f, 1f) else 1f
        }

    val ladderDone: Boolean get() = ladder && playing && liveBpm >= bpm

    fun toggle(scope: CoroutineScope) = if (playing) stop() else start(scope)

    fun start(scope: CoroutineScope) {
        if (playing) return
        liveBpm = if (ladder) ladderStart else bpm
        round = 0
        playing = true
        job = scope.launch(Dispatchers.Default) { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        beat = -1
    }

    /** Zapne / vypne zrýchľovanie; ak metronóm hrá, začne odznova (zrýchľovanie od začiatočného tempa). */
    fun setLadder(on: Boolean, scope: CoroutineScope) {
        ladder = on
        if (playing) {
            stop()
            start(scope)
        }
    }

    private suspend fun run() {
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val silence = ShortArray(rate / 100) // 10 ms
        try {
            track.play()
            var b = 0
            var first = true
            var barsInRound = 0
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val perBar = beats.coerceAtLeast(1)
                if (b >= perBar) b = 0
                // začiatok taktu: pri zrýchľovaní sa počítajú kolá a tempo stúpa
                if (b == 0 && !first && ladder) {
                    barsInRound++
                    if (barsInRound >= ladderBars.coerceAtLeast(1)) {
                        barsInRound = 0
                        round++
                        runCatching { onRound?.invoke() }
                        if (liveBpm < bpm) liveBpm = (liveBpm + ladderStep.coerceAtLeast(1)).coerceAtMost(bpm)
                    }
                }
                first = false
                beat = b
                val tempo = if (ladder) liveBpm else bpm
                if (!ladder) liveBpm = bpm
                var remaining = (rate * 60.0 / tempo.coerceIn(MIN_BPM, MAX_BPM)).roundToInt()
                if (sound) {
                    val c = if (b == 0 && perBar > 1) accent else normal
                    val len = min(c.size, remaining)
                    track.write(c, 0, len)
                    remaining -= len
                }
                while (remaining > 0 && kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val len = min(silence.size, remaining)
                    track.write(silence, 0, len)
                    remaining -= len
                }
                b++
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
            beat = -1
        }
    }

    private val accent = click(1760.0, 0.95)
    private val normal = click(1175.0, 0.7)

    /** Krátky „tik“: sínus s rýchlym doznievaním (25 ms). */
    private fun click(freq: Double, volume: Double): ShortArray {
        val len = rate * 25 / 1000
        return ShortArray(len) { i ->
            val t = i.toDouble() / rate
            val env = exp(-t * 160.0)
            (sin(2 * PI * freq * t) * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

/** Taliansky názov tempa. */
fun tempoName(bpm: Int): String = when {
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

/**
 * Tempo, takt a postupné zrýchľovanie sa pamätajú pre každú skladbu zvlášť (a zálohujú sa).
 * Skladba, ktorá ešte nemá uložené tempo, ho dostane z MIDI (napr. z katalógu), ak ho MIDI pozná.
 */
object MetronomePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("metronome", Context.MODE_PRIVATE)

    /** Má skladba vlastné uložené tempo? */
    fun hasTempo(c: Context, pieceId: Long): Boolean = prefs(c).contains("bpm_$pieceId")

    /** Uložené tempo a takt skladby (alebo null). */
    fun tempoOf(c: Context, pieceId: Long): Pair<Int, Int>? {
        val p = prefs(c)
        if (!p.contains("bpm_$pieceId")) return null
        return p.getInt("bpm_$pieceId", 100) to p.getInt("beats_$pieceId", 4)
    }

    suspend fun load(c: Context, pieceId: Long, engine: MetronomeEngine) {
        val p = prefs(c)
        val saved = p.contains("bpm_$pieceId")
        engine.bpm = p.getInt("bpm_$pieceId", p.getInt("bpm_last", 100))
        engine.beats = p.getInt("beats_$pieceId", 4)
        engine.sound = p.getBoolean("sound", true)
        if (!saved) {
            // tempo a takt z nôt skladby (MIDI z katalógu alebo importované), ak ich MIDI naozaj obsahuje
            midiTempo(c, pieceId)?.let { (bpm, beats) ->
                engine.bpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
                if (beats != null && beats in listOf(1, 2, 3, 4, 6)) engine.beats = beats
            }
        }
        val ladder = p.getString("lad_$pieceId", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        if (ladder != null && ladder.size >= 4) {
            engine.ladder = ladder[0] == 1
            engine.ladderFrom = ladder[1]
            engine.ladderStep = ladder[2].coerceIn(1, 20)
            engine.ladderBars = ladder[3].coerceIn(1, 32)
        } else {
            engine.ladder = false
            engine.ladderFrom = defaultLadderFrom(engine.bpm)
            engine.ladderStep = 2
            engine.ladderBars = 4
        }
    }

    fun save(c: Context, pieceId: Long, engine: MetronomeEngine) {
        prefs(c).edit()
            .putInt("bpm_$pieceId", engine.bpm)
            .putInt("bpm_last", engine.bpm)
            .putInt("beats_$pieceId", engine.beats)
            .putBoolean("sound", engine.sound)
            .putString("lad_$pieceId", "${if (engine.ladder) 1 else 0},${engine.ladderFrom},${engine.ladderStep},${engine.ladderBars}")
            .apply()
    }

    /** Predvolený začiatok zrýchľovania: asi 70 % tempa skladby. */
    fun defaultLadderFrom(bpm: Int): Int = (bpm * 0.7).roundToInt().coerceIn(MIN_BPM, (bpm - 1).coerceAtLeast(MIN_BPM))

    private suspend fun midiTempo(c: Context, pieceId: Long): Pair<Int, Int?>? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val app = c.applicationContext as com.daklok.crescendo.CrescendoApp
            // vlastné nahrávky z klavíra majú vždy 120 BPM – tie nepoužijeme
            val src = app.pieceMidi.sources(pieceId).firstOrNull { it.kind != com.daklok.crescendo.data.MidiSourceKind.RECORDING }
                ?: return@runCatching null
            val bytes = src.file.readBytes()
            val bpm = com.daklok.crescendo.data.midi.MidiFile.realTempoBpm(bytes) ?: return@runCatching null
            bpm to com.daklok.crescendo.data.midi.MidiFile.beatsPerBar(bytes)
        }.getOrNull()
    }
}

/**
 * Plávajúci panel metronómu v čítačke. Tempo a takt sú uložené pri skladbe; postupné zrýchľovanie
 * začne pomalšie a po každom kole pridá pár BPM až po tempo skladby. [spot] = práve cvičený úsek
 * (každé kolo sa mu pripočíta), [onUnlinkSpot] ho odpojí.
 */
@Composable
fun MetronomePanel(
    engine: MetronomeEngine,
    scope: CoroutineScope,
    onChanged: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    spot: String? = null,
    spotReps: Int = 0,
    onUnlinkSpot: () -> Unit = {},
) {
    val haptic = rememberHaptic()
    val taps = remember { ArrayList<Long>() }
    val scheme = MaterialTheme.colorScheme
    fun setBpm(v: Int) {
        engine.bpm = v.coerceIn(MIN_BPM, MAX_BPM)
        // začiatok zrýchľovania ostáva pod cieľom
        if (engine.ladderFrom >= engine.bpm) engine.ladderFrom = (engine.bpm - engine.ladderStep).coerceAtLeast(MIN_BPM)
        onChanged()
    }
    // dosiahnuté cieľové tempo: jemné zavibrovanie
    LaunchedEffect(engine.ladderDone) { if (engine.ladderDone) haptic(HapticFeedbackType.LongPress) }

    ToolPanel(
        title = stringResource(R.string.metronome),
        subtitle = stringResource(R.string.metronome_saved_for_piece),
        icon = Icons.Outlined.Timer,
        shape = ExpressiveShapes.Cookie,
        container = scheme.primaryContainer,
        content = scheme.onPrimaryContainer,
        onClose = onClose,
        modifier = modifier.widthIn(max = 380.dp),
        actions = {
            ToolButton(
                icon = if (engine.sound) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                description = stringResource(R.string.metronome_sound),
                active = !engine.sound,
                onClick = {
                    engine.sound = !engine.sound
                    onChanged()
                },
            )
        },
        spacing = 12.dp,
    ) {
        val maxBody = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
        Column(
            Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // tempo: veľké číslo, po stranách tlačidlá (podržaním sa mení rýchlo)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RepeatButton(Icons.Outlined.Remove, stringResource(R.string.metronome_slower)) { setBpm(engine.bpm - 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = engine.shownBpm,
                        transitionSpec = {
                            (androidx.compose.animation.slideInVertically(Motion.bouncy()) { it / 3 } +
                                androidx.compose.animation.fadeIn(tween(140))) togetherWith
                                androidx.compose.animation.fadeOut(tween(100))
                        },
                        label = "bpm",
                    ) { v ->
                        Text(
                            "$v",
                            style = MaterialTheme.typography.displayLarge,
                            color = scheme.primary,
                            maxLines = 1,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BPM", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(tempoName(engine.shownBpm), style = MaterialTheme.typography.labelLarge, color = scheme.tertiary)
                        if (engine.ladder && engine.playing) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.ladder_goal_short, engine.bpm),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                RepeatButton(Icons.Outlined.Add, stringResource(R.string.metronome_faster)) { setBpm(engine.bpm + 1) }
            }
            Slider(
                value = engine.bpm.toFloat(),
                onValueChange = { setBpm(it.roundToInt()) },
                valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            )

            // takt: spojená skupina tlačidiel
            ConnectedChoice(
                options = listOf(1, 2, 3, 4, 6),
                selected = engine.beats,
                label = { n -> "$n/4" },
                onSelect = { n ->
                    engine.beats = n
                    onChanged()
                },
            )

            // doby: prvá (prízvuk) je vlnitý kruh, aktuálna doba sa nadýchne
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            ) {
                repeat(engine.beats.coerceAtLeast(1)) { i -> BeatDot(active = engine.beat == i, accent = i == 0 && engine.beats > 1) }
            }

            LadderCard(
                engine = engine,
                onToggle = {
                    engine.setLadder(!engine.ladder, scope)
                    onChanged()
                },
                onChanged = onChanged,
                spot = spot,
                spotReps = spotReps,
                onUnlinkSpot = onUnlinkSpot,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = {
                    // ťukanie tempa: priemer posledných intervalov
                    haptic(HapticFeedbackType.TextHandleMove)
                    val now = System.currentTimeMillis()
                    if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                    taps.add(now)
                    while (taps.size > 5) taps.removeAt(0)
                    if (taps.size >= 2) {
                        val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
                        setBpm((60_000.0 / avg).roundToInt())
                    }
                },
                shape = RoundedCornerShape(50),
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.metronome_tap), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(14.dp))
            PlayCookie(
                size = 72.dp,
                playing = engine.playing,
                pulseKey = engine.beat,
                contentDescription = stringResource(if (engine.playing) R.string.metronome_stop else R.string.metronome_start),
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    engine.toggle(scope)
                },
            )
        }
    }
}

/**
 * Postupné zrýchľovanie: prepínač, začiatočné tempo, krok a dĺžka kola; počas hrania priebeh ku cieľu.
 */
@Composable
private fun LadderCard(
    engine: MetronomeEngine,
    onToggle: () -> Unit,
    onChanged: () -> Unit,
    spot: String?,
    spotReps: Int,
    onUnlinkSpot: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (engine.ladder) scheme.tertiaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerHighest,
        tween(220),
        label = "ladderBg",
    )
    Surface(shape = RoundedCornerShape(24.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(Motion.bouncy())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, Modifier.size(22.dp), tint = scheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ladder_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (engine.ladder) stringResource(
                            R.string.ladder_summary,
                            engine.ladderStart,
                            engine.ladderStep,
                            androidx.compose.ui.res.pluralStringResource(R.plurals.bars_after, engine.ladderBars, engine.ladderBars),
                            engine.bpm,
                        )
                        else stringResource(R.string.ladder_desc),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = engine.ladder, onCheckedChange = null)
            }
            if (engine.ladder) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (spot != null) {
                        Surface(shape = RoundedCornerShape(50), color = scheme.surface.copy(alpha = 0.7f)) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp), tint = scheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.ladder_spot, spot, spotReps),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                androidx.compose.material3.IconButton(onClick = onUnlinkSpot, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.ladder_spot_unlink), Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (engine.playing) {
                        // priebeh ku cieľovému tempu
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (engine.ladderDone) stringResource(R.string.ladder_done)
                                else stringResource(R.string.ladder_round, engine.round + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (engine.ladderDone) scheme.tertiary else scheme.onSurface,
                            )
                            Spacer(Modifier.width(12.dp))
                            com.daklok.crescendo.ui.common.WavyLinearProgress(
                                progress = engine.ladderProgress,
                                modifier = Modifier.weight(1f),
                                moving = !engine.ladderDone,
                                color = scheme.tertiary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${engine.liveBpm}/${engine.bpm}",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    // začiatočné tempo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ladder_from, engine.ladderStart),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(88.dp),
                        )
                        Slider(
                            value = engine.ladderStart.toFloat(),
                            onValueChange = {
                                engine.ladderFrom = it.roundToInt()
                                onChanged()
                            },
                            valueRange = MIN_BPM.toFloat()..(engine.bpm - 1).coerceAtLeast(MIN_BPM + 1).toFloat(),
                            enabled = !engine.playing,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.ladder_step), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            ConnectedChoice(
                                options = listOf(1, 2, 4, 5),
                                selected = engine.ladderStep,
                                label = { "+$it" },
                                onSelect = {
                                    engine.ladderStep = it
                                    onChanged()
                                },
                                height = 36.dp,
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.ladder_every), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            ConnectedChoice(
                                options = listOf(1, 2, 4, 8),
                                selected = engine.ladderBars,
                                label = { "$it" },
                                onSelect = {
                                    engine.ladderBars = it
                                    onChanged()
                                },
                                height = 36.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tlačidlo +/−: ťuk zmení o jeden krok, podržanie mení rýchlo dookola. */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, onStep: () -> Unit) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val corner by animateIntAsState(if (pressed) 22 else 32, Motion.bouncy(), label = "repeatShape")
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(percent = corner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic(HapticFeedbackType.TextHandleMove)
                    step()
                    val job = scope.launch {
                        delay(420)
                        while (isActive) {
                            step()
                            delay(70)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun BeatDot(active: Boolean, accent: Boolean) {
    val scale by animateFloatAsState(if (active) 1.3f else 1f, Motion.bouncy(), label = "beat")
    val color by animateColorAsState(
        when {
            active && accent -> MaterialTheme.colorScheme.tertiary
            active -> MaterialTheme.colorScheme.primary
            accent -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "beatColor",
    )
    Box(
        Modifier
            .size(if (accent) 22.dp else 16.dp)
            .scale(scale)
            .background(color, if (accent) ExpressiveShapes.Cookie else CircleShape),
    )
}
