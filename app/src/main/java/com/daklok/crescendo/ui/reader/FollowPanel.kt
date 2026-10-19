package com.daklok.crescendo.ui.reader

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.follow.AudioChroma
import com.daklok.crescendo.data.follow.FollowFrame
import com.daklok.crescendo.data.follow.FollowReference
import com.daklok.crescendo.data.follow.FollowStore
import com.daklok.crescendo.data.follow.ScoreFollower
import com.daklok.crescendo.data.follow.buildReference
import com.daklok.crescendo.data.follow.midiChroma
import com.daklok.crescendo.data.music.HeldNotes
import com.daklok.crescendo.ui.common.ConnectedChoice
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.ExpressiveSwitch
import com.daklok.crescendo.ui.common.WavyLinearProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.floor
import kotlin.math.roundToInt

/** Snímky za sekundu (posun 2048 vzoriek pri 44,1 kHz). */
private const val HOP = 2048
private const val FPS = MIC_RATE.toFloat() / HOP

/**
 * Otáčanie podľa zvuku: nácvik (zapamätá si skladbu aj miesta otočenia) a sledovanie hry (otáča samo).
 * Vstup je mikrofón alebo digitálny klavír (MIDI). Stav číta UI; poloha v notách sa sem posiela z čítačky.
 */
@Stable
class FollowController(private val app: CrescendoApp, private val scope: CoroutineScope) {
    enum class Mode { IDLE, LEARNING, FOLLOWING }

    private val store = FollowStore(app)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var pieceId = -1L

    var mode by mutableStateOf(Mode.IDLE)
        private set

    /** Nacvičená skladba (alebo null). */
    var reference by mutableStateOf<FollowReference?>(null)
        private set

    /** Hlasitosť vstupu 0..1 (na „dýchajúcu“ ikonku). */
    var level by mutableFloatStateOf(0f)
        private set

    /** Dĺžka nácviku v sekundách. */
    var learnSec by mutableIntStateOf(0)
        private set

    /** Kde v skladbe sme pri sledovaní (0..1) a ako dobre hra sedí. */
    var progress by mutableFloatStateOf(0f)
        private set
    var quality by mutableFloatStateOf(0f)
        private set

    /** Posledná chyba (mikrofón obsadený, krátky nácvik…), na zobrazenie v paneli. */
    var error by mutableStateOf<Int?>(null)

    /** O koľko skôr otočiť (ms). */
    var leadMs by mutableIntStateOf(app.getSharedPreferences(PREFS, 0).getInt("lead_ms", 600))
        private set

    /** Aktuálna poloha v notách (prvá zobrazená strana, pri posúvaní aj časť strany) a posledná zobrazená strana. */
    @Volatile
    var position = 0f
    @Volatile
    var lastVisible = 0

    /** Čas a cieľ posledného otočenia (aby ho čítačka nepovažovala za ručné). */
    @Volatile
    var lastTurnAt = 0L
        private set

    @Volatile
    private var pendingReset: Float? = null

    fun load(id: Long) {
        if (id == pieceId) return
        stop()
        pieceId = id
        reference = null
        scope.launch { reference = withContext(Dispatchers.IO) { store.load(id) } }
    }

    fun setLead(ms: Int) {
        leadMs = ms.coerceIn(0, 3000)
        app.getSharedPreferences(PREFS, 0).edit().putInt("lead_ms", leadMs).apply()
    }

    /** Nácvik: zapamätá si zvuk a polohu v notách v každej snímke, kým sa nezavolá [finishLearning]. */
    fun startLearning(midi: Boolean, lastPage: Int, onAutoFinish: () -> Unit) {
        stop()
        error = null
        mode = Mode.LEARNING
        learnSec = 0
        val frames = ArrayList<FollowFrame>()
        val positions = ArrayList<Float>()
        learning = frames to positions
        learningMidi = midi
        job = scope.launch(Dispatchers.Default) {
            var silentRun = 0
            val ok = input(midi) { f ->
                synchronized(frames) {
                    frames.add(f)
                    positions.add(position)
                }
                level = levelOf(f)
                learnSec = (frames.size / FPS).toInt()
                // na poslednej strane a dlhšie ticho = skladba sa dohrala
                silentRun = if (f.silent) silentRun + 1 else 0
                if (lastVisible >= lastPage && silentRun > FPS * 6 && frames.size > FPS * 20) {
                    main.post { onAutoFinish() }
                }
            }
            if (!ok && mode == Mode.LEARNING) {
                error = R.string.follow_err_input
                mode = Mode.IDLE
            }
        }
    }

    private var learning: Pair<ArrayList<FollowFrame>, ArrayList<Float>>? = null
    private var learningMidi = false

