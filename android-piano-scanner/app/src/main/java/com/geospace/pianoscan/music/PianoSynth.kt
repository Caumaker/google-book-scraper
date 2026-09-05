package com.geospace.pianoscan.music

import com.geospace.pianoscan.data.TimedNote
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sintetizador de piano por sintese aditiva: cada nota e a soma de parciais levemente
 * inarmonicos com decaimento independente, como numa corda real. Sem samples e sem
 * dependencia externa, roda em qualquer aparelho.
 */
object SineTable {
    const val SIZE = 4096
    private const val MASK = SIZE - 1
    private val table = FloatArray(SIZE) { sin(2.0 * PI * it / SIZE).toFloat() }

    fun lookup(phase: Float): Float {
        val x = phase * SIZE
        val i = x.toInt()
        val frac = x - i
        val a = table[i and MASK]
        val b = table[(i + 1) and MASK]
        return a + (b - a) * frac
    }
}

class PianoVoice {
    private val maxPartials = 14
    private val phase = FloatArray(maxPartials)
    private val inc = FloatArray(maxPartials)
    private val amp = FloatArray(maxPartials)
    private val decay = FloatArray(maxPartials)

    var active = false
        private set
    var midi = 0
        private set
    var startedAt = 0L
        private set
    var offSample = Long.MAX_VALUE
        private set

    private var used = 0
    private var elapsed = 0.0f
    private var releasing = false
    private var releaseGain = 1.0f
    private var attackTau = 0.004f
    private var pan = 0.5f
    private var sampleRate = 44100.0f

    fun noteOn(midi: Int, velocity: Int, sampleRate: Int, offSample: Long, order: Long) {
        this.midi = midi
        this.offSample = offSample
        this.sampleRate = sampleRate.toFloat()
        this.startedAt = order

        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val nyquist = sampleRate / 2.0
        val inharmonicity = 0.0004 + 0.0009 * exp(-(midi - 21) / 30.0)
        val t60 = (16.0 * exp(-freq / 850.0)).coerceIn(0.9, 16.0)
        val gain = (velocity / 127.0).pow(1.4)
        // toque mais forte traz mais harmonicos, como o martelo real
        val brightness = 0.9 + 0.9 * (velocity / 127.0)

        used = 0
        for (h in 1..maxPartials) {
            val f = freq * h * sqrt(1.0 + inharmonicity * h * h)
            if (f >= nyquist * 0.95) break
            phase[used] = 0.0f
            inc[used] = (f / sampleRate).toFloat()
            amp[used] = (gain / h.toDouble().pow(1.35 / brightness)).toFloat()
            val tau = t60 / (6.9 * (1.0 + 0.42 * (h - 1)))
            decay[used] = exp(-1.0 / (tau * sampleRate)).toFloat()
            used++
        }
        if (used == 0) {
            active = false
            return
        }
        attackTau = 0.0018f + 0.004f * (1f - velocity / 127f)
        pan = ((midi - 21).coerceIn(0, 87) / 87.0f) * 0.6f + 0.2f
        elapsed = 0.0f
        releasing = false
        releaseGain = 1.0f
        active = true
    }

    fun noteOff() {
        releasing = true
    }

    fun kill() {
        active = false
        offSample = Long.MAX_VALUE
    }

    /** Soma esta voz no buffer estereo intercalado. */
    fun render(buffer: FloatArray, frames: Int) {
        if (!active) return
        val releaseCoef = exp(-1.0 / (0.09 * sampleRate)).toFloat()
        val attackCoef = exp(-1.0 / (attackTau * sampleRate)).toFloat()
        val left = 1.0f - pan
        val right = pan
        var attack = 1.0f - exp(-elapsed / attackTau)

        var i = 0
        while (i < frames) {
            var s = 0.0f
            var p = 0
            while (p < used) {
                var ph = phase[p] + inc[p]
                if (ph >= 1.0f) ph -= 1.0f
                phase[p] = ph
                s += SineTable.lookup(ph) * amp[p]
                amp[p] *= decay[p]
                p++
            }
            attack = 1.0f - (1.0f - attack) * attackCoef
            if (releasing) releaseGain *= releaseCoef
            val v = s * attack * releaseGain * 0.16f
            buffer[i * 2] += v * left
            buffer[i * 2 + 1] += v * right
            i++
        }
        elapsed += frames / sampleRate

        var peak = 0.0f
        for (p in 0 until used) peak = max(peak, amp[p])
        if (peak < 1e-5f || releaseGain < 1e-4f) kill()
    }
}

