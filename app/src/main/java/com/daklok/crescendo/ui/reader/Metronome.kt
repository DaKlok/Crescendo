package com.daklok.crescendo.ui.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
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
 */
class MetronomeEngine {
    private val rate = 44_100
    private var job: Job? = null

    var playing by mutableStateOf(false)
        private set

    /** Práve znejúca doba (0 = prvá), −1 keď nehrá. */
    var beat by mutableIntStateOf(-1)
        private set

    var bpm by mutableIntStateOf(100)
    var beats by mutableIntStateOf(4)
    var sound by mutableStateOf(true)

    private val accent = click(1760.0, 0.95)
    private val normal = click(1175.0, 0.7)

    fun toggle(scope: CoroutineScope) = if (playing) stop() else start(scope)

    fun start(scope: CoroutineScope) {
        if (playing) return
        playing = true
        job = scope.launch(Dispatchers.Default) { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        beat = -1
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
            var n = 0L
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val perBar = beats.coerceAtLeast(1)
                val b = (n % perBar).toInt()
                beat = b
                var remaining = (rate * 60.0 / bpm.coerceIn(MIN_BPM, MAX_BPM)).roundToInt()
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
                n++
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
            beat = -1
        }
    }

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

/** Tempo a takt sa pamätajú pre každú skladbu zvlášť (a zálohujú sa). */
object MetronomePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("metronome", Context.MODE_PRIVATE)
    fun load(c: Context, pieceId: Long, engine: MetronomeEngine) {
        val p = prefs(c)
        engine.bpm = p.getInt("bpm_$pieceId", p.getInt("bpm_last", 100))
        engine.beats = p.getInt("beats_$pieceId", 4)
        engine.sound = p.getBoolean("sound", true)
    }
    fun save(c: Context, pieceId: Long, engine: MetronomeEngine) {
        prefs(c).edit()
            .putInt("bpm_$pieceId", engine.bpm)
            .putInt("bpm_last", engine.bpm)
            .putInt("beats_$pieceId", engine.beats)
            .putBoolean("sound", engine.sound)
            .apply()
    }
}

/** Plávajúci panel metronómu v čítačke. */
@Composable
fun MetronomePanel(
    engine: MetronomeEngine,
    scope: CoroutineScope,
    onChanged: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val taps = remember { ArrayList<Long>() }
    fun setBpm(v: Int) {
        engine.bpm = v.coerceIn(MIN_BPM, MAX_BPM)
        onChanged()
    }

    Surface(
        modifier = modifier.widthIn(max = 340.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.metronome),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    engine.sound = !engine.sound
                    onChanged()
                }) {
                    Icon(
                        if (engine.sound) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                        contentDescription = stringResource(R.string.metronome_sound),
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }

            // tempo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { setBpm(engine.bpm - 1) }) { Icon(Icons.Outlined.Remove, null) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
                    Text(
                        "${engine.bpm}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "BPM · ${tempoName(engine.bpm)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { setBpm(engine.bpm + 1) }) { Icon(Icons.Outlined.Add, null) }
            }
            Slider(
                value = engine.bpm.toFloat(),
                onValueChange = { setBpm(it.roundToInt()) },
                valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            )

            // takt
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 2, 3, 4, 6).forEach { n ->
                    FilterChip(
                        selected = engine.beats == n,
                        onClick = {
                            engine.beats = n
                            onChanged()
                        },
                        label = { Text(if (n == 1) "–" else "$n/4") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // doby
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                repeat(engine.beats.coerceAtLeast(1)) { i -> BeatDot(active = engine.beat == i, accent = i == 0 && engine.beats > 1) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
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
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.metronome_tap))
                }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        engine.toggle(scope)
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (engine.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (engine.playing) R.string.metronome_stop else R.string.metronome_start),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BeatDot(active: Boolean, accent: Boolean) {
    val scale by animateFloatAsState(if (active) 1.25f else 1f, Motion.bouncy(), label = "beat")
    val color by animateColorAsState(
        when {
            active && accent -> MaterialTheme.colorScheme.tertiary
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "beatColor",
    )
    Box(
        Modifier
            .size(if (accent) 18.dp else 14.dp)
            .scale(scale)
            .background(color, CircleShape),
    )
}
