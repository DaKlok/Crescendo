package com.daklok.crescendo.data.midi

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.daklok.crescendo.CrescendoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Z MIDI nahrávky (.mid) vyrobí zvuk klavíra, ktorý sa dá prehrať v tablete.
 * Hotový zvuk je v filesDir/midi_render – nezálohuje sa (dá sa kedykoľvek vyrobiť znova z .mid).
 */
/**
 * Ako vyrobiť zvuk: [hint] = skupina nástrojov z katalógu (guitar, strings, organ…) – použije sa,
 * keď MIDI nemá nastavené nástroje; prázdny = vlastná nahrávka z klavíra. [maxSeconds] > 0 = len začiatok (ukážka).
 */
data class RenderOptions(val hint: String = "", val maxSeconds: Double = 0.0) {
    val suffix: String get() = (if (hint.isNotBlank()) "_${hint}3" else "") + (if (maxSeconds > 0) "_preview" else "")
}

object MidiRenderer {
    private const val SAMPLE_RATE = 44_100
    private const val CHANNELS = 2

    /** Práve vyrábané nahrávky: cesta k .mid → priebeh 0..1. */
    private val _rendering = MutableStateFlow<Map<String, Float>>(emptyMap())
    val rendering: StateFlow<Map<String, Float>> = _rendering.asStateFlow()

    /** Nahrávky, pri ktorých výroba zvuku zlyhala. */
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private val lock = Mutex()
    private var engineRef: SoftReference<PianoEngine>? = null

    fun isMidi(f: File) = f.extension.equals("mid", ignoreCase = true)

    private fun dir(context: Context) = File(context.filesDir, "midi_render").apply { mkdirs() }

    private fun base(mid: File, opt: RenderOptions = RenderOptions()) =
        "${mid.parentFile?.name}_${mid.nameWithoutExtension}" + opt.suffix

    /** Súbor na prehranie: pri MIDI hotový zvuk (null, kým ešte nie je), inak samotný súbor. */
    fun playable(context: Context, f: File, opt: RenderOptions = RenderOptions()): File? {
        if (!isMidi(f)) return f
        val d = dir(context)
        return listOf(File(d, base(f, opt) + ".m4a"), File(d, base(f, opt) + ".wav")).firstOrNull { it.exists() && it.length() > 0 }
    }

    /** Zmaže všetky vyrobené zvuky k MIDI súboru (aj organ / ukážku). */
    fun delete(context: Context, mid: File) {
        val b = base(mid)
        dir(context).listFiles()?.filter { it.name.startsWith("$b.") || it.name.startsWith(b + "_") }?.forEach { it.delete() }
    }

    fun deleteForPiece(context: Context, pieceId: Long) {
        dir(context).listFiles()?.filter { it.name.startsWith("${pieceId}_") }?.forEach { it.delete() }
    }

    /** Spustí výrobu zvuku na pozadí (beží ďalej aj po zatvorení panela). */
    fun request(context: Context, mid: File) {
        val app = context.applicationContext as CrescendoApp
        val key = mid.path
        if (key in _rendering.value || playable(app, mid) != null) return
        _rendering.update { it + (key to 0f) }
        _failed.update { it - key }
        app.appScope.launch(Dispatchers.Default) {
            val ok = runCatching { lock.withLock { render(app, mid, RenderOptions()) {} } }.getOrDefault(false)
            if (!ok) _failed.update { it + key }
            _rendering.update { it - key }
        }
    }

