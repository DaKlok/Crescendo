package com.daklok.crescendo.data.midi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Nahratý tón klavíra (mono, 16 bit) pre kláves [note] (MIDI číslo). */
class PianoSample(val note: Int, val pcm: ShortArray)

/**
 * Jednoduchý „sampler“ klavíra: z MIDI nôt vyrobí zvuk pomocou nahratých tónov skutočného krídla.
 * Každý kláves použije najbližší nahratý tón (preladený max. o 1–2 poltóny), sila úderu mení
 * hlasitosť aj jas, pravý pedál drží tóny, ľavý stlmí, stredný (sostenuto) drží len stlačené.
 * Čistý Kotlin – bez Androidu, dá sa otestovať aj na počítači.
 */
class PianoEngine(samples: List<PianoSample>, private val sampleRate: Int = 44_100) {

    private val nearest = arrayOfNulls<PianoSample>(128)

    init {
        require(samples.isNotEmpty())
        for (n in 0 until 128) nearest[n] = samples.minBy { abs(it.note - n) }
    }

    private inner class Voice(val note: Int, velocity: Int, soft: Boolean) {
        val pcm: ShortArray
        val rate: Double
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
            val s = nearest[note]!!
            pcm = s.pcm
            rate = 2.0.pow((note - s.note) / 12.0)
            val v = velocity.coerceIn(1, 127) / 127.0
            // ~26 dB rozdiel medzi pp a ff
            val gain = 10.0.pow(-1.3 * (1 - v)) * (if (soft) 0.72 else 1.0) / 32768.0
            val x = ((note - 64) / 44.0).coerceIn(-1.0, 1.0) * 0.3 // basy vľavo, výšky vpravo (pohľad klaviristu)
            val angle = (x + 1) * PI / 4
            gainL = (gain * cos(angle) * 1.4142).toFloat()
            gainR = (gain * sin(angle) * 1.4142).toFloat()
            val fc = (1200 + 17000 * v * v) * (if (soft) 0.6 else 1.0)
            lpK = (1 - exp(-2 * PI * fc / sampleRate)).toFloat()
        }

        fun release(seconds: Double) {
            val c = exp(-1.0 / (seconds * sampleRate)).toFloat()
            if (c < releaseCoef) releaseCoef = c
        }
    }

    private fun releaseTime(note: Int): Double = 0.32 - 0.18 * ((note - 21).coerceIn(0, 87) / 87.0)

    /** Najvyššie klávesy skutočného klavíra nemajú dusítka – doznievajú aj po pustení. */
    private fun hasDamper(note: Int) = note < 89

    /**
     * Vyrobí zvuk: [sink] dostáva prekladané stereo vzorky (L, R, L, R…) a počet snímok.
     * Pole sa opakovane používa – treba ho spracovať hneď. [progress] 0..1.
     */
    fun render(
        events: List<NoteEvent>,
        sink: (ShortArray, Int) -> Unit,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ) {
        val sorted = events.sortedBy { it.time }
        val voices = ArrayList<Voice>()
        var sustain = false
        var soft = false
        val sostenuto = BooleanArray(128)

        fun releaseIfFree(v: Voice) {
            if (!v.keyDown && !sustain && !sostenuto[v.note] && hasDamper(v.note)) v.release(releaseTime(v.note))
        }

        fun apply(e: NoteEvent) {
            when (e.status) {
                0x90 -> {
                    // opätovný úder toho istého klávesu – starý tón rýchlo stlmíme
                    voices.forEach { if (it.note == e.data1) it.release(0.06) }
                    if (voices.size >= MAX_VOICES) {
                        voices.firstOrNull { it.releaseCoef == 1f && !it.keyDown }?.release(0.03)
                            ?: voices.first().release(0.03)
                    }
                    voices.add(Voice(e.data1, e.data2, soft))
                }
                0x80 -> voices.forEach {
                    if (it.note == e.data1 && it.keyDown) {
                        it.keyDown = false
                        releaseIfFree(it)
                    }
                }
                0xB0 -> {
                    val down = e.data2 >= 64
                    when (e.data1) {
                        64 -> {
                            sustain = down
                            if (!down) voices.forEach { releaseIfFree(it) }
                        }
                        66 -> {
                            if (down) {
                                voices.forEach { if (it.keyDown) sostenuto[it.note] = true }
                            } else {
                                sostenuto.fill(false)
                                voices.forEach { releaseIfFree(it) }
                            }
                        }
                        67 -> soft = down
                    }
                }
            }
        }

        val total = (sorted.lastOrNull()?.time ?: 0.0)
        val maxEnd = total + MAX_TAIL_SECONDS
        val mix = FloatArray(BLOCK * 2)
        val out = ShortArray(OUT_FRAMES * 2)
        var outFrames = 0
        var frame = 0L
        var ei = 0
        var lastProgress = -1f

        while (true) {
            if ((frame / BLOCK) % 512 == 0L && cancelled()) return
            val t = frame.toDouble() / sampleRate
            val blockEnd = (frame + BLOCK).toDouble() / sampleRate
            while (ei < sorted.size && sorted[ei].time < blockEnd) apply(sorted[ei++])
            if ((ei >= sorted.size && voices.isEmpty()) || t > maxEnd) break

            mix.fill(0f)
            val it = voices.iterator()
            while (it.hasNext()) {
                val v = it.next()
                val pcm = v.pcm
                val last = pcm.size - 1
                var pos = v.pos
                var env = v.env
                var lp = v.lp
                val coef = v.releaseCoef
                val k = v.lpK
                val gl = v.gainL
                val gr = v.gainR
                var i = 0
                while (i < BLOCK) {
                    val idx = pos.toInt()
                    if (idx >= last) {
                        v.done = true
                        break
                    }
                    val frac = (pos - idx).toFloat()
                    val a = pcm[idx].toFloat()
                    val s = a + (pcm[idx + 1] - a) * frac
                    lp += k * (s - lp)
                    val y = lp * env
                    mix[2 * i] += y * gl
                    mix[2 * i + 1] += y * gr
                    env *= coef
                    pos += v.rate
                    i++
                }
                v.pos = pos
                v.env = env
                v.lp = lp
                if (v.done || env < 1e-4f) it.remove()
            }

            for (i in 0 until BLOCK) {
                out[2 * outFrames] = toPcm(mix[2 * i])
                out[2 * outFrames + 1] = toPcm(mix[2 * i + 1])
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

    /** Hlasitosť a jemné obmedzenie špičiek (veľké akordy s pedálom nepretečú). */
    private fun toPcm(x: Float): Short {
        val s = x * MASTER
        val a = abs(s)
        val y = if (a <= KNEE) s else (if (s > 0) 1f else -1f) * (KNEE + (1 - KNEE) * tanh((a - KNEE) / (1 - KNEE)))
        return (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }

    companion object {
        private const val BLOCK = 64
        private const val OUT_FRAMES = 4096
        private const val MAX_VOICES = 72
        private const val MAX_TAIL_SECONDS = 12.0
        private const val MASTER = 2.8f
        private const val KNEE = 0.55f
    }
}
