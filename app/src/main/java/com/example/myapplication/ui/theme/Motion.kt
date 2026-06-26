// ─── ui/theme/Motion.kt ───────────────────────────────────────────────────────
// Micro-interacciones y movimientos finos reutilizables para dar a la app un
// acabado elegante (mismo espíritu que la web): aparición suave de tarjetas,
// micro-rebote al tocar y un degradado dorado de marca.
package com.example.myapplication.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Degradado dorado metalizado para acentos/botones principales. */
val GoldGradient = Brush.linearGradient(
    listOf(Color(0xFFE6CF8B), Color(0xFFC8A24B), Color(0xFF8A6A12))
)

/**
 * Micro-rebote al presionar: la superficie se encoge suavemente y vuelve con un
 * resorte sutil. Reemplaza a `clickable` (sin ripple) para un tacto más fino.
 */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounceScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Aparición elegante: la tarjeta entra con fundido + leve deslizamiento hacia
 * arriba. [index] escalona la entrada de los elementos de una lista.
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 8) * 55).toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "appear"
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 46f
    }
}
