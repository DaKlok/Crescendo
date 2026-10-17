package com.daklok.crescendo.data.midi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Spoločné rozhranie „syntetizátorov“: z MIDI udalostí vyrobí stereo zvuk. */
interface MidiSynth {
    fun render(
        events: List<NoteEvent>,
        sink: (ShortArray, Int) -> Unit,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    )
}

/**
 * Píšťalový organ: aditívna syntéza (registrácia principál 8′ + oktáva 4′ + 2′ + mixtúra,
 * v basoch subbas 16′), tóny znejú rovnako silno, kým je kláves stlačený, a doznievajú v „kostolnej“ ozvene.
 * Čistý Kotlin – dá sa otestovať aj na počítači.
 */
class OrganEngine(private val sampleRate: Int = 44_100) : MidiSynth {

    /** Pásmovo obmedzené tabuľky (bez vysokých alikvotov pri vysokých tónoch – žiadne skreslenie). */
    private val tables: Array<FloatArray>
    private val tableTop: DoubleArray
    private val sine = FloatArray(TABLE + 1) { sin(2 * PI * it / TABLE).toFloat() }

    init {
        val harmonics = doubleArrayOf(0.0, 1.0, 0.55, 0.18, 0.32, 0.06, 0.14, 0.0, 0.12, 0.0, 0.04, 0.0, 0.05, 0.0, 0.0, 0.0, 0.03)
        val bands = 9
        tables = Array(bands) { FloatArray(TABLE + 1) }
        tableTop = DoubleArray(bands)
        for (b in 0 until bands) {
            val top = 55.0 * 2.0.pow(b + 1) // najvyšší základný tón tabuľky
            tableTop[b] = top
            val t = tables[b]
            var peak = 0.0
            val tmp = DoubleArray(TABLE)
            for (i in 0 until TABLE) {
                var v = 0.0
                for (h in 1 until harmonics.size) {
                    if (harmonics[h] == 0.0 || top * h > sampleRate * 0.45) continue
                    v += harmonics[h] * sin(2 * PI * h * i / TABLE)
                }
                tmp[i] = v
                peak = maxOf(peak, abs(v))
            }
            for (i in 0 until TABLE) t[i] = (tmp[i] / peak).toFloat()
            t[TABLE] = t[0]
        }
    }

    private inner class Pipe(val note: Int, detuneCents: Double) {
        val freq = 440.0 * 2.0.pow((note - 69 + detuneCents / 100.0) / 12.0)
        val table = tables[tableTop.indexOfFirst { freq <= it }.let { if (it < 0) tables.lastIndex else it }]
        val inc = freq * TABLE / sampleRate
        val subInc = inc / 2
        val sub = if (note < 48) 0.42f * ((48 - note).coerceAtMost(12) / 12f).coerceAtLeast(0.4f) else 0f
        var phase = 0.0
        var subPhase = 0.0
        var env = 0f
        val attack = 1f / (0.028f * sampleRate) // ~28 ms nábeh píšťaly
        var releasing = false
        val releaseCoef = exp(-1.0 / (0.07 * sampleRate)).toFloat()
        val gainL: Float
        val gainR: Float

        init {
            // píšťaly sú rozostavené striedavo vľavo a vpravo (ako na skutočnom organe)
            val x = if (note % 2 == 0) -0.35 else 0.35
            val a = (x + 1) * PI / 4
            gainL = (cos(a) * 1.4142 * VOICE_GAIN).toFloat()
            gainR = (sin(a) * 1.4142 * VOICE_GAIN).toFloat()
        }
    }

    /** Nový organ pre mixér (vlastné píšťaly a ozvena). */
    fun synth(): BlockSynth = OrganSynth()

    override fun render(
        events: List<NoteEvent>,
        sink: (ShortArray, Int) -> Unit,
        progress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val organ = synth()
        MidiMixer.render(events, { _, _ -> organ }, sink, progress, cancelled, sampleRate, tailSeconds = TAIL_SECONDS)
    }

