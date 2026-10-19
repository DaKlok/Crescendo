package com.daklok.crescendo.ui.reader

import kotlin.math.sin
import kotlin.math.cos
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.ConnectedChoice
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Box
import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.data.music.Chord
import com.daklok.crescendo.data.music.ChordDetector
import com.daklok.crescendo.data.music.ChordListener
import com.daklok.crescendo.data.music.ChordQuality
import com.daklok.crescendo.data.music.ChordSmoother
import com.daklok.crescendo.data.music.HeldNotes
import com.daklok.crescendo.ui.common.PianoConnectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

// v slovenčine sa tón B volá H (a B je B♭); inde medzinárodné názvy
internal val NOTE_NAMES_SK = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "B", "H")
internal val NOTE_NAMES_EN = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
internal val NOTE_NAMES: Array<String>
    get() = if (java.util.Locale.getDefault().language == "sk") NOTE_NAMES_SK else NOTE_NAMES_EN

/**
 * Odhad základnej frekvencie algoritmom YIN (spoľahlivý pre nástroje aj hlas).
 * Vráti Hz alebo −1, ak signál nie je dosť silný / periodický.
 */
private fun yinPitch(buf: FloatArray, rate: Int): Float {
    val half = buf.size / 2
    // ticho -> nič
    var energy = 0f
    for (v in buf) energy += v * v
    if (energy / buf.size < 1e-5f) return -1f

    val d = FloatArray(half)
    for (tau in 1 until half) {
        var sum = 0f
        for (i in 0 until half) {
            val delta = buf[i] - buf[i + tau]
            sum += delta * delta
        }
        d[tau] = sum
    }
    // kumulatívna normalizácia
    val cmnd = FloatArray(half)
    cmnd[0] = 1f
    var running = 0f
    for (tau in 1 until half) {
        running += d[tau]
        cmnd[tau] = if (running == 0f) 1f else d[tau] * tau / running
    }
    val minTau = rate / 1500 // do ~1500 Hz
    val maxTau = (rate / 40).coerceAtMost(half - 1) // od ~40 Hz
    var tau = minTau
    while (tau < maxTau) {
        if (cmnd[tau] < 0.15f) {
            while (tau + 1 < maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
            // parabolická interpolácia pre presnosť
            val x0 = if (tau > 1) cmnd[tau - 1] else cmnd[tau]
            val x2 = if (tau + 1 < half) cmnd[tau + 1] else cmnd[tau]
            val denom = 2f * (2f * cmnd[tau] - x2 - x0)
            val better = if (denom != 0f) tau + (x2 - x0) / denom else tau.toFloat()
            return rate / better
        }
        tau++
    }
    return -1f
}

internal const val MIC_RATE = 44_100

/**
 * Počúva mikrofón a volá [onFrame] s rámcom [frame] vzoriek (−1..1) každých [hop] vzoriek.
 * Skončí, keď sa zruší korutina. Ak je mikrofón obsadený (napr. nahrávanie), ticho skončí.
 */
@SuppressLint("MissingPermission")
internal suspend fun listenMic(frame: Int, hop: Int, onFrame: (FloatArray) -> Unit) = withContext(Dispatchers.Default) {
    val minBuf = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = runCatching {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            MIC_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, hop * 4),
        )
    }.getOrNull() ?: return@withContext
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
        rec.release()
        return@withContext
    }
    val shorts = ShortArray(hop)
    val window = FloatArray(frame)
    try {
        if (runCatching { rec.startRecording() }.isFailure) return@withContext
        while (currentCoroutineContext().isActive) {
            var read = 0
            while (read < hop) {
                val n = rec.read(shorts, read, hop - read)
                if (n <= 0) break
                read += n
            }
            if (read < hop) {
                // chyba čítania – krátka pauza namiesto točenia naprázdno
                kotlinx.coroutines.delay(100)
                continue
            }
            // posuvné okno: staré vzorky dopredu, nové na koniec
            System.arraycopy(window, hop, window, 0, frame - hop)
            for (i in 0 until hop) window[frame - hop + i] = shorts[i] / 32768f
            onFrame(window)
        }
    } finally {
        runCatching { rec.stop() }
        rec.release()
    }
}

