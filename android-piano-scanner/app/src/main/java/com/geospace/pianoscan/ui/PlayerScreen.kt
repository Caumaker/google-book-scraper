package com.geospace.pianoscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geospace.pianoscan.music.ArrangeStyle
import com.geospace.pianoscan.music.PlayerState
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    state: UiState,
    player: PlayerState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Double) -> Unit,
    onStyle: (ArrangeStyle) -> Unit,
    onTempo: (Int) -> Unit,
    onExportMidi: () -> Unit,
    onExportWav: () -> Unit
) {
    val score = state.score ?: return
    val arrangement = state.arrangement ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    score.title.ifBlank { "Sem titulo" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    listOfNotNull(
                        score.composer.ifBlank { null },
                        score.keySignature,
                        score.timeSignature,
                        "${score.measures.size} compassos"
                    ).joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onExportMidi) {
                Icon(Icons.Filled.Share, contentDescription = "Exportar MIDI")
            }
        }

        Surface(color = Color(0xFFFBF9F4), modifier = Modifier.fillMaxWidth()) {
            GrandStaff(
                score = score,
                notes = arrangement.notes,
                positionBeats = player.positionBeats
            )
        }

        PianoKeyboard(
            notes = arrangement.notes,
            positionBeats = player.positionBeats
        )

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Slider(
                value = player.positionBeats.toFloat(),
                onValueChange = { onSeek(it.toDouble()) },
                valueRange = 0f..(player.totalBeats.toFloat().coerceAtLeast(1f))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Compasso ${measureOf(player.positionBeats, score.beatsPerMeasure)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${state.tempo} bpm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Parar")
                }
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(68.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pausar" else "Tocar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                AssistChip(
                    onClick = onExportWav,
                    label = { Text("WAV") },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Andamento",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = state.tempo.toFloat(),
                onValueChange = { onTempo(it.roundToInt()) },
                valueRange = 40f..200f
            )

            Text(
                "Arranjo",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArrangeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = state.style == style,
                        onClick = { onStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }
            Text(
                state.style.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (score.notes.isNotBlank()) {
                Text(
                    score.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(8.dp))
    }
}

private fun measureOf(beat: Double, beatsPerMeasure: Double): Int =
    if (beatsPerMeasure <= 0.0) 1 else (beat / beatsPerMeasure).toInt() + 1
