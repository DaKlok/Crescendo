package com.daklok.crescendo.data.midi

import kotlin.math.abs

/** Nota na zobrazenie (padajúci obdĺžnik): začiatok a koniec v sekundách, výška, ruka. */
class VisNote(val start: Double, val end: Double, val pitch: Int, val right: Boolean)

/** Skupina nôt, ktoré treba stlačiť naraz (akord) – pre režim „čakaj na mňa“. */
class VisChord(val time: Double, val pitches: Set<Int>, val right: Boolean)

/**
 * Skladba pripravená pre vizualizér: noty zoradené podľa začiatku, rozsah klaviatúry
 * a akordy na čakanie (zvlášť pre pravú a ľavú ruku).
 */
class VisSong(
    val notes: List<VisNote>,
    val duration: Double,
    /** Najnižší a najvyšší zobrazený kláves (zarovnané na celé oktávy, aspoň 2 oktávy). */
    val low: Int,
    val high: Int,
    val maxNoteLength: Double,
) {
    /** Akordy pre vybrané ruky (null = obe). */
    fun chords(right: Boolean?): List<VisChord> {
        val chosen = notes.filter { right == null || it.right == right }
        val out = ArrayList<VisChord>()
        var i = 0
        while (i < chosen.size) {
            val t = chosen[i].start
            val group = HashSet<Int>()
            var j = i
            while (j < chosen.size && chosen[j].start - t < CHORD_WINDOW) {
                group.add(chosen[j].pitch)
                j++
            }
            out.add(VisChord(t, group, chosen[i].right))
            i = j
        }
        return out
    }

    /** Index prvej noty, ktorá môže byť viditeľná v čase [time] (rýchle hľadanie). */
    fun firstVisible(time: Double): Int {
        val from = time - maxNoteLength
        var lo = 0
        var hi = notes.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (notes[mid].start < from) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val CHORD_WINDOW = 0.06

        /**
         * Rozdelenie na dve ruky tak, ako by sa to hralo: sleduje polohu pravej a ľavej ruky, ruka má najviac
         * 5 prstov a rozpätie zhruba oktávu (+ pár poltónov), ruky sa nekrížia. Pri každom údere (akorde) vyberie
         * deliaci bod s najmenšou „námahou“. [hint] = odporúčanie zo stopy MIDI (true = pravá), ak je.
         * Vracia pre každú notu true = pravá ruka.
         */
        fun splitHands(notes: List<Triple<Double, Double, Int>>, hint: List<Boolean?>): List<Boolean> {
            val n = notes.size
            val result = BooleanArray(n)
            val order = (0 until n).sortedWith(compareBy({ notes[it].first }, { notes[it].third }))
            var cR = 67.0
            var cL = 50.0
            // stred rúk podľa začiatku skladby (aby prvé noty nezačali na zlej strane)
            run {
                val first = order.take(16).map { notes[it].third }
                if (first.isNotEmpty() && first.max() - first.min() >= 12) {
                    cR = first.max() - 4.0
                    cL = first.min() + 4.0
                } else if (first.isNotEmpty()) {
                    val m = first.average()
                    if (m >= 60) cR = m else cL = m
                }
            }
            val held = ArrayList<Int>() // indexy znejúcich nôt
            var i = 0
            while (i < order.size) {
                val t = notes[order[i]].first
                val group = ArrayList<Int>()
                while (i < order.size && notes[order[i]].first - t < 0.035) {
                    group.add(order[i])
                    i++
                }
                held.removeAll { notes[it].second <= t + 0.01 }
                val heldL = held.filter { !result[it] }.map { notes[it].third }
                val heldR = held.filter { result[it] }.map { notes[it].third }
                val ps = group.sortedBy { notes[it].third }
                var bestK = 0
                var bestCost = Double.MAX_VALUE
                for (k in 0..ps.size) {
                    val newL = ps.subList(0, k).map { notes[it].third }
                    val newR = ps.subList(k, ps.size).map { notes[it].third }
                    var cost = newL.sumOf { kotlin.math.abs(it - cL) } + newR.sumOf { kotlin.math.abs(it - cR) }
                    val allL = newL + heldL
                    val allR = newR + heldR
                    if (allL.isNotEmpty()) {
                        val span = allL.max() - allL.min()
                        if (span > 14) cost += (span - 14) * 10.0
                        if (allL.size > 5) cost += (allL.size - 5) * 25.0
                    }
                    if (allR.isNotEmpty()) {
                        val span = allR.max() - allR.min()
                        if (span > 14) cost += (span - 14) * 10.0
                        if (allR.size > 5) cost += (allR.size - 5) * 25.0
                    }
                    if (allL.isNotEmpty() && allR.isNotEmpty() && allL.max() > allR.min()) cost += 30.0 // prekríženie
                    // odporúčanie zo stopy
                    for ((j, idx) in ps.withIndex()) {
                        val h = hint.getOrNull(idx) ?: continue
                        if (h != (j >= k)) cost += 12.0
                    }
                    if (cost < bestCost) {
                        bestCost = cost
                        bestK = k
                    }
                }
                val newL = ArrayList<Int>()
                val newR = ArrayList<Int>()
                for ((j, idx) in ps.withIndex()) {
                    val right = j >= bestK
                    result[idx] = right
                    if (right) newR.add(notes[idx].third) else newL.add(notes[idx].third)
                    held.add(idx)
                }
                if (newL.isNotEmpty()) cL = cL * 0.55 + newL.average() * 0.45
                if (newR.isNotEmpty()) cR = cR * 0.55 + newR.average() * 0.45
                // ruky sa nesmú „prekrížiť“ ani úplne splynúť
                if (cR < cL + 5) {
                    val mid = (cR + cL) / 2
                    cR = mid + 2.5
                    cL = mid - 2.5
                }
            }
            return result.toList()
        }

        /**
         * Z MIDI udalostí: spáruje note on / off (podľa kanála), určí ruky – pri viacerých kanáloch
         * (stopách) má vyššie položený kanál pravú ruku, pri jednom kanáli rozhoduje stredné C.
         */
        fun from(events: List<NoteEvent>): VisSong {
            // zdvojenia o oktávu (napodobnené organové registre) nie sú noty, ktoré sa hrajú
            val sorted = MidiTools.removeOctaveDoublings(events).sortedBy { it.time }
            val open = HashMap<Int, ArrayDeque<Pair<Double, Int>>>() // (kanál*128+nota) → začiatky
            data class Raw(val start: Double, val end: Double, val pitch: Int, val channel: Int)
            val raw = ArrayList<Raw>()
            for (e in sorted) {
                val key = MidiTools.voiceKey(e) * 128 + e.data1
                when (e.status) {
                    0x90 -> open.getOrPut(key) { ArrayDeque() }.addLast(e.time to e.data2)
                    0x80 -> open[key]?.removeFirstOrNull()?.let { (s, _) ->
                        raw.add(Raw(s, maxOf(e.time, s + 0.06), e.data1, MidiTools.voiceKey(e)))
                    }
                }
            }
            // noty bez konca (chýba note off) – krátky koniec
            for ((key, starts) in open) for ((s, _) in starts) raw.add(Raw(s, s + 0.4, key % 128, key / 128))
            if (raw.isEmpty()) return VisSong(emptyList(), 0.0, 48, 84, 0.0)

            // zdvojené tóny (ten istý tón naraz vo viacerých hlasoch / stopách) – jeden obdĺžnik
            raw.sortWith(compareBy({ it.start }, { it.pitch }))
            val merged = ArrayList<Raw>()
            val lastByPitch = HashMap<Int, Int>()
            for (r in raw) {
                val prev = lastByPitch[r.pitch]?.let { merged[it] }
                if (prev != null && r.start < prev.end - 0.02) {
                    if (r.end > prev.end) merged[lastByPitch[r.pitch]!!] = prev.copy(end = r.end)
                    continue
                }
                lastByPitch[r.pitch] = merged.size
                merged.add(r)
            }

            // pomôcka zo stôp: pri dvoch stopách s notami je vyššia pravdepodobne pravá ruka
            val channels = merged.map { it.channel }.distinct()
            val channelRight: Map<Int, Boolean> = if (channels.size == 2) {
                val avg = channels.associateWith { ch -> merged.filter { it.channel == ch }.map { it.pitch }.average() }
                val hi = avg.maxBy { it.value }.key
                if (kotlin.math.abs(avg.values.first() - avg.values.last()) >= 5) channels.associateWith { it == hi } else emptyMap()
            } else emptyMap()

            val hands = splitHands(merged.map { Triple(it.start, it.end, it.pitch) }, merged.map { channelRight[it.channel] })
            val notes = merged.mapIndexed { i, r -> VisNote(r.start, r.end, r.pitch, hands[i]) }.sortedBy { it.start }

            var low = notes.minOf { it.pitch }
            var high = notes.maxOf { it.pitch }
            // celé oktávy od C po H, aspoň dve oktávy
            low = (low / 12) * 12
            high = (high / 12) * 12 + 11
            while (high - low < 23) {
                if (low > 21) low -= 12 else high += 12
            }
            low = low.coerceAtLeast(12)
            high = high.coerceAtMost(119)
            val duration = notes.maxOf { it.end }
            val maxLen = notes.maxOf { abs(it.end - it.start) }
            return VisSong(notes, duration, low, high, maxLen)
        }
    }
}
