package com.example.myapplication.worker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.MyApplicationTheme

class AlarmFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val titulo      = intent.getStringExtra(AlarmSoundService.EXTRA_TITULO) ?: "Cobro pendiente"
        val descripcion = intent.getStringExtra(AlarmSoundService.EXTRA_DESCRIPCION) ?: ""

        val app = application as MyApplication

        setContent {
            // La alarma respeta el mismo tema y tamaño de letra que el resto de la app.
            val temaOscuro  by app.temaOscuro.collectAsStateWithLifecycle()
            val escalaTexto by app.escalaTexto.collectAsStateWithLifecycle()

            MyApplicationTheme(
                oscuro      = temaOscuro ?: isSystemInDarkTheme(),
                escalaTexto = escalaTexto
            ) {
                // El botón de silenciar cambia de aspecto según el sonido siga o no.
                val sonando by AlarmSoundService.sonando.collectAsStateWithLifecycle()

                BackHandler(enabled = AlarmSoundService.estaActiva) { }
                PantallaAlarmaActiva(
                    titulo      = titulo,
                    descripcion = descripcion,
                    sonando     = sonando,
                    onSilenciar = { silenciar() },
                    onApagar    = { apagarYCerrar() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AlarmSoundService.estaActiva) finish()
    }

    /**
     * Calla el sonido sin cerrar la pantalla: el usuario deja de ser molestado al
     * instante pero conserva el detalle de los pendientes delante para leerlo.
     */
    private fun silenciar() {
        startService(
            Intent(this, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_SILENCIAR
            }
        )
    }

    private fun apagarYCerrar() {
        startService(
            Intent(this, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_STOP
            }
        )
        finish()
    }
}

// ─── Detalle de la alarma ────────────────────────────────────────────────────
// El detalle llega como texto plano multilínea, construido por NotificadorPendientes:
//
//   POR COBRAR (2 · S/ 300.00)
//   • Juan Pérez: Cuarto 3 · vence HOY
//   • María López: Cuarto 5 · vencido hace 2 días · Pago por partes
//
//   POR PAGAR (1 · S/ 50.00)
//   • Luz: Marzo 2026 · vence HOY
//
// Antes se pintaba como un único bloque de texto con negritas, y con varios
// registros todo se leía amontonado. Ahora se interpreta a una estructura y cada
// grupo y cada registro se dibujan por separado.

private data class ItemAlarma(val titulo: String, val detalle: String, val esParcial: Boolean)

private data class GrupoAlarma(
    val etiqueta: String,     // "POR COBRAR" / "POR PAGAR"
    val resumen:  String,     // "2 · S/ 300.00"
    val esCobro:  Boolean,
    val items:    List<ItemAlarma>
)

private val REGEX_ENCABEZADO = Regex("""^(POR COBRAR|POR PAGAR)\s*\((.*)\)\s*$""")

/** Interpreta el texto plano del aviso. Devuelve vacío si no tiene el formato esperado. */
private fun parsearDetalle(descripcion: String): List<GrupoAlarma> {
    val grupos = mutableListOf<GrupoAlarma>()
    var etiqueta: String? = null
    var resumen = ""
    var items = mutableListOf<ItemAlarma>()

    fun cerrarGrupo() {
        etiqueta?.let {
            grupos += GrupoAlarma(it, resumen, it == "POR COBRAR", items.toList())
        }
        etiqueta = null
        resumen = ""
        items = mutableListOf()
    }

    descripcion.split("\n").forEach { linea ->
        val encabezado = REGEX_ENCABEZADO.find(linea.trim())
        when {
            encabezado != null -> {
                cerrarGrupo()
                etiqueta = encabezado.groupValues[1]
                resumen = encabezado.groupValues[2]
            }
            linea.trimStart().startsWith("• ") -> {
                val resto = linea.trimStart().removePrefix("• ")
                val parcial = resto.endsWith(MARCA_PARCIAL)
                val limpio = if (parcial) resto.removeSuffix(MARCA_PARCIAL) else resto
                val sep = limpio.indexOf(": ")
                items += if (sep >= 0) {
                    ItemAlarma(limpio.substring(0, sep), limpio.substring(sep + 2), parcial)
                } else {
                    ItemAlarma(limpio, "", parcial)
                }
            }
            // Las líneas en blanco solo separan grupos: no aportan nada al parseo.
        }
    }
    cerrarGrupo()
    return grupos
}

private const val MARCA_PARCIAL = " · Pago por partes"

/**
 * Un grupo de pendientes (cobrar o pagar) con su encabezado y sus registros.
 * El color distingue los dos grupos de un vistazo, que era justo lo que se
 * confundía cuando todo iba en un mismo bloque de texto.
 */
@Composable
private fun GrupoAlarmaCard(grupo: GrupoAlarma) {
    val acento     = if (grupo.esCobro) AppTheme.colores.exito else AppTheme.colores.advertencia
    val contenedor = if (grupo.esCobro) AppTheme.colores.exitoContenedorTenue
                     else AppTheme.colores.advertenciaContenedor

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border   = BorderStroke(1.dp, acento.copy(alpha = 0.45f))
    ) {
        // Encabezado: etiqueta a la izquierda, cantidad y total a la derecha.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(contenedor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (grupo.esCobro) Icons.Filled.CallReceived else Icons.Filled.CallMade,
                contentDescription = null,
                tint = acento,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (grupo.esCobro) "Por cobrar" else "Por pagar",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = acento
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = grupo.resumen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = acento
            )
        }

        grupo.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = AppTheme.colores.borde)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    item.titulo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colores.texto
                )
                if (item.detalle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.detalle,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = AppTheme.colores.textoMedio
                    )
                }
                if (item.esParcial) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pago por partes",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colores.textoSobreAcento,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppTheme.colores.advertencia)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Botón de silenciar de la esquina.
 *
 * Corta el sonido al instante sin cerrar el aviso: el usuario deja de ser
 * molestado pero conserva delante el detalle de los pendientes. Una vez
 * silenciado queda deshabilitado, como indicador de que la alarma sigue en pie
 * pero ya no suena; para quitarla del todo está "Apagar alarma".
 */
