package com.daklok.crescendo.ui.reader

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.ShapeIcon
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Obálka hlasitosti nahrávky (na kreslenie a zarovnanie): [values] 0..1, celková dĺžka a kde v nej
 * začína a končí zvuk (bez ticha na začiatku a na konci).
 */
class Envelope(val values: FloatArray, val durationMs: Long, val soundStartMs: Long, val soundEndMs: Long) {
    val soundMs: Long get() = (soundEndMs - soundStartMs).coerceAtLeast(1)
}

private val envelopeCache = HashMap<String, Envelope>()

/** Prečíta nahrávku a spočíta obálku ([bins] hodnôt). Beží na pozadí, výsledok sa pamätá. */
suspend fun loadEnvelope(file: File, bins: Int = 320): Envelope? = withContext(Dispatchers.Default) {
    val key = file.path + ":" + file.lastModified() + ":" + bins
    synchronized(envelopeCache) { envelopeCache[key] }?.let { return@withContext it }
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return@withContext null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durUs <= 0) return@withContext null
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
        val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec = c
        c.configure(format, null, null, 0)
        c.start()
        val sum = DoubleArray(bins)
        val cnt = IntArray(bins)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var guard = 0
        while (guard++ < 400_000 && isActive) {
            if (!inputDone) {
                val i = c.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = c.getInputBuffer(i)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = c.dequeueOutputBuffer(info, 10_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                rate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else if (o >= 0) {
                val buf = c.getOutputBuffer(o)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val sb = buf.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                val frames = sb.remaining() / channels
                val t0 = info.presentationTimeUs
                for (f in 0 until frames step 4) {
                    val us = t0 + f * 1_000_000L / rate
                    val bin = (us * bins / durUs).toInt().coerceIn(0, bins - 1)
                    val v = sb.get(f * channels) / 32768.0
                    sum[bin] += v * v
                    cnt[bin]++
                }
                c.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        val db = FloatArray(bins) { i ->
            val rms = if (cnt[i] > 0) sqrt(sum[i] / cnt[i]) else 0.0
            (20 * log10(rms + 1e-7)).toFloat()
        }
        val max = db.maxOrNull() ?: -100f
        val values = FloatArray(bins) { i -> ((db[i] - (max - 48f)) / 48f).coerceIn(0f, 1f) }
        // zvuk = aspoň 30 dB nad tichom
        val threshold = max - 30f
        val first = db.indexOfFirst { it > threshold }.coerceAtLeast(0)
        val last = db.indexOfLast { it > threshold }.let { if (it < 0) bins - 1 else it }
        val durMs = durUs / 1000
        val env = Envelope(values, durMs, first * durMs / bins, (last + 1) * durMs / bins)
        synchronized(envelopeCache) { envelopeCache[key] = env }
        env
    } catch (e: Exception) {
        null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
    }
}

/**
 * Dva prehrávače pre porovnanie: hrá vždy jedna nahrávka; pri prepnutí sa druhá pustí z toho istého miesta
 * v skladbe (zarovnané podľa začiatku a dĺžky zvuku), alebo hrajú za sebou.
 */
@Stable
class ComparePlayer(private val scope: CoroutineScope) {
    private val players = arrayOfNulls<MediaPlayer>(2)
    private var ticker: Job? = null

    /** 0 = A, 1 = B. */
    var active by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var durationA by mutableLongStateOf(0L)
        private set
    var durationB by mutableLongStateOf(0L)
        private set

    /** Za sebou: po dohraní A začne B. */
    var sequential by mutableStateOf(true)

    /** Obálky (kvôli zarovnaniu); kým nie sú, zarovnáva sa len podľa dĺžky. */
    var envelopes: Array<Envelope?> = arrayOfNulls(2)

    fun duration(i: Int) = if (i == 0) durationA else durationB

    fun load(a: File, b: File) {
        release()
        listOf(a, b).forEachIndexed { i, f ->
            players[i] = runCatching {
                MediaPlayer().apply {
                    setDataSource(f.path)
                    prepare()
                    setOnCompletionListener { onCompleted(i) }
                }
            }.getOrNull()
        }
        durationA = players[0]?.duration?.toLong() ?: 0L
        durationB = players[1]?.duration?.toLong() ?: 0L
        active = 0
        position = start(0)
    }

    /** Kde v nahrávke začína zvuk (ms). */
    fun start(i: Int): Long = envelopes[i]?.soundStartMs ?: 0L

    /** To isté miesto v skladbe v druhej nahrávke (podľa začiatku a dĺžky zvuku). */
    fun map(from: Int, ms: Long): Long {
        val to = 1 - from
        val ea = envelopes[from]
        val eb = envelopes[to]
        return if (ea != null && eb != null) {
            val f = ((ms - ea.soundStartMs).toDouble() / ea.soundMs).coerceIn(0.0, 1.0)
            (eb.soundStartMs + f * eb.soundMs).toLong()
        } else {
            val da = duration(from).coerceAtLeast(1)
            (ms.toDouble() / da * duration(to)).toLong()
        }.coerceIn(0, duration(to))
    }

    private fun onCompleted(i: Int) {
        if (i != active) return
        if (sequential && i == 0 && players[1] != null) {
            active = 1
            val p = players[1] ?: return
            runCatching {
                p.seekTo(start(1).toInt())
                p.start()
            }
            position = start(1)
        } else {
            playing = false
            ticker?.cancel()
            position = duration(i)
        }
    }

    fun toggle() = if (playing) pause() else play()

    fun play() {
        val p = players[active] ?: return
        // na konci začne odznova (pri „za sebou“ od A)
        if (position >= duration(active) - 200) {
            if (sequential) active = 0
            position = start(active)
        }
        val pl = players[active] ?: return
        runCatching {
            pl.seekTo(position.toInt())
            pl.playbackParams = PlaybackParams().setSpeed(1f).setPitch(1f)
            pl.start()
        }
        if (p !== pl) runCatching { p.pause() }
        playing = true
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && playing) {
                val cur = players[active]
                if (cur != null) position = runCatching { cur.currentPosition.toLong() }.getOrDefault(position)
                delay(40)
            }
        }
    }

    fun pause() {
        players.forEach { runCatching { if (it?.isPlaying == true) it.pause() } }
        playing = false
        ticker?.cancel()
    }

    /** Prepne na druhú nahrávku na tom istom mieste v skladbe (hrá ďalej, ak hrala). */
    fun switchTo(i: Int) {
        if (i == active) return
        val target = map(active, position)
        val was = playing
        pause()
        active = i
        position = target
        if (was) play()
    }

    fun seek(i: Int, ms: Long) {
        val was = playing
        if (i != active) pause()
        active = i
        position = ms.coerceIn(0, duration(i))
        runCatching { players[i]?.seekTo(position.toInt()) }
        if (was && !playing) play()
    }

    /** Späť na začiatok zvuku (pri „za sebou“ na začiatok A). */
    fun restart() {
        val was = playing
        pause()
        if (sequential) active = 0
        position = start(active)
        if (was) play()
    }

    fun release() {
        ticker?.cancel()
        players.forEachIndexed { i, p ->
            runCatching { p?.release() }
            players[i] = null
        }
        playing = false
    }
}

