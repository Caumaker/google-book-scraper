package com.geospace.pianoscan.music

import com.geospace.pianoscan.data.Hand
import com.geospace.pianoscan.data.TimedNote
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Escreve um Standard MIDI File formato 1: uma trilha de andamento e uma trilha por mao.
 * O arquivo abre em qualquer DAW ou editor de partitura.
 */
object MidiWriter {

    private const val TPQ = 480

    fun write(file: File, notes: List<TimedNote>, bpm: Int, title: String) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            val tracks = listOf(
                tempoTrack(bpm, title),
                noteTrack(notes.filter { it.hand == Hand.RIGHT }, channel = 0, name = "Mao direita"),
                noteTrack(notes.filter { it.hand == Hand.LEFT }, channel = 1, name = "Mao esquerda")
            )
            out.write(header(tracks.size))
            tracks.forEach { out.write(it) }
        }
    }

    private fun header(trackCount: Int): ByteArray {
        val b = ByteArrayOutputStream()
        b.write("MThd".toByteArray(Charsets.US_ASCII))
        b.writeInt32(6)
        b.writeInt16(1)               // formato 1
        b.writeInt16(trackCount)
        b.writeInt16(TPQ)
        return b.toByteArray()
    }

    private fun tempoTrack(bpm: Int, title: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.writeVarLen(0)
        body.write(0xFF); body.write(0x03)
        val name = title.take(60).toByteArray(Charsets.US_ASCII)
        body.writeVarLen(name.size); body.write(name)

        val microsPerQuarter = (60_000_000.0 / bpm.coerceIn(20, 260)).toInt()
        body.writeVarLen(0)
        body.write(0xFF); body.write(0x51); body.write(0x03)
        body.write((microsPerQuarter shr 16) and 0xFF)
        body.write((microsPerQuarter shr 8) and 0xFF)
        body.write(microsPerQuarter and 0xFF)

        body.writeVarLen(0)
        body.write(0xFF); body.write(0x2F); body.write(0x00)
        return chunk(body.toByteArray())
    }

    private fun noteTrack(notes: List<TimedNote>, channel: Int, name: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.writeVarLen(0)
        body.write(0xFF); body.write(0x03)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        body.writeVarLen(nameBytes.size); body.write(nameBytes)

        // program change: acoustic grand piano
        body.writeVarLen(0)
        body.write(0xC0 or channel); body.write(0)

        data class Ev(val tick: Int, val on: Boolean, val midi: Int, val velocity: Int)
        val events = ArrayList<Ev>(notes.size * 2)
        for (n in notes) {
            val on = (n.startBeat * TPQ).toInt()
            val off = ((n.startBeat + n.durationBeats) * TPQ).toInt().coerceAtLeast(on + 1)
            events += Ev(on, true, n.midi, n.velocity)
            events += Ev(off, false, n.midi, 0)
        }
        // note off antes de note on no mesmo tick evita cortar a nota seguinte
        events.sortWith(compareBy({ it.tick }, { it.on }))

        var last = 0
        for (e in events) {
            body.writeVarLen(e.tick - last)
            last = e.tick
            body.write((if (e.on) 0x90 else 0x80) or channel)
            body.write(e.midi.coerceIn(0, 127))
            body.write(e.velocity.coerceIn(0, 127))
        }

        body.writeVarLen(0)
        body.write(0xFF); body.write(0x2F); body.write(0x00)
        return chunk(body.toByteArray())
    }

    private fun chunk(body: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        b.write("MTrk".toByteArray(Charsets.US_ASCII))
        b.writeInt32(body.size)
        b.write(body)
        return b.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt32(v: Int) {
        write((v ushr 24) and 0xFF); write((v ushr 16) and 0xFF)
        write((v ushr 8) and 0xFF); write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeInt16(v: Int) {
        write((v ushr 8) and 0xFF); write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeVarLen(value: Int) {
        var v = value.coerceAtLeast(0)
        val stack = ArrayDeque<Int>()
        stack.addLast(v and 0x7F)
        v = v shr 7
        while (v > 0) {
            stack.addLast((v and 0x7F) or 0x80)
            v = v shr 7
        }
        while (stack.isNotEmpty()) write(stack.removeLast())
    }
}
