package com.daklok.crescendo.data.midi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Nástroj, ktorý hrá po blokoch: dostáva MIDI udalosti a pridáva svoj zvuk do stereo bufferov.
 * Viac nástrojov naraz (napr. husle + violončelo) mieša [MidiMixer].
 */
interface BlockSynth {
    fun event(e: NoteEvent)

    /** Pripočíta [frames] snímok do [l] / [r] (hodnoty okolo ±1). */
    fun mix(l: FloatArray, r: FloatArray, frames: Int)

    /** Ešte znie (doznievajúce tóny, ozvena)? */
    val busy: Boolean
}

/** Nahratý tón nástroja (mono, 16 bit) pre kláves [note] (MIDI číslo). */
class PianoSample(val note: Int, val pcm: ShortArray)

/**
 * Ako sa nástroj správa:
 * [sustained] – tón znie, kým je kláves stlačený (sláčikové, dychové – vzorka sa slučkuje),
 * inak doznieva sám (klavír, gitara, harfa);
 * [release] – doznenie po pustení (s); [noDamperFrom] – od tejto noty sa po pustení netlmí (klavír);
 * [pedal] – reaguje na pedále; [dynamicsDb] – rozdiel hlasitosti medzi pp a ff; [gain] – hlasitosť.
 */
data class SamplerSpec(
    val sustained: Boolean = false,
    val release: Double = 0.3,
    val releaseHigh: Double = release,
    val noDamperFrom: Int = 128,
    val pedal: Boolean = false,
    val dynamicsDb: Double = 22.0,
    val brightMin: Double = 1600.0,
    val panWidth: Double = 0.25,
    val gain: Float = 1f,
)

