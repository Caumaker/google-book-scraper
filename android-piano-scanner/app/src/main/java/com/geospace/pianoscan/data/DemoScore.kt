package com.geospace.pianoscan.data

/**
 * Peca de demonstracao (Hino a Alegria, Beethoven, dominio publico) para o app
 * ser testavel antes de configurar a chave da API.
 */
object DemoScore {

    private data class N(val midi: Int, val start: Double, val dur: Double)

    fun build(): Score {
        val melody = listOf(
            listOf(N(64, 0.0, 1.0), N(64, 1.0, 1.0), N(65, 2.0, 1.0), N(67, 3.0, 1.0)),
            listOf(N(67, 0.0, 1.0), N(65, 1.0, 1.0), N(64, 2.0, 1.0), N(62, 3.0, 1.0)),
            listOf(N(60, 0.0, 1.0), N(60, 1.0, 1.0), N(62, 2.0, 1.0), N(64, 3.0, 1.0)),
            listOf(N(64, 0.0, 1.5), N(62, 1.5, 0.5), N(62, 2.0, 2.0)),
            listOf(N(64, 0.0, 1.0), N(64, 1.0, 1.0), N(65, 2.0, 1.0), N(67, 3.0, 1.0)),
            listOf(N(67, 0.0, 1.0), N(65, 1.0, 1.0), N(64, 2.0, 1.0), N(62, 3.0, 1.0)),
            listOf(N(60, 0.0, 1.0), N(60, 1.0, 1.0), N(62, 2.0, 1.0), N(64, 3.0, 1.0)),
            listOf(N(62, 0.0, 1.5), N(60, 1.5, 0.5), N(60, 2.0, 2.0))
        )
        val chords = listOf("C", "G", "C", "G", "C", "G", "Am", "C")
        val bass = listOf(48, 43, 48, 43, 48, 43, 45, 48)

        val measures = melody.mapIndexed { index, notes ->
            val right = notes.map {
                NoteEvent(
                    pitch = Pitch.name(it.midi),
                    midi = it.midi,
                    startBeat = it.start,
                    durationBeats = it.dur,
                    hand = "right",
                    velocity = 86
                )
            }
            val root = bass[index]
            val left = listOf(
                NoteEvent(Pitch.name(root), root, 0.0, 2.0, "left", 62),
                NoteEvent(Pitch.name(root + 7), root + 7, 2.0, 2.0, "left", 55)
            )
            Measure(number = index + 1, chordSymbol = chords[index], notes = right + left)
        }

        return Score(
            title = "Hino a Alegria",
            composer = "L. van Beethoven",
            keySignature = "C major",
            timeSignature = "4/4",
            tempoBpm = 104,
            measures = measures,
            notes = "Peca de demonstracao embutida no app."
        )
    }
}
