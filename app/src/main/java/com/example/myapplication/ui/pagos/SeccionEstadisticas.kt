// ─── ui/pagos/SeccionEstadisticas.kt ─────────────────────────────────────────
// Potencial de ingresos del propietario, calculado sobre el PRECIO DE LOS CUARTOS
// (no sobre lo cobrado). Responde a "cuánto podría ganar", "cuánto gano hoy" y
// "cuánto estoy dejando de ganar", con el desglose por piso.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.EstadisticaPiso
import com.example.myapplication.data.model.EstadisticasMobile
import com.example.myapplication.data.model.UiState
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.appear

@Composable
fun SeccionEstadisticas(vm: PagosViewModel) {
    val state by vm.estadisticasState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarEstadisticas() }

    Box(Modifier.fillMaxSize().background(AppTheme.colores.fondo)) {
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.message, color = AppTheme.colores.error)
            }

            is UiState.Success -> ContenidoEstadisticas(s.data)

            else -> Unit
        }
    }
}

@Composable
private fun ContenidoEstadisticas(datos: EstadisticasMobile) {
    if (datos.cuartos == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aún no tienes cuartos registrados.", color = AppTheme.colores.textoSuave)
        }
        return
    }

    // Qué porcentaje del potencial se está cobrando: resume la salud del negocio
    // en un solo número, sin tener que comparar dos importes a ojo.
    val ocupacion = if (datos.potencial > 0) (datos.actual / datos.potencial).toFloat() else 0f

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TarjetaResumen(
                titulo   = "Ingresos actuales",
                monto    = datos.actual,
                detalle  = "${datos.cuartosAlquilados} de ${datos.cuartos} cuartos alquilados",
                icono    = Icons.Default.TrendingUp,
                acento   = AppTheme.colores.exito,
                fondo    = AppTheme.colores.exitoContenedorTenue,
                destacada = true
            )
        }

        item {
            TarjetaResumen(
                titulo  = "Ingresos potenciales",
                monto   = datos.potencial,
                detalle = "Si se alquilaran los ${datos.cuartos} cuartos",
                icono   = Icons.Default.Savings,
                acento  = AppTheme.colores.dorado,
                fondo   = AppTheme.colores.doradoContenedor
            )
        }

        item {
            TarjetaResumen(
                titulo  = "Ingresos muertos",
                monto   = datos.muerto,
                detalle = "${datos.cuartos - datos.cuartosAlquilados} cuarto(s) sin alquilar",
                icono   = Icons.Default.TrendingDown,
                acento  = AppTheme.colores.advertencia,
                fondo   = AppTheme.colores.advertenciaContenedor
            )
        }

        item { BarraOcupacion(ocupacion) }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Por piso",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AppTheme.colores.dorado
            )
        }

        items(datos.porPiso, key = { it.idPiso }) { piso -> TarjetaPiso(piso) }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Los montos son mensuales y salen del precio de cada cuarto, " +
                    "no de lo efectivamente cobrado.",
                fontSize = 11.sp,
                color = AppTheme.colores.textoSuave
            )
        }
    }
}

@Composable
private fun TarjetaResumen(
    titulo: String,
    monto: Double,
    detalle: String,
    icono: ImageVector,
    acento: Color,
    fondo: Color,
    destacada: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth().appear(),
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = BorderStroke(if (destacada) 2.dp else 1.dp, acento.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, null, tint = acento, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = acento)
                Text(
                    "S/ ${"%.2f".format(monto)}",
                    fontSize = if (destacada) 26.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    color = AppTheme.colores.texto
                )
                Text(detalle, fontSize = 12.sp, color = AppTheme.colores.textoSuave)
            }
        }
    }
}

/** Proporción del potencial que se está cobrando hoy. */
@Composable
private fun BarraOcupacion(ocupacion: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border = BorderStroke(1.dp, AppTheme.colores.borde),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Aprovechamiento",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colores.texto
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(ocupacion * 100).toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AppTheme.colores.exito
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ocupacion.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = AppTheme.colores.exito,
                trackColor = AppTheme.colores.advertenciaContenedor,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Parte del ingreso potencial que hoy se está generando.",
                fontSize = 11.sp,
                color = AppTheme.colores.textoSuave
            )
        }
    }
}

@Composable
private fun TarjetaPiso(piso: EstadisticaPiso) {
    // Un piso lleno no tiene ingreso muerto: se resalta el caso contrario, que es
    // donde el propietario puede actuar.
    val tieneVacios = piso.cuartosLibres > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border = BorderStroke(
            1.dp,
            if (tieneVacios) AppTheme.colores.advertencia.copy(alpha = 0.5f)
            else AppTheme.colores.exito.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${piso.casa} · ${piso.piso}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppTheme.colores.texto
                    )
                    Text(
                        "${piso.cuartosAlquilados}/${piso.cuartos} alquilados",
                        fontSize = 12.sp,
                        color = AppTheme.colores.textoMedio
                    )
                }
                Text(
                    "S/ ${"%.2f".format(piso.actual)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = AppTheme.colores.exito
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatoPiso("Potencial", piso.potencial, AppTheme.colores.dorado, Modifier.weight(1f))
                DatoPiso(
                    "Muerto",
                    piso.muerto,
                    if (tieneVacios) AppTheme.colores.advertencia else AppTheme.colores.textoSuave,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DatoPiso(etiqueta: String, monto: Double, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colores.superficieTenue)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(etiqueta, fontSize = 11.sp, color = AppTheme.colores.textoSuave)
        Text(
            "S/ ${"%.2f".format(monto)}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
    }
}
