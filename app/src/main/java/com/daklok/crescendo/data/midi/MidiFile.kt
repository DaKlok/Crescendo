package com.daklok.crescendo.data.midi

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Jedna MIDI udalosť v čase (sekundy od začiatku).
 * [status] je bez kanála: 0x90 note on, 0x80 note off, 0xB0 control change, 0xC0 zmena nástroja (program v [data1]).
 * [channel] 0–15 (pri súboroch s viacerými nástrojmi – každý hlas má svoj kanál).
 */
data class NoteEvent(val time: Double, val status: Int, val data1: Int, val data2: Int, val channel: Int = 0, val track: Int = 0)

/** Zápis a čítanie štandardných MIDI súborov (.mid). */
object MidiFile {
    /** 500 tikov na štvrťovú notu pri tempe 120 BPM → 1 tik = 1 ms. */
    private const val DIVISION = 500
    private const val TEMPO_US = 500_000

    /** Zapíše udalosti ako MIDI súbor (formát 0, jeden kanál). */
    fun write(file: File, events: List<NoteEvent>) {
        val track = ByteArrayOutputStream()
        fun vlq(value: Long) {
            var v = value.coerceAtLeast(0)
            val bytes = ArrayList<Int>()
            bytes.add((v and 0x7F).toInt())
            v = v shr 7
            while (v > 0) {
                bytes.add(((v and 0x7F) or 0x80).toInt())
                v = v shr 7
            }
            for (i in bytes.indices.reversed()) track.write(bytes[i])
        }
        // tempo
        vlq(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20))
        // zvuk: akustický klavír
        vlq(0)
        track.write(0xC0)
        track.write(0)
        var lastTick = 0L
        for (e in events.sortedBy { it.time }) {
            val tick = Math.round(e.time * 1000.0).coerceAtLeast(lastTick)
            vlq(tick - lastTick)
            lastTick = tick
            track.write((e.status and 0xF0) or (e.channel and 0x0F))
            track.write(e.data1 and 0x7F)
            if (e.status and 0xF0 != 0xC0) track.write(e.data2 and 0x7F)
        }
        vlq(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val body = track.toByteArray()
        val out = ByteArrayOutputStream()
        fun int32(v: Int) = out.write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
        fun int16(v: Int) = out.write(byteArrayOf((v ushr 8).toByte(), v.toByte()))
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        int32(6)
        int16(0)
        int16(1)
        int16(DIVISION)
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        int32(body.size)
        out.write(body)
        file.writeBytes(out.toByteArray())
    }

    /**
     * Prečíta MIDI súbor (formát 0 aj 1, zmeny tempa, running status).
     * Vráti noty a pedále zoradené podľa času; note on s nulovou silou je note off.
     */
    fun read(file: File): List<NoteEvent> = parse(file.readBytes())

