package com.geospace.pianoscan.music

import com.geospace.pianoscan.data.TimedNote
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile

/** Renderiza o arranjo offline e grava um WAV estereo 16 bits. */
object WavExporter {

    fun export(file: File, notes: List<TimedNote>, bpm: Int, sampleRate: Int = 44100) {
        file.parentFile?.mkdirs()
        val synth = PianoSynth(sampleRate)
        val totalFrames = synth.load(notes, bpm)
        val blockFrames = 1024
        val buffer = FloatArray(blockFrames * 2)
        val pcm = ByteArray(blockFrames * 4)
        var written = 0L

        BufferedOutputStream(file.outputStream()).use { out ->
            out.write(riffHeader(0, sampleRate))
            while (written < totalFrames) {
                val frames = minOf(blockFrames.toLong(), totalFrames - written).toInt()
                synth.render(buffer, frames)
                var j = 0
                for (i in 0 until frames * 2) {
                    val s = (buffer[i].coerceIn(-1f, 1f) * 32767f).toInt()
                    pcm[j++] = (s and 0xFF).toByte()
                    pcm[j++] = ((s shr 8) and 0xFF).toByte()
                }
                out.write(pcm, 0, frames * 4)
                written += frames
            }
        }

        // corrige os tamanhos do cabecalho agora que sabemos quantos quadros foram gravados
        RandomAccessFile(file, "rw").use { raf ->
            val dataBytes = (written * 4).toInt()
            raf.seek(4); raf.write(int32le(36 + dataBytes))
            raf.seek(40); raf.write(int32le(dataBytes))
        }
    }

    private fun riffHeader(dataBytes: Int, sampleRate: Int = 44100): ByteArray {
        val channels = 2
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val b = ByteArrayBuilder()
        b.ascii("RIFF"); b.int32(36 + dataBytes); b.ascii("WAVE")
        b.ascii("fmt "); b.int32(16); b.int16(1); b.int16(channels)
        b.int32(sampleRate); b.int32(byteRate); b.int16(channels * bits / 8); b.int16(bits)
        b.ascii("data"); b.int32(dataBytes)
        return b.toByteArray()
    }

    private fun int32le(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    private class ByteArrayBuilder {
        private val out = java.io.ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun int32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun int16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
