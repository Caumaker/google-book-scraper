package com.geospace.pianoscan.music

import com.geospace.pianoscan.data.Hand
import com.geospace.pianoscan.data.Score
import com.geospace.pianoscan.data.TimedNote
import kotlin.math.floor
import kotlin.random.Random

enum class ArrangeStyle(val label: String, val description: String) {
    ORIGINAL("Original", "Toca exatamente o que esta escrito na partitura"),
    SOLO_PIANO("Piano solo", "Melodia com acordes em bloco na mao esquerda"),
    BALADA("Balada", "Baixo e acordes quebrados em colcheias"),
    ARPEJO("Arpejo", "Mao esquerda em semicolcheias continuas"),
    JAZZ("Jazz", "Swing, baixo caminhante e voicings sem fundamental"),
    VALSA("Valsa", "Baixo no 1, acordes nos tempos fracos")
}

data class Arrangement(
    val notes: List<TimedNote>,
    val tempoBpm: Int,
    val swing: Boolean,
    val chordLabels: List<String>
) {
    val totalBeats: Double
        get() = notes.maxOfOrNull { it.startBeat + it.durationBeats } ?: 0.0
}

object Arranger {

    fun arrange(
        score: Score,
        style: ArrangeStyle,
        tempoBpm: Int = score.tempoBpm,
        humanize: Boolean = true
    ): Arrangement {
        val beats = score.beatsPerMeasure
        val flat = score.flatten()
        val labels = ArrayList<String>(score.measures.size)

        if (style == ArrangeStyle.ORIGINAL) {
            score.measures.forEach { labels += ChordParser.label(ChordParser.infer(it)) }
            return Arrangement(
                notes = if (humanize) humanize(flat, 12345) else flat,
                tempoBpm = tempoBpm,
                swing = false,
                chordLabels = labels
            )
        }

        val out = ArrayList<TimedNote>()

        // Mao direita: preserva a melodia original. Se a transcricao nao separou as maos,
        // usa como melodia tudo que estiver do do central para cima.
        val hasRight = flat.any { it.hand == Hand.RIGHT }
        val melody = if (hasRight) flat.filter { it.hand == Hand.RIGHT }
        else flat.filter { it.midi >= 60 }
        out += melody

        var previous: Chord? = null
        score.measures.forEachIndexed { index, measure ->
            val chord = ChordParser.infer(measure) ?: previous
            labels += ChordParser.label(chord)
            if (chord != null) {
                previous = chord
                val start = index * beats
                out += when (style) {
                    ArrangeStyle.SOLO_PIANO -> blockChords(chord, start, beats)
                    ArrangeStyle.BALADA -> brokenChords(chord, start, beats)
                    ArrangeStyle.ARPEJO -> arpeggio(chord, start, beats)
                    ArrangeStyle.JAZZ -> jazz(chord, nextChord(score, index) ?: chord, start, beats)
                    ArrangeStyle.VALSA -> waltz(chord, start, beats)
                    ArrangeStyle.ORIGINAL -> emptyList()
                }
            }
        }

        val swing = style == ArrangeStyle.JAZZ
        var result: List<TimedNote> = out.sortedBy { it.startBeat }
        if (swing) result = applySwing(result)
        if (humanize) result = humanize(result, 6789)

        return Arrangement(result, tempoBpm, swing, labels)
    }

    private fun nextChord(score: Score, index: Int): Chord? =
        score.measures.getOrNull(index + 1)?.let { ChordParser.infer(it) }

    // --- padroes de acompanhamento -------------------------------------------------