/** Ladička: zachytí tón z mikrofónu a ukáže notu a odchýlku v centoch. */
@SuppressLint("MissingPermission")
@Composable
fun TunerContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasMicPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var a4 by remember { mutableIntStateOf(440) }
    var freq by remember { mutableFloatStateOf(-1f) }

    if (permitted) {
        LaunchedEffect(Unit) {
            val recent = ArrayDeque<Float>()
            listenMic(frame = 4096, hop = 4096) { buf ->
                val f = yinPitch(buf, MIC_RATE)
                if (f > 0) {
                    recent.addLast(f)
                    while (recent.size > 5) recent.removeFirst()
                    freq = recent.sorted()[recent.size / 2] // medián proti skokom
                } else {
                    recent.clear()
                    freq = -1f
                }
            }
        }
    }

    // nota a odchýlka
    val midi = if (freq > 0) 69 + 12 * log2(freq / a4) else Float.NaN
    val nearest = if (midi.isNaN()) 0 else midi.roundToInt()
    val cents = if (midi.isNaN()) 0f else ((midi - nearest) * 100f)
    val inTune = !midi.isNaN() && abs(cents) < 5f
    val needle by animateFloatAsState(if (midi.isNaN()) 0f else cents.coerceIn(-50f, 50f), spring(stiffness = 300f), label = "needle")
    val color by animateColorAsState(
        when {
            midi.isNaN() -> MaterialTheme.colorScheme.outline
            inTune -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        },
        label = "tuneColor",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!permitted) {
            Text(
                stringResource(R.string.tuner_need_mic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.tuner_allow_mic))
            }
            return@Column
        }
        // oblúková stupnica −50…+50 centov: zelená zóna uprostred, guľka ukazuje odchýlku, tón v strede oblúka
        val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        val zone = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        val tick = MaterialTheme.colorScheme.outlineVariant
        val center = MaterialTheme.colorScheme.primary
        val onDot = MaterialTheme.colorScheme.surface
        val hasPitch = !midi.isNaN()
        Box(
            Modifier
                .fillMaxWidth()
                .height(156.dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val sw = 12.dp.toPx()
                val cx = size.width / 2f
                val r = minOf(size.width / 2f - sw * 2f, (size.height - sw * 2f) / 0.83f)
                val cy = sw * 1.5f + r
                val tl = Offset(cx - r, cy - r)
                val sz = Size(2f * r, 2f * r)
                drawArc(track, 190f, 160f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                // ±5 centov = naladené
                drawArc(zone, 262f, 16f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                for (c in -50..50 step 10) {
                    val a = Math.toRadians((270f + c / 50f * 80f).toDouble())
                    val r1 = r + sw
                    val r2 = r1 + if (c == 0) 10.dp.toPx() else 5.dp.toPx()
                    drawLine(
                        if (c == 0) center else tick,
                        Offset(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat()),
                        Offset(cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if (hasPitch) {
                    val a = Math.toRadians((270f + needle / 50f * 80f).toDouble())
                    val p = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                    drawCircle(color, radius = sw * 1.05f, center = p)
                    drawCircle(onDot, radius = sw * 0.38f, center = p)
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    if (!hasPitch) "-" else NOTE_NAMES[((nearest % 12) + 12) % 12],
                    style = MaterialTheme.typography.displayLarge,
                    color = color,
                )
                if (hasPitch) {
                    Text(
                        "${nearest / 12 - 1}",
                        style = MaterialTheme.typography.titleLarge,
                        color = color,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                    )
                }
            }
        }
        Text(
            if (!hasPitch) stringResource(R.string.tuner_play_note)
            else String.format(java.util.Locale.US, "%.1f Hz · %+d ct", freq, cents.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.Outlined.Remove, stringResource(R.string.tuner_a4_down), onClick = { a4 = (a4 - 1).coerceAtLeast(415) })
            Text(
                "A4 = $a4 Hz",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            ToolButton(Icons.Outlined.Add, stringResource(R.string.tuner_a4_up), onClick = { a4 = (a4 + 1).coerceAtMost(466) })
        }
    }
}

/**
 * Samostatný panel „Ladička“ v čítačke: režim Tón (ladenie) a Akord (celý akord – z klavíra cez MIDI,
 * inak z mikrofónu).
 */
@Composable
fun TunerPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(1) }
    val scheme = MaterialTheme.colorScheme
    val noteLabel = stringResource(R.string.tuner_mode_note)
    val chordLabel = stringResource(R.string.tuner_mode_chord)
    ToolPanel(
        title = stringResource(R.string.tuner),
        icon = Icons.Outlined.GraphicEq,
        shape = ExpressiveShapes.Clover,
        container = scheme.tertiaryContainer,
        content = scheme.onTertiaryContainer,
        onClose = onClose,
        modifier = modifier.widthIn(max = 360.dp),
    ) {
        ConnectedChoice(
            options = listOf(0, 1),
            selected = mode,
            label = { if (it == 0) noteLabel else chordLabel },
            onSelect = { mode = it },
        )
        if (mode == 0) {
            TunerContent(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        } else {
            ChordContent(Modifier.fillMaxWidth())
        }
    }
}

/** Názov akordu, napr. „Am7“ alebo „C/E“ (v slovenčine H a B). */
private fun chordSymbol(c: Chord): String {
    val names = NOTE_NAMES
    if (c.quality == null) return c.notes.joinToString(" · ") { names[it] }
    val base = names[c.root] + c.quality.symbol
    return if (c.bass != c.root) base + "/" + names[c.bass] else base
}

@Composable
private fun qualityName(q: ChordQuality): String = stringResource(
    when (q) {
        ChordQuality.MAJOR -> R.string.chord_major
        ChordQuality.MINOR -> R.string.chord_minor
        ChordQuality.DOM7, ChordQuality.DOM7_NO5 -> R.string.chord_dom7
        ChordQuality.MAJ7, ChordQuality.MAJ7_NO5 -> R.string.chord_maj7
        ChordQuality.MIN7, ChordQuality.MIN7_NO5 -> R.string.chord_min7
        ChordQuality.HALF_DIM -> R.string.chord_half_dim
        ChordQuality.DIM7 -> R.string.chord_dim7
        ChordQuality.DIM -> R.string.chord_dim
        ChordQuality.AUG -> R.string.chord_aug
        ChordQuality.SUS4 -> R.string.chord_sus4
        ChordQuality.SUS2 -> R.string.chord_sus2
        ChordQuality.SIX -> R.string.chord_six
        ChordQuality.MIN6 -> R.string.chord_min6
        ChordQuality.ADD9 -> R.string.chord_add9
        ChordQuality.MADD9 -> R.string.chord_madd9
        ChordQuality.NINE -> R.string.chord_nine
        ChordQuality.MAJ9 -> R.string.chord_maj9
        ChordQuality.MIN9 -> R.string.chord_min9
        ChordQuality.MIN_MAJ7 -> R.string.chord_min_maj7
        ChordQuality.SEVEN_SUS4 -> R.string.chord_7sus4
        ChordQuality.POWER -> R.string.chord_power
    },
)

/**
 * Režim „Akord“: ukáže celý akord, jeho druh, tóny a klaviatúru.
 * Ak je pripojený digitálny klavír, berie stlačené klávesy (presné); inak počúva mikrofón.
 */
@Composable
private fun ChordContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val midi = (context.applicationContext as CrescendoApp).midi
    val instruments by midi.instruments.collectAsStateWithLifecycle()
    val fromPiano = instruments.isNotEmpty()

    // posledný rozpoznaný akord ostáva zobrazený (stlmene), kým nezaznie ďalší
    var chord by remember { mutableStateOf<Chord?>(null) }
    var sounding by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf<List<Int>>(emptyList()) }

    if (midi.supported) {
        DisposableEffect(Unit) {
            midi.start()
            val held = HeldNotes()
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val listener: (ByteArray, Int, Int, Long) -> Unit = { msg, offset, count, _ ->
                if (held.onBytes(msg, offset, count)) {
                    val notes = held.notes()
                    main.post {
                        if (notes.isNotEmpty()) {
                            chord = ChordDetector.detect(notes.map { it % 12 }.toSet(), notes.min())
                            keys = notes
                        }
                        sounding = notes.isNotEmpty()
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

    var permitted by remember { mutableStateOf(hasMicPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    if (!fromPiano && permitted) {
        LaunchedEffect(Unit) {
            val listener = ChordListener(MIC_RATE)
            val smoother = ChordSmoother()
            listenMic(frame = listener.frameSize, hop = listener.frameSize / 2) { buf ->
                val notes = listener.notes(buf)
                val c = smoother.push(notes, listener.lastEnergy)
                if (c != null) {
                    chord = c
                    keys = notes.filter { it % 12 in c.notes }
                }
                sounding = c != null
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(if (sounding) scheme.primary else scheme.onSurfaceVariant, label = "chordColor")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // odkiaľ sa počúva: pripojený klavír (presne) alebo mikrofón
        PianoConnectionCard(
            compact = true,
            connectedHint = stringResource(R.string.chord_hint_piano),
            disconnectedHint = stringResource(R.string.chord_hint_mic),
        )
        if (!fromPiano && !permitted) {
            Text(
                stringResource(R.string.tuner_need_mic),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.tuner_allow_mic))
            }
            return@Column
        }
        val c = chord
        Text(
            c?.let { chordSymbol(it) } ?: "-",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        Text(
            when {
                c == null -> stringResource(if (fromPiano) R.string.chord_play_piano else R.string.chord_play_mic)
                c.quality == null -> stringResource(R.string.chord_unknown)
                c.bass != c.root -> qualityName(c.quality) + " · " + stringResource(R.string.chord_bass, NOTE_NAMES[c.bass])
                else -> qualityName(c.quality)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (c != null && c.quality != null) {
            Text(
                c.notes.joinToString(" · ") { NOTE_NAMES[it] },
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        MiniKeyboard(
            pitchClasses = c?.notes?.toSet().orEmpty(),
            root = c?.takeIf { it.quality != null }?.root,
            dim = !sounding,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(top = 6.dp),
        )
    }
}

/** Dve oktávy klaviatúry s vyznačenými tónmi akordu (základný tón výraznejšie). */
@Composable
private fun MiniKeyboard(pitchClasses: Set<Int>, root: Int?, dim: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val on = if (dim) scheme.primary.copy(alpha = 0.45f) else scheme.primary
    val onSoft = if (dim) scheme.tertiary.copy(alpha = 0.45f) else scheme.tertiary
    val white = scheme.surface
    val black = scheme.onSurface
    val line = scheme.outlineVariant
    Canvas(modifier) {
        val whites = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val blacks = intArrayOf(1, 3, -1, 6, 8, 10, -1)
        val count = 14
        val w = size.width / count
        val h = size.height
        for (i in 0 until count) {
            val pc = whites[i % 7]
            val x = i * w
            val fill = when {
                pc == root -> on
                pc in pitchClasses -> onSoft
                else -> white
            }
            drawRoundRect(fill, Offset(x + 1f, 0f), androidx.compose.ui.geometry.Size(w - 2f, h), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            drawRoundRect(
                line,
                Offset(x + 1f, 0f),
                androidx.compose.ui.geometry.Size(w - 2f, h),
                androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f),
            )
        }
        for (i in 0 until count) {
            val pc = blacks[i % 7]
            if (pc < 0 || i == count - 1) continue
            val bw = w * 0.6f
            val x = (i + 1) * w - bw / 2
            val fill = when {
                pc == root -> on
                pc in pitchClasses -> onSoft
                else -> black
            }
            drawRoundRect(fill, Offset(x, 0f), androidx.compose.ui.geometry.Size(bw, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        }
    }
}
