// ─── ui/theme/Theme.kt ───────────────────────────────────────────────────────
// Tema de la app con soporte de modo claro y modo oscuro.
//
// Además del ColorScheme de Material 3, se expone una paleta semántica propia
// ([AppColors], accesible como `AppTheme.colores`) porque la app usa muchos
// colores con significado fijo (semáforo de vencimientos, dorado de marca,
// contenedores de estado…) que Material 3 no cubre. Cada token tiene un valor
// para claro y otro para oscuro, de modo que ninguna pantalla necesita saber
// en qué tema está: pide el token por su rol y obtiene el color correcto.
//
// Los valores del tema claro son exactamente los que la app usaba antes de
// existir el modo oscuro, así que el aspecto en claro no cambia.
package com.example.myapplication.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════════════════
//  PALETA SEMÁNTICA
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Colores de la app agrupados por *rol*, no por tono.
 *
 * Regla de uso: nunca escribas un `Color(0x…)` literal en una pantalla; elige el
 * token cuyo rol describa lo que estás pintando. Así el modo oscuro funciona
 * solo, sin condicionales repartidos por la UI.
 */
@Immutable
data class AppColors(
    val esOscuro: Boolean,

    // ── Superficies y bordes ──────────────────────────────────────────────────
    /** Fondo general de una pantalla (detrás de las tarjetas). */
    val fondo: Color,
    /** Fondo de tarjetas, hojas inferiores, barras y menús. */
    val superficie: Color,
    /** Superficie apagada: elementos deshabilitados o "eliminados". */
    val superficieTenue: Color,
    /** Borde estándar de tarjetas. */
    val borde: Color,
    /** Borde de elementos apagados. */
    val bordeTenue: Color,
    /** Borde de campos de texto y fondo de botones inactivos. */
    val bordeCampo: Color,

    // ── Texto ─────────────────────────────────────────────────────────────────
    /** Texto principal sobre [superficie]. */
    val texto: Color,
    /** Texto secundario (datos de apoyo, subtítulos con peso). */
    val textoMedio: Color,
    /** Texto terciario: etiquetas, ayudas, estados vacíos. */
    val textoSuave: Color,
    /** Texto e iconos sobre un color de acento saturado (botón, círculo, chip). */
    val textoSobreAcento: Color,

    // ── Marca (dorado) ────────────────────────────────────────────────────────
    val dorado: Color,
    val doradoClaro: Color,
    val doradoContenedor: Color,
    val doradoContenedorTexto: Color,
    /** Tinta oscura de marca: texto sobre el degradado dorado. Igual en ambos temas. */
    val tinta: Color,

    // ── Semáforo · al día / éxito ─────────────────────────────────────────────
    val exito: Color,
    val exitoFuerte: Color,
    val exitoTexto: Color,
    val exitoBrillante: Color,
    val exitoContenedor: Color,
    val exitoContenedorTenue: Color,

    // ── Semáforo · por vencer / advertencia ───────────────────────────────────
    val advertencia: Color,
    val advertenciaBorde: Color,
    val advertenciaTexto: Color,
    val advertenciaContenedor: Color,
    val advertenciaContenedorAlt: Color,
    val ambar: Color,
    val naranjaSuave: Color,

    // ── Semáforo · vencido / peligro ──────────────────────────────────────────
    val peligro: Color,
    val peligroFuerte: Color,
    val peligroTexto: Color,
    val peligroContenedor: Color,
    val peligroContenedorTenue: Color,
    /** Rojo de mensajes de error. */
    val error: Color,

    // ── Estado de cuartos ─────────────────────────────────────────────────────
    val ocupadoContenedor: Color,
    val ocupadoTexto: Color,
    val neutroContenedor: Color,
    val neutroTexto: Color,

    // ── Verde oliva (hora del aviso diario) ───────────────────────────────────
    val oliva: Color,
    val olivaTexto: Color,
    val olivaContenedor: Color,

    // ── Botón neutro de máximo énfasis ────────────────────────────────────────
    val botonNeutro: Color,
    val botonNeutroTexto: Color,

    // ── Marca externa ─────────────────────────────────────────────────────────
    /** Verde de WhatsApp: color de marca ajeno, idéntico en ambos temas. */
    val whatsapp: Color,
)

