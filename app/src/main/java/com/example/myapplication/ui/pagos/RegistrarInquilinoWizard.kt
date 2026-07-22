// ─── ui/pagos/RegistrarInquilinoWizard.kt ─────────────────────────────────────
// Asistente para alquilar un cuarto (registrar inquilino). Replica el formulario
// web de registro: Datos personales → Contrato y detalle → Garantía → Servicios.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.CuartoLibre
import com.example.myapplication.data.model.ServicioNuevo
import com.example.myapplication.data.model.UiState
import com.example.myapplication.util.aMonto
import com.example.myapplication.util.aMontoOrNull
import java.time.Instant
import java.time.ZoneOffset

private val WzAzul  = Color(0xFF8A6A12)
private val WzVerde = Color(0xFF2E7D32)
private val WzNaranja = Color(0xFFED6C02)

private val DIAS_LIMPIEZA = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrarInquilinoWizard(
    vm: PagosViewModel,
    cuarto: CuartoLibre,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val accionState by vm.registrarInquilinoState.collectAsStateWithLifecycle()
    val isLoading = accionState is UiState.Loading

    var step by remember { mutableStateOf(0) } // 0 datos · 1 contrato · 2 garantía · 3 servicios

    // Datos personales
    var nombre by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Contrato y detalle
    var fechaPago by remember { mutableStateOf("") }      // día 1-31
    var diaLimpieza by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var generarProximoMes by remember { mutableStateOf(false) } // esNuevoCheck
    var fechaIngreso by remember { mutableStateOf("") }   // yyyy-MM-dd, opcional (ingreso real)

    // Garantía
    var garantiaPagada by remember { mutableStateOf<Boolean?>(null) }
    var fechaEsperada by remember { mutableStateOf("") }  // yyyy-MM-dd

    // Servicios
    var tieneServicio by remember { mutableStateOf<Boolean?>(null) }
    val servicios = remember { mutableStateListOf<ServicioNuevo>() }

    // Cierra el wizard cuando el registro fue exitoso.
    LaunchedEffect(accionState) {
        if (accionState is UiState.Success) onSuccess((accionState as UiState.Success<String>).data)
    }

    // Validación por paso
    val dniValido = dni.length == 8
    val celularValido = celular.length == 9
    val paso0Valido = nombre.isNotBlank() && apellidos.isNotBlank() && dniValido && celularValido
    val diaPagoNum = fechaPago.toIntOrNull()
    val paso1Valido = (diaPagoNum != null && diaPagoNum in 1..31) && diaLimpieza.isNotBlank()
    val paso2Valido = garantiaPagada == true || (garantiaPagada == false && fechaEsperada.isNotBlank())
    val paso3Valido = tieneServicio == false || (tieneServicio == true && servicios.isNotEmpty())

    fun cerrar() {
        if (isLoading) return
        vm.resetRegistrarInquilinoState()
        onDismiss()
    }

    Dialog(
        onDismissRequest = { cerrar() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
            Column(Modifier.fillMaxSize()) {
                // ── Barra superior ──
                TopAppBar(
                    title = { Text("Alquilar Cuarto ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { cerrar() }, enabled = !isLoading) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                // ── Resumen del cuarto (siempre visible) ──
                CuartoResumen(cuarto)

                // ── Indicador de paso ──
                val titulos = listOf("Datos personales", "Contrato y detalle", "Garantía", "Servicios")
                Text(
                    "Paso ${step + 1} de 4 · ${titulos[step]}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WzAzul,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )

                // ── Contenido del paso ──
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        0 -> PasoDatos(
                            nombre, { nombre = filtrarLetras(it) },
                            apellidos, { apellidos = filtrarLetras(it) },
                            dni, { dni = it.filter(Char::isDigit).take(8) },
                            celular, { celular = it.filter(Char::isDigit).take(9) },
                            email, { email = it }
                        )
                        1 -> PasoContrato(
                            fechaPago, { v -> fechaPago = v.filter(Char::isDigit).take(2) },
                            diaLimpieza, { diaLimpieza = it },
                            descripcion, { if (it.length <= 90) descripcion = it },
                            generarProximoMes, { generarProximoMes = it },
                            fechaIngreso, { fechaIngreso = it }
                        )
                        2 -> PasoGarantia(
                            cuarto, garantiaPagada,
                            onElegir = { pagada -> garantiaPagada = pagada; if (pagada) fechaEsperada = "" },
                            fechaEsperada, { fechaEsperada = it }
                        )
                        else -> PasoServicios(
                            tieneServicio, { tieneServicio = it; if (it == false) servicios.clear() },
                            servicios,
                            onAgregar = { s -> servicios.add(s) },
                            onQuitar = { idx -> servicios.removeAt(idx) }
                        )
                    }

                    (accionState as? UiState.Error)?.let { err ->
                        Text(err.message, color = Color.Red, fontSize = 13.sp)
                    }
                }

                // ── Navegación ──
                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = { if (!isLoading) step-- },
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f)
                            ) { Text("Atrás") }
                        }
                        if (step < 3) {
                            val habilitado = when (step) {
                                0 -> paso0Valido; 1 -> paso1Valido; else -> paso2Valido
                            }
                            Button(
                                onClick = { step++ },
                                enabled = habilitado,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = WzAzul)
                            ) { Text("Siguiente") }
                        } else {
                            Button(
                                onClick = {
                                    vm.registrarInquilino(
                                        idCuarto = cuarto.idCuarto,
                                        nombre = nombre.trim(), apellidos = apellidos.trim(),
                                        dni = dni, celular = celular, email = email.ifBlank { null },
                                        fechaPago = diaPagoNum ?: 1, diaLimpieza = diaLimpieza,
                                        descripcion = descripcion.ifBlank { null },
                                        esnuevo = !generarProximoMes,
                                        garantiaPagada = garantiaPagada == true,
                                        fechaEsperadaGarantia = if (garantiaPagada == false) fechaEsperada else null,
                                        servicios = servicios.toList(),
                                        fechaIngreso = fechaIngreso.ifBlank { null }
                                    )
                                },
                                enabled = paso3Valido && !isLoading,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = WzVerde)
                            ) {
                                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Registrar inquilino", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Resumen del cuarto ────────────────────────────────────────────────────────
@Composable
private fun CuartoResumen(cuarto: CuartoLibre) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EFD8)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            ResumenItem("Cuarto", cuarto.nroCuarto, Icons.Default.MeetingRoom, WzAzul)
            ResumenItem("Precio", "S/ ${cuarto.precio?.toDoubleOrNull()?.let { "%.2f".format(it) } ?: "—"}", Icons.Default.Payments, WzVerde)
            ResumenItem("Garantía", "S/ ${cuarto.garantia?.toDoubleOrNull()?.let { "%.2f".format(it) } ?: "—"}", Icons.Default.Shield, WzNaranja)
        }
    }
}

