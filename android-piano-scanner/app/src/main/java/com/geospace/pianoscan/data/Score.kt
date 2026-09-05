package com.geospace.pianoscan.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelo de partitura devolvido pelo modelo de visao e usado por todo o app.
 * Tempos sao expressos em batidas (quarter notes), relativos ao inicio do compasso.
 */
@Serializable
data class Score(
    val title: String = "Sem titulo",
    val composer: String = "",
    @SerialName("keySignature") val keySignature: String = "C major",
    @SerialName("timeSignature") val timeSignature: String = "4/4",
    @SerialName("tempoBpm") val tempoBpm: Int = 90,
    val measures: List<Measure> = emptyList(),
    val notes: String = ""
) {
    val beatsPerMeasure: Double
        get() {
            val parts = timeSignature.split("/")
            val num = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 4.0
            val den = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 4.0
            // quantas semininas cabem no compasso
            return num * (4.0 / den)
        }

    /** Achata a partitura em eventos com tempo absoluto em batidas. */
    fun flatten(): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        var cursor = 0.0
        for (m in measures) {
            for (n in m.notes) {
                if (n.midi in 21..108 && n.durationBeats > 0.0) {
                    out += TimedNote(
                        midi = n.midi,
                        startBeat = cursor + n.startBeat,
                        durationBeats = n.durationBeats,
                        velocity = n.velocity.coerceIn(1, 127),
                        hand = n.handEnum,
                        measure = m.number
                    )
                }
            }
            cursor += beatsPerMeasure
        }
        return out.sortedBy { it.startBeat }
    }

    val totalBeats: Double get() = measures.size * beatsPerMeasure

    fun isEmpty(): Boolean = measures.none { it.notes.isNotEmpty() }
}

@Serializable
data class Measure(
    val number: Int = 0,
    @SerialName("chordSymbol") val chordSymbol: String = "",
    val notes: List<NoteEvent> = emptyList()
)

@Serializable
data class NoteEvent(
    /** Nome cientifico, ex.: "C#4". Informativo. */
    val pitch: String = "",
    /** Numero MIDI. 60 = C4 = do central. */
    val midi: Int = 60,
    /** Inicio em batidas dentro do compasso. */
    @SerialName("startBeat") val startBeat: Double = 0.0,
    /** Duracao em batidas. 1.0 = seminima. */
    @SerialName("durationBeats") val durationBeats: Double = 1.0,
    /** "right" (clave de sol) ou "left" (clave de fa). */
    val hand: String = "right",
    val velocity: Int = 80
) {
    val handEnum: Hand get() = if (hand.equals("left", true)) Hand.LEFT else Hand.RIGHT
}

enum class Hand { LEFT, RIGHT }

data class TimedNote(
    val midi: Int,
    val startBeat: Double,
    val durationBeats: Double,
    val velocity: Int,
    val hand: Hand,
    val measure: Int = 0
)

object Pitch {
    private val SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(midi: Int): String {
        val octave = midi / 12 - 1
        return SHARP[((midi % 12) + 12) % 12] + octave
    }

    /** Converte "C#4", "Bb3", "F##2" em numero MIDI. Devolve null se nao reconhecer. */
    fun toMidi(name: String): Int? {
        val s = name.trim()
        if (s.isEmpty()) return null
        val base = when (s[0].uppercaseChar()) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
            else -> return null
        }
        var i = 1
        var accidental = 0
        while (i < s.length && (s[i] == '#' || s[i] == 'b' || s[i] == '-')) {
            accidental += if (s[i] == '#') 1 else -1
            i++
        }
        val octave = s.substring(i).toIntOrNull() ?: return null
        return (octave + 1) * 12 + base + accidental
    }
}
