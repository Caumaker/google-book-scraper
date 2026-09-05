package com.geospace.pianoscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.geospace.pianoscan.data.Hand
import com.geospace.pianoscan.data.Score
import com.geospace.pianoscan.data.TimedNote
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PC_DEGREE = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val BLACK_KEYS = setOf(1, 3, 6, 8, 10)

private fun diatonicStep(midi: Int): Int {
    val octave = midi / 12 - 1
    return octave * 7 + PC_DEGREE[((midi % 12) + 12) % 12]
}

private fun isAccidental(midi: Int) = ((midi % 12) + 12) % 12 in BLACK_KEYS

/**
 * Pauta dupla rolavel, com clave de sol e clave de fa, linhas suplementares,
 * cifras acima do sistema e um cursor que acompanha a execucao.
 */
@Composable
fun GrandStaff(
    score: Score,
    notes: List<TimedNote>,
    positionBeats: Double,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()

    val spacing = with(density) { 9.dp.toPx() }
    val pxPerBeat = with(density) { 58.dp.toPx() }
    val leftMargin = with(density) { 76.dp.toPx() }
    val trebleTop = with(density) { 46.dp.toPx() }
    val gap = with(density) { 58.dp.toPx() }
    val bassTop = trebleTop + spacing * 4 + gap
    val totalBeats = maxOf(score.totalBeats, notes.maxOfOrNull { it.startBeat + it.durationBeats } ?: 0.0)
    val contentWidthPx = leftMargin + (totalBeats * pxPerBeat).toFloat() + pxPerBeat
    val contentWidth = with(density) { contentWidthPx.toDp() }
    val heightDp = with(density) { (bassTop + spacing * 4 + 46.dp.toPx()).toDp() }

    val playheadX = leftMargin + (positionBeats * pxPerBeat).toFloat()

    LaunchedEffect(positionBeats) {
        val target = (playheadX - 320f).roundToInt().coerceIn(0, scroll.maxValue)
        if (kotlin.math.abs(target - scroll.value) > 24) scroll.scrollTo(target)
    }

    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 12.dp.toPx() }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(heightDp)
            .horizontalScroll(scroll)
    ) {
        Canvas(
            Modifier
                .width(contentWidth)
                .fillMaxHeight()
        ) {
            val ink = Color(0xFF2A2C33)
            val faint = Color(0x33808893)
            val accent = Color(0xFFE0A340)

            // linhas das duas pautas
            for (i in 0..4) {
                val yTreble = trebleTop + i * spacing
                val yBass = bassTop + i * spacing
                drawLine(ink, Offset(0f, yTreble), Offset(size.width, yTreble), 1.2f)
                drawLine(ink, Offset(0f, yBass), Offset(size.width, yBass), 1.2f)
            }

            // chave (brace) e barra inicial
            drawLine(ink, Offset(1.5f, trebleTop), Offset(1.5f, bassTop + spacing * 4), 3f)
            drawTrebleClef(trebleTop, spacing, ink)
            drawBassClef(bassTop, spacing, ink)

            // barras de compasso
            val beatsPerMeasure = score.beatsPerMeasure
            var measure = 0
            var beat = 0.0
            while (beat <= totalBeats + 0.001) {
                val x = leftMargin + (beat * pxPerBeat).toFloat()
                drawLine(
                    if (measure % 4 == 0) ink else faint,
                    Offset(x, trebleTop),
                    Offset(x, trebleTop + spacing * 4),
                    1f
                )
                drawLine(
                    if (measure % 4 == 0) ink else faint,
                    Offset(x, bassTop),
                    Offset(x, bassTop + spacing * 4),
                    1f
                )
                measure++
                beat += beatsPerMeasure
            }

            // cifras do arranjo
            labelPaint.color = android.graphics.Color.argb(255, 200, 140, 60)
            score.measures.forEachIndexed { index, m ->
                val label = m.chordSymbol
                if (label.isNotBlank()) {
                    val x = leftMargin + (index * beatsPerMeasure * pxPerBeat).toFloat() + 4f
                    drawContext.canvas.nativeCanvas.drawText(label, x, trebleTop - spacing * 2.2f, labelPaint)
                }
            }

            // notas
            for (n in notes) {
                val x = leftMargin + (n.startBeat * pxPerBeat).toFloat()
                val onTreble = n.hand == Hand.RIGHT
                val staffTop = if (onTreble) trebleTop else bassTop
                val bottomStep = if (onTreble) 30 else 18
                val step = diatonicStep(n.midi)
                val y = staffTop + spacing * 4 - (step - bottomStep) * (spacing / 2f)
                val active = positionBeats >= n.startBeat &&
                    positionBeats < n.startBeat + n.durationBeats
                val color = if (active) accent else ink

                drawLedgers(staffTop, spacing, step, bottomStep, x, ink)

                val headW = spacing * 1.3f
                val headH = spacing * 0.95f
                rotate(-18f, Offset(x, y)) {
                    if (n.durationBeats >= 2.0) {
                        drawOval(
                            color,
                            topLeft = Offset(x - headW / 2, y - headH / 2),
                            size = Size(headW, headH),
                            style = Stroke(width = 1.8f)
                        )
                    } else {
                        drawOval(
                            color,
                            topLeft = Offset(x - headW / 2, y - headH / 2),
                            size = Size(headW, headH)
                        )
                    }
                }

                if (isAccidental(n.midi)) {
                    drawSharp(x - headW * 1.1f, y, spacing, color)
                }

                if (n.durationBeats < 4.0) {
                    val up = step < bottomStep + 4
                    val stemX = if (up) x + headW / 2 - 1f else x - headW / 2 + 1f
                    val stemY = if (up) y - spacing * 3.4f else y + spacing * 3.4f
                    drawLine(color, Offset(stemX, y), Offset(stemX, stemY), 1.6f)
                    if (n.durationBeats <= 0.5) {
                        val flagDir = if (up) 1f else -1f
                        val flag = Path().apply {
                            moveTo(stemX, stemY)
                            quadraticTo(
                                stemX + spacing * 1.4f, stemY + flagDir * spacing * 0.9f,
                                stemX + spacing * 0.5f, stemY + flagDir * spacing * 2.1f
                            )
                        }
                        drawPath(flag, color, style = Stroke(width = 1.8f))
                    }
                }
            }

            // cursor de execucao
            drawLine(
                Color(0xCCE0533D),
                Offset(playheadX, trebleTop - spacing * 3),
                Offset(playheadX, bassTop + spacing * 7),
                2.5f
            )
        }
    }
}