/** Sampler: každá nota použije najbližší nahratý tón (preladený), sila úderu mení hlasitosť aj jas. */
class Sampler(
    samples: List<PianoSample>,
    private val spec: SamplerSpec,
    private val sampleRate: Int = 44_100,
) : BlockSynth {

    private val nearest = arrayOfNulls<PianoSample>(128)
    private val loopStart = HashMap<PianoSample, Int>()
    private val loopEnd = HashMap<PianoSample, Int>()
    private val norm: Float

    init {
        require(samples.isNotEmpty())
        for (n in 0 until 128) nearest[n] = samples.minBy { abs(it.note - n) }
        // slučka pre držané tóny: po nábehu až kúsok pred koniec vzorky
        for (s in samples) {
            val len = s.pcm.size
            loopStart[s] = (0.45 * sampleRate).toInt().coerceAtMost(len / 3)
            loopEnd[s] = (len - (0.25 * sampleRate).toInt()).coerceAtLeast(len * 2 / 3)
        }
        // vyrovnanie hlasitosti medzi nástrojmi (podľa priemernej sily vzoriek)
        var sum = 0.0
        var count = 0L
        for (s in samples) {
            val end = minOf(s.pcm.size, sampleRate) // prvá sekunda
            for (i in 0 until end step 4) {
                val v = s.pcm[i] / 32768.0
                sum += v * v
                count++
            }
        }
        val rms = if (count > 0) sqrt(sum / count) else 0.1
        norm = (REFERENCE_RMS / rms.coerceAtLeast(1e-4)).toFloat()
    }

    private inner class Voice(val note: Int, velocity: Int, soft: Boolean) {
        val sample = nearest[note]!!
        val pcm = sample.pcm
        val rate = 2.0.pow((note - sample.note) / 12.0)
        val ls = loopStart[sample]!!
        val le = loopEnd[sample]!!
        val loopLen = (le - ls).toDouble()
        val fade = minOf(0.12 * sampleRate, loopLen / 3)
        val gainL: Float
        val gainR: Float
        val lpK: Float
        var pos = 0.0
        var env = 1f
        var releaseCoef = 1f
        var lp = 0f
        var keyDown = true
        var done = false

        init {
            val v = velocity.coerceIn(1, 127) / 127.0
            val gain = 10.0.pow(-(spec.dynamicsDb / 20.0) * (1 - v)) * (if (soft) 0.72 else 1.0) / 32768.0 * norm * spec.gain
            val x = ((note - 64) / 44.0).coerceIn(-1.0, 1.0) * spec.panWidth
            val angle = (x + 1) * PI / 4
            gainL = (gain * cos(angle) * 1.4142).toFloat()
            gainR = (gain * sin(angle) * 1.4142).toFloat()
            val fc = (spec.brightMin + 17000 * v * v) * (if (soft) 0.6 else 1.0)
            lpK = (1 - exp(-2 * PI * fc.coerceAtMost(sampleRate * 0.45) / sampleRate)).toFloat()
        }

        fun release(seconds: Double) {
            val c = exp(-1.0 / (seconds * sampleRate)).toFloat()
            if (c < releaseCoef) releaseCoef = c
        }
    }

    private val voices = ArrayList<Voice>()
    private var sustain = false
    private var soft = false
    private val sostenuto = BooleanArray(128)

    override val busy: Boolean get() = voices.isNotEmpty()

    private fun releaseTime(note: Int): Double {
        val t = ((note - 21).coerceIn(0, 87) / 87.0)
        return spec.release + (spec.releaseHigh - spec.release) * t
    }

    private fun releaseIfFree(v: Voice) {
        if (v.keyDown) return
        if (spec.pedal && (sustain || sostenuto[v.note])) return
        if (v.note >= spec.noDamperFrom) return
        v.release(releaseTime(v.note))
    }

    override fun event(e: NoteEvent) {
        when (e.status) {
            0x90 -> {
                // opätovný úder toho istého tónu – starý rýchlo stlmíme
                voices.forEach { if (it.note == e.data1) it.release(0.06) }
                if (voices.size >= MAX_VOICES) {
                    (voices.firstOrNull { it.releaseCoef == 1f && !it.keyDown } ?: voices.first()).release(0.03)
                }
                voices.add(Voice(e.data1, e.data2, soft))
            }
            0x80 -> voices.forEach {
                if (it.note == e.data1 && it.keyDown) {
                    it.keyDown = false
                    releaseIfFree(it)
                }
            }
            0xB0 -> if (spec.pedal) {
                val down = e.data2 >= 64
                when (e.data1) {
                    64 -> {
                        sustain = down
                        if (!down) voices.forEach { releaseIfFree(it) }
                    }
                    66 -> if (down) {
                        voices.forEach { if (it.keyDown) sostenuto[it.note] = true }
                    } else {
                        sostenuto.fill(false)
                        voices.forEach { releaseIfFree(it) }
                    }
                    67 -> soft = down
                }
            }
        }
    }

    override fun mix(l: FloatArray, r: FloatArray, frames: Int) {
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            val pcm = v.pcm
            val last = pcm.size - 1
            val looping = spec.sustained
            var pos = v.pos
            var env = v.env
            var lp = v.lp
            val coef = v.releaseCoef
            val k = v.lpK
            val gl = v.gainL
            val gr = v.gainR
            val holding = looping && coef == 1f
            var i = 0
            while (i < frames) {
                if (holding && pos >= v.le) pos -= v.loopLen
                val idx = pos.toInt()
                if (idx >= last) {
                    v.done = true
                    break
                }
                val frac = (pos - idx).toFloat()
                val a = pcm[idx].toFloat()
                var s = a + (pcm[idx + 1] - a) * frac
                if (holding && pos > v.le - v.fade) {
                    // prelínanie na konci slučky – bez cvaknutia
                    val t = ((pos - (v.le - v.fade)) / v.fade).toFloat()
                    val p2 = pos - v.loopLen
                    val i2 = p2.toInt().coerceAtLeast(0)
                    val b = pcm[i2].toFloat()
                    val s2 = b + (pcm[i2 + 1] - b) * (p2 - i2).toFloat()
                    s = s * (1 - t) + s2 * t
                }
                lp += k * (s - lp)
                val y = lp * env
                l[i] += y * gl
                r[i] += y * gr
                env *= coef
                pos += v.rate
                i++
            }
            v.pos = pos
            v.env = env
            v.lp = lp
            if (v.done || env < 1e-4f) it.remove()
        }
    }

    companion object {
        private const val MAX_VOICES = 72

        /** Cieľová sila (rovnaká pre všetky nástroje, aby jeden nebol oveľa hlasnejší ako druhý). */
        private const val REFERENCE_RMS = 0.2
    }
}

/**
 * Mieša viac nástrojov podľa MIDI kanálov. [router] vyberie nástroj pre (program, nota) –
 * null = nehrá sa (napr. bicie). Výstup sú prekladané 16-bit stereo vzorky do [sink].
 */
object MidiMixer {
    private const val BLOCK = 64
    private const val OUT_FRAMES = 4096
    private const val KNEE = 0.6f

