package com.geospace.pianoscan.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geospace.pianoscan.data.ApiKeyStore
import com.geospace.pianoscan.data.ClaudeVisionClient
import com.geospace.pianoscan.data.DemoScore
import com.geospace.pianoscan.data.ImageUtils
import com.geospace.pianoscan.data.Score
import com.geospace.pianoscan.music.ArrangeStyle
import com.geospace.pianoscan.music.Arrangement
import com.geospace.pianoscan.music.Arranger
import com.geospace.pianoscan.music.MidiWriter
import com.geospace.pianoscan.music.PlaybackEngine
import com.geospace.pianoscan.music.WavExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Stage { CAPTURE, PLAYER }

data class UiState(
    val stage: Stage = Stage.CAPTURE,
    val pages: List<Bitmap> = emptyList(),
    val busy: Boolean = false,
    val busyText: String = "",
    val error: String? = null,
    val score: Score? = null,
    val arrangement: Arrangement? = null,
    val style: ArrangeStyle = ArrangeStyle.ORIGINAL,
    val tempo: Int = 90,
    val hasApiKey: Boolean = false,
    val hint: String = ""
)

class ScoreViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = ApiKeyStore(app)
    val engine = PlaybackEngine()

    private val _ui = MutableStateFlow(UiState(hasApiKey = keyStore.hasKey))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val playerState = engine.state

    // --- entrada de imagens ---------------------------------------------------------

    fun addPage(uri: Uri) = viewModelScope.launch {
        val bitmap = withContext(Dispatchers.IO) {
            ImageUtils.loadNormalized(getApplication<Application>(), uri)
        }
        if (bitmap == null) {
            _ui.value = _ui.value.copy(error = "Nao consegui abrir essa imagem.")
        } else {
            _ui.value = _ui.value.copy(pages = _ui.value.pages + bitmap, error = null)
        }
    }

    fun addPage(file: File) = viewModelScope.launch {
        val bitmap = withContext(Dispatchers.IO) { ImageUtils.loadNormalized(file) }
        if (bitmap != null) {
            _ui.value = _ui.value.copy(pages = _ui.value.pages + bitmap, error = null)
        }
    }

    fun removePage(index: Int) {
        val pages = _ui.value.pages.toMutableList()
        if (index in pages.indices) {
            pages.removeAt(index)
            _ui.value = _ui.value.copy(pages = pages)
        }
    }

    fun setHint(text: String) {
        _ui.value = _ui.value.copy(hint = text)
    }

    // --- chave da API ---------------------------------------------------------------

    fun apiKey(): String = keyStore.apiKey

    fun saveApiKey(value: String) {
        keyStore.apiKey = value
        _ui.value = _ui.value.copy(hasApiKey = keyStore.hasKey)
    }

    // --- transcricao ----------------------------------------------------------------

    fun transcribe() = viewModelScope.launch {
        val state = _ui.value
        if (state.pages.isEmpty()) {
            _ui.value = state.copy(error = "Fotografe ou escolha ao menos uma pagina.")
            return@launch
        }
        if (!keyStore.hasKey) {
            _ui.value = state.copy(error = "Configure a chave da API da Anthropic nas preferencias.")
            return@launch
        }
        _ui.value = state.copy(busy = true, busyText = "Lendo a partitura...", error = null)

        val result = ClaudeVisionClient(keyStore.apiKey).transcribe(state.pages, state.hint)
        result.fold(
            onSuccess = { score ->
                if (score.isEmpty()) {
                    _ui.value = _ui.value.copy(
                        busy = false,
                        error = "Nao identifiquei notas na foto. Tente enquadrar so a pauta, com boa luz."
                    )
                } else {
                    openScore(score)
                }
            },
            onFailure = { e ->
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = e.message ?: "Falha ao transcrever."
                )
            }
        )
    }

    fun loadDemo() = openScore(DemoScore.build())

    private fun openScore(score: Score) {
        val tempo = score.tempoBpm.coerceIn(30, 220)
        val arrangement = Arranger.arrange(score, ArrangeStyle.ORIGINAL, tempo)
        engine.prepare(arrangement.notes, tempo)
        _ui.value = _ui.value.copy(
            stage = Stage.PLAYER,
            busy = false,
            error = null,
            score = score,
            arrangement = arrangement,
            style = ArrangeStyle.ORIGINAL,
            tempo = tempo
        )
    }

    // --- arranjo e transporte -------------------------------------------------------

    fun setStyle(style: ArrangeStyle) {
        val score = _ui.value.score ?: return
        val tempo = _ui.value.tempo
        val arrangement = Arranger.arrange(score, style, tempo)
        engine.prepare(arrangement.notes, tempo)
        _ui.value = _ui.value.copy(style = style, arrangement = arrangement)
    }

    fun setTempo(bpm: Int) {
        val clamped = bpm.coerceIn(30, 220)
        _ui.value = _ui.value.copy(tempo = clamped)
        engine.setTempo(clamped)
    }

    fun backToCapture() {
        engine.stop()
        _ui.value = _ui.value.copy(stage = Stage.CAPTURE, error = null)
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    // --- exportacao -----------------------------------------------------------------

    suspend fun exportMidi(): File? = withContext(Dispatchers.IO) {
        val state = _ui.value
        val arrangement = state.arrangement ?: return@withContext null
        val file = File(exportDir(), "${safeName(state)}.mid")
        MidiWriter.write(file, arrangement.notes, state.tempo, state.score?.title ?: "PianoScan")
        file
    }

    suspend fun exportWav(): File? = withContext(Dispatchers.IO) {
        val state = _ui.value
        val arrangement = state.arrangement ?: return@withContext null
        val file = File(exportDir(), "${safeName(state)}.wav")
        WavExporter.export(file, arrangement.notes, state.tempo)
        file
    }

    fun setBusy(busy: Boolean, text: String = "") {
        _ui.value = _ui.value.copy(busy = busy, busyText = text)
    }

    private fun exportDir(): File =
        File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }

    private fun safeName(state: UiState): String {
        val base = state.score?.title?.ifBlank { "partitura" } ?: "partitura"
        return base.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "partitura" }
            .replace(' ', '_') + "_" + state.style.name.lowercase()
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }
}