private val ColoresClaros = AppColors(
    esOscuro = false,

    fondo           = Color(0xFFF5F5F5),
    superficie      = Color.White,
    superficieTenue = Color(0xFFEEEEEE),
    borde           = Color(0xFFE0E0E0),
    bordeTenue      = Color(0xFFBDBDBD),
    bordeCampo      = Color.LightGray,

    texto            = Color(0xFF212121),
    textoMedio       = Color.DarkGray,
    textoSuave       = Color.Gray,
    textoSobreAcento = Color.White,

    dorado                = Color(0xFF8A6A12),
    doradoClaro           = Color(0xFFC8A24B),
    doradoContenedor      = Color(0xFFF7EFD8),
    doradoContenedorTexto = Color(0xFF4A3A0C),
    tinta                 = Color(0xFF15151A),

    exito                = Color(0xFF388E3C),
    exitoFuerte          = Color(0xFF2E7D32),
    exitoTexto           = Color(0xFF1B5E20),
    exitoBrillante       = Color(0xFF4CAF50),
    exitoContenedor      = Color(0xFFC8E6C9),
    exitoContenedorTenue = Color(0xFFE8F5E9),

    advertencia              = Color(0xFFE65100),
    advertenciaBorde         = Color(0xFFF57F17),
    advertenciaTexto         = Color(0xFFBF360C),
    advertenciaContenedor    = Color(0xFFFFF3E0),
    advertenciaContenedorAlt = Color(0xFFFFF9C4),
    ambar                    = Color(0xFFFFC107),
    naranjaSuave             = Color(0xFFED6C02),

    peligro                = Color(0xFFD32F2F),
    peligroFuerte          = Color(0xFFC62828),
    peligroTexto           = Color(0xFFB71C1C),
    peligroContenedor      = Color(0xFFFFCDD2),
    peligroContenedorTenue = Color(0xFFFFEBEE),
    error                  = Color.Red,

    ocupadoContenedor = Color(0xFFEFE2BC),
    ocupadoTexto      = Color(0xFF6E5410),
    neutroContenedor  = Color(0xFFEEEEEE),
    neutroTexto       = Color(0xFF616161),

    oliva           = Color(0xFF558B2F),
    olivaTexto      = Color(0xFF33691E),
    olivaContenedor = Color(0xFFF1F8E9),

    botonNeutro      = Color.Black,
    botonNeutroTexto = Color.White,

    whatsapp = Color(0xFF25D366),
)

private val ColoresOscuros = AppColors(
    esOscuro = true,

    fondo           = Color(0xFF121218),
    superficie      = Color(0xFF1D1D24),
    superficieTenue = Color(0xFF272730),
    borde           = Color(0xFF3A3A45),
    bordeTenue      = Color(0xFF4A4A56),
    bordeCampo      = Color(0xFF4A4A56),

    texto            = Color(0xFFECECF2),
    textoMedio       = Color(0xFFC3C3CE),
    textoSuave       = Color(0xFF9B9BA8),
    // Los acentos en oscuro son tonos claros, así que encima va tinta oscura.
    textoSobreAcento = Color(0xFF14140F),

    dorado                = Color(0xFFE3C170),
    doradoClaro           = Color(0xFFD9B665),
    doradoContenedor      = Color(0xFF3A3016),
    doradoContenedorTexto = Color(0xFFF6E7BC),
    tinta                 = Color(0xFF15151A),

    exito                = Color(0xFF66BB6A),
    exitoFuerte          = Color(0xFF81C784),
    exitoTexto           = Color(0xFFA5D6A7),
    exitoBrillante       = Color(0xFF81C784),
    exitoContenedor      = Color(0xFF1E3A22),
    exitoContenedorTenue = Color(0xFF19301C),

    advertencia              = Color(0xFFFF9800),
    advertenciaBorde         = Color(0xFFFFB300),
    advertenciaTexto         = Color(0xFFFFB086),
    advertenciaContenedor    = Color(0xFF3B2A17),
    advertenciaContenedorAlt = Color(0xFF3A3517),
    ambar                    = Color(0xFFFFC107),
    naranjaSuave             = Color(0xFFFFA726),

    peligro                = Color(0xFFEF5350),
    peligroFuerte          = Color(0xFFE57373),
    peligroTexto           = Color(0xFFFFA9A3),
    peligroContenedor      = Color(0xFF44201F),
    peligroContenedorTenue = Color(0xFF3A1B1B),
    error                  = Color(0xFFFF7A72),

    ocupadoContenedor = Color(0xFF3A3116),
    ocupadoTexto      = Color(0xFFE8CE8E),
    neutroContenedor  = Color(0xFF272730),
    neutroTexto       = Color(0xFFA8A8B4),

    oliva           = Color(0xFF9CCC65),
    olivaTexto      = Color(0xFFC5E1A5),
    olivaContenedor = Color(0xFF232F1A),

    // En oscuro el "botón negro" se invierte a un neutro elevado y legible.
    botonNeutro      = Color(0xFF3A3A46),
    botonNeutroTexto = Color(0xFFECECF2),

    whatsapp = Color(0xFF25D366),
)