private fun DrawScope.drawLedgers(
    staffTop: Float,
    spacing: Float,
    step: Int,
    bottomStep: Int,
    x: Float,
    color: Color
) {
    val half = spacing / 2f
    val width = spacing * 1.9f
    var s = bottomStep + 10
    while (s <= step) {
        val y = staffTop + spacing * 4 - (s - bottomStep) * half
        drawLine(color, Offset(x - width / 2, y), Offset(x + width / 2, y), 1.2f)
        s += 2
    }
    s = bottomStep - 2
    while (s >= step) {
        val y = staffTop + spacing * 4 - (s - bottomStep) * half
        drawLine(color, Offset(x - width / 2, y), Offset(x + width / 2, y), 1.2f)
        s -= 2
    }
}

private fun DrawScope.drawSharp(x: Float, y: Float, spacing: Float, color: Color) {
    val h = spacing * 1.6f
    val w = spacing * 0.7f
    drawLine(color, Offset(x - w / 3, y - h / 2), Offset(x - w / 3, y + h / 2), 1.3f)
    drawLine(color, Offset(x + w / 3, y - h / 2 - 2f), Offset(x + w / 3, y + h / 2 - 2f), 1.3f)
    drawLine(color, Offset(x - w, y - h / 6), Offset(x + w, y - h / 6 - 3f), 1.6f)
    drawLine(color, Offset(x - w, y + h / 4), Offset(x + w, y + h / 4 - 3f), 1.6f)
}