    fun render(
        events: List<NoteEvent>,
        router: (program: Int, note: Int) -> BlockSynth?,
        sink: (ShortArray, Int) -> Unit,
        progress: (Float) -> Unit,
        cancelled: () -> Boolean,
        sampleRate: Int = 44_100,
        tailSeconds: Double = 12.0,
    ) {
        val sorted = events.sortedBy { it.time }
        val program = IntArray(16)
        val used = LinkedHashSet<BlockSynth>()
        val byChannel = Array(16) { LinkedHashSet<BlockSynth>() }
        val noteSynth = HashMap<Int, BlockSynth>() // (kanál * 128 + nota) → nástroj, ktorý ju hrá
        val total = sorted.lastOrNull()?.time ?: 0.0
        val maxEnd = total + tailSeconds
        val l = FloatArray(BLOCK)
        val r = FloatArray(BLOCK)
        val out = ShortArray(OUT_FRAMES * 2)
        var outFrames = 0
        var frame = 0L
        var ei = 0
        var lastProgress = -1f

        while (true) {
            if ((frame / BLOCK) % 512 == 0L && cancelled()) return
            val t = frame.toDouble() / sampleRate
            val blockEnd = (frame + BLOCK).toDouble() / sampleRate
            while (ei < sorted.size && sorted[ei].time < blockEnd) {
                val e = sorted[ei++]
                val ch = e.channel and 15
                when (e.status) {
                    0xC0 -> program[ch] = e.data1
                    0x90 -> {
                        val synth = router(program[ch], e.data1) ?: continue
                        noteSynth[ch * 128 + e.data1] = synth
                        used.add(synth)
                        byChannel[ch].add(synth)
                        synth.event(e)
                    }
                    0x80 -> noteSynth.remove(ch * 128 + e.data1)?.event(e)
                    0xB0 -> byChannel[ch].forEach { it.event(e) }
                }
            }
            val busy = used.any { it.busy }
            if ((ei >= sorted.size && !busy) || t > maxEnd) break

            l.fill(0f)
            r.fill(0f)
            for (s in used) if (s.busy) s.mix(l, r, BLOCK)
            for (i in 0 until BLOCK) {
                out[2 * outFrames] = toPcm(l[i])
                out[2 * outFrames + 1] = toPcm(r[i])
                outFrames++
                if (outFrames == OUT_FRAMES) {
                    sink(out, outFrames)
                    outFrames = 0
                }
            }
            frame += BLOCK
            if (total > 0) {
                val p = (t / total).toFloat().coerceIn(0f, 1f)
                if (p - lastProgress >= 0.01f) {
                    lastProgress = p
                    progress(p)
                }
            }
        }
        if (outFrames > 0) sink(out, outFrames)
        progress(1f)
    }

