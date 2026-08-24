package com.example.myapplication.ui.pagos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.appear
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.R
import com.example.myapplication.data.model.ConceptoIndividual
import com.example.myapplication.data.model.MovimientoIndividual
import com.example.myapplication.data.model.UiState
import com.example.myapplication.util.aMonto
import com.example.myapplication.util.aMontoOrNull

// Acentos de la sección. Son propiedades composables para seguir el tema activo.
private val IndAzul: Color
    @Composable get() = AppTheme.colores.dorado
private val IndVerde: Color
    @Composable get() = AppTheme.colores.exitoFuerte
private val IndRojo: Color
    @Composable get() = AppTheme.colores.peligroFuerte

// Semáforo por proximidad al vencimiento (igual que cobros/servicios): rojo, amarillo, verde.
private data class SemColores(val fondo: Color, val borde: Color, val texto: Color)

@Composable
private fun coloresPorVencimiento(diasRestantes: Int): SemColores = with(AppTheme.colores) {
    when {
        // diasRestantes == 0 (vence hoy) cuenta como vencido: hoy es la fecha límite.
        diasRestantes <= 0 -> SemColores(peligroContenedor, peligro, peligroTexto)            // vencido
        diasRestantes <= 5 -> SemColores(advertenciaContenedorAlt, advertenciaBorde, advertencia) // por vencer
        else               -> SemColores(exitoContenedor, exito, exitoTexto)                  // al día
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SECCIÓN INDIVIDUAL (ingresos o gastos según `tipo`)
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionIndividual(vm: PagosViewModel, tipo: String) {
    val esIngreso = tipo == "ingreso"
    val acento = if (esIngreso) IndVerde else IndRojo
    val verbo  = if (esIngreso) "Cobrar" else "Pagar"

    var subtab by remember(tipo) { mutableStateOf(0) }

    val pendState by vm.movIndividualState.collectAsStateWithLifecycle()
    val realState by vm.movIndividualRealizadosState.collectAsStateWithLifecycle()
    val cptState  by vm.conceptosIndividualState.collectAsStateWithLifecycle()
    val accion    by vm.accionIndividualState.collectAsStateWithLifecycle()

    var movARegistrar by remember { mutableStateOf<MovimientoIndividual?>(null) }
    var movARevertir  by remember { mutableStateOf<MovimientoIndividual?>(null) }
    var movAPosponer  by remember { mutableStateOf<MovimientoIndividual?>(null) }
    var movDetalle    by remember { mutableStateOf<MovimientoIndividual?>(null) }
    var cptAEliminar  by remember { mutableStateOf<ConceptoIndividual?>(null) }
    var cptAEditar    by remember { mutableStateOf<ConceptoIndividual?>(null) }
    var nuevoConcepto by remember { mutableStateOf(false) }
    var mensaje       by remember { mutableStateOf<String?>(null) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subtab, tipo) {
        when (subtab) {
            0 -> vm.cargarMovimientos(tipo)
            1 -> vm.cargarMovimientosRealizados(tipo)
            2 -> vm.cargarConceptos(tipo)
        }
    }
    LaunchedEffect(accion) {
        when (val a = accion) {
            is UiState.Success -> { mensaje = a.data; vm.resetAccionIndividualState() }
            is UiState.Error -> { errorMsg = a.message; vm.resetAccionIndividualState() }
            else -> Unit
        }
    }

    // Para ingresos: tocar la tarjeta abre el detalle con llamada/WhatsApp.
    val onDetalle: ((MovimientoIndividual) -> Unit)? = if (esIngreso) ({ m -> movDetalle = m }) else null

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subtab, containerColor = AppTheme.colores.superficie, contentColor = acento) {
            listOf("Pendientes", "Realizados", "Conceptos").forEachIndexed { i, t ->
                Tab(
                    selected = subtab == i,
                    onClick = { subtab = i },
                    text = { Text(t, fontWeight = if (subtab == i) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(AppTheme.colores.fondo)) {
            when (subtab) {
                0 -> ListaMovimientosPendientes(pendState, acento, verbo, mostrarPosponer = esIngreso, onRegistrar = { movARegistrar = it }, onPosponer = { movAPosponer = it }, onDetalle = onDetalle)
                1 -> ListaMovimientosRealizados(realState, onRevertir = { movARevertir = it }, onDetalle = onDetalle)
                else -> ListaConceptos(
                    cptState, esIngreso, acento,
                    onNuevo     = { nuevoConcepto = true },
                    onEditar    = { cptAEditar = it },
                    onEliminar  = { cptAEliminar = it },
                    onRestaurar = { vm.restaurarConcepto(it.idConcepto, tipo) }
                )
            }
        }
    }

    // Mensaje de éxito
    mensaje?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensaje = null },
            confirmButton = { TextButton(onClick = { mensaje = null }) { Text("OK") } },
            title = { Text("Listo") },
            text = { Text(msg) }
        )
    }

    // Mensaje de error (antes la acción fallaba en silencio)
    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("Entendido") } },
            title = { Text("No se pudo completar") },
            text = { Text(msg) }
        )
    }

    // Detalle del ingreso (llamada / WhatsApp)
    movDetalle?.let { mov ->
        DialogoDetalleIngreso(mov) { movDetalle = null }
    }

    // Posponer recordatorio de este movimiento
    movAPosponer?.let { mov ->
        PosponerRecordatorioDialog(
            clave = "movimiento:${mov.idConcepto}:${mov.anio}-${mov.mes}",
            titulo = mov.nombre,
            onDismiss = { movAPosponer = null }
        )
    }

    // Registrar (cobrar / pagar) — confirmación simple
    movARegistrar?.let { mov ->
        DialogoRegistrar(mov, acento,
            onConfirm = { monto ->
                // Sin método, sin descripción y sin celular: solo confirma el cobro/pago.
                vm.registrarMovimiento(mov.idConcepto, mov.idMovimiento, monto, "Efectivo", null, tipo, null)
                movARegistrar = null
            },
            onDismiss = { movARegistrar = null }
        )
    }

    // Revertir
    movARevertir?.let { mov ->
        AlertDialog(
            onDismissRequest = { movARevertir = null },
            confirmButton = {
                Button(
                    onClick = { mov.idMovimiento?.let { vm.revertirMovimiento(it, tipo) }; movARevertir = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.advertencia)
                ) { Text("Sí, revertir") }
            },
            dismissButton = { TextButton(onClick = { movARevertir = null }) { Text("Cancelar") } },
            title = { Text("Revertir registro") },
            text = { Text("¿Revertir ${mov.nombre} (${mov.nombreMes} ${mov.anio})?") }
        )
    }

    // Eliminar concepto
    cptAEliminar?.let { cpt ->
        AlertDialog(
            onDismissRequest = { cptAEliminar = null },
            confirmButton = {
                Button(
                    onClick = { vm.eliminarConcepto(cpt.idConcepto, tipo); cptAEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRojo)
                ) { Text("Sí, eliminar") }
            },
            dismissButton = { TextButton(onClick = { cptAEliminar = null }) { Text("Cancelar") } },
            title = { Text("Eliminar concepto") },
            text = { Text("¿Eliminar \"${cpt.nombre}\"? Dejará de generar movimientos.") }
        )
    }

    // Nuevo concepto
    if (nuevoConcepto) {
        DialogoConcepto(esIngreso, acento, conceptoExistente = null,
            onConfirm = { nombre, desc, monto, dia, precioFijo, celular ->
                vm.crearConcepto(tipo, nombre, desc, monto, dia, precioFijo, celular)
                nuevoConcepto = false
            },
            onDismiss = { nuevoConcepto = false }
        )
    }

    // Editar concepto
    cptAEditar?.let { cpt ->
        DialogoConcepto(esIngreso, acento, conceptoExistente = cpt,
            onConfirm = { nombre, desc, monto, dia, precioFijo, celular ->
                vm.editarConcepto(cpt.idConcepto, tipo, nombre, desc, monto, dia, precioFijo, celular)
                cptAEditar = null
            },
            onDismiss = { cptAEditar = null }
        )
    }
}

