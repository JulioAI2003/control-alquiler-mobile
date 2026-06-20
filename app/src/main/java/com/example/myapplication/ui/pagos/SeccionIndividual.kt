package com.example.myapplication.ui.pagos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val IndAzul  = Color(0xFF1A237E)
private val IndVerde = Color(0xFF2E7D32)
private val IndRojo  = Color(0xFFC62828)

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
    var movDetalle    by remember { mutableStateOf<MovimientoIndividual?>(null) }
    var cptAEliminar  by remember { mutableStateOf<ConceptoIndividual?>(null) }
    var nuevoConcepto by remember { mutableStateOf(false) }
    var mensaje       by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subtab, tipo) {
        when (subtab) {
            0 -> vm.cargarMovimientos(tipo)
            1 -> vm.cargarMovimientosRealizados(tipo)
            2 -> vm.cargarConceptos(tipo)
        }
    }
    LaunchedEffect(accion) {
        if (accion is UiState.Success) {
            mensaje = (accion as UiState.Success<String>).data
            vm.resetAccionIndividualState()
        }
    }

    // Para ingresos: tocar la tarjeta abre el detalle con llamada/WhatsApp.
    val onDetalle: ((MovimientoIndividual) -> Unit)? = if (esIngreso) ({ m -> movDetalle = m }) else null

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subtab, containerColor = Color.White, contentColor = acento) {
            listOf("Pendientes", "Realizados", "Conceptos").forEachIndexed { i, t ->
                Tab(
                    selected = subtab == i,
                    onClick = { subtab = i },
                    text = { Text(t, fontWeight = if (subtab == i) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF5F5F5))) {
            when (subtab) {
                0 -> ListaMovimientosPendientes(pendState, acento, verbo, onRegistrar = { movARegistrar = it }, onDetalle = onDetalle)
                1 -> ListaMovimientosRealizados(realState, onRevertir = { movARevertir = it }, onDetalle = onDetalle)
                else -> ListaConceptos(cptState, esIngreso, acento, { nuevoConcepto = true }) { cptAEliminar = it }
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

    // Detalle del ingreso (llamada / WhatsApp)
    movDetalle?.let { mov ->
        DialogoDetalleIngreso(mov) { movDetalle = null }
    }

    // Registrar (cobrar / pagar)
    movARegistrar?.let { mov ->
        DialogoRegistrar(mov, verbo, acento,
            onConfirm = { monto, metodo, desc, celular ->
                vm.registrarMovimiento(mov.idConcepto, mov.idMovimiento, monto, metodo, desc, tipo, celular)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
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
        DialogoNuevoConcepto(esIngreso, acento,
            onConfirm = { nombre, desc, monto, dia, precioFijo, celular ->
                vm.crearConcepto(tipo, nombre, desc, monto, dia, precioFijo, celular)
                nuevoConcepto = false
            },
            onDismiss = { nuevoConcepto = false }
        )
    }
}

// ── Lista de pendientes ───────────────────────────────────────────────────────
@Composable
private fun ListaMovimientosPendientes(
    state: UiState<List<MovimientoIndividual>>,
    acento: Color, verbo: String,
    onRegistrar: (MovimientoIndividual) -> Unit,
    onDetalle: ((MovimientoIndividual) -> Unit)? = null
) {
    when (val s = state) {
        is UiState.Success -> {
            if (s.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay ${if (verbo == "Cobrar") "cobros" else "pagos"} pendientes.", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.data, key = { "${it.idConcepto}-${it.mes}-${it.anio}" }) { mov ->
                        val clickMod = onDetalle?.let { cb -> Modifier.clickable { cb(mov) } } ?: Modifier
                        Card(
                            Modifier.fillMaxWidth().then(clickMod),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, acento.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(acento), contentAlignment = Alignment.Center) {
                                    Text(mov.nombre.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(mov.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(mov.etiquetaDias, fontSize = 12.sp, color = acento, fontWeight = FontWeight.ExtraBold)
                                    Text("${mov.nombreMes} ${mov.anio} · Día ${mov.dia}", fontSize = 11.sp, color = Color.DarkGray)
                                    if (onDetalle != null) Text("Toca para ver detalle / contactar", fontSize = 10.sp, color = IndAzul)
                                }
                                Button(
                                    onClick = { onRegistrar(mov) },
                                    colors = ButtonDefaults.buttonColors(containerColor = acento),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) { Text("$verbo S/ ${"%.2f".format(mov.montoMostrar)}", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin registros.", color = Color.Gray) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.data, key = { it.idMovimiento ?: "${it.idConcepto}-${it.mes}-${it.anio}" }) { mov ->
                        val clickMod = onDetalle?.let { cb -> Modifier.clickable { cb(mov) } } ?: Modifier
                        Card(
                            Modifier.fillMaxWidth().then(clickMod),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, IndVerde.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(mov.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("S/ ${"%.2f".format(mov.montoPagado?.toDoubleOrNull() ?: mov.montoMostrar)}", fontSize = 12.sp, color = IndVerde, fontWeight = FontWeight.Bold)
                                    Text("${mov.nombreMes} ${mov.anio} · ${mov.metodoPago ?: "—"}", fontSize = 11.sp, color = Color.DarkGray)
                                    if (mov.fechaRegistro != null) Text("Registrado: ${mov.fechaRegistro.take(10)}", fontSize = 11.sp, color = Color.DarkGray)
                                }
                                TextButton(onClick = { onRevertir(mov) }) {
                                    Text("Revertir", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
        else -> Unit
    }
}

// ── Lista de conceptos ────────────────────────────────────────────────────────
@Composable
private fun ListaConceptos(
    state: UiState<List<ConceptoIndividual>>,
    esIngreso: Boolean, acento: Color,
    onNuevo: () -> Unit,
    onEliminar: (ConceptoIndividual) -> Unit
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
                        Text("No hay ${if (esIngreso) "ingresos" else "gastos"} configurados.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.data, key = { it.idConcepto }) { cpt ->
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cpt.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (!cpt.descripcion.isNullOrBlank()) Text(cpt.descripcion, fontSize = 11.sp, color = Color.DarkGray)
                                        Text("S/ ${cpt.monto} · Día ${cpt.diaVencimiento}", fontSize = 12.sp, color = acento, fontWeight = FontWeight.Bold)
                                        val notas = buildList {
                                            if (!cpt.precioFijo) add("Precio variable")
                                            if (esIngreso && !cpt.celular.isNullOrBlank()) add("📞 ${cpt.celular}")
                                        }
                                        if (notas.isNotEmpty()) Text(notas.joinToString(" · "), fontSize = 11.sp, color = Color.Gray)
                                    }
                                    TextButton(onClick = { onEliminar(cpt) }) { Text("Eliminar", color = IndRojo, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
            else -> Unit
        }
    }
}

// ── Detalle del cobro (mismo diseño que el detalle del inquilino) ─────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoDetalleIngreso(mov: MovimientoIndividual, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Cobro", fontWeight = FontWeight.Black, fontSize = 22.sp, color = IndAzul)
            Spacer(Modifier.height(16.dp))

            // Concepto
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Concepto", fontSize = 11.sp, color = Color.Gray); Text(mov.nombre, fontWeight = FontWeight.Bold) }
            }

            // Celular con botones de llamar / WhatsApp
            if (!mov.celular.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = IndAzul)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Celular", fontSize = 11.sp, color = Color.Gray)
                        Text(mov.celular, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(IndAzul).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${mov.celular}"))) }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Phone, "Llamar", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF25D366)).clickable {
                            val num = mov.celular.replace(Regex("[^\\d]"), "")
                            val waNum = if (num.length == 9) "51$num" else num
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum"))) }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }

            // Período
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.DateRange, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Período", fontSize = 11.sp, color = Color.Gray); Text("${mov.nombreMes} ${mov.anio} · Día ${mov.dia}", fontWeight = FontWeight.Bold) }
            }

            // Monto
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Payments, null, tint = IndAzul)
                Spacer(Modifier.width(12.dp))
                Column { Text("Monto", fontSize = 11.sp, color = Color.Gray); Text("S/ ${"%.2f".format(mov.montoMostrar)}", fontWeight = FontWeight.Bold) }
            }

            // Descripción
            if (!mov.descripcion.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Info, null, tint = IndAzul)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Descripción", fontSize = 11.sp, color = Color.Gray); Text(mov.descripcion, fontWeight = FontWeight.Bold) }
                }
            }

            if (mov.celular.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Sin celular registrado. Ingrésalo al cobrar este ingreso.", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Diálogo: registrar movimiento ─────────────────────────────────────────────
@Composable
private fun DialogoRegistrar(
    mov: MovimientoIndividual, verbo: String, acento: Color,
    onConfirm: (monto: Double?, metodo: String, desc: String?, celular: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var monto by remember { mutableStateOf("%.2f".format(mov.montoMostrar)) }
    var metodo by remember { mutableStateOf("efectivo") }
    var desc by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf(mov.celular ?: "") }
    val montoEditable = !mov.precioFijo
    val montoValido = monto.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(monto.toDoubleOrNull(), metodo, desc.ifBlank { null }, celular.ifBlank { null }) },
                enabled = montoValido,
                colors = ButtonDefaults.buttonColors(containerColor = acento)
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("$verbo: ${mov.nombre}") },
        text = {
            Column {
                Text("${mov.nombreMes} ${mov.anio}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = monto, onValueChange = { if (montoEditable) monto = it },
                    label = { Text("Monto (S/)") }, singleLine = true, enabled = montoEditable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (!montoEditable) ({ Text("Precio fijo") }) else null
                )
                if (mov.esIngreso) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = celular, onValueChange = { celular = it },
                        label = { Text("Celular del cliente") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = metodo, onValueChange = { metodo = it }, label = { Text("Método") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción (opcional)") })
            }
        }
    )
}

// ── Diálogo: nuevo concepto ───────────────────────────────────────────────────
@Composable
private fun DialogoNuevoConcepto(
    esIngreso: Boolean, acento: Color,
    onConfirm: (nombre: String, desc: String?, monto: Double, dia: Int, precioFijo: Boolean, celular: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var esFijo by remember { mutableStateOf(true) }
    val valido = nombre.isNotBlank() && monto.toDoubleOrNull() != null && (dia.toIntOrNull() ?: 0) in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(nombre, desc.ifBlank { null }, monto.toDouble(), dia.toInt(), esFijo, celular.ifBlank { null }) },
                enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = acento)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Nuevo ${if (esIngreso) "ingreso" else "gasto"}") },
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

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
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
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
            else -> Unit
        }
    }
}

@Composable
private fun ResumenCard(titulo: String, valor: Double, color: Color, subtitulo: String?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(titulo, fontSize = 13.sp, color = Color.Gray)
            Text("S/ ${"%.2f".format(valor)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
            if (subtitulo != null) Text(subtitulo, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
