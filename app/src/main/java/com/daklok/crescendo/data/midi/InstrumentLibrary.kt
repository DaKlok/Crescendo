package com.daklok.crescendo.data.midi

import android.content.Context
import java.io.File
import java.lang.ref.SoftReference
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nahraté tóny ďalších nástrojov (gitara, husle, flauta…) – sťahujú sa až vtedy, keď ich treba,
 * a ostanú uložené v filesDir/instruments (nezálohujú sa – dajú sa kedykoľvek stiahnuť znova).
 *
 * Zdroj: tonejs-instruments (Nicholaus Brosowsky, CC BY 3.0). Upravené balíky (mono OGG) sú na webe
 * aplikácie; ak by tam neboli, stiahnu sa pôvodné MP3 priamo z projektu.
 */
object InstrumentLibrary {
    private const val PACKS_URL = "https://daklok.github.io/Crescendo/instruments"
    private const val FALLBACK_URL = "https://cdn.jsdelivr.net/gh/nbrosowsky/tonejs-instruments@master/samples"
    private const val FALLBACK_URL_2 = "https://raw.githubusercontent.com/nbrosowsky/tonejs-instruments/master/samples"

    /** Nástroj → (MIDI nota, pôvodný názov súboru). */
    private val MANIFEST: Map<String, List<Pair<Int, String>>> = mapOf(
        "bass-electric" to listOf(25 to "Cs1", 28 to "E1", 31 to "G1", 34 to "As1", 37 to "Cs2", 40 to "E2", 43 to "G2", 46 to "As2", 49 to "Cs3", 52 to "E3", 55 to "G3", 58 to "As3", 61 to "Cs4", 64 to "E4", 67 to "G4", 70 to "As4", 73 to "Cs5"),
        "bassoon" to listOf(43 to "G2", 45 to "A2", 48 to "C3", 55 to "G3", 57 to "A3", 60 to "C4", 64 to "E4", 67 to "G4", 69 to "A4", 72 to "C5"),
        "cello" to listOf(36 to "C2", 38 to "D2", 39 to "Ds2", 40 to "E2", 41 to "F2", 43 to "G2", 44 to "Gs2", 45 to "A2", 46 to "As2", 47 to "B2", 48 to "C3", 49 to "Cs3", 50 to "D3", 51 to "Ds3", 52 to "E3", 53 to "F3", 54 to "Fs3", 55 to "G3", 56 to "Gs3", 57 to "A3", 58 to "As3", 59 to "B3", 60 to "C4", 61 to "Cs4", 62 to "D4", 63 to "Ds4", 64 to "E4", 65 to "F4", 66 to "Fs4", 67 to "G4", 68 to "Gs4", 69 to "A4", 71 to "B4", 72 to "C5"),
        "clarinet" to listOf(50 to "D3", 53 to "F3", 58 to "As3", 62 to "D4", 65 to "F4", 70 to "As4", 74 to "D5", 77 to "F5", 82 to "As5", 86 to "D6", 90 to "Fs6"),
        "contrabass" to listOf(30 to "Fs1", 31 to "G1", 34 to "As1", 36 to "C2", 38 to "D2", 40 to "E2", 42 to "Fs2", 44 to "Gs2", 45 to "A2", 49 to "Cs3", 52 to "E3", 56 to "Gs3", 59 to "B3"),
        "flute" to listOf(60 to "C4", 64 to "E4", 69 to "A4", 72 to "C5", 76 to "E5", 81 to "A5", 84 to "C6", 88 to "E6", 93 to "A6", 96 to "C7"),
        "french-horn" to listOf(33 to "A1", 36 to "C2", 39 to "Ds2", 43 to "G2", 50 to "D3", 53 to "F3", 57 to "A3", 60 to "C4", 74 to "D5", 77 to "F5"),
        "guitar-acoustic" to listOf(38 to "D2", 39 to "Ds2", 40 to "E2", 41 to "F2", 42 to "Fs2", 43 to "G2", 44 to "Gs2", 45 to "A2", 46 to "As2", 47 to "B2", 48 to "C3", 49 to "Cs3", 50 to "D3", 51 to "Ds3", 52 to "E3", 53 to "F3", 54 to "Fs3", 55 to "G3", 56 to "Gs3", 57 to "A3", 58 to "As3", 59 to "B3", 60 to "C4", 61 to "Cs4", 62 to "D4", 63 to "Ds4", 64 to "E4", 65 to "F4", 66 to "Fs4", 67 to "G4", 68 to "Gs4", 69 to "A4", 70 to "As4", 71 to "B4", 72 to "C5", 73 to "Cs5", 74 to "D5"),
        "guitar-electric" to listOf(37 to "Cs2", 40 to "E2", 42 to "Fs2", 45 to "A2", 48 to "C3", 51 to "Ds3", 54 to "Fs3", 57 to "A3", 60 to "C4", 63 to "Ds4", 66 to "Fs4", 69 to "A4", 72 to "C5", 75 to "Ds5", 78 to "Fs5", 81 to "A5", 84 to "C6"),
        "guitar-nylon" to listOf(35 to "B1", 38 to "D2", 40 to "E2", 42 to "Fs2", 44 to "Gs2", 45 to "A2", 47 to "B2", 49 to "Cs3", 50 to "D3", 52 to "E3", 54 to "Fs3", 55 to "G3", 57 to "A3", 59 to "B3", 61 to "Cs4", 63 to "Ds4", 64 to "E4", 66 to "Fs4", 68 to "Gs4", 69 to "A4", 71 to "B4", 73 to "Cs5", 74 to "D5", 76 to "E5", 78 to "Fs5", 79 to "G5", 80 to "Gs5", 81 to "A5", 82 to "As5"),
        "harmonium" to listOf(36 to "C2", 37 to "Cs2", 38 to "D2", 39 to "Ds2", 40 to "E2", 41 to "F2", 42 to "Fs2", 43 to "G2", 44 to "Gs2", 45 to "A2", 46 to "As2", 47 to "B2", 48 to "C3", 49 to "Cs3", 50 to "D3", 51 to "Ds3", 52 to "E3", 53 to "F3", 54 to "Fs3", 55 to "G3", 56 to "Gs3", 57 to "A3", 58 to "As3", 59 to "B3", 60 to "C4", 61 to "Cs4", 62 to "D4", 63 to "Ds4", 64 to "E4", 65 to "F4", 67 to "G4", 68 to "Gs4", 69 to "A4", 70 to "As4", 71 to "B4", 72 to "C5", 73 to "Cs5", 74 to "D5"),
        "harp" to listOf(28 to "E1", 31 to "G1", 35 to "B1", 38 to "D2", 41 to "F2", 45 to "A2", 48 to "C3", 52 to "E3", 55 to "G3", 59 to "B3", 62 to "D4", 65 to "F4", 69 to "A4", 72 to "C5", 76 to "E5", 79 to "G5", 83 to "B5", 86 to "D6", 89 to "F6", 93 to "A6", 95 to "B6", 98 to "D7", 101 to "F7"),
        "saxophone" to listOf(49 to "Cs3", 50 to "D3", 51 to "Ds3", 52 to "E3", 53 to "F3", 54 to "Fs3", 55 to "G3", 56 to "Gs3", 58 to "As3", 59 to "B3", 60 to "C4", 61 to "Cs4", 62 to "D4", 63 to "Ds4", 64 to "E4", 65 to "F4", 66 to "Fs4", 67 to "G4", 68 to "Gs4", 69 to "A4", 70 to "As4", 71 to "B4", 72 to "C5", 73 to "Cs5", 74 to "D5", 75 to "Ds5", 76 to "E5", 77 to "F5", 78 to "Fs5", 79 to "G5", 80 to "Gs5", 81 to "A5"),
        "trombone" to listOf(34 to "As1", 37 to "Cs2", 39 to "Ds2", 41 to "F2", 44 to "Gs2", 46 to "As2", 48 to "C3", 50 to "D3", 51 to "Ds3", 53 to "F3", 56 to "Gs3", 58 to "As3", 60 to "C4", 61 to "Cs4", 62 to "D4", 63 to "Ds4", 65 to "F4"),
        "trumpet" to listOf(57 to "A3", 60 to "C4", 63 to "Ds4", 65 to "F4", 67 to "G4", 70 to "As4", 74 to "D5", 77 to "F5", 81 to "A5", 84 to "C6"),
        "tuba" to listOf(29 to "F1", 34 to "As1", 39 to "Ds2", 41 to "F2", 46 to "As2", 50 to "D3", 53 to "F3", 58 to "As3", 62 to "D4"),
        "violin" to listOf(55 to "G3", 57 to "A3", 60 to "C4", 64 to "E4", 67 to "G4", 69 to "A4", 72 to "C5", 76 to "E5", 79 to "G5", 81 to "A5", 84 to "C6", 88 to "E6", 91 to "G6", 93 to "A6", 96 to "C7"),
        "xylophone" to listOf(67 to "G4", 72 to "C5", 79 to "G5", 84 to "C6", 91 to "G6", 96 to "C7", 103 to "G7", 108 to "C8"),
    )