    /** Ukončí nácvik a uloží ho. Vráti false, ak bol príliš krátky alebo sa neotočila ani jedna strana. */
    fun finishLearning(onResult: (Boolean) -> Unit) {
        val data = learning ?: return
        learning = null
        job?.cancel()
        job = null
        mode = Mode.IDLE
        level = 0f
        val id = pieceId
        val midi = learningMidi
        scope.launch {
            val ref = withContext(Dispatchers.Default) {
                val (frames, positions) = data
                synchronized(frames) { buildReference(ArrayList(frames), ArrayList(positions), FPS, if (midi) 1 else 0) }
            }
            if (ref == null || ref.turns == 0) {
                error = if (ref == null) R.string.follow_err_short else R.string.follow_err_no_turn
                onResult(false)
                return@launch
            }
            withContext(Dispatchers.IO) { runCatching { store.save(id, ref) } }
            reference = ref
            onResult(true)
        }
    }

    /** Sledovanie hry od strany [startPage]; [onTurn] dostane stranu, na ktorú treba otočiť. */
    fun startFollowing(midi: Boolean, startPage: Float, onTurn: (Int) -> Unit) {
        val ref = reference ?: return
        stop()
        error = null
        val follower = ScoreFollower(ref)
        if (!follower.usable) {
            error = R.string.follow_err_short
            return
        }
        follower.reset(startPage)
        mode = Mode.FOLLOWING
        progress = follower.progress
        job = scope.launch(Dispatchers.Default) {
            val ok = input(midi) { f ->
                level = levelOf(f)
                pendingReset?.let {
                    pendingReset = null
                    follower.reset(it)
                }
                if (f.silent) return@input
                follower.push(f.chroma)
                progress = follower.progress
                quality = follower.quality
                val lead = (leadMs / 1000f * ref.fps).roundToInt()
                val target = floor(follower.positionAhead(lead)).toInt()
                val now = System.currentTimeMillis()
                if (target > lastVisible && follower.quality > MIN_QUALITY && now - lastTurnAt > 1500) {
                    lastTurnAt = now
                    main.post { onTurn(target) }
                }
            }
            if (!ok && mode == Mode.FOLLOWING) {
                error = R.string.follow_err_input
                mode = Mode.IDLE
            }
        }
    }

    /** Ručné otočenie počas sledovania: pokračuje sa od tejto strany. */
    fun reanchor(page: Float) {
        if (mode == Mode.FOLLOWING) pendingReset = page
    }

    fun stop() {
        job?.cancel()
        job = null
        learning = null
        mode = Mode.IDLE
        level = 0f
    }

    fun forget() {
        stop()
        val id = pieceId
        reference = null
        scope.launch(Dispatchers.IO) { store.delete(id) }
    }

    private fun levelOf(f: FollowFrame) = ((f.levelDb + 60f) / 45f).coerceIn(0f, 1f)

    /**
     * Číta vstup (mikrofón alebo klavír) a volá [onFrame] asi 21-krát za sekundu, kým sa nezruší.
     * Vráti false, ak sa vstup nepodarilo otvoriť (mikrofón obsadený, klavír nepripojený).
     */
    private suspend fun input(midi: Boolean, onFrame: (FollowFrame) -> Unit): Boolean {
        if (midi) {
            val m = app.midi
            if (!m.supported) return false
            val held = HeldNotes()
            val struck = HashSet<Int>()
            val listener: (ByteArray, Int, Int, Long) -> Unit = { msg, offset, count, _ ->
                // údery od poslednej snímky (aj krátke staccato, ktoré sa medzitým pustí)
                var i = offset
                while (i + 2 < offset + count) {
                    val st = msg[i].toInt() and 0xF0
                    if (st == 0x90 && (msg[i + 2].toInt() and 0x7F) > 0) synchronized(struck) { struck.add(msg[i + 1].toInt() and 0x7F) }
                    i++
                }
                held.onBytes(msg, offset, count)
            }
            m.start()
            m.addListener(listener)
            try {
                val periodNs = (1e9 / FPS).toLong()
                var next = System.nanoTime() + periodNs
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val wait = (next - System.nanoTime()) / 1_000_000
                    if (wait > 0) delay(wait)
                    next += periodNs
                    val notes = HashSet(held.notes())
                    synchronized(struck) {
                        notes.addAll(struck)
                        struck.clear()
                    }
                    onFrame(midiChroma(notes))
                }
            } finally {
                m.removeListener(listener)
                m.stop()
            }
            return true
        }
        val chroma = AudioChroma(MIC_RATE)
        var got = false
        listenMic(frame = 4096, hop = HOP) { buf ->
            got = true
            onFrame(chroma.frame(buf))
        }
        // mikrofón sa neotvoril (alebo bol hneď obsadený)
        return got || !kotlinx.coroutines.currentCoroutineContext().isActive
    }

    companion object {
        const val PREFS = "follow"
        private const val MIN_QUALITY = 0.42f
    }
}

/**
 * Panel „Otáčanie podľa zvuku“: vysvetlenie a nácvik, potom zapnutie sledovania, citlivosť (o koľko skôr otočiť)
 * a zdroj zvuku (mikrofón / klavír).
 */