// ── Lista de pendientes ───────────────────────────────────────────────────────
@Composable
private fun ListaMovimientosPendientes(
    state: UiState<List<MovimientoIndividual>>,
    acento: Color, verbo: String,
    mostrarPosponer: Boolean,
    onRegistrar: (MovimientoIndividual) -> Unit,
    onPosponer: (MovimientoIndividual) -> Unit,
    onDetalle: ((MovimientoIndividual) -> Unit)? = null
) {
    when (val s = state) {
        is UiState.Success -> {
            if (s.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay ${if (verbo == "Cobrar") "cobros" else "pagos"} pendientes.", color = AppTheme.colores.textoSuave)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(s.data, key = { _, it -> "${it.idConcepto}-${it.mes}-${it.anio}" }) { index, mov ->
                        val clickMod = onDetalle?.let { cb -> Modifier.clickable { cb(mov) } } ?: Modifier
                        val col = coloresPorVencimiento(mov.diasRestantes)
                        Card(
                            Modifier.fillMaxWidth().appear(index).then(clickMod),
                            colors = CardDefaults.cardColors(containerColor = col.fondo),
                            border = BorderStroke(1.dp, col.borde.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(col.borde), contentAlignment = Alignment.Center) {
                                    Text(mov.nombre.take(1).uppercase(), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(mov.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(mov.etiquetaDias, fontSize = 12.sp, color = col.texto, fontWeight = FontWeight.ExtraBold)
                                    Text("${mov.nombreMes} ${mov.anio} · Día ${mov.dia}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                    if (onDetalle != null) Text("Toca para ver detalle / contactar", fontSize = 10.sp, color = IndAzul)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Button(
                                        onClick = { onRegistrar(mov) },
                                        colors = ButtonDefaults.buttonColors(containerColor = col.borde),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) { Text("$verbo S/ ${"%.2f".format(mov.montoMostrar)}", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                                    if (mostrarPosponer) {
                                        Button(
                                            onClick = { onPosponer(mov) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.botonNeutro, contentColor = AppTheme.colores.botonNeutroTexto),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) { Text("Posponer", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
        else -> Unit
    }
}

// ── Lista de realizados ───────────────────────────────────────────────────────
@Composable
private fun ListaMovimientosRealizados(
    state: UiState<List<MovimientoIndividual>>,
    onRevertir: (MovimientoIndividual) -> Unit,
    onDetalle: ((MovimientoIndividual) -> Unit)? = null
) {
    when (val s = state) {
        is UiState.Success -> {
            if (s.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin registros.", color = AppTheme.colores.textoSuave) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(s.data, key = { _, it -> it.idMovimiento ?: "${it.idConcepto}-${it.mes}-${it.anio}" }) { index, mov ->
                        val clickMod = onDetalle?.let { cb -> Modifier.clickable { cb(mov) } } ?: Modifier
                        Card(
                            Modifier.fillMaxWidth().appear(index).then(clickMod),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                            border = BorderStroke(1.dp, IndVerde.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(mov.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("S/ ${"%.2f".format(mov.montoPagado?.toDoubleOrNull() ?: mov.montoMostrar)}", fontSize = 12.sp, color = IndVerde, fontWeight = FontWeight.Bold)
                                    Text("${mov.nombreMes} ${mov.anio} · ${mov.metodoPago ?: "—"}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                    if (mov.fechaRegistro != null) Text("Registrado: ${mov.fechaRegistro.take(10)}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                }
                                TextButton(onClick = { onRevertir(mov) }) {
                                    Text("Revertir", color = AppTheme.colores.advertencia, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
        else -> Unit
    }
}

// ── Lista de conceptos ────────────────────────────────────────────────────────
@Composable
private fun ListaConceptos(
    state: UiState<List<ConceptoIndividual>>,
    esIngreso: Boolean, acento: Color,
    onNuevo: () -> Unit,
    onEditar: (ConceptoIndividual) -> Unit,
    onEliminar: (ConceptoIndividual) -> Unit,
    onRestaurar: (ConceptoIndividual) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = onNuevo,
            colors = ButtonDefaults.buttonColors(containerColor = acento),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("Nuevo ${if (esIngreso) "ingreso" else "gasto"}", fontWeight = FontWeight.Bold) }

        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay ${if (esIngreso) "ingresos" else "gastos"} configurados.", color = AppTheme.colores.textoSuave)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(s.data, key = { _, it -> it.idConcepto }) { index, cpt ->
                            Box(Modifier.appear(index)) {
                                if (cpt.eliminado) ConceptoEliminadoCard(cpt, onRestaurar)
                                else ConceptoCard(cpt, esIngreso, acento, onEditar, onEliminar)
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
            else -> Unit
        }
    }
}

// Tarjeta de concepto activo (editar / eliminar)
@Composable
private fun ConceptoCard(
    cpt: ConceptoIndividual, esIngreso: Boolean, acento: Color,
    onEditar: (ConceptoIndividual) -> Unit, onEliminar: (ConceptoIndividual) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cpt.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (!cpt.descripcion.isNullOrBlank()) Text(cpt.descripcion, fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                Text("S/ ${cpt.monto} · Día ${cpt.diaVencimiento}", fontSize = 12.sp, color = acento, fontWeight = FontWeight.Bold)
                val notas = buildList {
                    if (!cpt.precioFijo) add("Precio variable")
                    if (esIngreso && !cpt.celular.isNullOrBlank()) add("📞 ${cpt.celular}")
                }
                if (notas.isNotEmpty()) Text(notas.joinToString(" · "), fontSize = 11.sp, color = AppTheme.colores.textoSuave)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEditar(cpt) }) {
                    Icon(Icons.Default.Edit, "Editar", tint = IndAzul, modifier = Modifier.size(20.dp))
                }
                TextButton(onClick = { onEliminar(cpt) }) { Text("Eliminar", color = IndRojo, fontSize = 12.sp) }
            }
        }
    }
}

// Tarjeta de concepto en papelera (atenuada, con deshacer + cuenta regresiva)
@Composable
private fun ConceptoEliminadoCard(cpt: ConceptoIndividual, onRestaurar: (ConceptoIndividual) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficieTenue),
        border = BorderStroke(1.dp, AppTheme.colores.bordeTenue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cpt.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTheme.colores.textoSuave)
                Text("Eliminado · ${textoCuentaRegresiva(cpt.minutosParaBorrado ?: 0)}", fontSize = 11.sp, color = IndRojo)
            }
            Button(
                onClick = { onRestaurar(cpt) },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.advertencia),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Deshacer", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// Cuenta regresiva (minutos) que baja sola cada minuto desde el valor del servidor.
@Composable
private fun textoCuentaRegresiva(minIniciales: Int): String {
    var restante by remember(minIniciales) { mutableStateOf(minIniciales) }
    LaunchedEffect(minIniciales) {
        while (restante > 0) { kotlinx.coroutines.delay(60_000); restante -= 1 }
    }
    val h = restante / 60
    val m = restante % 60
    return when {
        restante <= 0 -> "se eliminará en breve"
        h > 0         -> "se borra en ${h}h ${m}m"
        else          -> "se borra en ${m}m"
    }
}

// ── Detalle del cobro (mismo diseño que el detalle del inquilino) ─────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoDetalleIngreso(mov: MovimientoIndividual, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Cobro", fontWeight = FontWeight.Black, fontSize = 22.sp, color = IndAzul)
            Spacer(Modifier.height(16.dp))

            // Concepto
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Concepto", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(mov.nombre, fontWeight = FontWeight.Bold) }
            }

            // Celular con botones de llamar / WhatsApp
            if (!mov.celular.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = IndAzul)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Celular", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text(mov.celular, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(IndAzul).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${mov.celular}"))) }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Phone, "Llamar", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colores.whatsapp).clickable {
                            val num = mov.celular.replace(Regex("[^\\d]"), "")
                            val waNum = if (num.length == 9) "51$num" else num
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum"))) }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp)) }
                }
            }

            // Período
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.DateRange, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Período", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text("${mov.nombreMes} ${mov.anio} · Día ${mov.dia}", fontWeight = FontWeight.Bold) }
            }

            // Monto
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Payments, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Monto", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text("S/ ${"%.2f".format(mov.montoMostrar)}", fontWeight = FontWeight.Bold) }
            }

            // Descripción
            if (!mov.descripcion.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Info, null, tint = IndAzul)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Descripción", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(mov.descripcion, fontWeight = FontWeight.Bold) }
                }
            }

            if (mov.celular.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Sin celular registrado. Ingrésalo al cobrar este ingreso.", fontSize = 12.sp, color = AppTheme.colores.textoSuave)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Diálogo: registrar movimiento (confirmación simple) ───────────────────────
// Solo confirma cobro/pago. Oculta método de pago y descripción. Pide monto solo
// si el concepto es de precio variable; en ingresos permite editar/agregar celular.
@Composable
private fun DialogoRegistrar(
    mov: MovimientoIndividual, acento: Color,
    onConfirm: (monto: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val accion = if (mov.esIngreso) "cobro" else "pago"
    var monto by remember { mutableStateOf("%.2f".format(mov.montoMostrar)) }
    val montoEditable = !mov.precioFijo
    val montoValido = monto.aMontoOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(monto.aMontoOrNull()) },
                enabled = montoValido,
                colors = ButtonDefaults.buttonColors(containerColor = acento)
            ) { Text("Sí, registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Registrar $accion") },
        text = {
            Column {
                Text(
                    "¿Registrar el $accion de \"${mov.nombre}\" por S/ ${"%.2f".format(mov.montoMostrar)}?",
                    fontSize = 14.sp
                )
                Text("${mov.nombreMes} ${mov.anio}", fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                // Único input posible: el monto, y solo si el concepto es de precio variable.
                if (montoEditable) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = monto, onValueChange = { monto = it },
                        label = { Text("Monto (S/)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }
    )
}

// ── Diálogo: crear o editar concepto ──────────────────────────────────────────
// Si [conceptoExistente] != null, precarga los valores y actúa como edición.
@Composable
private fun DialogoConcepto(
    esIngreso: Boolean, acento: Color,
    conceptoExistente: ConceptoIndividual?,
    onConfirm: (nombre: String, desc: String?, monto: Double, dia: Int, precioFijo: Boolean, celular: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val esEdicion = conceptoExistente != null
    var nombre by remember { mutableStateOf(conceptoExistente?.nombre ?: "") }
    var desc by remember { mutableStateOf(conceptoExistente?.descripcion ?: "") }
    var monto by remember { mutableStateOf(conceptoExistente?.monto ?: "") }
    var dia by remember { mutableStateOf(conceptoExistente?.diaVencimiento?.toString() ?: "") }
    var celular by remember { mutableStateOf(conceptoExistente?.celular ?: "") }
    var esFijo by remember { mutableStateOf(conceptoExistente?.precioFijo ?: true) }
    val valido = nombre.isNotBlank() && monto.aMontoOrNull() != null && (dia.toIntOrNull() ?: 0) in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(nombre, desc.ifBlank { null }, monto.aMonto(), dia.toInt(), esFijo, celular.ifBlank { null }) },
                enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = acento)
            ) { Text(if (esEdicion) "Actualizar" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(if (esEdicion) "Editar ${if (esIngreso) "ingreso" else "gasto"}" else "Nuevo ${if (esIngreso) "ingreso" else "gasto"}") },
        text = {
            Column {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it },
                    label = { Text(if (esIngreso) "Nombre (Ej. Internet - Juan)" else "Nombre (Ej. Netflix)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción (opcional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = monto, onValueChange = { monto = it }, label = { Text("Monto mensual (S/)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dia, onValueChange = { dia = it }, label = { Text("Día de vencimiento (1-31)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (esIngreso) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = celular, onValueChange = { celular = it },
                        label = { Text("Celular del cliente (opcional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = esFijo, onCheckedChange = { esFijo = it })
                        Text("El precio es fijo")
                    }
                }
            }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  RESUMEN INDIVIDUAL
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SeccionResumenIndividual(vm: PagosViewModel) {
    val state by vm.resumenIndividualState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarResumenIndividual() }

    Box(Modifier.fillMaxSize().background(AppTheme.colores.fondo)) {
        when (val s = state) {
            is UiState.Success -> {
                val r = s.data
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResumenCard("Ingresos del mes", r.ingresos, IndVerde, "${r.numIngresos} registro(s)")
                    ResumenCard("Gastos del mes", r.gastos, IndRojo, "${r.numGastos} registro(s)")
                    ResumenCard("Balance", r.balance, if (r.balance >= 0) IndVerde else IndRojo, null)
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
            else -> Unit
        }
    }
}

@Composable
private fun ResumenCard(titulo: String, valor: Double, color: Color, subtitulo: String?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border = BorderStroke(2.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(titulo, fontSize = 13.sp, color = AppTheme.colores.textoSuave)
            Text("S/ ${"%.2f".format(valor)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
            if (subtitulo != null) Text(subtitulo, fontSize = 11.sp, color = AppTheme.colores.textoSuave)
        }
    }
}