    private val cache = HashMap<String, SoftReference<List<PianoSample>>>()

    fun isKnown(name: String) = name in MANIFEST

    private fun dir(context: Context, name: String) = File(File(context.filesDir, "instruments"), name)

    /** Najpoužívanejšie nástroje sú pribalené priamo v aplikácii (assets/instruments) – netreba ich sťahovať. */
    private fun bundled(context: Context, name: String): Boolean =
        bundledCache.getOrPut(name) { runCatching { context.assets.list("instruments/$name").orEmpty().isNotEmpty() }.getOrDefault(false) }

    private val bundledCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Funguje web aplikácie s balíkmi? (null = ešte nevieme) – aby sa neskúšal pri každom tóne znova. */
    @Volatile
    private var packsOnline: Boolean? = null

    /** Je nástroj už stiahnutý (alebo pribalený)? */
    fun isReady(context: Context, name: String): Boolean {
        if (bundled(context, name)) return true
        val notes = MANIFEST[name] ?: return false
        val d = dir(context, name)
        return notes.all { (midi, _) -> File(d, "$midi.ogg").length() > 0 || File(d, "$midi.mp3").length() > 0 }
    }

    /**
     * Stiahne chýbajúce tóny nástroja ([onProgress] 0..1). Vráti false, ak sa nepodarilo
     * (napr. bez internetu) – vtedy sa skladba zahrá náhradným nástrojom.
     */
    fun ensure(context: Context, name: String, onProgress: (Float) -> Unit = {}): Boolean {
        if (bundled(context, name)) return true
        val notes = MANIFEST[name] ?: return false
        val d = dir(context, name).apply { mkdirs() }
        val missing = notes.filter { (midi, _) -> File(d, "$midi.ogg").length() == 0L && File(d, "$midi.mp3").length() == 0L }
        if (missing.isEmpty()) return true
        // sťahuje sa naraz viac tónov (oveľa rýchlejšie ako po jednom)
        val done = java.util.concurrent.atomic.AtomicInteger(notes.size - missing.size)
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        try {
            val tasks = missing.map { (midi, source) ->
                pool.submit {
                    if (failed.get()) return@submit
                    val ok = fetchNote(name, midi, source, d)
                    if (!ok) failed.set(true)
                    onProgress(done.incrementAndGet().toFloat() / notes.size)
                }
            }
            tasks.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdownNow()
        }
        return !failed.get()
    }

