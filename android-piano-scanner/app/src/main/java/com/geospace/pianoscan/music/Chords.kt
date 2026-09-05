package com.geospace.pianoscan.music

import com.geospace.pianoscan.data.Measure
import com.geospace.pianoscan.data.NoteEvent
import com.geospace.pianoscan.data.Pitch

/** Acorde como classe de altura da fundamental (0..11) e intervalos em semitons. */
data class Chord(
    val root: Int,
    val intervals: List<Int>,
    val label: String
) {
    /** Notas do acorde a partir de uma oitava de referencia. */
    fun voicing(lowestMidi: Int): List<Int> {
        val base = nearestRootAtOrAbove(lowestMidi)
        return intervals.map { base + it }
    }

    fun nearestRootAtOrAbove(lowestMidi: Int): Int {
        var m = (lowestMidi / 12) * 12 + root
        while (m < lowestMidi) m += 12
        return m
    }

    val bass: Int get() = root
}

object ChordParser {

    private val ROOTS = mapOf(
        "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11
    )

    private val QUALITIES: List<Pair<Regex, List<Int>>> = listOf(
        Regex("^(maj7|M7|Maj7|Δ)") to listOf(0, 4, 7, 11),
        Regex("^(maj9|M9)") to listOf(0, 4, 7, 11, 14),
        Regex("^(m7b5|min7b5|ø)") to listOf(0, 3, 6, 10),
        Regex("^(dim7|o7)") to listOf(0, 3, 6, 9),
        Regex("^(dim|o)") to listOf(0, 3, 6),
        Regex("^(m9|min9|-9)") to listOf(0, 3, 7, 10, 14),
        Regex("^(m7|min7|-7)") to listOf(0, 3, 7, 10),
        Regex("^(m6|min6)") to listOf(0, 3, 7, 9),
        Regex("^(m|min|-)") to listOf(0, 3, 7),
        Regex("^(aug|\\+)") to listOf(0, 4, 8),
        Regex("^(sus4|sus)") to listOf(0, 5, 7),
        Regex("^(sus2)") to listOf(0, 2, 7),
        Regex("^(13)") to listOf(0, 4, 7, 10, 14, 21),
        Regex("^(11)") to listOf(0, 4, 7, 10, 17),
        Regex("^(9)") to listOf(0, 4, 7, 10, 14),
        Regex("^(7)") to listOf(0, 4, 7, 10),
        Regex("^(6)") to listOf(0, 4, 7, 9),
        Regex("^(maj|M)") to listOf(0, 4, 7)
    )

    fun parse(symbol: String): Chord? {
        val s = symbol.trim()
        if (s.isEmpty()) return null
        val letter = s[0].uppercaseChar().toString()
        var root = ROOTS[letter] ?: return null
        var i = 1
        while (i < s.length && (s[i] == '#' || s[i] == 'b')) {
            root += if (s[i] == '#') 1 else -1
            i++
        }
        root = ((root % 12) + 12) % 12
        val rest = s.substring(i).substringBefore("/")
        val intervals = QUALITIES.firstOrNull { it.first.containsMatchIn(rest) }?.second
            ?: listOf(0, 4, 7)
        return Chord(root, intervals, s)
    }

    private val TEMPLATES = listOf(
        Triple(listOf(0, 4, 7, 10), "7", 1.0),
        Triple(listOf(0, 4, 7, 11), "maj7", 1.0),
        Triple(listOf(0, 3, 7, 10), "m7", 1.0),
        Triple(listOf(0, 3, 6, 10), "m7b5", 0.9),
        Triple(listOf(0, 4, 7), "", 1.05),
        Triple(listOf(0, 3, 7), "m", 1.05),
        Triple(listOf(0, 3, 6), "dim", 0.85),
        Triple(listOf(0, 4, 8), "aug", 0.8),
        Triple(listOf(0, 5, 7), "sus4", 0.8)
    )

    private val NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    /** Deduz o acorde de um compasso pelo peso de duracao de cada classe de altura. */
    fun infer(measure: Measure): Chord? {
        parse(measure.chordSymbol)?.let { return it }
        if (measure.notes.isEmpty()) return null

        val weights = DoubleArray(12)
        var lowest = 127
        for (n in measure.notes) {
            val pc = ((n.midi % 12) + 12) % 12
            // notas graves e longas pesam mais na definicao da harmonia
            val positional = if (n.startBeat < 0.01) 1.6 else 1.0
            weights[pc] += n.durationBeats * positional
            if (n.midi < lowest) lowest = n.midi
        }
        val total = weights.sum()
        if (total <= 0.0) return null

        var best: Chord? = null
        var bestScore = -1.0
        for (root in 0..11) {
            for ((intervals, suffix, bonus) in TEMPLATES) {
                var score = 0.0
                for (iv in intervals) score += weights[(root + iv) % 12]
                score = score / total * bonus
                if (root == ((lowest % 12) + 12) % 12) score *= 1.15
                if (score > bestScore) {
                    bestScore = score
                    best = Chord(root, intervals, NAMES[root] + suffix)
                }
            }
        }
        return if (bestScore >= 0.55) best else null
    }

    fun label(chord: Chord?): String = chord?.label ?: "-"
}

internal fun NoteEvent.pitchName(): String = if (pitch.isNotBlank()) pitch else Pitch.name(midi)
