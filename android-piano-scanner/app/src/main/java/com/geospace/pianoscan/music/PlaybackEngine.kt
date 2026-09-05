package com.geospace.pianoscan.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.geospace.pianoscan.data.TimedNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class PlayerState(
    val isPlaying: Boolean = false,
    val positionBeats: Double = 0.0,
    val totalBeats: Double = 0.0,
    val tempoBpm: Int = 90
)

/**
 * Transporte de audio: mantem o [PianoSynth] alimentando um AudioTrack numa thread propria.
 */
class PlaybackEngine(private val sampleRate: Int = 44100) {

    private val synth = PianoSynth(sampleRate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(true)

    private var notes: List<TimedNote> = emptyList()
    private var bpm: Int = 90
    private val blockFrames = 256

    @Synchronized
    fun prepare(notes: List<TimedNote>, bpm: Int) {
        stop()
        this.notes = notes
        this.bpm = bpm.coerceIn(20, 260)
        val length = synth.load(notes, this.bpm)
        _state.value = PlayerState(
            isPlaying = false,
            positionBeats = 0.0,
            totalBeats = length / synth.samplesPerBeat(this.bpm),
            tempoBpm = this.bpm
        )
    }

    @Synchronized
    fun setTempo(newBpm: Int) {
        val wasPlaying = _state.value.isPlaying
        val beat = _state.value.positionBeats
        prepare(notes, newBpm)
        seek(beat)
        if (wasPlaying) play()
    }

    @Synchronized
    fun play() {
        if (notes.isEmpty()) return
        if (synth.finished()) synth.seek(0)
        paused.set(false)
        if (running.get()) {
            _state.value = _state.value.copy(isPlaying = true)
            return
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(blockFrames * 2 * 4 * 4)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track = audioTrack
        audioTrack.play()
        running.set(true)
        _state.value = _state.value.copy(isPlaying = true)

        thread = Thread({ renderLoop(audioTrack) }, "piano-render").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun renderLoop(audioTrack: AudioTrack) {
        val buffer = FloatArray(blockFrames * 2)
        val silence = FloatArray(blockFrames * 2)
        while (running.get()) {
            if (paused.get()) {
                audioTrack.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
                continue
            }
            synth.render(buffer, blockFrames)
            audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            _state.value = _state.value.copy(
                positionBeats = synth.position / synth.samplesPerBeat(bpm),
                isPlaying = true
            )
            if (synth.finished()) {
                paused.set(true)
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
        runCatching {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    fun pause() {
        paused.set(true)
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seek(beat: Double) {
        val target = (beat.coerceAtLeast(0.0) * synth.samplesPerBeat(bpm)).toLong()
        synth.seek(target)
        _state.value = _state.value.copy(positionBeats = beat.coerceAtLeast(0.0))
    }

    @Synchronized
    fun stop() {
        paused.set(true)
        running.set(false)
        thread?.join(500)
        thread = null
        track = null
        synth.seek(0)
        _state.value = _state.value.copy(isPlaying = false, positionBeats = 0.0)
    }

    fun release() = stop()
}
