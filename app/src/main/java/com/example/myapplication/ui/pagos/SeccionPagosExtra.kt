package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.GastoExtra
import com.example.myapplication.data.model.UiState
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.appear
import com.example.myapplication.util.DatosGastoEscaneado
import com.example.myapplication.util.aMonto
import com.example.myapplication.util.aMontoOrNull
import java.time.LocalDate

private val ExtraAcento: Color
    @Composable get() = AppTheme.colores.peligroFuerte

private fun nombreMes(mes: Int) = listOf(
    "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
    "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
).getOrElse(mes) { mes.toString() }

// ═════════════════════════════════════════════════════════════════════════════
//  PAGOS EXTRA — gastos fuera de lo estimado (registrados a mano o escaneando
//  una captura de Yape compartida desde la Galería). Los "chips" de arriba son
//  el mapeo mensual: cuánto se gastó en cada mes del año en curso.
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionPagosExtra(vm: PagosViewModel) {
    val listaState by vm.gastosExtraState.collectAsStateWithLifecycle()
    val resumenState by vm.resumenGastosExtraState.collectAsStateWithLifecycle()
    val accionState by vm.accionGastoExtraState.collectAsStateWithLifecycle()
    val escaneoState by vm.escaneoGastoState.collectAsStateWithLifecycle()

    val anioActual = remember { LocalDate.now().year }
    var mesFiltro by remember { mutableStateOf<Int?>(null) }
    var mostrarNuevo by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<GastoExtra?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.cargarResumenGastosExtra(anioActual) }
    LaunchedEffect(mesFiltro) { vm.cargarGastosExtra(mes = mesFiltro, anio = anioActual) }

    LaunchedEffect(accionState) {
        when (val a = accionState) {
            is UiState.Success -> { mensaje = a.data; vm.resetAccionGastoExtraState() }
            is UiState.Error -> { errorMsg = a.message; vm.resetAccionGastoExtraState() }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Mapeo mensual: total gastado cada mes del año en curso, con "Todos" para no filtrar.
        val meses = (resumenState as? UiState.Success)?.data?.meses.orEmpty()
        if (meses.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipMes("Todos", mesFiltro == null, meses.sumOf { it.totalDouble }) { mesFiltro = null }
                meses.forEach { m ->
                    ChipMes(nombreMes(m.mes), mesFiltro == m.mes, m.totalDouble) { mesFiltro = m.mes }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = listaState) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sin pagos extra registrados.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(s.data, key = { _, it -> it.idGasto }) { index, gasto ->
                                TarjetaGastoExtra(gasto, Modifier.appear(index)) { aEliminar = gasto }
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = AppTheme.colores.error, modifier = Modifier.padding(24.dp))
                }
                else -> Unit
            }

            FloatingActionButton(
                onClick = { mostrarNuevo = true },
                containerColor = ExtraAcento,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) { Icon(Icons.Default.Add, "Agregar pago extra", tint = AppTheme.colores.textoSobreAcento) }
        }
    }

    // Confirmación tras escanear una captura compartida desde la Galería.
    if (escaneoState !is UiState.Idle) {
        DialogoConfirmarGastoEscaneado(
            estado = escaneoState,
            onGuardar = { monto, asunto, fecha ->
                vm.registrarGastoExtra(monto, asunto, fecha, origen = "yape")
                vm.resetEscaneoGastoState()
            },
            onDescartar = { vm.resetEscaneoGastoState() }
        )
    }

    // Alta manual (botón "+")
    if (mostrarNuevo) {
        DialogoGastoExtraForm(
            titulo = "Nuevo pago extra",
            montoInicial = "", asuntoInicial = "",
            fechaInicial = LocalDate.now().toString(),
            onConfirm = { monto, asunto, fecha ->
                vm.registrarGastoExtra(monto, asunto, fecha)
                mostrarNuevo = false
            },
            onDismiss = { mostrarNuevo = false }
        )
    }

    // Eliminar
    aEliminar?.let { gasto ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            confirmButton = {
                Button(
                    onClick = { vm.eliminarGastoExtra(gasto.idGasto); aEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ExtraAcento)
                ) { Text("Sí, eliminar") }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } },
            title = { Text("Eliminar registro") },
            text = { Text("¿Eliminar \"${gasto.asunto}\" por S/ ${"%.2f".format(gasto.montoDouble)}?") }
        )
    }

    mensaje?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensaje = null },
            confirmButton = { TextButton(onClick = { mensaje = null }) { Text("OK") } },
            title = { Text("Listo") }, text = { Text(msg) }
        )
    }
    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("Entendido") } },
            title = { Text("No se pudo completar") }, text = { Text(msg) }
        )
    }
}

