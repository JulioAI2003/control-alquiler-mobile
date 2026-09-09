// ─── ui/pagos/HistorialPagosInquilino.kt ─────────────────────────────────────
// Historial de recibos de un inquilino, abierto desde la sección Inquilinos.
//
// Muestra pagados y pendientes en la misma lista, del mes más reciente al más
// antiguo, porque la pregunta que se responde aquí es "¿cómo viene pagando esta
// persona?" y para eso los meses impagos importan tanto como los saldados.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.InquilinoMobile
import com.example.myapplication.data.model.PagoHistorial
import com.example.myapplication.data.model.UiState
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.util.formatearFecha

/** Cómo se lee un recibo de un vistazo: pagado, a medias o sin tocar. */
private enum class EstadoRecibo(val etiqueta: String) {
    PAGADO("Pagado"), PARCIAL("Pago por partes"), PENDIENTE("Pendiente")
}

private fun PagoHistorial.estadoRecibo(): EstadoRecibo = when {
    pagada  -> EstadoRecibo.PAGADO
    enCurso -> EstadoRecibo.PARCIAL
    else    -> EstadoRecibo.PENDIENTE
}

@Composable
private fun colorDe(estado: EstadoRecibo): Pair<Color, Color> = with(AppTheme.colores) {
    when (estado) {
        EstadoRecibo.PAGADO    -> exito to exitoContenedorTenue
        EstadoRecibo.PARCIAL   -> advertencia to advertenciaContenedor
        EstadoRecibo.PENDIENTE -> peligro to peligroContenedorTenue
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialPagosSheet(
    inquilino: InquilinoMobile,
    vm: PagosViewModel,
    onDismiss: () -> Unit
) {
    val state by vm.historialInquilinoState.collectAsStateWithLifecycle()

    LaunchedEffect(inquilino.idInquilino) { vm.cargarHistorialInquilino(inquilino.idInquilino) }

    val pagos = (state as? UiState.Success)?.data.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReceiptLong, null, tint = AppTheme.colores.dorado)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Historial de pagos",
                        fontWeight = FontWeight.Black, fontSize = 20.sp,
                        color = AppTheme.colores.dorado
                    )
                    Text(
                        "${inquilino.nombre} ${inquilino.apellidos} · Cuarto ${inquilino.nroCuarto}",
                        fontSize = 12.sp, color = AppTheme.colores.textoSuave
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                is UiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UiState.Error -> Text(
                    s.message, color = AppTheme.colores.error,
                    fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp)
                )

                is UiState.Success -> {
                    if (pagos.isEmpty()) {
                        Text(
                            "Este inquilino todavía no tiene recibos generados.",
                            fontSize = 13.sp, color = AppTheme.colores.textoSuave,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        ResumenHistorial(pagos)
                        Spacer(Modifier.height(12.dp))
                        // Altura acotada: con dos años de recibos la hoja llegaría al
                        // tope de la pantalla y taparía el botón de cerrar.
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pagos, key = { it.idPago }) { FilaRecibo(it) }
                        }
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Totales de toda la relación con el inquilino, no solo del mes en curso. */
@Composable
private fun ResumenHistorial(pagos: List<PagoHistorial>) {
    val cobrado   = pagos.sumOf { it.pagadoNum }
    val pendiente = pagos.sumOf { it.saldoNum }
    val saldados  = pagos.count { it.pagada }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.doradoContenedor),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp)) {
            DatoResumen("Recibos", "$saldados de ${pagos.size}", AppTheme.colores.doradoContenedorTexto, Modifier.weight(1f))
            DatoResumen("Cobrado", "S/ ${"%.2f".format(cobrado)}", AppTheme.colores.exitoTexto, Modifier.weight(1f))
            DatoResumen("Por cobrar", "S/ ${"%.2f".format(pendiente)}", AppTheme.colores.peligroTexto, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DatoResumen(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(titulo, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppTheme.colores.doradoContenedorTexto)
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun FilaRecibo(pago: PagoHistorial) {
    val estado = pago.estadoRecibo()
    val (acento, fondo) = colorDe(estado)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border   = BorderStroke(1.dp, acento.copy(alpha = 0.45f)),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Cabecera: periodo a la izquierda, estado a la derecha.
            Row(
                Modifier.fillMaxWidth().background(fondo).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${pago.nombreMes} ${pago.anio}",
                    fontSize = 14.sp, fontWeight = FontWeight.Black, color = acento,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    estado.etiqueta,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = AppTheme.colores.textoSobreAcento,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(acento)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Column(Modifier.padding(14.dp)) {
                Linea("Facturado", "S/ ${"%.2f".format(pago.montoFacturadoNum)}", AppTheme.colores.texto)

                if (pago.pagadoNum > 0) {
                    val detalle = if (pago.abonos > 1) " (${pago.abonos} abonos)" else ""
                    Linea("Cobrado", "S/ ${"%.2f".format(pago.pagadoNum)}$detalle", AppTheme.colores.exitoTexto)
                }

                if (pago.saldoNum > 0) {
                    Linea("Saldo pendiente", "S/ ${"%.2f".format(pago.saldoNum)}", AppTheme.colores.peligroTexto)
                }

                // El ajuste llega con signo: positivo bajó el saldo, negativo lo subió.
                if (kotlin.math.abs(pago.ajusteNum) >= 0.005) {
                    val esDescuento = pago.ajusteNum > 0
                    Linea(
                        if (esDescuento) "Descuento" else "Recargo",
                        "S/ ${"%.2f".format(kotlin.math.abs(pago.ajusteNum))}",
                        if (esDescuento) AppTheme.colores.exitoTexto else AppTheme.colores.advertenciaTexto
                    )
                    pago.motivoReajuste?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        if (pago.pagada) {
                            append("Pagado el ${formatearFecha(pago.fechaPago)}")
                            pago.metodoPago?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        } else {
                            append("Vence el ${pago.dia} de ${pago.nombreMes.lowercase()}")
                            pago.fechaCompromiso?.let {
                                append(" · compromiso ${formatearFecha(it)}")
                            }
                        }
                    },
                    fontSize = 11.sp, color = AppTheme.colores.textoSuave
                )
            }
        }
    }
}

@Composable
private fun Linea(titulo: String, valor: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(titulo, fontSize = 12.sp, color = AppTheme.colores.textoMedio, modifier = Modifier.weight(1f))
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
