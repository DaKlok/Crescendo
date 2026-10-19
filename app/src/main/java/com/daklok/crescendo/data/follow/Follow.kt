package com.daklok.crescendo.data.follow

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Otáčanie podľa zvuku. Pri nácviku si aplikácia zapamätá, ako skladba znie (chroma: sila 12 tónov
 * v oktáve, asi 21 snímok za sekundu) a kde v notách práve si (strana). Pri hraní potom živý zvuk
 * priebežne zarovnáva s nacvičeným (online DTW) a keď dôjdeš na miesto, kde si pri nácviku otočil,
 * otočí sama. Funguje s mikrofónom aj s digitálnym klavírom (MIDI) a pre akýkoľvek nástroj.
 */

/** Počet hodnôt v jednej snímke (12 tónov). */
const val CHROMA = 12

/** Jedna snímka zvuku: normalizovaná chroma (alebo ticho). */
class FollowFrame(val chroma: FloatArray, val silent: Boolean, val levelDb: Float)

/** Výpočet chroma zo zvuku mikrofónu (rámec 4096 vzoriek, posun 2048 = asi 21,5 snímky za sekundu). */
class AudioChroma(private val rate: Int, private val n: Int = 4096) {
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { i -> (0.5 - 0.5 * cos(2 * PI * i / (n - 1))).toFloat() }
    private val bits = Integer.numberOfTrailingZeros(n)
    private val rev = IntArray(n) { i -> Integer.reverse(i) ushr (32 - bits) }
    private val cosT = FloatArray(n / 2) { i -> cos(2 * PI * i / n).toFloat() }
    private val sinT = FloatArray(n / 2) { i -> -sin(2 * PI * i / n).toFloat() }
    private val kMin = (120.0 * n / rate).roundToInt().coerceAtLeast(1)
    private val kMax = (5000.0 * n / rate).roundToInt().coerceAtMost(n / 2 - 1)
    private val pcOf = IntArray(n / 2) { k ->
        if (k < kMin || k > kMax) -1 else {
            val f = k.toDouble() * rate / n
            val midi = 69 + 12 * log2(f / 440.0)
            ((midi.roundToInt() % 12) + 12) % 12
        }
    }

    /** Spracuje rámec (−1..1, dĺžka [n]). */
    fun frame(samples: FloatArray, silenceDb: Float = SILENCE_DB): FollowFrame {
        var sum = 0.0
        for (i in 0 until n) {
            val v = samples[i]
            sum += v * v
            re[rev[i]] = v * window[i]
            im[rev[i]] = 0f
        }
        val rms = sqrt(sum / n)
        val db = (20 * log10(rms + 1e-9)).toFloat()
        val chroma = FloatArray(CHROMA)
        if (db < silenceDb) return FollowFrame(chroma, true, db)
        fft()
        val norm = 4f / n
        for (k in kMin..kMax) {
            val pc = pcOf[k]
            if (pc < 0) continue
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) * norm
            chroma[pc] += ln(1f + 40f * mag)
        }
        return FollowFrame(normalize(chroma), false, db)
    }

    private fun fft() {
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }

    companion object {
        /** Pod touto hlasitosťou (dBFS) je snímka ticho. */
        const val SILENCE_DB = -50f
    }
}

/** Chroma z kláves digitálneho klavíra (MIDI): znejúce tóny a ich hlavné alikvoty, aby sa podobala zvuku. */
fun midiChroma(notes: Collection<Int>): FollowFrame {
    val c = FloatArray(CHROMA)
    if (notes.isEmpty()) return FollowFrame(c, true, -90f)
    for (m in notes) {
        val pc = ((m % 12) + 12) % 12
        c[pc] += 1f
        c[(pc + 7) % 12] += 0.33f
        c[(pc + 4) % 12] += 0.18f
    }
    return FollowFrame(normalize(c), false, -20f)
}

private fun normalize(c: FloatArray): FloatArray {
    var s = 0f
    for (v in c) s += v * v
    val len = sqrt(s)
    if (len > 1e-6f) for (i in c.indices) c[i] /= len
    return c
}