/** Nahrávka na výber: súbor na prehranie, názov (dátum) a čas vzniku. */
class CompareTrack(val playable: File, val title: String, val added: Long)

/**
 * Porovnanie dvoch vlastných nahrávok (napr. dnes a pred mesiacom): za sebou, alebo prepínanie A / B
 * na tom istom mieste v skladbe. Obe obálky pod sebou ukazujú, kde sa hrá, a či je novšia rýchlejšia.
 */
@Composable
fun CompareRecordingsSheet(tracks: List<CompareTrack>, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val player = remember { ComparePlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val sorted = remember(tracks) { tracks.sortedBy { it.added } }
    var a by remember { mutableStateOf(sorted.first()) }
    var b by remember { mutableStateOf(sorted.last()) }
    var envA by remember { mutableStateOf<Envelope?>(null) }
    var envB by remember { mutableStateOf<Envelope?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(a, b) {
        loading = true
        envA = null
        envB = null
        player.load(a.playable, b.playable)
        val ea = loadEnvelope(a.playable)
        val eb = loadEnvelope(b.playable)
        envA = ea
        envB = eb
        player.envelopes = arrayOf(ea, eb)
        if (!player.playing) player.seek(0, player.start(0))
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaceContainerLow) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(Icons.AutoMirrored.Outlined.CompareArrows, ExpressiveShapes.Squircle, scheme.secondaryContainer, scheme.onSecondaryContainer, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.compare_desc), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            // výber nahrávok A a B
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackPicker("A", a, sorted, scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f)) { a = it }
                TrackPicker("B", b, sorted, scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f)) { b = it }
            }
            // obálky pod sebou so spoločným miestom v skladbe
            val posA = if (player.active == 0) player.position else player.map(1, player.position)
            val posB = if (player.active == 1) player.position else player.map(0, player.position)
            Wave(
                label = "A",
                env = envA,
                durationMs = player.durationA,
                positionMs = posA,
                active = player.active == 0,
                color = scheme.primary,
                onSeek = { ms -> player.seek(0, ms) },
            )
            Wave(
                label = "B",
                env = envB,
                durationMs = player.durationB,
                positionMs = posB,
                active = player.active == 1,
                color = scheme.tertiary,
                onSeek = { ms -> player.seek(1, ms) },
            )
            // ako sa líši tempo (dĺžka zvuku)
            val ea = envA
            val eb = envB
            if (ea != null && eb != null && !loading) {
                val diff = ((ea.soundMs - eb.soundMs) * 100.0 / ea.soundMs).roundToInt()
                Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHigh) {
                    Text(
                        when {
                            abs(diff) < 2 -> stringResource(R.string.compare_same_tempo, formatClock(ea.soundMs), formatClock(eb.soundMs))
                            diff > 0 -> stringResource(R.string.compare_faster, diff, formatClock(ea.soundMs), formatClock(eb.soundMs))
                            else -> stringResource(R.string.compare_slower, -diff, formatClock(ea.soundMs), formatClock(eb.soundMs))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            // ovládanie: od začiatku, prehrať / pauza a čo práve počuješ (A alebo B)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconButton(onClick = { player.restart() }) { Icon(Icons.Outlined.Replay, stringResource(R.string.compare_restart)) }
                Box(contentAlignment = Alignment.Center) {
                    PlayCookie(
                        size = 64.dp,
                        playing = player.playing,
                        enabled = !loading,
                        contentDescription = stringResource(if (player.playing) R.string.pause else R.string.play),
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            player.toggle()
                        },
                    )
                    if (loading) CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 3.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.compare_now_playing), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    // prepínač A / B: druhá nahrávka hrá z toho istého miesta v skladbe
                    ConnectedChoice(
                        options = listOf(0, 1),
                        selected = player.active,
                        onSelect = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            player.switchTo(it)
                        },
                        height = 52.dp,
                    ) { i, _ ->
                        Text(if (i == 0) "A" else "B", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                stringResource(R.string.compare_switch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            // po dohraní A automaticky B
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerHigh)
                    .clickable { player.sequential = !player.sequential }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.compare_sequential), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.compare_sequential_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = player.sequential, onCheckedChange = null)
            }
        }
    }
}