/** Clave de sol desenhada em espiral, ancorada na linha de sol (2a de baixo para cima). */
private fun DrawScope.drawTrebleClef(staffTop: Float, spacing: Float, color: Color) {
    val gLine = staffTop + spacing * 3
    val cx = 34f
    val path = Path()

    // espiral interna terminando na linha de sol
    val turns = 2.1
    val steps = 90
    val r0 = spacing * 1.45f
    var started = false
    for (i in 0..steps) {
        val t = i / steps.toDouble() * turns
        val r = r0 * (1.0 - t / (turns + 0.55))
        val a = PI * 0.5 + t * 2 * PI
        val px = cx + (r * cos(a)).toFloat()
        val py = gLine - (r * sin(a)).toFloat()
        if (!started) {
            path.moveTo(px, py)
            started = true
        } else {
            path.lineTo(px, py)
        }
    }

    // haste ascendente com a volta superior
    path.moveTo(cx, gLine - r0)
    path.cubicTo(
        cx - spacing * 1.9f, gLine - spacing * 2.4f,
        cx - spacing * 0.4f, staffTop - spacing * 2.4f,
        cx + spacing * 0.35f, staffTop - spacing * 1.2f
    )
    path.cubicTo(
        cx + spacing * 1.5f, staffTop + spacing * 0.6f,
        cx + spacing * 0.2f, gLine + spacing * 1.4f,
        cx - spacing * 0.1f, staffTop + spacing * 5.6f
    )
    path.cubicTo(
        cx - spacing * 0.3f, staffTop + spacing * 6.6f,
        cx - spacing * 1.4f, staffTop + spacing * 6.4f,
        cx - spacing * 1.2f, staffTop + spacing * 5.4f
    )
    drawPath(path, color, style = Stroke(width = 2.2f))
}

/** Clave de fa: cabeca na linha de fa (4a de baixo para cima) e os dois pontos. */
private fun DrawScope.drawBassClef(staffTop: Float, spacing: Float, color: Color) {
    val fLine = staffTop + spacing
    val cx = 26f
    drawCircle(color, spacing * 0.55f, Offset(cx, fLine))
    val path = Path().apply {
        moveTo(cx + spacing * 0.4f, fLine - spacing * 0.35f)
        cubicTo(
            cx + spacing * 2.4f, fLine + spacing * 0.4f,
            cx + spacing * 1.6f, fLine + spacing * 2.6f,
            cx - spacing * 0.4f, fLine + spacing * 3.1f
        )
    }
    drawPath(path, color, style = Stroke(width = 2.2f))
    drawCircle(color, spacing * 0.2f, Offset(cx + spacing * 1.9f, fLine - spacing * 0.5f))
    drawCircle(color, spacing * 0.2f, Offset(cx + spacing * 1.9f, fLine + spacing * 0.5f))
}

/**
 * Teclado de piano que acende as teclas tocadas no instante atual.
 */
@Composable
fun PianoKeyboard(
    notes: List<TimedNote>,
    positionBeats: Double,
    modifier: Modifier = Modifier
) {
    val active = remember(positionBeats, notes) {
        notes.filter {
            positionBeats >= it.startBeat && positionBeats < it.startBeat + it.durationBeats
        }.map { it.midi }.toSet()
    }
    val lowest = remember(notes) { (notes.minOfOrNull { it.midi } ?: 48).coerceIn(21, 84) }
    val highest = remember(notes) { (notes.maxOfOrNull { it.midi } ?: 84).coerceIn(24, 108) }

    val firstC = (lowest / 12) * 12
    val lastC = ((highest / 12) + 1) * 12
    val whiteKeys = (firstC..lastC).filter { !isAccidental(it) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color(0xFF15161A))
    ) {
        if (whiteKeys.isEmpty()) return@Canvas
        val w = size.width / whiteKeys.size
        val h = size.height

        whiteKeys.forEachIndexed { index, midi ->
            val on = midi in active
            drawRect(
                if (on) Color(0xFFE0A340) else Color(0xFFF7F5F0),
                topLeft = Offset(index * w, 0f),
                size = Size(w - 1.5f, h)
            )
        }

        whiteKeys.forEachIndexed { index, midi ->
            val black = midi + 1
            if (isAccidental(black) && black <= lastC) {
                val on = black in active
                val bw = w * 0.62f
                drawRect(
                    if (on) Color(0xFFC77E1E) else Color(0xFF1B1C21),
                    topLeft = Offset((index + 1) * w - bw / 2, 0f),
                    size = Size(bw, h * 0.62f)
                )
            }
        }
    }
}