    private fun fetchNote(name: String, midi: Int, source: String, d: File): Boolean {
        val ogg = File(d, "$midi.ogg")
        val mp3 = File(d, "$midi.mp3")
        if (packsOnline != false) {
            val ok = fetch("$PACKS_URL/$name/$midi.ogg", ogg)
            if (packsOnline == null) packsOnline = ok
            if (ok) return true
        }
        return fetch("$FALLBACK_URL/$name/$source.mp3", mp3) || fetch("$FALLBACK_URL_2/$name/$source.mp3", mp3)
    }

    /** Dekódované tóny nástroja (alebo null, ak ešte nie je stiahnutý). */
    fun samples(context: Context, name: String): List<PianoSample>? {
        cache[name]?.get()?.let { return it }
        val notes = MANIFEST[name] ?: return null
        if (bundled(context, name)) {
            val list = notes.mapNotNull { (midi, _) ->
                MidiRenderer.decodeAsset(context, "instruments/$name/$midi.ogg")?.takeIf { it.isNotEmpty() }?.let { PianoSample(midi, it) }
            }
            if (list.isNotEmpty()) {
                cache[name] = SoftReference(list)
                return list
            }
        }
        val d = dir(context, name)
        val list = notes.mapNotNull { (midi, _) ->
            val f = File(d, "$midi.ogg").takeIf { it.length() > 0 } ?: File(d, "$midi.mp3").takeIf { it.length() > 0 }
            f?.let { MidiRenderer.decodeFile(it) }?.takeIf { it.isNotEmpty() }?.let { PianoSample(midi, it) }
        }
        if (list.isEmpty()) return null
        cache[name] = SoftReference(list)
        return list
    }

    private fun fetch(url: String, target: File): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode !in 200..299) return@runCatching false
            val tmp = File(target.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.length() > 0 && tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