/** Výber nahrávky (A alebo B): dátum a dĺžka, ťuk ukáže ostatné nahrávky. */
@Composable
private fun TrackPicker(
    tag: String,
    selected: CompareTrack,
    all: List<CompareTrack>,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onPick: (CompareTrack) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tag, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    selected.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            all.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.title, fontWeight = if (t === selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onPick(t)
                    },
                )
            }
        }
    }
}

/** Obálka nahrávky: odohraná časť plnou farbou, zvyšok jemne; ťuk / ťahanie = skok. */
@Composable
private fun Wave(
    label: String,
    env: Envelope?,
    durationMs: Long,
    positionMs: Long,
    active: Boolean,
    color: Color,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (active) color else scheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (active) color.copy(alpha = 0.12f) else scheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) }
                    }
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures { change, _ ->
                            if (durationMs > 0) onSeek((change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * durationMs).toLong())
                        }
                    },
            ) {
                val values = env?.values ?: return@Canvas
                val n = values.size
                val slot = size.width / n
                val w = (slot * 0.62f).coerceAtLeast(1f)
                val played = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                val mid = size.height / 2f
                for (i in 0 until n) {
                    val h = (values[i] * (size.height - 8f)).coerceAtLeast(2f)
                    val x = i * slot + (slot - w) / 2f
                    val c = if (i.toFloat() / n <= played) color else color.copy(alpha = 0.3f)
                    drawRoundRect(c, Offset(x, mid - h / 2f), Size(w, h), CornerRadius(w / 2f))
                }
                val px = played.coerceIn(0f, 1f) * size.width
                drawLine(if (active) color else scheme.outline, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
