// ─── ui/onboarding/Coachmark.kt ───────────────────────────────────────────────
// Indicaciones de ayuda (coach-marks): un overlay que oscurece la pantalla, deja
// "iluminado" el botón al que apunta y muestra un globo explicativo. Los pasos se
// definen por rol y se muestran una sola vez por usuario (persistido aparte).
package com.example.myapplication.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Oro      = Color(0xFFC8A24B)
private val OroClaro = Color(0xFFE6CF8B)
private val Tinta    = Color(0xFF15151A)

/** Un paso de la guía: [anchorId] = id del elemento a resaltar (null = mensaje centrado). */
data class CoachStep(val anchorId: String?, val titulo: String, val texto: String)

class CoachmarkState {
    val anchors = mutableStateMapOf<String, Rect>()
    var steps by mutableStateOf<List<CoachStep>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set

    val activo: Boolean get() = index in steps.indices
    val current: CoachStep? get() = steps.getOrNull(index)

    fun start(nuevos: List<CoachStep>) { steps = nuevos; index = 0 }
    fun next() { index = if (index >= steps.lastIndex) -1 else index + 1 }
    fun finish() { index = -1 }
}

@Composable
fun rememberCoachmarkState(): CoachmarkState = remember { CoachmarkState() }

/** Marca un elemento como objetivo de la guía (registra su posición en pantalla). */
fun Modifier.coachAnchor(state: CoachmarkState, id: String): Modifier =
    this.onGloballyPositioned { state.anchors[id] = it.boundsInRoot() }

/** Overlay que dibuja el foco + globo. Colócalo al final de un Box raíz (fillMaxSize). */
@Composable
fun CoachmarkOverlay(state: CoachmarkState, onFinish: () -> Unit) {
    if (!state.activo) return
    val step = state.current ?: return
    val density = LocalDensity.current
    val noInteraction = remember { MutableInteractionSource() }

    fun avanzar() {
        if (state.index >= state.steps.lastIndex) { state.finish(); onFinish() } else state.next()
    }
    fun saltar() { state.finish(); onFinish() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // consume toques: tocar fuera del globo avanza al siguiente paso
            .clickable(interactionSource = noInteraction, indication = null) { avanzar() }
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val rect: Rect? = step.anchorId?.let { state.anchors[it] }
        val scrim = Color(0xCC0B0B10)
        val padPx = with(density) { 6.dp.toPx() }

        // ── Foco (scrim con "agujero" sobre el objetivo) ──
        Canvas(Modifier.fillMaxSize()) {
            if (rect == null) {
                drawRect(scrim, Offset.Zero, Size(wPx, hPx))
            } else {
                val l = (rect.left - padPx).coerceAtLeast(0f)
                val t = (rect.top - padPx).coerceAtLeast(0f)
                val r = (rect.right + padPx).coerceAtMost(wPx)
                val b = (rect.bottom + padPx).coerceAtMost(hPx)
                drawRect(scrim, Offset(0f, 0f), Size(wPx, t))                  // arriba
                drawRect(scrim, Offset(0f, b), Size(wPx, (hPx - b).coerceAtLeast(0f))) // abajo
                drawRect(scrim, Offset(0f, t), Size(l, (b - t).coerceAtLeast(0f)))     // izquierda
                drawRect(scrim, Offset(r, t), Size((wPx - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f))) // derecha
                drawRoundRect(
                    color = OroClaro, topLeft = Offset(l, t),
                    size = Size((r - l).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(with(density) { 14.dp.toPx() }),
                    style = Stroke(width = with(density) { 2.dp.toPx() })
                )
            }
        }

        // ── Posición vertical del globo (debajo del objetivo si cabe, si no, encima) ──
        val estimadoPx = with(density) { 230.dp.toPx() }
        val bubbleYpx = when {
            rect == null -> hPx * 0.34f
            rect.bottom + estimadoPx < hPx -> rect.bottom + with(density) { 18.dp.toPx() }
            else -> (rect.top - estimadoPx).coerceAtLeast(with(density) { 24.dp.toPx() })
        }
        val bubbleYdp = with(density) { bubbleYpx.toDp() }

        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = bubbleYdp)
                    .fillMaxWidth()
                    // el globo no debe propagar el toque al scrim
                    .clickable(interactionSource = noInteraction, indication = null) {},
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(step.titulo, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Tinta)
                    Spacer(Modifier.height(8.dp))
                    Text(step.texto, fontSize = 14.sp, color = Color(0xFF4B4B55), lineHeight = 20.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.index + 1} / ${state.steps.size}",
                            fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { saltar() }) { Text("Saltar", color = Color.Gray) }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = { avanzar() },
                            colors = ButtonDefaults.buttonColors(containerColor = Oro, contentColor = Tinta),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (state.index >= state.steps.lastIndex) "¡Entendido!" else "Siguiente",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