/**
 * Nacvičená skladba: snímky zvuku a poloha v notách v každej snímke (strana, pri plynulom posúvaní aj časť strany).
 */
class FollowReference(
    val fps: Float,
    /** 0 = mikrofón, 1 = klavír (MIDI). */
    val source: Int,
    val learnedAt: Long,
    val chroma: FloatArray,
    val silent: BooleanArray,
    val position: FloatArray,
) {
    val frames: Int get() = silent.size
    val durationSec: Int get() = (frames / fps).roundToInt()

    /** Koľkokrát sa pri nácviku otočila strana. */
    val turns: Int
        get() {
            var n = 0
            for (i in 1 until frames) if (position[i].toInt() > position[i - 1].toInt()) n++
            return n
        }
}

/** Uloženie nacvičených skladieb (priečinok „follow“, je v zálohe aj v synchronizácii). */
class FollowStore(context: Context) {
    private val dir = File(context.filesDir, DIR)

    private fun file(pieceId: Long) = File(dir, "$pieceId.bin")

    fun exists(pieceId: Long): Boolean = file(pieceId).length() > 0

    fun delete(pieceId: Long) {
        file(pieceId).delete()
    }

    fun save(pieceId: Long, ref: FollowReference) {
        dir.mkdirs()
        val tmp = File(dir, "$pieceId.tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(1)
            o.writeFloat(ref.fps)
            o.writeByte(ref.source)
            o.writeLong(ref.learnedAt)
            o.writeInt(ref.frames)
            val bytes = ByteArray(CHROMA)
            for (i in 0 until ref.frames) {
                for (k in 0 until CHROMA) bytes[k] = (ref.chroma[i * CHROMA + k] * 255f).roundToInt().coerceIn(0, 255).toByte()
                o.write(bytes)
                o.writeByte(if (ref.silent[i]) 1 else 0)
                o.writeFloat(ref.position[i])
            }
        }
        if (!tmp.renameTo(file(pieceId))) {
            tmp.copyTo(file(pieceId), overwrite = true)
            tmp.delete()
        }
    }

    fun load(pieceId: Long): FollowReference? = runCatching {
        val f = file(pieceId)
        if (!f.exists()) return null
        DataInputStream(BufferedInputStream(f.inputStream())).use { i ->
            if (i.readInt() != MAGIC) return null
            i.readInt() // verzia
            val fps = i.readFloat()
            val source = i.readByte().toInt()
            val at = i.readLong()
            val n = i.readInt()
            if (n <= 0 || n > 200_000) return null
            val chroma = FloatArray(n * CHROMA)
            val silent = BooleanArray(n)
            val pos = FloatArray(n)
            val bytes = ByteArray(CHROMA)
            for (k in 0 until n) {
                i.readFully(bytes)
                for (c in 0 until CHROMA) chroma[k * CHROMA + c] = (bytes[c].toInt() and 0xFF) / 255f
                silent[k] = i.readByte().toInt() != 0
                pos[k] = i.readFloat()
            }
            FollowReference(fps, source, at, chroma, silent, pos)
        }
    }.getOrNull()

    /** Strany pribudli na začiatok skladby: poloha v nacvičenej skladbe sa posunie. */
    fun shiftPages(pieceId: Long, by: Int) {
        val ref = load(pieceId) ?: return
        for (i in ref.position.indices) ref.position[i] += by
        runCatching { save(pieceId, ref) }
    }

    companion object {
        const val DIR = "follow"
        private const val MAGIC = 0x43524631 // „CRF1“
    }
}

/**
 * Sledovanie hry podľa nacvičenej skladby: online zarovnanie (DTW s posuvným oknom). Tichu sa nevenuje
 * (pauzy pri hraní ani pri nácviku nič neposunú), tempo môže byť pomalšie aj rýchlejšie ako pri nácviku.
 * Keď hra nesedí s nacvičeným (rozohrávanie, opakovanie úseku, preskočený riadok), poloha stojí
 * a po chvíli sa posledné dve sekundy hry vyhľadajú v celej skladbe a pokračuje sa odtiaľ.
 */
