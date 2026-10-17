package com.daklok.crescendo.data.midi

/**
 * Klavír: z MIDI nôt vyrobí zvuk pomocou nahratých tónov skutočného krídla (Salamander Grand Piano).
 * Každý kláves použije najbližší nahratý tón, sila úderu mení hlasitosť aj jas, pravý pedál drží tóny,
 * ľavý stlmí, stredný (sostenuto) drží len stlačené. Čistý Kotlin – dá sa otestovať aj na počítači.
 */
class PianoEngine(private val samples: List<PianoSample>, private val sampleRate: Int = 44_100) : MidiSynth {

    /** Nový nástroj „klavír“ pre mixér (každé vykreslenie má vlastný stav). */
    fun synth(): BlockSynth = Sampler(samples, InstrumentMap.spec("piano"), sampleRate)

    override fun render(
        events: List<NoteEvent>,
        sink: (ShortArray, Int) -> Unit,
        progress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val piano = synth()
        MidiMixer.render(events, { _, _ -> piano }, sink, progress, cancelled, sampleRate)
    }
}
