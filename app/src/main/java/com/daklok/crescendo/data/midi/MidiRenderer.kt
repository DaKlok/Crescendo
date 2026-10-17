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

/**
 * Z MIDI nahrávky (.mid) vyrobí zvuk klavíra, ktorý sa dá prehrať v tablete.
 * Hotový zvuk je v filesDir/midi_render – nezálohuje sa (dá sa kedykoľvek vyrobiť znova z .mid).
 */
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

    private fun base(mid: File) = "${mid.parentFile?.name}_${mid.nameWithoutExtension}"

    /** Súbor na prehranie: pri MIDI hotový zvuk (null, kým ešte nie je), inak samotný súbor. */
    fun playable(context: Context, f: File): File? {
        if (!isMidi(f)) return f
        val d = dir(context)
        return listOf(File(d, base(f) + ".m4a"), File(d, base(f) + ".wav")).firstOrNull { it.exists() && it.length() > 0 }
    }

    fun delete(context: Context, mid: File) {
        val d = dir(context)
        File(d, base(mid) + ".m4a").delete()
        File(d, base(mid) + ".wav").delete()
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
            val ok = runCatching { lock.withLock { render(app, mid) } }.getOrDefault(false)
            if (!ok) _failed.update { it + key }
            _rendering.update { it - key }
        }
    }

    /**
     * Vyrobí zvuk hneď (čaká na výsledok) – napr. ukážka v katalógu. Vráti súbor na prehranie alebo null.
     */
    suspend fun renderNow(context: Context, mid: File): File? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        playable(context, mid) ?: run {
            val key = mid.path
            _rendering.update { it + (key to 0f) }
            try {
                val ok = runCatching { lock.withLock { render(context.applicationContext, mid) } }.getOrDefault(false)
                if (ok) playable(context, mid) else null
            } finally {
                _rendering.update { it - key }
            }
        }
    }

    private fun render(context: Context, mid: File): Boolean {
        if (!mid.exists()) return false
        val events = MidiFile.read(mid)
        if (events.isEmpty()) return false
        val engine = engine(context) ?: return false
        val key = mid.path
        val progress: (Float) -> Unit = { p -> _rendering.update { m -> if (key in m) m + (key to p) else m } }
        val d = dir(context)
        // najprv AAC (malý súbor), ak by kodek na zariadení zlyhal, WAV
        val targets = listOf(File(d, base(mid) + ".m4a") to true, File(d, base(mid) + ".wav") to false)
        for ((target, aac) in targets) {
            val tmp = File(target.path + ".tmp")
            val ok = runCatching {
                (if (aac) AacWriter(tmp, SAMPLE_RATE, CHANNELS) else WavWriter(tmp, SAMPLE_RATE, CHANNELS)).use { w ->
                    engine.render(events, { pcm, frames -> w.write(pcm, frames) }, progress, cancelled = { !mid.exists() })
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

    /** Dekóduje vzorku (OGG) do 16-bit mono PCM. */
    private fun decodeAsset(context: Context, asset: String): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var tmpFile: File? = null
        try {
            runCatching {
                context.assets.openFd(asset).use { afd -> extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
            }.onFailure {
                // skomprimovaný asset – skopírujeme ho do dočasného súboru
                val f = File(context.cacheDir, "piano_" + asset.substringAfterLast('/'))
                context.assets.open(asset).use { input -> f.outputStream().use { input.copyTo(it) } }
                tmpFile = f
                extractor.setDataSource(f.path)
            }
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
            tmpFile?.delete()
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