class ScoreFollower(private val ref: FollowReference) {
    /** Snímky nacvičenej skladby so zvukom (ticho sa vynechá). */
    private val idx: IntArray = (0 until ref.frames).filter { !ref.silent[it] }.toIntArray()
    private val m = idx.size
    private var d = FloatArray(m) { INF }
    private var nd = FloatArray(m) { INF }
    private var lo = 0
    private var hi = -1

    /** Posledné snímky živej hry (na vyhľadanie, keď sa sledovanie stratí). */
    private val history = ArrayDeque<FloatArray>()
    private var lostRun = 0

    /** Najlepšia zhoda (index medzi snímkami so zvukom). */
    var center = 0
        private set

    /** Ako dobre živá hra sedí s nácvikom (0..1, kĺzavý priemer). */
    var quality = 0.5f
        private set

    val usable: Boolean get() = m >= 20

    /** Začne od polohy [page] (napr. strana, na ktorej práve si). */
    fun reset(page: Float) {
        resetAt(firstIndexAt(page))
        quality = 0.5f
        lostRun = 0
    }

    private fun resetAt(s: Int) {
        center = s.coerceIn(0, (m - 1).coerceAtLeast(0))
        lo = (center - BACK).coerceAtLeast(0)
        hi = (center + AHEAD).coerceAtMost(m - 1)
        d.fill(INF)
        for (i in lo..hi) d[i] = kotlin.math.abs(i - center) * 0.03f
    }

    /** Index prvej snímky so zvukom na strane [page] alebo za ňou. */
    private fun firstIndexAt(page: Float): Int {
        if (m == 0) return 0
        for (i in 0 until m) if (ref.position[idx[i]] >= page - 0.001f) return i
        return m - 1
    }

    /** Spracuje živú snímku (so zvukom); vráti polohu v notách. */
    fun push(x: FloatArray): Float {
        if (m == 0) return 0f
        history.addLast(x)
        while (history.size > HISTORY) history.removeFirst()
        // sedí hra s nácvikom okolo aktuálneho miesta? ak nie, poloha stojí
        var local = 1f
        for (i in center..(center + 8).coerceAtMost(m - 1)) local = kotlin.math.min(local, cost(x, i))
        if (local > LOST) {
            lostRun++
            quality = quality * 0.92f + (1f - local) * 0.08f
            if (lostRun >= RECOVER_AFTER && lostRun % 8 == 0) recover()
            return ref.position[idx[center]]
        }
        lostRun = 0
        val newLo = (center - BACK).coerceAtLeast(0)
        val newHi = (center + AHEAD).coerceAtMost(m - 1)
        var best = INF
        var bestI = center
        for (i in newLo..newHi) {
            var p = prev(i) + STAY
            val a = prev(i - 1)
            if (a < p) p = a
            val b = prev(i - 2) + SKIP1
            if (b < p) p = b
            val c = prev(i - 3) + SKIP2
            if (c < p) p = c
            val v = if (p >= INF) INF else p + cost(x, i)
            nd[i] = v
            if (v < best) {
                best = v
                bestI = i
            }
        }
        if (best < INF) for (i in newLo..newHi) if (nd[i] < INF) nd[i] -= best
        val t = d
        d = nd
        nd = t
        lo = newLo
        hi = newHi
        center = bestI
        quality = quality * 0.92f + (1f - cost(x, bestI)) * 0.08f
        return ref.position[idx[center]]
    }