    private fun blockChords(chord: Chord, start: Double, beats: Double): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        val root = chord.nearestRootAtOrAbove(36)
        out += TimedNote(root - 12, start, beats.coerceAtMost(2.0), 74, Hand.LEFT)
        val voicing = chord.voicing(48).take(4)
        val half = beats / 2.0
        listOf(0.0, half).forEach { offset ->
            voicing.forEach { midi ->
                out += TimedNote(midi, start + offset, half * 0.92, if (offset == 0.0) 62 else 54, Hand.LEFT)
            }
        }
        return out
    }

    private fun brokenChords(chord: Chord, start: Double, beats: Double): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        val root = chord.nearestRootAtOrAbove(36)
        out += TimedNote(root - 12, start, beats, 76, Hand.LEFT)

        val tones = chord.voicing(48).take(4)
        val pattern = buildList {
            add(tones[0])
            add(tones.getOrElse(2) { tones[0] + 7 })
            add(tones.getOrElse(1) { tones[0] + 4 })
            add(tones.getOrElse(3) { tones[0] + 12 })
        }
        var beat = 0.0
        var i = 0
        while (beat < beats - 1e-6) {
            out += TimedNote(pattern[i % pattern.size], start + beat, 0.5, 56, Hand.LEFT)
            beat += 0.5
            i++
        }
        return out
    }

    private fun arpeggio(chord: Chord, start: Double, beats: Double): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        val root = chord.nearestRootAtOrAbove(36)
        out += TimedNote(root - 12, start, beats, 72, Hand.LEFT)

        val tones = chord.voicing(45)
        val up = tones + tones.map { it + 12 }
        val cycle = up + up.reversed().drop(1).dropLast(1)
        var beat = 0.0
        var i = 0
        while (beat < beats - 1e-6) {
            out += TimedNote(cycle[i % cycle.size], start + beat, 0.28, 48, Hand.LEFT)
            beat += 0.25
            i++
        }
        return out
    }

    private fun jazz(chord: Chord, next: Chord, start: Double, beats: Double): List<TimedNote> {
        val out = ArrayList<TimedNote>()

        // voicing sem fundamental: 3a, 7a e tensoes, entre C3 e C4
        val rootless = chord.intervals.filter { it != 0 }.map { chord.nearestRootAtOrAbove(48) + it }
            .map { if (it > 76) it - 12 else it }
        listOf(0.0, (beats / 2.0) + 0.5).filter { it < beats }.forEach { offset ->
            rootless.forEach { midi ->
                out += TimedNote(midi, start + offset, 0.9, 52, Hand.LEFT)
            }
        }

        // baixo caminhante em semininas, aproximando cromaticamente o proximo acorde
        val root = chord.nearestRootAtOrAbove(36)
        val targetRoot = next.nearestRootAtOrAbove(36)
        val steps = floor(beats).toInt().coerceAtLeast(1)
        val degrees = listOf(0, 4, 7, 9)
        for (i in 0 until steps) {
            val midi = when {
                i == 0 -> root
                i == steps - 1 -> approach(targetRoot)
                else -> root + degrees[i % degrees.size]
            }
            out += TimedNote(midi, start + i, 0.95, if (i == 0) 78 else 68, Hand.LEFT)
        }
        return out
    }

    private fun approach(target: Int): Int = target - 1

    private fun waltz(chord: Chord, start: Double, beats: Double): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        val root = chord.nearestRootAtOrAbove(36)
        out += TimedNote(root - 12, start, 0.95, 78, Hand.LEFT)
        val voicing = chord.voicing(50).take(3)
        var beat = 1.0
        while (beat < beats - 1e-6) {
            voicing.forEach { out += TimedNote(it, start + beat, 0.85, 52, Hand.LEFT) }
            beat += 1.0
        }
        return out
    }

    // --- refinamentos ---------------------------------------------------------------

    /** Colcheias em proporcao 2:1, o balanco caracteristico do jazz. */
    private fun applySwing(notes: List<TimedNote>): List<TimedNote> = notes.map { n ->
        val whole = floor(n.startBeat)
        val frac = n.startBeat - whole
        if (frac > 0.45 && frac < 0.55) {
            n.copy(startBeat = whole + 2.0 / 3.0, durationBeats = n.durationBeats * 0.8)
        } else n
    }

    /** Micro variacoes de tempo e dinamica para o resultado nao soar mecanico. */
    private fun humanize(notes: List<TimedNote>, seed: Int): List<TimedNote> {
        val rnd = Random(seed)
        return notes.map { n ->
            val jitter = (rnd.nextDouble() - 0.5) * 0.018
            val dyn = (rnd.nextDouble() - 0.5) * 10.0
            n.copy(
                startBeat = (n.startBeat + jitter).coerceAtLeast(0.0),
                velocity = (n.velocity + dyn).toInt().coerceIn(20, 127)
            )
        }
    }
}