@Composable
fun FollowPanel(
    follow: FollowController,
    midiAvailable: Boolean,
    useMidi: Boolean,
    onUseMidi: (Boolean) -> Unit,
    onLearn: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ref = follow.reference
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    ToolPanel(
        title = stringResource(R.string.follow_title),
        subtitle = stringResource(R.string.follow_beta),
        icon = Icons.Outlined.Hearing,
        shape = ExpressiveShapes.Cookie12,
        container = scheme.primary,
        content = scheme.onPrimary,
        onClose = onClose,
        modifier = modifier.widthIn(max = 400.dp),
    ) {
        Column(
            Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            follow.error?.let { e ->
                Surface(shape = RoundedCornerShape(16.dp), color = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
                    Text(stringResource(e), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
            if (ref == null) {
                // ešte nenacvičené: tri kroky
                listOf(R.string.follow_step1, R.string.follow_step2, R.string.follow_step3).forEachIndexed { i, res ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(scheme.primaryContainer, if (i == 1) ExpressiveShapes.Clover else ExpressiveShapes.Cookie),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            } else {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ref.learnedAt))
                Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHighest) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ref.source == 1) Icons.Outlined.Piano else Icons.Outlined.Mic,
                            null,
                            Modifier.size(20.dp),
                            tint = scheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.follow_learned_info, date, formatClock(ref.durationSec * 1000L), ref.turns),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val on = follow.mode == FollowController.Mode.FOLLOWING
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
                        .clickable { onToggleFollow(!on) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.follow_switch), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                !on -> stringResource(R.string.follow_switch_desc)
                                follow.quality < 0.42f -> stringResource(R.string.follow_lost)
                                else -> stringResource(R.string.follow_listening)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ExpressiveSwitch(checked = on, onCheckedChange = null)
                }
                if (on) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelDot(follow.level)
                        Spacer(Modifier.width(12.dp))
                        WavyLinearProgress(
                            progress = follow.progress,
                            modifier = Modifier.weight(1f),
                            moving = follow.level > 0.05f,
                        )
                    }
                }
                // citlivosť: o koľko skôr otočiť
                Column {
                    Text(
                        stringResource(R.string.follow_lead, String.format(java.util.Locale.getDefault(), "%.1f", follow.leadMs / 1000f)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = follow.leadMs.toFloat(),
                        onValueChange = { follow.setLead(((it / 100f).roundToInt() * 100)) },
                        valueRange = 0f..3000f,
                    )
                }
            }
            if (midiAvailable) {
                ConnectedChoice(
                    options = listOf(false, true),
                    selected = useMidi,
                    onSelect = onUseMidi,
                ) { midi, _ ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (midi) Icons.Outlined.Piano else Icons.Outlined.Mic, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (midi) R.string.rec_source_piano else R.string.rec_source_mic),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (ref == null) {
            Button(onClick = onLearn, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                Icon(Icons.Outlined.Hearing, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.follow_learn), style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLearn) {
                    Icon(Icons.Outlined.Replay, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.follow_relearn))
                }
                TextButton(onClick = { follow.forget() }) {
                    Text(stringResource(R.string.follow_forget), color = scheme.error)
                }
            }
        }
    }
}

/** Bodka, ktorá sa „nadýchne“ podľa hlasitosti vstupu. */
@Composable
private fun LevelDot(level: Float) {
    val s by animateFloatAsState(0.7f + level * 0.6f, tween(90), label = "level")
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(22.dp)
                .scale(s)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + level * 0.6f), ExpressiveShapes.Cookie),
        )
        Icon(Icons.Outlined.Hearing, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/**
 * Malý ukazovateľ nad notami počas nácviku alebo sledovania: ikonka podľa hlasitosti, čo sa deje,
 * pri nácviku tlačidlo Hotovo; krížik zastaví.
 */
@Composable
fun FollowStatusPill(
    follow: FollowController,
    page: Int,
    pageCount: Int,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val learning = follow.mode == FollowController.Mode.LEARNING
    Surface(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (learning) scheme.tertiaryContainer else scheme.primaryContainer,
        contentColor = if (learning) scheme.onTertiaryContainer else scheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelDot(follow.level)
            Spacer(Modifier.width(8.dp))
            Text(
                if (learning) stringResource(R.string.follow_learning_pill, formatClock(follow.learnSec * 1000L), page + 1, pageCount)
                else if (follow.quality < 0.42f) stringResource(R.string.follow_lost_short)
                else stringResource(R.string.follow_listening_short),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            if (learning) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = onDone,
                    shape = RoundedCornerShape(50),
                    color = scheme.tertiary,
                    contentColor = scheme.onTertiary,
                ) {
                    Row(Modifier.height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.follow_done), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            androidx.compose.material3.IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.follow_stop), Modifier.size(18.dp))
            }
        }
    }
}
