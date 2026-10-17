package com.daklok.crescendo.data.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Druh akordu: značka (C**m7**) a intervaly od základného tónu. */
enum class ChordQuality(val symbol: String, val intervals: Set<Int>) {
    MAJOR("", setOf(0, 4, 7)),
    MINOR("m", setOf(0, 3, 7)),
    DOM7("7", setOf(0, 4, 7, 10)),
    MAJ7("maj7", setOf(0, 4, 7, 11)),
    MIN7("m7", setOf(0, 3, 7, 10)),
    HALF_DIM("m7♭5", setOf(0, 3, 6, 10)),
    DIM7("dim7", setOf(0, 3, 6, 9)),
    DIM("dim", setOf(0, 3, 6)),
    AUG("aug", setOf(0, 4, 8)),
    SUS4("sus4", setOf(0, 5, 7)),
    SUS2("sus2", setOf(0, 2, 7)),
    SIX("6", setOf(0, 4, 7, 9)),
    MIN6("m6", setOf(0, 3, 7, 9)),
    ADD9("add9", setOf(0, 2, 4, 7)),
    MADD9("madd9", setOf(0, 2, 3, 7)),
    NINE("9", setOf(0, 2, 4, 7, 10)),
    MAJ9("maj9", setOf(0, 2, 4, 7, 11)),
    MIN9("m9", setOf(0, 2, 3, 7, 10)),
    MIN_MAJ7("m(maj7)", setOf(0, 3, 7, 11)),
    SEVEN_SUS4("7sus4", setOf(0, 5, 7, 10)),

    // septakordy bez kvinty (bežné v hre)
    DOM7_NO5("7", setOf(0, 4, 10)),
    MAJ7_NO5("maj7", setOf(0, 4, 11)),
    MIN7_NO5("m7", setOf(0, 3, 10)),

    /** Dva tóny vzdialené o kvintu („power chord“). */
    POWER("5", setOf(0, 7)),
}

/**
 * Rozpoznaný akord. [root] a [bass] sú triedy tónov 0–11 (C = 0).
 * [quality] je null, keď tóny netvoria známy akord (zobrazia sa len tóny).
 */
data class Chord(val root: Int, val quality: ChordQuality?, val bass: Int, val notes: List<Int>)

object ChordDetector {

    /**
     * Akord z tried tónov (0–11). [bass] je najnižší znejúci tón (kvôli obratom: C/E).
     * Vráti null pre prázdnu množinu.
     */
    fun detect(pitchClasses: Set<Int>, bass: Int?): Chord? {
        if (pitchClasses.isEmpty()) return null
        val pcs = pitchClasses.map { ((it % 12) + 12) % 12 }.toSet()
        val b = bass?.let { ((it % 12) + 12) % 12 }?.takeIf { it in pcs } ?: pcs.min()
        val ordered = pcs.sortedBy { (it - b + 12) % 12 }
        if (pcs.size == 1) return Chord(b, null, b, ordered)

        // 1) presná zhoda so šablónou; pri viacerých výkladoch (C6 = Am7) má prednosť bas ako základ
        val exact = ArrayList<Pair<Int, ChordQuality>>()
        for (q in ChordQuality.entries) {
            if (q.intervals.size != pcs.size) continue
            for (r in pcs) if (pcs.map { (it - r + 12) % 12 }.toSet() == q.intervals) exact.add(r to q)
        }
        exact.minWithOrNull(compareBy<Pair<Int, ChordQuality>> { if (it.first == b) 0 else 1 }.thenBy { it.second.ordinal })
            ?.let { (r, q) -> return Chord(r, q, b, ordered) }

        // 2) najväčšia šablóna (aspoň trojzvuk), ktorá je celá obsiahnutá – ostatné tóny sú ozdoby
        if (pcs.size >= 3) {
            var best: Pair<Int, ChordQuality>? = null
            var bestScore = -1
            for (q in ChordQuality.entries) {
                if (q.intervals.size < 3) continue
                for (r in pcs) {
                    val rel = pcs.map { (it - r + 12) % 12 }.toSet()
                    if (!rel.containsAll(q.intervals)) continue
                    val score = q.intervals.size * 10 + (if (r == b) 5 else 0) - q.ordinal / 10
                    if (score > bestScore) {
                        bestScore = score
                        best = r to q
                    }
                }
            }
            best?.let { (r, q) -> return Chord(r, q, b, ordered) }
        }
        return Chord(b, null, b, ordered)
    }
}