/** Reverb curto de placa, so para tirar a secura do sinal. */
class SimpleReverb(sampleRate: Int, private val wet: Float = 0.20f) {
    private val combBuffers = intArrayOf(1557, 1617, 1491, 1422)
        .map { FloatArray((it * sampleRate / 44100.0).toInt().coerceAtLeast(8)) }
    private val combIndex = IntArray(combBuffers.size)
    private val feedback = 0.78f

    private val apBuffer = FloatArray((225 * sampleRate / 44100.0).toInt().coerceAtLeast(8))
    private var apIndex = 0

    fun process(buffer: FloatArray, frames: Int) {
        if (wet <= 0f) return
        var i = 0
        while (i < frames) {
            val l = buffer[i * 2]
            val r = buffer[i * 2 + 1]
            val mono = (l + r) * 0.5f

            var acc = 0.0f
            for (c in combBuffers.indices) {
                val buf = combBuffers[c]
                val idx = combIndex[c]
                val out = buf[idx]
                buf[idx] = mono + out * feedback
                combIndex[c] = (idx + 1) % buf.size
                acc += out
            }
            acc *= 0.25f

            val apOut = apBuffer[apIndex]
            apBuffer[apIndex] = acc + apOut * 0.5f
            apIndex = (apIndex + 1) % apBuffer.size
            val tail = apOut - acc * 0.5f

            buffer[i * 2] = l * (1f - wet) + tail * wet
            buffer[i * 2 + 1] = r * (1f - wet) + tail * wet * 0.95f
            i++
        }
    }

    fun reset() {
        combBuffers.forEach { it.fill(0f) }
        apBuffer.fill(0f)
        combIndex.fill(0)
        apIndex = 0
    }
}

/** Evento de nota ja convertido para amostras. */
data class ScheduledNote(
    val midi: Int,
    val velocity: Int,
    val onSample: Long,
    val offSample: Long
)

class PianoSynth(
    val sampleRate: Int = 44100,
    maxVoices: Int = 28
) {
    private val voices = Array(maxVoices) { PianoVoice() }
    private val reverb = SimpleReverb(sampleRate)
    private var order = 0L

    private var schedule: List<ScheduledNote> = emptyList()
    private var nextIndex = 0
    private var cursor = 0L
    private var lengthSamples = 0L

    fun samplesPerBeat(bpm: Int): Double = 60.0 / bpm.coerceIn(20, 260) * sampleRate

    /** Prepara o cronograma e devolve a duracao total em amostras, com cauda. */
    fun load(notes: List<TimedNote>, bpm: Int): Long {
        val spb = samplesPerBeat(bpm)
        schedule = notes
            .map {
                ScheduledNote(
                    midi = it.midi,
                    velocity = it.velocity,
                    onSample = (it.startBeat * spb).toLong(),
                    offSample = ((it.startBeat + it.durationBeats) * spb).toLong()
                )
            }
            .sortedBy { it.onSample }
        lengthSamples = (schedule.maxOfOrNull { it.offSample } ?: 0L) + sampleRate * 2L
        seek(0L)
        return lengthSamples
    }

    fun seek(sample: Long) {
        cursor = sample.coerceAtLeast(0L)
        nextIndex = schedule.indexOfFirst { it.onSample >= cursor }
            .let { if (it < 0) schedule.size else it }
        voices.forEach { it.kill() }
        reverb.reset()
    }

    val position: Long get() = cursor
    val length: Long get() = lengthSamples

    fun finished(): Boolean = cursor >= lengthSamples || (nextIndex >= schedule.size && voices.none { it.active })

    /** Preenche [buffer] (estereo intercalado) com [frames] quadros. */
    fun render(buffer: FloatArray, frames: Int) {
        java.util.Arrays.fill(buffer, 0, frames * 2, 0.0f)

        val blockEnd = cursor + frames
        while (nextIndex < schedule.size && schedule[nextIndex].onSample < blockEnd) {
            allocate(schedule[nextIndex])
            nextIndex++
        }

        for (v in voices) {
            if (v.active && cursor >= v.offSample) v.noteOff()
            v.render(buffer, frames)
        }

        reverb.process(buffer, frames)

        var i = 0
        val n = frames * 2
        while (i < n) {
            buffer[i] = softClip(buffer[i])
            i++
        }
        cursor = blockEnd
    }

    private fun softClip(x: Float): Float {
        val v = x * 0.9f
        return when {
            v > 1f -> 1f
            v < -1f -> -1f
            else -> v - v * v * v / 3f
        }
    }

    private fun allocate(note: ScheduledNote) {
        if (note.offSample <= cursor) return
        val voice = voices.firstOrNull { !it.active } ?: voices.minByOrNull { it.startedAt }!!
        voice.noteOn(note.midi, note.velocity, sampleRate, note.offSample, order++)
    }
}