    /** Jemné obmedzenie špičiek (veľké akordy nepretečú). */
    private fun toPcm(x: Float): Short {
        val a = abs(x)
        val y = if (a <= KNEE) x else (if (x > 0) 1f else -1f) * (KNEE + (1 - KNEE) * tanh((a - KNEE) / (1 - KNEE)))
        return (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
}

/**
 * Ktorý nástroj zahrá daný MIDI program (General MIDI). Vracia názov balíka vzoriek
 * („piano“, „organ“, „violin“…) alebo null (bicie a efekty sa nehrajú).
 * Pri sláčikových súboroch sa podľa výšky tónu vyberie husle / violončelo / kontrabas.
 */
object InstrumentMap {
    fun forProgram(program: Int, note: Int): String? = when (program) {
        in 0..7 -> "piano"
        8, in 9..13 -> "xylophone"
        14 -> "piano"
        15 -> "harp"
        in 16..19 -> "organ"
        in 20..23 -> "harmonium"
        24 -> "guitar-nylon"
        25 -> "guitar-acoustic"
        in 26..31 -> "guitar-electric"
        32, 43 -> "contrabass"
        in 33..39 -> "bass-electric"
        40 -> "violin"
        41 -> if (note < 55) "cello" else "violin"
        42 -> "cello"
        44, 45, in 48..51 -> when {
            note < 40 -> "contrabass"
            note < 57 -> "cello"
            else -> "violin"
        }
        46 -> "harp"
        47 -> "contrabass"
        in 52..54 -> "harmonium" // spev a zbor – harmónium (ako sprievod v kostole)
        55 -> "piano"
        56, 59 -> "trumpet"
        57 -> "trombone"
        58 -> "tuba"
        60, in 61..63 -> "french-horn"
        in 64..67 -> "saxophone"
        68, 69, 71 -> "clarinet"
        70 -> "bassoon"
        in 72..79 -> "flute"
        in 80..103 -> "piano"
        104, 105 -> "guitar-acoustic"
        106 -> "guitar-nylon"
        107 -> "harp"
        108 -> "xylophone"
        109, 111 -> "harmonium"
        110 -> "violin"
        else -> null
    }

    /**
     * Ak MIDI súbor nemá nastavené nástroje (všade klavír), použije sa program podľa katalógu
     * ([hint] = skupina nástrojov: guitar, strings, winds, voice, organ…). Inak null.
     */
    fun fallbackProgram(hint: String): Int? = when (hint) {
        "organ" -> 19
        "guitar" -> 24
        "strings" -> 48
        "winds" -> 73
        "voice" -> 52
        else -> null
    }

    /** Správanie jednotlivých nástrojov. */
    fun spec(name: String): SamplerSpec = when (name) {
        "piano" -> SamplerSpec(release = 0.32, releaseHigh = 0.14, noDamperFrom = 89, pedal = true, dynamicsDb = 26.0, brightMin = 1200.0, panWidth = 0.3)
        "guitar-nylon", "guitar-acoustic" -> SamplerSpec(release = 0.45, releaseHigh = 0.3, dynamicsDb = 20.0, brightMin = 1800.0, gain = 0.9f)
        "guitar-electric", "bass-electric" -> SamplerSpec(release = 0.3, dynamicsDb = 16.0, brightMin = 2000.0, gain = 0.8f)
        "harp" -> SamplerSpec(release = 0.9, releaseHigh = 0.5, pedal = true, dynamicsDb = 20.0)
        "xylophone" -> SamplerSpec(release = 0.25, noDamperFrom = 0, dynamicsDb = 18.0, gain = 0.8f)
        "harmonium" -> SamplerSpec(sustained = true, release = 0.12, dynamicsDb = 8.0, brightMin = 3000.0, gain = 0.7f)
        "violin", "cello", "contrabass" -> SamplerSpec(sustained = true, release = 0.18, dynamicsDb = 18.0, brightMin = 2500.0, gain = 0.85f)
        else -> SamplerSpec(sustained = true, release = 0.12, dynamicsDb = 16.0, brightMin = 2500.0, gain = 0.85f) // dychové
    }
}

/** Úpravy MIDI pred zobrazením / prehraním. */
object MidiTools {
    /** Hlas = stopa + kanál. */
    fun voiceKey(e: NoteEvent): Int = e.track * 16 + (e.channel and 15)

    /**
     * Odstráni hlasy (stopa + kanál), ktoré len zdvojujú iný hlas o oktávu (alebo viac) – takto Mutopia napodobňuje
     * organové registre (4′, 16′). Zo skupiny zdvojených hlasov ostane ten „stredný“ (pri dvoch nižší).
     * Staršie MIDI z Lilypondu dáva osnovy s rovnakým nástrojom do jedného kanála – preto sa rozlišuje aj stopa.
     */
    fun removeOctaveDoublings(events: List<NoteEvent>): List<NoteEvent> {
        class N(val start: Double, var end: Double, val pitch: Int)
        val byChannel = HashMap<Int, ArrayList<N>>()
        val open = HashMap<Int, N>()
        for (e in events.sortedBy { it.time }) {
            val ch = voiceKey(e)
            val key = ch * 128 + e.data1
            when (e.status) {
                0x90 -> N(e.time, e.time, e.data1).also {
                    open[key] = it
                    byChannel.getOrPut(ch) { ArrayList() }.add(it)
                }
                0x80 -> open.remove(key)?.end = e.time
            }
        }
        val channels = byChannel.keys.filter { (byChannel[it]?.size ?: 0) >= 8 }
        if (channels.size < 2) return events

        fun doubles(b: Int, a: Int): Boolean {
            val nb = byChannel[b]!!
            val na = byChannel[a]!!.sortedBy { it.start }
            val starts = na.map { it.start }
            var match = 0
            for (n in nb) {
                var lo = starts.binarySearch(n.start - 0.03).let { if (it < 0) -it - 1 else it }
                var found = false
                while (lo < na.size && na[lo].start <= n.start + 0.03) {
                    val m = na[lo]
                    val d = n.pitch - m.pitch
                    if (d != 0 && d % 12 == 0 && kotlin.math.abs(m.end - n.end) < 0.08) {
                        found = true
                        break
                    }
                    lo++
                }
                if (found) match++
            }
            return match >= nb.size * 0.9
        }

        // skupiny navzájom zdvojených kanálov
        val parent = HashMap<Int, Int>().apply { channels.forEach { put(it, it) } }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]!!).also { parent[x] = it }
        for (a in channels) for (b in channels) {
            if (a < b && doubles(a, b) && doubles(b, a)) parent[find(a)] = find(b)
        }
        val drop = HashSet<Int>()
        channels.groupBy { find(it) }.values.filter { it.size >= 2 }.forEach { group ->
            val sorted = group.sortedBy { ch -> byChannel[ch]!!.map { it.pitch }.average() }
            val keep = sorted[(sorted.size - 1) / 2] // stred (pri dvoch nižší)
            drop += group - keep
        }
        if (drop.isEmpty()) return events
        return events.filter { it.status == 0xC0 || voiceKey(it) !in drop }
    }
}