private val LightColorScheme = lightColorScheme(
    primary            = Color(0xFF8A6A12),  // dorado profundo (acentos/botones)
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFF7EFD8),  // dorado muy claro
    onPrimaryContainer = Color(0xFF4A3A0C),  // texto sobre dorado claro
    secondary          = Color(0xFFC8A24B),  // champagne (acento secundario)
    onSecondary        = Color(0xFF15151A),
    background       = Color(0xFFF5F5F5),
    surface          = Color.White,
    onSurface        = Color(0xFF212121),
    onBackground     = Color(0xFF212121),
    error            = Color(0xFFD32F2F),
    onError          = Color.White,
    outline          = Color(0xFFBDBDBD)
)

private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFFE3C170),  // dorado luminoso sobre fondo oscuro
    // Contenido sobre un acento: en oscuro los acentos son claros, así que va tinta.
    onPrimary          = Color(0xFF3D2F05),
    primaryContainer   = Color(0xFF574413),
    onPrimaryContainer = Color(0xFFF7E7BB),
    secondary          = Color(0xFFD9B665),
    onSecondary        = Color(0xFF3A2E05),
    // Fondo del elemento seleccionado en el menú lateral. Sin esto Material 3
    // usaría su lila de serie, que desentona con el dorado de la marca.
    secondaryContainer   = Color(0xFF574413),
    onSecondaryContainer = Color(0xFFF7E7BB),
    tertiary             = Color(0xFFD9B665),
    onTertiary           = Color(0xFF3A2E05),
    background            = Color(0xFF121218),
    surface               = Color(0xFF1D1D24),
    surfaceVariant        = Color(0xFF2A2A33),
    surfaceContainer      = Color(0xFF1D1D24),
    surfaceContainerHigh  = Color(0xFF272730),
    surfaceContainerLow   = Color(0xFF17171D),
    onSurface             = Color(0xFFECECF2),
    onSurfaceVariant      = Color(0xFFC3C3CE),
    onBackground          = Color(0xFFECECF2),
    error            = Color(0xFFEF5350),
    onError          = Color(0xFF3A0A08),
    outline          = Color(0xFF4A4A56),
    // Color de HorizontalDivider.
    outlineVariant   = Color(0xFF3A3A45),
    // Snackbar por defecto (pantalla de cambiar contraseña): claro sobre oscuro.
    inverseSurface   = Color(0xFFECECF2),
    inverseOnSurface = Color(0xFF1D1D24),
    scrim            = Color(0xFF000000)
)

private val LocalAppColors = staticCompositionLocalOf { ColoresClaros }

/** Punto de acceso a la paleta semántica: `AppTheme.colores.textoSuave`. */
object AppTheme {
    val colores: AppColors
        @Composable get() = LocalAppColors.current
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TEMA
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Aplica el tema de la app.
 *
 * @param oscuro si es `true` se usa la paleta oscura. Por defecto sigue al
 *   ajuste del sistema; las pantallas que dependen de la preferencia guardada
 *   por el usuario deben pasarla explícitamente.
 * @param escalaTexto multiplicador del tamaño de letra elegido en Ajustes
 *   (1f = normal). Se aplica una sola vez aquí, sobre el `fontScale` del
 *   sistema, de modo que todos los tamaños en `sp` de la app crecen o se
 *   encogen juntos y ninguna pantalla necesita saber que existe este ajuste.
 *   Solo afecta al texto: las medidas en `dp` no cambian, así que la
 *   distribución de las pantallas se mantiene.
 */
@Composable
fun MyApplicationTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    escalaTexto: Float = 1f,
    content: @Composable () -> Unit
) {
    val colores = if (oscuro) ColoresOscuros else ColoresClaros

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Los iconos de las barras del sistema deben invertirse con el tema: si no,
            // en modo oscuro quedan iconos oscuros sobre una barra oscura (invisibles).
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !oscuro
                isAppearanceLightNavigationBars = !oscuro
            }

            // El fondo de la ventana lo fija el tema XML, que sigue al modo del
            // sistema y no a esta preferencia. Se iguala a mano para que no se vea
            // un destello claro (al arrancar, rotar o abrir el teclado) cuando el
            // usuario elige oscuro con el sistema en claro.
            window.setBackgroundDrawable(ColorDrawable(colores.fondo.toArgb()))
        }
    }

    // El ajuste del usuario multiplica (no reemplaza) al del sistema: quien ya
    // tenga la letra grande en Android conserva ese tamaño como punto de partida.
    val densidadSistema = LocalDensity.current
    val densidad = remember(densidadSistema, escalaTexto) {
        Density(densidadSistema.density, densidadSistema.fontScale * escalaTexto)
    }

    CompositionLocalProvider(
        LocalAppColors provides colores,
        LocalDensity   provides densidad
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) DarkColorScheme else LightColorScheme,
            content     = content
        )
    }
}