    fun parse(data: ByteArray): List<NoteEvent> {
        var p = 0
        fun u8(): Int = data[p++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Int = (u16() shl 16) or u16()
        fun tag(): String = String(data, p, 4, Charsets.US_ASCII).also { p += 4 }

        require(data.size >= 14 && tag() == "MThd") { "Not a MIDI file" }
        val headerLen = u32()
        u16() // formát
        val tracks = u16()
        val division = u16()
        p += headerLen - 6
        require(division and 0x8000 == 0) { "SMPTE timing not supported" }

        // (tik, poradie, udalosť) – tempo sa prepočíta až po zlúčení stôp
        class Raw(val tick: Long, val order: Int, val status: Int, val d1: Int, val d2: Int, val tempo: Int = 0, val ch: Int = 0, val tr: Int = 0)
        val raws = ArrayList<Raw>()
        var order = 0
        repeat(tracks) { trackIndex ->
            if (p + 8 > data.size) return@repeat
            val id = tag()
            val len = u32()
            val end = (p + len).coerceAtMost(data.size)
            if (id != "MTrk") {
                p = end
                return@repeat
            }
            var tick = 0L
            var running = 0
            while (p < end) {
                var delta = 0L
                while (true) {
                    val b = u8()
                    delta = (delta shl 7) or (b and 0x7F).toLong()
                    if (b and 0x80 == 0) break
                }
                tick += delta
                var status = data[p].toInt() and 0xFF
                if (status < 0x80) status = running else p++
                when {
                    status == 0xFF -> {
                        val type = u8()
                        var l = 0
                        while (true) {
                            val b = u8()
                            l = (l shl 7) or (b and 0x7F)
                            if (b and 0x80 == 0) break
                        }
                        if (type == 0x51 && l == 3) {
                            val t = (u8() shl 16) or (u8() shl 8) or u8()
                            raws.add(Raw(tick, order++, 0xFF, 0, 0, t))
                        } else {
                            p += l
                        }
                        if (type == 0x2F) p = end
                    }
                    status == 0xF0 || status == 0xF7 -> {
                        var l = 0
                        while (true) {
                            val b = u8()
                            l = (l shl 7) or (b and 0x7F)
                            if (b and 0x80 == 0) break
                        }
                        p += l
                    }
                    else -> {
                        running = status
                        val type = status and 0xF0
                        val drums = status and 0x0F == 9 // kanál 10 = bicie – klavírom nehráme
                        val d1 = u8()
                        val d2 = if (type == 0xC0 || type == 0xD0) 0 else u8()
                        val ch = status and 0x0F
                        if (!drums) when (type) {
                            0x90 -> raws.add(Raw(tick, order++, if (d2 == 0) 0x80 else 0x90, d1, d2, ch = ch, tr = trackIndex))
                            0x80 -> raws.add(Raw(tick, order++, 0x80, d1, d2, ch = ch, tr = trackIndex))
                            0xB0 -> raws.add(Raw(tick, order++, 0xB0, d1, d2, ch = ch, tr = trackIndex))
                            0xC0 -> raws.add(Raw(tick, order++, 0xC0, d1, 0, ch = ch, tr = trackIndex))
                        }
                    }
                }
            }
            p = end
        }

        raws.sortWith(compareBy<Raw> { it.tick }.thenBy { it.order })
        val out = ArrayList<NoteEvent>(raws.size)
        var tempo = TEMPO_US.toDouble()
        var lastTick = 0L
        var seconds = 0.0
        for (r in raws) {
            seconds += (r.tick - lastTick) * tempo / 1_000_000.0 / division
            lastTick = r.tick
            if (r.status == 0xFF) tempo = r.tempo.toDouble() else out.add(NoteEvent(seconds, r.status, r.d1, r.d2, r.ch, r.tr))
        }
        return out
    }
}

/**
 * Zachytáva MIDI správy z klavíra počas nahrávania (aj s „running status“).
 * Časy sú v nanosekundách (System.nanoTime), ako ich dáva Android MIDI API.
 */
class MidiCapture(private val startNanos: Long) {
    private val events = ArrayList<NoteEvent>()
    private var running = 0
    private val held = BooleanArray(128)
    private val pedals = IntArray(128)

    /** Počet zahraných nôt (na zobrazenie počas nahrávania). */
    @Volatile
    var notes = 0
        private set

    @Synchronized
    fun onBytes(msg: ByteArray, offset: Int, count: Int, timestampNanos: Long) {
        val t = ((if (timestampNanos > 0) timestampNanos else System.nanoTime()) - startNanos) / 1e9
        if (t < 0) return
        var i = offset
        val end = offset + count
        while (i < end) {
            var status = msg[i].toInt() and 0xFF
            if (status >= 0xF8) { // real-time (clock, active sensing…)
                i++
                continue
            }
            if (status == 0xF0) {
                while (i < end && (msg[i].toInt() and 0xFF) != 0xF7) i++
                i++
                running = 0
                continue
            }
            if (status >= 0xF0) { // iné systémové správy
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
            when {
                type == 0x90 && d2 > 0 -> {
                    events.add(NoteEvent(t, 0x90, d1, d2))
                    held[d1] = true
                    notes++
                }
                type == 0x80 || type == 0x90 -> {
                    events.add(NoteEvent(t, 0x80, d1, 64))
                    held[d1] = false
                }
                type == 0xB0 && (d1 == 64 || d1 == 66 || d1 == 67) -> {
                    events.add(NoteEvent(t, 0xB0, d1, d2))
                    pedals[d1] = d2
                }
            }
        }
    }

    /**
     * Ukončí nahrávanie: pustí držané noty a pedále a posunie začiatok tesne pred prvú notu.
     * Vráti null, ak sa nič nezahralo.
     */
    @Synchronized
    fun finish(stopNanos: Long): List<NoteEvent>? {
        if (events.none { it.status == 0x90 }) return null
        val stopT = (stopNanos - startNanos) / 1e9
        val list = ArrayList(events)
        for (n in 0 until 128) if (held[n]) list.add(NoteEvent(stopT, 0x80, n, 64))
        for (cc in intArrayOf(64, 66, 67)) if (pedals[cc] > 0) list.add(NoteEvent(stopT, 0xB0, cc, 0))
        list.sortBy { it.time }
        // ticho na začiatku skrátime na pol sekundy (pedál stlačený pred prvou notou ostane)
        val firstNote = list.first { it.status == 0x90 }.time
        val shift = (firstNote - 0.5).coerceAtLeast(0.0)
        return list.map { it.copy(time = (it.time - shift).coerceAtLeast(0.0)) }
    }
}