    /**
     * Vyrobí zvuk hneď (čaká na výsledok) – napr. ukážka v katalógu alebo referenčná nahrávka.
     * [onProgress] dostáva priebeh 0..1. Vráti súbor na prehranie alebo null.
     */
    suspend fun renderNow(
        context: Context,
        mid: File,
        opt: RenderOptions = RenderOptions(),
        onProgress: (Float) -> Unit = {},
    ): File? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        playable(context, mid, opt) ?: run {
            val key = mid.path + opt.suffix
            _rendering.update { it + (key to 0f) }
            try {
                val ok = try {
                    lock.withLock { render(context.applicationContext, mid, opt, onProgress) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                if (ok) playable(context, mid, opt) else null
            } finally {
                _rendering.update { it - key }
            }
        }
    }

    private fun render(context: Context, mid: File, opt: RenderOptions, onProgress: (Float) -> Unit): Boolean {
        if (!mid.exists()) return false
        // kým sme čakali na zámok, mohol ten istý zvuk vyrobiť niekto iný (vizualizér aj nahrávky naraz)
        if (playable(context, mid, opt) != null) return true
        var events = MidiFile.read(mid)
        if (events.isEmpty()) return false
        if (opt.maxSeconds > 0) events = cut(events, opt.maxSeconds)
        val key = mid.path + opt.suffix
        val report: (Float) -> Unit = { p ->
            _rendering.update { m -> if (key in m) m + (key to p) else m }
            onProgress(p)
        }

        // organová / klavírna skladba z katalógu: celá znie ako organ / klavír – MIDI z Mutopie často
        // napodobňuje organové registre flautami a zdvojením o oktávu, to vynecháme (organ má registre vlastné)
        val forced = when (opt.hint) {
            "organ" -> 19
            "piano" -> 0
            else -> null
        }
        if (forced != null) {
            if (forced == 19) events = MidiTools.removeOctaveDoublings(events)
            events = (0 until 16).map { NoteEvent(0.0, 0xC0, forced, 0, it) } + events.filter { it.status != 0xC0 }
        } else {
            // MIDI bez nastavených nástrojov (všade klavír) → nástroj podľa katalógu (gitara, husle…)
            val programs = events.filter { it.status == 0xC0 }.map { it.data1 }.toSet()
            val fallback = InstrumentMap.fallbackProgram(opt.hint)
            if (fallback != null && programs.all { it in 0..7 }) {
                events = (0 until 16).map { NoteEvent(0.0, 0xC0, fallback, 0, it) } + events.filter { it.status != 0xC0 }
            }
        }

        // ktoré nástroje skladba potrebuje
        val needed = LinkedHashSet<String>()
        run {
            val prog = IntArray(16)
            for (e in events.sortedBy { it.time }) {
                if (e.status == 0xC0) prog[e.channel and 15] = e.data1
                else if (e.status == 0x90) InstrumentMap.forProgram(prog[e.channel and 15], e.data1)?.let { needed.add(it) }
            }
        }
        val toDownload = needed.filter { InstrumentLibrary.isKnown(it) && !InstrumentLibrary.isReady(context, it) }
        val downloadShare = if (toDownload.isEmpty()) 0f else 0.25f
        toDownload.forEachIndexed { i, name ->
            InstrumentLibrary.ensure(context, name) { p -> report(downloadShare * (i + p) / toDownload.size) }
        }

        val piano = engine(context) ?: return false
        val synths = HashMap<String, BlockSynth>()
        var pianoSynth: BlockSynth? = null
        fun pianoFallback() = pianoSynth ?: piano.synth().also { pianoSynth = it }
        val router: (Int, Int) -> BlockSynth? = { program, note ->
            InstrumentMap.forProgram(program, note)?.let { name ->
                synths.getOrPut(name) {
                    when (name) {
                        "piano" -> pianoFallback()
                        "organ" -> OrganEngine(SAMPLE_RATE).synth()
                        // nástroj sa nepodarilo stiahnuť (napr. bez internetu) → klavír
                        else -> InstrumentLibrary.samples(context, name)?.let { Sampler(it, InstrumentMap.spec(name), SAMPLE_RATE) }
                            ?: pianoFallback()
                    }
                }
            }
        }
        val engine = object : MidiSynth {
            override fun render(
                events: List<NoteEvent>,
                sink: (ShortArray, Int) -> Unit,
                progress: (Float) -> Unit,
                cancelled: () -> Boolean,
            ) = MidiMixer.render(events, router, sink, progress, cancelled, SAMPLE_RATE)
        }
        val progress: (Float) -> Unit = { p -> report(downloadShare + (1 - downloadShare) * p) }
        val d = dir(context)
        // najprv AAC (malý súbor), ak by kodek na zariadení zlyhal, WAV
        val targets = listOf(File(d, base(mid, opt) + ".m4a") to true, File(d, base(mid, opt) + ".wav") to false)
        for ((target, aac) in targets) {
            val tmp = File(target.path + ".tmp")
            val ok = runCatching {
                (if (aac) AacWriter(tmp, SAMPLE_RATE, CHANNELS) else WavWriter(tmp, SAMPLE_RATE, CHANNELS)).use { w ->
                    engine.render(events, { pcm, frames -> w.write(pcm, frames) }, progress, { !mid.exists() })
                    w.finish()
                }
                mid.exists() && tmp.length() > 0 && tmp.renameTo(target)
            }.getOrDefault(false)
            if (ok) return true
            tmp.delete()
            if (!mid.exists()) return false
        }
        return false
    }

    /** Len prvých [seconds] sekúnd (ukážka) – držané tóny a pedál sa na konci pustia. */
    private fun cut(events: List<NoteEvent>, seconds: Double): List<NoteEvent> {
        val kept = events.filter { it.time < seconds }
        val held = HashSet<Pair<Int, Int>>() // (kanál, nota)
        for (e in kept) {
            if (e.status == 0x90) held.add(e.channel to e.data1) else if (e.status == 0x80) held.remove(e.channel to e.data1)
        }
        val channels = kept.map { it.channel }.toSet()
        return kept + held.map { (ch, n) -> NoteEvent(seconds, 0x80, n, 64, ch) } +
            channels.map { NoteEvent(seconds, 0xB0, 64, 0, it) }
    }

    private fun engine(context: Context): PianoEngine? {
        engineRef?.get()?.let { return it }
        val names = context.assets.list("piano").orEmpty().filter { it.endsWith(".ogg") }
        val samples = names.mapNotNull { name ->
            val note = name.substringBefore('.').toIntOrNull() ?: return@mapNotNull null
            decodeAsset(context, "piano/$name")?.let { PianoSample(note, it) }
        }
        if (samples.isEmpty()) return null
        return PianoEngine(samples, SAMPLE_RATE).also { engineRef = SoftReference(it) }
    }

    /** Dekóduje vzorku (OGG) z aplikácie do 16-bit mono PCM. */
    internal fun decodeAsset(context: Context, asset: String): ShortArray? {
        var tmpFile: File? = null
        try {
            return decode { extractor ->
                runCatching {
                    context.assets.openFd(asset).use { afd -> extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                }.onFailure {
                    // skomprimovaný asset – skopírujeme ho do dočasného súboru
                    val f = File(context.cacheDir, "piano_" + asset.substringAfterLast('/'))
                    context.assets.open(asset).use { input -> f.outputStream().use { input.copyTo(it) } }
                    tmpFile = f
                    extractor.setDataSource(f.path)
                }
            }
        } finally {
            tmpFile?.delete()
        }
    }

    /** Dekóduje zvukový súbor (OGG / MP3) do 16-bit mono PCM; ticho na začiatku odstráni. */
    internal fun decodeFile(file: File): ShortArray? = decode { it.setDataSource(file.path) }?.let { trimStart(it) }

    /** Odstráni ticho na začiatku (MP3 má na začiatku pár desiatok ms prázdna). */
    private fun trimStart(pcm: ShortArray): ShortArray {
        val threshold = 180 // ~ −45 dB
        val first = pcm.indexOfFirst { abs(it.toInt()) > threshold }
        if (first <= 0) return pcm
        val start = (first - 64).coerceAtLeast(0)
        return pcm.copyOfRange(start, pcm.size)
    }

    private fun decode(setSource: (MediaExtractor) -> Unit): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            setSource(extractor)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            var out = ShortArray(SAMPLE_RATE * 4)
            var size = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var guard = 0
            while (guard++ < 200_000) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                } else if (o >= 0) {
                    val buf = c.getOutputBuffer(o)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val sb = buf.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                    val frames = sb.remaining() / channels
                    if (size + frames > out.size) out = out.copyOf(maxOf(out.size * 2, size + frames))
                    for (f in 0 until frames) {
                        var sum = 0
                        for (ch in 0 until channels) sum += sb.get(f * channels + ch)
                        out[size++] = (sum / channels).toShort()
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return if (size > 0) out.copyOf(size) else null
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

private interface PcmWriter : Closeable {
    fun write(pcm: ShortArray, frames: Int)
    fun finish()
}

/** Zápis do .m4a (AAC) cez MediaCodec + MediaMuxer. */
private class AacWriter(file: File, private val sampleRate: Int, private val channels: Int) : PcmWriter {
    private val codec: MediaCodec
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var started = false
    private var frames = 0L

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            muxer.release()
            throw e
        }
    }

    private fun pts() = frames * 1_000_000L / sampleRate

    override fun write(pcm: ShortArray, frames: Int) {
        val total = frames * channels
        var off = 0
        var guard = 0
        while (off < total) {
            check(guard++ < 100_000) { "encoder stalled" }
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) {
                val buf = codec.getInputBuffer(i)!!
                buf.clear()
                val max = (buf.capacity() / 2).let { it - it % channels }
                val n = minOf(max, total - off)
                buf.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, off, n)
                codec.queueInputBuffer(i, 0, n * 2, pts(), 0)
                this.frames += n / channels
                off += n
            }
            drain(false)
        }
    }

    override fun finish() {
        var guard = 0
        while (guard++ < 1000) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) {
                codec.queueInputBuffer(i, 0, 0, pts(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain(false)
        }
        drain(true)
    }

    private fun drain(endOfStream: Boolean) {
        var idle = 0
        while (true) {
            val o = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || idle++ > 300) return
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                o >= 0 -> {
                    val buf: ByteBuffer = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}

/** Záložný zápis do .wav (bez kompresie). */
private class WavWriter(file: File, private val sampleRate: Int, private val channels: Int) : PcmWriter {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0) }
    private var dataBytes = 0L
    private val bytes = ByteBuffer.allocate(4096 * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)

    init {
        raf.write(ByteArray(44))
    }

    override fun write(pcm: ShortArray, frames: Int) {
        var off = 0
        val total = frames * channels
        while (off < total) {
            bytes.clear()
            val n = minOf(bytes.capacity() / 2, total - off)
            for (i in 0 until n) bytes.putShort(pcm[off + i])
            raf.write(bytes.array(), 0, n * 2)
            dataBytes += n * 2
            off += n
        }
    }

    override fun finish() {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
        h.putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        h.put("data".toByteArray()).putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(h.array())
    }

    override fun close() {
        raf.close()
    }
}
