package com.daklok.crescendo.ui.reader

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.pdf.PdfDocument
import com.daklok.crescendo.ui.common.PlayCookie
import com.daklok.crescendo.ui.common.rememberHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Medzera medzi stranami pri posúvaní. */
val PageGap = 12.dp

/**
 * Strany pod sebou na šírku (plynulé posúvanie nahor). [ratios] = pomer šírka/výška každej strany
 * (s orezaním okrajov), kým nie sú známe, ukáže sa krúžok. [widthFraction] = šírka strán (štipnutím).
 */
@Composable
fun ContinuousPages(
    document: PdfDocument,
    ratios: FloatArray?,
    listState: LazyListState,
    annotations: AnnotationController,
    fingerDrawing: Boolean,
    cropMargins: Boolean,
    night: Boolean,
    widthFraction: Float,
    userScrollEnabled: Boolean,
    overlay: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ratios == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PageGap),
        contentPadding = PaddingValues(vertical = PageGap),
    ) {
        items(count = document.pageCount, key = { it }) { i ->
            Box(
                Modifier
                    .fillMaxWidth(widthFraction.coerceIn(0.4f, 1f))
                    .aspectRatio(ratios.getOrElse(i) { 0.7071f }.coerceIn(0.2f, 5f)),
            ) {
                PageView(
                    document, i, annotations, fingerDrawing,
                    cropMargins = cropMargins, night = night, renderScale = 1f, overlay = overlay,
                )
            }
        }
    }
}

/** Pomery strán pre posúvanie: najprv rýchlo bez orezania, potom (ak treba) s orezaním okrajov od aktuálnej strany. */
suspend fun loadPageRatios(document: PdfDocument, crop: Boolean, around: Int, emit: (FloatArray) -> Unit) {
    val n = document.pageCount
    val base = FloatArray(n)
    for (i in 0 until n) base[i] = runCatching { document.aspectRatio(i) }.getOrDefault(0.7071f)
    emit(base.copyOf())
    if (!crop) return
    val out = base.copyOf()
    val order = (0 until n).sortedBy { kotlin.math.abs(it - around) }
    var changed = 0
    for (i in order) {
        val c = runCatching { document.contentBounds(i) }.getOrNull() ?: continue
        val cw = (c[2] - c[0]).coerceAtLeast(0.05f)
        val ch = (c[3] - c[1]).coerceAtLeast(0.05f)
        out[i] = cw / ch * base[i]
        changed++
        // po malých dávkach, aby sa rozloženie neprekresľovalo pri každej strane
        if (changed <= 3 || changed % 8 == 0) emit(out.copyOf())
    }
    emit(out.copyOf())
}

/** Aktuálna poloha v notách pri posúvaní: strana v hornej tretine obrazovky a ako ďaleko v nej sme. */
fun LazyListState.pagePosition(): Float {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty()) return firstVisibleItemIndex.toFloat()
    val line = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 3
    val item = items.lastOrNull { it.offset <= line } ?: items.first()
    val frac = ((line - item.offset).toFloat() / item.size.coerceAtLeast(1)).coerceIn(0f, 0.999f)
    return item.index + frac
}

/** Štipnutie pri posúvaní mení šírku strán (namiesto priblíženia). */
fun Modifier.pinchToWidth(width: () -> Float, onChange: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val z = event.calculateZoom()
                    if (z != 1f) onChange((width() * z).coerceIn(0.4f, 1f))
                    event.changes.forEach { it.consume() }
                    pinching = true
                }
            } while (event.changes.any { it.pressed })
            if (pinching) onEnd()
        }
    }

/**
 * Automatické posúvanie: celá skladba prejde za [durationSec] pri tempe [baseBpm]; keď sa zmení tempo
 * metronómu, posúvanie sa prispôsobí (polovičné tempo = polovičná rýchlosť).
 */
@Stable
class AutoScroll {
    /** Ovládanie je otvorené. */
    var shown by mutableStateOf(false)
    var running by mutableStateOf(false)

    /** Odpočítavanie pred štartom (3, 2, 1), 0 = nie. */
    var countdown by mutableIntStateOf(0)
    var durationSec by mutableIntStateOf(0)
    var baseBpm by mutableIntStateOf(100)

    /** Dĺžka pri tempe [bpm]. */
    fun effectiveSec(bpm: Int): Int =
        if (durationSec <= 0) 0 else (durationSec.toLong() * baseBpm / bpm.coerceAtLeast(1)).toInt().coerceAtLeast(10)
}