    private inner class OrganSynth : BlockSynth {
        private val pipes = ArrayList<Pipe>()
        private val rnd = java.util.Random(7)
        private val reverb = Reverb(sampleRate)
        private var tail = 0 // snímky ozveny po poslednej píšťale
        private var mixL = FloatArray(0)
        private var mixR = FloatArray(0)

        override val busy: Boolean get() = pipes.isNotEmpty() || tail > 0

        override fun event(e: NoteEvent) {
            when (e.status) {
                0x90 -> {
                    pipes.forEach { if (it.note == e.data1) it.releasing = true }
                    if (pipes.size < MAX_PIPES) pipes.add(Pipe(e.data1, rnd.nextGaussian() * 1.2))
                }
                0x80 -> pipes.forEach { if (it.note == e.data1) it.releasing = true }
            }
        }

        override fun mix(l: FloatArray, r: FloatArray, frames: Int) {
            if (mixL.size < frames) {
                mixL = FloatArray(frames)
                mixR = FloatArray(frames)
            }
            mixL.fill(0f)
            mixR.fill(0f)
            val it = pipes.iterator()
            while (it.hasNext()) {
                val p = it.next()
                val tab = p.table
                var ph = p.phase
                var sph = p.subPhase
                var env = p.env
                for (i in 0 until frames) {
                    if (p.releasing) env *= p.releaseCoef else if (env < 1f) env = minOf(1f, env + p.attack)
                    val idx = ph.toInt()
                    val frac = (ph - idx).toFloat()
                    var s = tab[idx] + (tab[idx + 1] - tab[idx]) * frac
                    if (p.sub > 0f) {
                        val sIdx = sph.toInt()
                        val sFrac = (sph - sIdx).toFloat()
                        // subbas 16′ – čistý sínus o oktávu nižšie
                        s += (sine[sIdx] + (sine[sIdx + 1] - sine[sIdx]) * sFrac) * p.sub
                    }
                    s *= env
                    mixL[i] += s * p.gainL
                    mixR[i] += s * p.gainR
                    ph += p.inc
                    if (ph >= TABLE) ph -= TABLE
                    sph += p.subInc
                    if (sph >= TABLE) sph -= TABLE
                }
                p.phase = ph
                p.subPhase = sph
                p.env = env
                if (p.releasing && env < 1e-4f) it.remove()
            }
            tail = if (pipes.isNotEmpty()) (TAIL_SECONDS * sampleRate).toInt() else (tail - frames).coerceAtLeast(0)
            for (i in 0 until frames) {
                reverb.process(mixL[i] + mixR[i])
                l[i] += (mixL[i] * DRY + reverb.left * WET) * MASTER
                r[i] += (mixR[i] * DRY + reverb.right * WET) * MASTER
            }
        }
    }

    /** Jednoduchý dozvuk (Freeverb): 4 hrebeňové + 2 všepriepustné filtre na kanál – „veľký kostol“. */
    private class Reverb(rate: Int) {
        private val scale = rate / 44_100.0
        private fun len(n: Int) = (n * scale).toInt().coerceAtLeast(1)
        private val combL = intArrayOf(1557, 1617, 1491, 1422, 1277, 1356).map { Comb(len(it)) }
        private val combR = intArrayOf(1580, 1640, 1514, 1445, 1300, 1379).map { Comb(len(it)) }
        private val apL = intArrayOf(556, 441).map { AllPass(len(it)) }
        private val apR = intArrayOf(579, 464).map { AllPass(len(it)) }

        var left = 0f
            private set
        var right = 0f
            private set

        fun process(input: Float) {
            val x = input * 0.12f
            var l = 0f
            var r = 0f
            for (c in combL) l += c.process(x)
            for (c in combR) r += c.process(x)
            for (a in apL) l = a.process(l)
            for (a in apR) r = a.process(r)
            left = l
            right = r
        }

        private class Comb(size: Int) {
            private val buf = FloatArray(size)
            private var i = 0
            private var store = 0f
            fun process(x: Float): Float {
                val y = buf[i]
                store = y * 0.75f + store * 0.25f // tlmenie výšok
                buf[i] = x + store * 0.88f // dĺžka dozvuku
                if (++i == buf.size) i = 0
                return y
            }
        }

        private class AllPass(size: Int) {
            private val buf = FloatArray(size)
            private var i = 0
            fun process(x: Float): Float {
                val b = buf[i]
                val y = -x + b
                buf[i] = x + b * 0.5f
                if (++i == buf.size) i = 0
                return y
            }
        }
    }

    companion object {
        private const val TABLE = 2048
        private const val MAX_PIPES = 64
        private const val TAIL_SECONDS = 4.0
        private const val VOICE_GAIN = 0.065
        private const val DRY = 0.8f
        private const val WET = 0.5f
        private const val MASTER = 1.0f
    }
}