@Composable
private fun ResumenItem(label: String, valor: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Paso 0: Datos personales ──────────────────────────────────────────────────
@Composable
private fun PasoDatos(
    nombre: String, onNombre: (String) -> Unit,
    apellidos: String, onApellidos: (String) -> Unit,
    dni: String, onDni: (String) -> Unit,
    celular: String, onCelular: (String) -> Unit,
    email: String, onEmail: (String) -> Unit
) {
    OutlinedTextField(nombre, onNombre, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(apellidos, onApellidos, label = { Text("Apellidos *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        dni, onDni, label = { Text("DNI *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("${dni.length}/8") },
        isError = dni.isNotEmpty() && dni.length != 8
    )
    OutlinedTextField(
        celular, onCelular, label = { Text("Celular *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = { Text("${celular.length}/9") },
        isError = celular.isNotEmpty() && celular.length != 9
    )
    OutlinedTextField(
        email, onEmail, label = { Text("Email (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
}

// ── Paso 1: Contrato y detalle ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasoContrato(
    fechaPago: String, onFechaPago: (String) -> Unit,
    diaLimpieza: String, onDiaLimpieza: (String) -> Unit,
    descripcion: String, onDescripcion: (String) -> Unit,
    generarProximoMes: Boolean, onGenerarProximoMes: (Boolean) -> Unit,
    fechaIngreso: String, onFechaIngreso: (String) -> Unit
) {
    OutlinedTextField(
        fechaPago, onFechaPago, label = { Text("Día de facturación *") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("Día del mes (1-31)") }
    )

    // Fecha de ingreso (opcional): para inquilinos que entraron en meses anteriores.
    // Si se elige una fecha pasada, el backend genera una deuda por cada mes hasta hoy.
    var showIngresoPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showIngresoPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.DateRange, null)
        Spacer(Modifier.width(8.dp))
        Text(if (fechaIngreso.isBlank()) "Fecha de ingreso (opcional)" else "Ingreso: $fechaIngreso")
    }
    if (fechaIngreso.isBlank()) {
        Text(
            "Si el inquilino entró en un mes anterior, elígela y se generarán las deudas pendientes de cada mes hasta hoy.",
            fontSize = 12.sp, color = Color.Gray
        )
    } else {
        TextButton(onClick = { onFechaIngreso("") }) { Text("Quitar fecha de ingreso") }
    }
    if (showIngresoPicker) {
        // Solo se permiten fechas pasadas o de hoy (una fecha futura no genera deuda atrasada).
        val hoyMillis = System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= hoyMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showIngresoPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onFechaIngreso(fecha.toString()) // yyyy-MM-dd
                    }
                    showIngresoPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showIngresoPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = diaLimpieza.ifBlank { "Seleccione" },
            onValueChange = {}, readOnly = true,
            label = { Text("Día de limpieza *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DIAS_LIMPIEZA.forEach { dia ->
                DropdownMenuItem(text = { Text(dia) }, onClick = { onDiaLimpieza(dia); expanded = false })
            }
        }
    }

    OutlinedTextField(
        descripcion, onDescripcion, label = { Text("Descripción / Notas (opcional)") },
        modifier = Modifier.fillMaxWidth(), minLines = 3,
        supportingText = { Text("${descripcion.length}/90") }
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = generarProximoMes, onCheckedChange = onGenerarProximoMes)
        Spacer(Modifier.width(8.dp))
        Text("Generar recibo desde el próximo mes", fontSize = 14.sp)
    }
}

// ── Paso 2: Garantía ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasoGarantia(
    cuarto: CuartoLibre,
    garantiaPagada: Boolean?,
    onElegir: (Boolean) -> Unit,
    fechaEsperada: String,
    onFechaEsperada: (String) -> Unit
) {
    val montoGarantia = cuarto.garantia?.toDoubleOrNull()
    Text(
        "Garantía del cuarto: S/ ${montoGarantia?.let { "%.2f".format(it) } ?: "—"}",
        fontWeight = FontWeight.Bold, fontSize = 15.sp
    )
    Text("¿El inquilino pagó la garantía?", fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onElegir(true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (garantiaPagada == true) WzVerde else Color.LightGray,
                contentColor = if (garantiaPagada == true) Color.White else Color.DarkGray
            ),
            modifier = Modifier.weight(1f)
        ) { Text("Sí, pagó") }
        Button(
            onClick = { onElegir(false) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (garantiaPagada == false) WzNaranja else Color.LightGray,
                contentColor = if (garantiaPagada == false) Color.White else Color.DarkGray
            ),
            modifier = Modifier.weight(1f)
        ) { Text("No, aún no") }
    }

    if (garantiaPagada == true) {
        Text("✓ La garantía se registrará como pagada al guardar.", color = WzVerde, fontSize = 13.sp)
    }
    if (garantiaPagada == false) {
        var showPicker by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DateRange, null)
            Spacer(Modifier.width(8.dp))
            Text(if (fechaEsperada.isBlank()) "Seleccionar fecha esperada *" else "Fecha esperada: $fechaEsperada")
        }
        Text("Se enviará un recordatorio diario desde esa fecha hasta que pague.", fontSize = 12.sp, color = Color.Gray)

        if (showPicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onFechaEsperada(fecha.toString()) // yyyy-MM-dd
                        }
                        showPicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

// ── Paso 3: Servicios adicionales ─────────────────────────────────────────────
@Composable
private fun PasoServicios(
    tieneServicio: Boolean?,
    onElegir: (Boolean) -> Unit,
    servicios: List<ServicioNuevo>,
    onAgregar: (ServicioNuevo) -> Unit,
    onQuitar: (Int) -> Unit
) {
    Text("¿Tiene algún servicio adicional? (luz, agua, internet, etc.)", fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onElegir(true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tieneServicio == true) WzAzul else Color.LightGray,
                contentColor = if (tieneServicio == true) Color.White else Color.DarkGray
            ),
            modifier = Modifier.weight(1f)
        ) { Text("Sí") }
        Button(
            onClick = { onElegir(false) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tieneServicio == false) WzAzul else Color.LightGray,
                contentColor = if (tieneServicio == false) Color.White else Color.DarkGray
            ),
            modifier = Modifier.weight(1f)
        ) { Text("No") }
    }

    if (tieneServicio == true) {
        var nombre by remember { mutableStateOf("") }
        var monto by remember { mutableStateOf("") }
        val agregable = nombre.isNotBlank() && (monto.aMontoOrNull() ?: -1.0) >= 0.0

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del servicio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            monto, { monto = it }, label = { Text("Monto (S/)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(
            onClick = {
                onAgregar(ServicioNuevo(nombre.trim(), monto.aMonto()))
                nombre = ""; monto = ""
            },
            enabled = agregable,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WzAzul)
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar servicio") }

        if (servicios.isEmpty()) {
            Text("Agrega al menos un servicio o elige \"No\".", fontSize = 12.sp, color = Color.Gray)
        } else {
            servicios.forEachIndexed { idx, s ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0)), shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.nombre, fontWeight = FontWeight.Bold)
                            Text("S/ ${"%.2f".format(s.monto)}", fontSize = 13.sp, color = WzVerde)
                        }
                        IconButton(onClick = { onQuitar(idx) }) {
                            Icon(Icons.Default.Delete, "Quitar", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

// ── util: filtra solo letras y espacios (acentos incluidos) ───────────────────
private fun filtrarLetras(s: String): String =
    s.filter { it.isLetter() || it.isWhitespace() }.take(50)