/** Dĺžka posúvania pre skladbu (v SharedPreferences „metronome“, je v zálohe aj synchronizácii). */
object AutoScrollPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("metronome", Context.MODE_PRIVATE)

    fun save(c: Context, pieceId: Long, durationSec: Int, bpm: Int) {
        prefs(c).edit().putString("scroll_$pieceId", "$durationSec,$bpm").apply()
    }

    /**
     * Uložená dĺžka, inak dĺžka nahrávky skladby (referenčnej), MIDI, alebo asi minúta na stranu.
     */
    suspend fun load(c: Context, pieceId: Long, pageCount: Int, bpm: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        prefs(c).getString("scroll_$pieceId", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 && it[0] > 0 }
            ?.let { return@withContext it[0] to it[1].coerceAtLeast(1) }
        val app = c.applicationContext as com.daklok.crescendo.CrescendoApp
        val audio = runCatching { app.pieceAudio.reference(pieceId)?.let { durationOf(it) } }.getOrNull()
        val midi = if (audio == null) {
            runCatching {
                app.pieceMidi.sources(pieceId).firstOrNull { it.kind != com.daklok.crescendo.data.MidiSourceKind.RECORDING }
                    ?.let { s -> com.daklok.crescendo.data.midi.MidiFile.read(s.file).lastOrNull()?.time?.roundToInt() }
            }.getOrNull()
        } else null
        val sec = (audio ?: midi)?.takeIf { it in 10..7200 } ?: (pageCount.coerceAtLeast(1) * 60)
        sec to bpm
    }

    private fun durationOf(f: File): Int? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it / 1000).toInt() }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

/**
 * Beží automatické posúvanie: rovnomerne podľa [pxPerSec] (číta sa priebežne). Keď používateľ posunie noty
 * prstom, posúvanie počká a potom pokračuje; na konci sa zastaví.
 */
@Composable
fun AutoScrollEffect(auto: AutoScroll, listState: LazyListState, pxPerSec: () -> Float) {
    val speed by rememberUpdatedState(pxPerSec)
    LaunchedEffect(auto.running) {
        if (!auto.running) return@LaunchedEffect
        try {
            for (i in 3 downTo 1) {
                auto.countdown = i
                delay(800)
            }
        } finally {
            // aj pri zrušení počas odpočítavania
            auto.countdown = 0
        }
        while (isActive && auto.running) {
            if (!listState.canScrollForward) {
                auto.running = false
                break
            }
            val job = launch {
                listState.scroll(MutatePriority.Default) {
                    var last = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                        last = now
                        scrollBy(speed() * dt)
                        if (!listState.canScrollForward) break
                    }
                }
            }
            job.join()
            if (!listState.canScrollForward) {
                auto.running = false
                break
            }
            // posunutie prstom prerušilo posúvanie: počkáme, kým dobehne, a pokračujeme
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            delay(350)
        }
    }
}

/** Ovládanie automatického posúvania: spustiť / pauza, pomalšie / rýchlejšie (dĺžka skladby) a zavrieť. */
@Composable
fun AutoScrollBar(
    auto: AutoScroll,
    bpm: Int,
    onToggle: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val scheme = MaterialTheme.colorScheme
    FloatingBar(modifier) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayCookie(
                size = 48.dp,
                playing = auto.running,
                contentDescription = stringResource(if (auto.running) R.string.pause else R.string.autoscroll_start),
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onToggle()
                },
            )
            Spacer(Modifier.width(8.dp))
            ToolButton(icon = Icons.Outlined.Remove, description = stringResource(R.string.autoscroll_slower), onClick = onSlower)
            Column(Modifier.widthIn(min = 76.dp).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatClock(auto.effectiveSec(bpm) * 1000L),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.autoscroll_at_bpm, bpm),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            ToolButton(icon = Icons.Outlined.Add, description = stringResource(R.string.autoscroll_faster), onClick = onFaster)
            Spacer(Modifier.width(4.dp))
            PanelCloseButton(onClose)
        }
    }
}

/** Veľké odpočítavanie pred štartom posúvania. */
@Composable
fun AutoScrollCountdown(value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(112.dp),
        shape = com.daklok.crescendo.ui.common.ExpressiveShapes.Cookie12,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(value.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        }
    }
}