/** Sledovanie stlačených kláves z MIDI klavíra (aj s „running status“). */
class HeldNotes {
    private val down = BooleanArray(128)
    private var running = 0

    /** Spracuje MIDI bajty; vráti true, ak sa zmenili stlačené klávesy. */
    @Synchronized
    fun onBytes(msg: ByteArray, offset: Int, count: Int): Boolean {
        var changed = false
        var i = offset
        val end = offset + count
        while (i < end) {
            var status = msg[i].toInt() and 0xFF
            if (status >= 0xF8) {
                i++
                continue
            }
            if (status >= 0xF0) {
                if (status == 0xF0) while (i < end && (msg[i].toInt() and 0xFF) != 0xF7) i++
                i++
                running = 0
                continue
            }
            if (status >= 0x80) {
                running = status
                i++
            } else {
                status = running
                if (status == 0) {
                    i++
                    continue
                }
            }
            val type = status and 0xF0
            val len = if (type == 0xC0 || type == 0xD0) 1 else 2
            if (i + len > end) break
            val d1 = msg[i].toInt() and 0x7F
            val d2 = if (len == 2) msg[i + 1].toInt() and 0x7F else 0
            i += len
            if (type == 0x90 && d2 > 0) {
                if (!down[d1]) changed = true
                down[d1] = true
            } else if (type == 0x80 || type == 0x90) {
                if (down[d1]) changed = true
                down[d1] = false
            } else if (type == 0xB0 && d1 == 123) { // all notes off
                down.fill(false)
                changed = true
            }
        }
        return changed
    }

    /** Stlačené klávesy (MIDI čísla) od najnižšej. */
    @Synchronized
    fun notes(): List<Int> = (0 until 128).filter { down[it] }
}

/**
 * Rozpoznanie tónov akordu zo zvuku (mikrofón): spektrum → výrazné vrcholy → noty,
 * pričom sa odfiltrujú vyššie harmonické tóny (alikvoty) nižších nôt.
 */
class ChordListener(private val sampleRate: Int, val frameSize: Int = 8192) {
    private companion object {
        const val MIN_NOTE = 33 // A1
        const val MAX_NOTE = 96 // C7
        const val MAX_H = 10
        val HW = DoubleArray(MAX_H + 1) { if (it == 0) 0.0 else 1.0 / Math.pow(it.toDouble(), 0.6) }
    }

    private val fftSize = frameSize * 2 // doplnenie nulami – jemnejšie rozlíšenie frekvencie
    private val window = FloatArray(frameSize) { (0.5 - 0.5 * cos(2 * PI * it / (frameSize - 1))).toFloat() }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val mag = DoubleArray(fftSize / 2)

    /** Priemerná energia posledného rámca (na rozpoznanie nového úderu). */
    var lastEnergy = 0.0
        private set