@Composable
private fun ChipMes(etiqueta: String, seleccionado: Boolean, total: Double, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) ExtraAcento else AppTheme.colores.superficie
        ),
        border = BorderStroke(1.dp, ExtraAcento.copy(alpha = if (seleccionado) 0f else 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                etiqueta, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (seleccionado) AppTheme.colores.textoSobreAcento else AppTheme.colores.texto
            )
            Text(
                "S/ ${"%.2f".format(total)}", fontSize = 11.sp,
                color = if (seleccionado) AppTheme.colores.textoSobreAcento else AppTheme.colores.textoSuave
            )
        }
    }
}

@Composable
private fun TarjetaGastoExtra(gasto: GastoExtra, modifier: Modifier = Modifier, onEliminar: () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border = BorderStroke(1.dp, ExtraAcento.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(ExtraAcento.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (gasto.esEscaneado) Icons.Default.PhotoCamera else Icons.Default.Receipt,
                    null, tint = ExtraAcento
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(gasto.asunto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${gasto.fecha.take(10)} · ${gasto.nombreMes}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                if (gasto.esEscaneado) {
                    Text("Escaneado de Yape", fontSize = 10.sp, color = ExtraAcento, fontWeight = FontWeight.Bold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("S/ ${"%.2f".format(gasto.montoDouble)}", fontWeight = FontWeight.Black, color = ExtraAcento, fontSize = 16.sp)
                TextButton(onClick = onEliminar, contentPadding = PaddingValues(0.dp)) {
                    Text("Eliminar", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                }
            }
        }
    }
}

// ── Diálogo: confirmar/editar lo leído de una captura compartida ──────────────
// Mientras escanea muestra un spinner; si reconoce datos, precarga un formulario
// editable; si falla, el mismo formulario aparece vacío con el motivo del error.
// Nunca se guarda sin que el usuario confirme el monto y el asunto.
@Composable
private fun DialogoConfirmarGastoEscaneado(
    estado: UiState<DatosGastoEscaneado>,
    onGuardar: (monto: Double, asunto: String, fecha: String) -> Unit,
    onDescartar: () -> Unit
) {
    when (estado) {
        is UiState.Loading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Leyendo la captura...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Extrayendo el monto, el asunto y la fecha de la imagen")
                }
            }
        )
        is UiState.Success -> DialogoGastoExtraForm(
            titulo = "Confirmar pago extra",
            montoInicial = estado.data.monto?.let { "%.2f".format(it) } ?: "",
            asuntoInicial = estado.data.asunto ?: "",
            fechaInicial = estado.data.fecha,
            notaEscaneo = "Se extrajeron de la imagen compartida. Completa a mano lo que haya quedado vacío antes de guardar.",
            onConfirm = onGuardar,
            onDismiss = onDescartar
        )
        is UiState.Error -> DialogoGastoExtraForm(
            titulo = "Confirmar pago extra",
            montoInicial = "", asuntoInicial = "", fechaInicial = null,
            notaEscaneo = estado.message,
            onConfirm = onGuardar,
            onDismiss = onDescartar
        )
        else -> Unit
    }
}

// ── Diálogo: formulario de monto/asunto/fecha, reusado por el alta manual y por
//    la confirmación tras escanear. Los 3 campos son obligatorios: si el escaneo no
//    encontró alguno, queda vacío y hay que completarlo a mano antes de guardar. ──
@Composable
private fun DialogoGastoExtraForm(
    titulo: String,
    montoInicial: String,
    asuntoInicial: String,
    fechaInicial: String? = null,
    notaEscaneo: String? = null,
    onConfirm: (monto: Double, asunto: String, fecha: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var monto by remember { mutableStateOf(montoInicial) }
    var asunto by remember { mutableStateOf(asuntoInicial) }
    var fecha by remember { mutableStateOf(fechaInicial ?: "") }
    val montoValido = (monto.aMontoOrNull() ?: 0.0) > 0
    val valido = montoValido && asunto.isNotBlank() && fecha.isNotBlank()

    fun abrirSelectorFecha() {
        val ld = runCatching { LocalDate.parse(fecha) }.getOrDefault(LocalDate.now())
        android.app.DatePickerDialog(
            context,
            { _, y, m, d -> fecha = "%04d-%02d-%02d".format(y, m + 1, d) },
            ld.year, ld.monthValue - 1, ld.dayOfMonth
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(monto.aMonto(), asunto.trim(), fecha) },
                enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = ExtraAcento)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(titulo) },
        text = {
            Column {
                if (notaEscaneo != null) {
                    Text(notaEscaneo, fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = monto,
                    onValueChange = { v -> monto = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Monto (S/)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = asunto, onValueChange = { asunto = it },
                    label = { Text("Asunto (Ej. Ferretería, taxi...)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { abrirSelectorFecha() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExtraAcento)
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (fecha.isBlank()) "Elegir fecha" else "Fecha: $fecha")
                }
            }
        }
    )
}