    /**
     * Vyhľadá posledné snímky hry v celej skladbe (DTW s voľným začiatkom); ak sa nájde dobrá zhoda,
     * pokračuje sa od nej (opakovaný úsek, preskočený riadok, začiatok inde).
     */
    private fun recover() {
        val h = history.size
        if (h < HISTORY / 2) return
        val live = history.toList()
        var prevRow = FloatArray(m)
        var row = FloatArray(m)
        for (j in 0 until m) prevRow[j] = cost(live[0], j)
        for (t in 1 until h) {
            val x = live[t]
            for (j in 0 until m) {
                var p = prevRow[j] + STAY
                if (j >= 1 && prevRow[j - 1] < p) p = prevRow[j - 1]
                if (j >= 2 && prevRow[j - 2] + SKIP1 < p) p = prevRow[j - 2] + SKIP1
                row[j] = p + cost(x, j)
            }
            val tmp = prevRow
            prevRow = row
            row = tmp
        }
        var bestJ = -1
        var bestScore = INF
        for (j in 0 until m) {
            // bližšie miesto má malú prednosť (opakujúce sa motívy)
            val score = prevRow[j] / h + kotlin.math.abs(j - center).toFloat() / m * 0.05f
            if (score < bestScore) {
                bestScore = score
                bestJ = j
            }
        }
        if (bestJ >= 0 && bestScore < FOUND) {
            resetAt(bestJ)
            lostRun = 0
            quality = 1f - bestScore
        }
    }

    private fun prev(i: Int): Float = if (i < lo || i > hi || i < 0) INF else d[i]

    private fun cost(x: FloatArray, i: Int): Float {
        val base = idx[i] * CHROMA
        var dot = 0f
        for (k in 0 until CHROMA) dot += x[k] * ref.chroma[base + k]
        return (1f - dot).coerceIn(0f, 1f)
    }

    /** Poloha v notách o [lead] snímok vpred (aby sa otočilo včas). */
    fun positionAhead(lead: Int): Float {
        if (m == 0) return 0f
        return ref.position[idx[(center + lead).coerceIn(0, m - 1)]]
    }

    /** Ako ďaleko v skladbe sme (0..1). */
    val progress: Float get() = if (m <= 1) 0f else center.toFloat() / (m - 1)

    private companion object {
        const val INF = 1e9f
        const val BACK = 40
        const val AHEAD = 280
        const val STAY = 0.12f
        const val SKIP1 = 0.10f
        const val SKIP2 = 0.32f
        /** Horšia zhoda než toto = hra nesedí (poloha stojí). */
        const val LOST = 0.5f
        /** Po koľkých nesediacich snímkach sa hľadá v celej skladbe (asi 1,5 s). */
        const val RECOVER_AFTER = 32
        const val HISTORY = 48
        /** Priemerná zhoda pri vyhľadaní, ktorá stačí na skok. */
        const val FOUND = 0.3f
    }
}

/** Zostaví nacvičenú skladbu z nahratých snímok; ticho na začiatku a na konci sa oreže. */
fun buildReference(
    frames: List<FollowFrame>,
    positions: List<Float>,
    fps: Float,
    source: Int,
): FollowReference? {
    if (frames.isEmpty()) return null
    var a = 0
    var b = frames.size - 1
    while (a < b && frames[a].silent) a++
    while (b > a && frames[b].silent) b--
    val n = b - a + 1
    if (n < (fps * 8).roundToInt()) return null
    val chroma = FloatArray(n * CHROMA)
    val silent = BooleanArray(n)
    val pos = FloatArray(n)
    for (i in 0 until n) {
        val f = frames[a + i]
        System.arraycopy(f.chroma, 0, chroma, i * CHROMA, CHROMA)
        silent[i] = f.silent
        pos[i] = positions[a + i]
    }
    // poloha nikdy neklesá (návrat o stranu pri nácviku sa nepočíta)
    for (i in 1 until n) pos[i] = max(pos[i], pos[i - 1])
    // skok o viac strán naraz (dve strany vedľa seba): úsek sa rozdelí rovnomerne po stranách,
    // aby sa pri jednej strane na obrazovke otáčalo aj v strede
    var segStart = 0
    for (i in 1 until n) {
        val prev = pos[i - 1]
        val cur = pos[i]
        if (cur <= prev) continue
        val whole = prev == kotlin.math.floor(prev) && cur == kotlin.math.floor(cur)
        val k = (cur - prev).toInt()
        if (whole && k >= 2) {
            val len = i - segStart
            for (j in segStart until i) pos[j] = prev + ((j - segStart).toLong() * k / len).toInt()
        }
        segStart = i
    }
    return FollowReference(fps, source, System.currentTimeMillis(), chroma, silent, pos)
}