@Composable
private fun BotonSilenciar(
    sonando:     Boolean,
    onSilenciar: () -> Unit,
    modifier:    Modifier = Modifier
) {
    val fondo = if (sonando) MaterialTheme.colorScheme.error else AppTheme.colores.superficieTenue
    val tinta = if (sonando) MaterialTheme.colorScheme.onError else AppTheme.colores.textoSuave

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = sonando, onClick = onSilenciar),
        shape           = CircleShape,
        color           = fondo,
        border          = if (sonando) null else BorderStroke(1.dp, AppTheme.colores.borde),
        shadowElevation = if (sonando) 6.dp else 0.dp
    ) {
        Row(
            modifier          = Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (sonando) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (sonando) "Silenciar alarma" else "Alarma silenciada",
                tint               = tinta,
                modifier           = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = if (sonando) "Silenciar" else "Silenciada",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = tinta,
                maxLines   = 1
            )
        }
    }
}

@Composable
private fun PantallaAlarmaActiva(
    titulo:      String,
    descripcion: String,
    sonando:     Boolean,
    onSilenciar: () -> Unit,
    onApagar:    () -> Unit
) {
    val pulso = rememberInfiniteTransition(label = "pulso")
    val escalaAnimada by pulso.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "escala"
    )
    // Al silenciar, el círculo deja de latir: la pantalla se calma con el sonido.
    val escala = if (sonando) escalaAnimada else 1f

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize()) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(escala)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier           = Modifier.size(46.dp),
                    tint               = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text       = titulo,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                color      = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // Cada grupo (cobrar/pagar) y cada registro van en su propio bloque, con
            // scroll perezoso: la alarma puede traer decenas de pendientes.
            val grupos = remember(descripcion) { parsearDetalle(descripcion) }

            if (grupos.isEmpty()) {
                // Formato inesperado: se muestra el texto tal cual antes que nada.
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie)
                ) {
                    Text(
                        text       = descripcion,
                        fontSize   = 15.sp,
                        lineHeight = 21.sp,
                        color      = AppTheme.colores.texto,
                        modifier   = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    grupos.forEach { grupo ->
                        item(key = grupo.etiqueta) { GrupoAlarmaCard(grupo) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onApagar,
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Apagar alarma", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Va por encima del contenido y anclado a la esquina para tenerlo a mano
        // en cuanto suena, sin buscar el botón de apagar al pie de la pantalla.
        BotonSilenciar(
            sonando     = sonando,
            onSilenciar = onSilenciar,
            modifier    = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        )
      }
    }
}