    /** Znejúce noty (MIDI čísla) v rámci; prázdne pri tichu. */
    fun notes(frame: FloatArray): List<Int> {
        var energy = 0.0
        for (v in frame) energy += v * v
        lastEnergy = energy / frame.size
        if (lastEnergy < 2e-6) return emptyList()

        re.fill(0.0)
        im.fill(0.0)
        for (i in 0 until frameSize) re[i] = (frame[i] * window[i]).toDouble()
        fft(re, im)
        for (k in mag.indices) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])

        val binHz = sampleRate.toDouble() / fftSize
        val lo = (55.0 / binHz).toInt().coerceAtLeast(2)
        val hi = (4100.0 / binHz).toInt().coerceAtMost(mag.size - 2)
        var maxMag = 0.0
        for (k in lo..hi) if (mag[k] > maxMag) maxMag = mag[k]
        if (maxMag <= 0) return emptyList()

        // výrazné vrcholy spektra (frekvencia, sila)
        val pf = ArrayList<Double>()
        val pm = ArrayList<Double>()
        for (k in lo..hi) {
            val m = mag[k]
            if (m < maxMag * 0.03 || m < mag[k - 1] || m < mag[k + 1]) continue
            val a = mag[k - 1]
            val c = mag[k + 1]
            val d = a - 2 * m + c
            val off = if (d != 0.0) 0.5 * (a - c) / d else 0.0
            pf.add((k + off) * binHz)
            pm.add(m)
        }
        if (pf.isEmpty()) return emptyList()
        val freqs = pf.toDoubleArray()
        val left = pm.toDoubleArray() // zostávajúca sila po „odčítaní“ nájdených nôt

        // Postupné hľadanie nôt (podľa Klapuriho): nota s najsilnejšou radou harmonických,
        // potom sa jej harmonické odčítajú – len do „hladkej“ obálky, aby ostali spoločné tóny (napr. kvinta).
        val found = ArrayList<Int>()
        var first = 0.0
        val matched = IntArray(MAX_H + 1)
        repeat(6) {
            var bestN = -1
            var bestS = 0.0
            for (n in MIN_NOTE..MAX_NOTE) {
                if (n in found) continue
                val s = salience(n, freqs, left, null)
                if (s > bestS) {
                    bestS = s
                    bestN = n
                }
            }
            if (bestN < 0 || bestS <= 0) return@repeat
            if (first == 0.0) first = bestS
            if (bestS < first * 0.16) return@repeat
            found.add(bestN)
            salience(bestN, freqs, left, matched)
            // odčítanie so spektrálnou hladkosťou
            val amps = DoubleArray(MAX_H + 2)
            for (h in 1..MAX_H) amps[h] = if (matched[h] >= 0) left[matched[h]] else 0.0
            for (h in 1..MAX_H) {
                val i = matched[h]
                if (i < 0) continue
                val neighbors = listOf(amps[h - 1].takeIf { h > 1 }, amps.getOrNull(h + 1)).filterNotNull().filter { it > 0 }
                val smooth = if (neighbors.isEmpty()) amps[h] else minOf(amps[h], neighbors.average() * 1.2)
                val sub = if (h == 1) amps[h] else smooth
                left[i] = (left[i] - sub).coerceAtLeast(0.0)
            }
        }
        return found.sorted()
    }

    /**
     * Sila „kandidáta“ [n]: vážený súčet vrcholov na jeho harmonických frekvenciách
     * (struny klavíra majú alikvoty mierne vyššie – preto širšia tolerancia smerom hore).
     * Ak [matched] nie je null, zapíše index vrcholu pre každú harmonickú (−1 = žiadny).
     */
    private fun salience(n: Int, freqs: DoubleArray, amp: DoubleArray, matched: IntArray?): Double {
        val f0 = 440.0 * Math.pow(2.0, (n - 69) / 12.0)
        var s = 0.0
        matched?.fill(-1)
        for (h in 1..MAX_H) {
            val target = f0 * h
            if (target > 4000) break
            val loR = 1 - 0.025
            val hiR = 1 + 0.025 + 0.003 * h
            var best = -1
            var bestA = 0.0
            for (i in freqs.indices) {
                val r = freqs[i] / target
                if (r < loR) continue
                if (r > hiR) break
                if (amp[i] > bestA) {
                    bestA = amp[i]
                    best = i
                }
            }
            if (best >= 0) {
                s += bestA * HW[h]
                matched?.set(h, best)
            }
        }
        return s
    }

    /** Rýchla Fourierova transformácia (radix-2, na mieste). */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * Upokojenie výsledku z mikrofónu: tón sa zaráta, ak sa objavil aspoň v 2 z posledných 4 rámcov.
 * Pri novom údere (prudký nárast hlasitosti) sa história vymaže, aby sa nový akord ukázal hneď.
 */
class ChordSmoother(private val history: Int = 4, private val need: Int = 2) {
    private val frames = ArrayDeque<List<Int>>()
    private var lastEnergy = 0.0

    fun push(notes: List<Int>, energy: Double): Chord? {
        if (energy > lastEnergy * 2.5) frames.clear()
        lastEnergy = energy
        if (notes.isEmpty()) {
            frames.clear()
            lastCandidate = null
            stable = null
            return null
        }
        frames.addLast(notes)
        while (frames.size > history) frames.removeFirst()
        val count = IntArray(12)
        for (f in frames) for (pc in f.map { it % 12 }.toSet()) count[pc]++
        val needed = minOf(need, frames.size)
        val pcs = (0 until 12).filter { count[it] >= needed }.toSet()
        if (pcs.isEmpty()) return null
        val bass = frames.flatten().filter { it % 12 in pcs }.minOrNull()
        val candidate = ChordDetector.detect(pcs, bass)
        // zmena akordu sa ukáže, až keď vyjde rovnako dvakrát za sebou (proti blikaniu); po údere hneď
        val previous = lastCandidate
        lastCandidate = candidate
        if (frames.size == 1 || candidate == previous || stable == null) stable = candidate
        return stable
    }

    private var lastCandidate: Chord? = null
    private var stable: Chord? = null

    fun reset() {
        frames.clear()
        lastEnergy = 0.0
        lastCandidate = null
        stable = null
    }
}
