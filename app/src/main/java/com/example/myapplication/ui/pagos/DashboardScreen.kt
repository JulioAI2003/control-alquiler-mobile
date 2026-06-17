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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.example.myapplication.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EstadoPago
import com.example.myapplication.data.model.Inquilino
import com.example.myapplication.data.model.CuartoLibre
import com.example.myapplication.data.model.InquilinoMobile
import com.example.myapplication.data.model.ServicioCasa
import com.example.myapplication.data.model.UiState
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

// 🎨 PALETA DE COLORES (SEMÁFORO RESTAURADO)
private data class EstadoColores(val fondo: Color, val borde: Color, val texto: Color)
private val coloresVencido    = EstadoColores(Color(0xFFFFCDD2), Color(0xFFD32F2F), Color(0xFFB71C1C))
private val coloresPorVencer  = EstadoColores(Color(0xFFFFF9C4), Color(0xFFF57F17), Color(0xFFE65100))
private val coloresAlDia      = EstadoColores(Color(0xFFC8E6C9), Color(0xFF388E3C), Color(0xFF1B5E20))

private fun Inquilino.colores(): EstadoColores = when (estadoPago) {
    EstadoPago.VENCIDO    -> coloresVencido
    EstadoPago.POR_VENCER -> coloresPorVencer
    EstadoPago.AL_DIA     -> coloresAlDia
}

private fun ServicioCasa.colores(): EstadoColores = when {
    pagado             -> coloresAlDia
    diasRestantes < 0  -> coloresVencido
    diasRestantes <= 5 -> coloresPorVencer
    else               -> coloresAlDia
}

private val AzulPrimario = Color(0xFF1A237E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit, onCambiarPassword: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MyApplication
    val vm: PagosViewModel = viewModel(factory = PagosViewModel.factory(app))
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("pendientes") }

    // Estados para Modales
    var inquilinoDetalle by remember { mutableStateOf<Inquilino?>(null) }
    var inquilinoAConfirmar by remember { mutableStateOf<Inquilino?>(null) }
    var servicioAConfirmar by remember { mutableStateOf<ServicioCasa?>(null) }
    var montoIngresadoServicio by remember { mutableStateOf("") }
    var mensajeExito by remember { mutableStateOf<String?>(null) }

    // Reset monto al abrir/cerrar el diálogo de servicio
    LaunchedEffect(servicioAConfirmar) { montoIngresadoServicio = "" }

    val pagoRapidoState by vm.pagoRapidoState.collectAsStateWithLifecycle()
    val pagarServicioState by vm.pagarServicioState.collectAsStateWithLifecycle()

    LaunchedEffect(pagoRapidoState) {
        if (pagoRapidoState is UiState.Success) {
            mensajeExito = (pagoRapidoState as UiState.Success<String>).data
            vm.resetPagoRapidoState()
        }
    }

    LaunchedEffect(pagarServicioState) {
        if (pagarServicioState is UiState.Success) {
            mensajeExito = (pagarServicioState as UiState.Success<String>).data
            vm.resetPagarServicioState()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                Spacer(Modifier.height(16.dp))
                Text("Cobros App", Modifier.padding(24.dp), fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PendingActions, null) },
                    label = { Text("Pendientes de Cobro") },
                    selected = currentScreen == "pendientes",
                    onClick = { currentScreen = "pendientes"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("Inquilinos Pagados") },
                    selected = currentScreen == "pagados",
                    onClick = { currentScreen = "pagados"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Group, null) },
                    label = { Text("Inquilinos") },
                    selected = currentScreen == "inquilinos",
                    onClick = { currentScreen = "inquilinos"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.MeetingRoom, null) },
                    label = { Text("Cuartos Libres") },
                    selected = currentScreen == "cuartos",
                    onClick = { currentScreen = "cuartos"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ElectricBolt, null) },
                    label = { Text("Servicios") },
                    selected = currentScreen == "servicios",
                    onClick = { currentScreen = "servicios"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 24.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, null) },
                    label = { Text("Cambiar contraseña") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onCambiarPassword() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, null, tint = Color.Red) },
                    label = { Text("Cerrar Sesión", color = Color.Red) },
                    selected = false,
                    onClick = { onLogout() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(when(currentScreen) { "pendientes" -> "Cobros"; "pagados" -> "Pagados"; "servicios" -> "Servicios"; "cuartos" -> "Cuartos Libres"; else -> "Inquilinos" }, fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menú")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.White, titleContentColor = AzulPrimario
                    )
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
                when (currentScreen) {
                    "pendientes" -> ListaPendientes(
                        vm = vm,
                        onCardClick = { inquilinoDetalle = it },
                        onPagarClick = { inquilinoAConfirmar = it }
                    )
                    "pagados"   -> SeccionPagados(vm)
                    "cuartos"   -> SeccionCuartosLibres(vm)
                    "servicios" -> SeccionServicios(vm, onPagarClick = { servicioAConfirmar = it })
                    else        -> SeccionInquilinos(vm)
                }
            }
        }
    }

    // --- MODALES ---

    // 1. Detalle Deslizable (Click en Tarjeta)
    if (inquilinoDetalle != null) {
        DetalleBottomSheet(inquilinoDetalle!!, onDismiss = { inquilinoDetalle = null })
    }

    // 2a. Confirmación pago de inquilino
    if (inquilinoAConfirmar != null) {
        AlertDialog(
            onDismissRequest = { inquilinoAConfirmar = null },
            confirmButton = {
                Button(onClick = {
                    vm.registrarPagoRapido(inquilinoAConfirmar!!)
                    inquilinoAConfirmar = null
                }, colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario)) {
                    Text("Sí, registrar pago")
                }
            },
            dismissButton = { TextButton(onClick = { inquilinoAConfirmar = null }) { Text("Cancelar") } },
            title = { Text("Confirmar Pago") },
            text = { Text("¿Deseas registrar el pago de ${inquilinoAConfirmar?.nombre}?") }
        )
    }

    // 2b. Confirmación pago de servicio
    if (servicioAConfirmar != null) {
        val srv = servicioAConfirmar!!
        val esPrecioFijo = srv.precioFijo
        val montoValido = esPrecioFijo || montoIngresadoServicio.toDoubleOrNull() != null

        AlertDialog(
            onDismissRequest = { servicioAConfirmar = null },
            confirmButton = {
                Button(
                    onClick = {
                        val monto = if (esPrecioFijo) null else montoIngresadoServicio.toDoubleOrNull()
                        vm.pagarServicio(
                            idServicio = srv.idServicio,
                            idPago     = srv.idPago,
                            montoPagado = monto
                        )
                        servicioAConfirmar = null
                    },
                    enabled = montoValido,
                    colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario)
                ) { Text("Sí, registrar pago") }
            },
            dismissButton = { TextButton(onClick = { servicioAConfirmar = null }) { Text("Cancelar") } },
            title = { Text("Confirmar Pago") },
            text = {
                Column {
                    Text("${srv.nombre} · ${srv.nombreMes} ${srv.anio}")
                    Spacer(Modifier.height(8.dp))
                    if (esPrecioFijo) {
                        Text("Monto: S/ ${"%.2f".format(srv.montoReferencial.toDoubleOrNull() ?: 0.0)}")
                    } else {
                        Text(
                            "Precio variable — ingrese el monto real de este mes:",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = montoIngresadoServicio,
                            onValueChange = { v ->
                                montoIngresadoServicio = v.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text("Monto (S/)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }

    // 3. Éxito Central (Al terminar el pago)
    if (mensajeExito != null) {
        AlertDialog(
            onDismissRequest = { mensajeExito = null },
            confirmButton = { Button(onClick = { mensajeExito = null }, Modifier.fillMaxWidth()) { Text("Entendido / Cerrar") } },
            icon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), Color(0xFF4CAF50)) },
            title = { Text(if (mensajeExito!!.contains("revertido", ignoreCase = true)) "¡Pago Revertido!" else "¡Pago Registrado!") },
            text = { Text(mensajeExito!!, fontSize = 16.sp) }
        )
    }
}

@Composable
fun ListaPendientes(vm: PagosViewModel, onCardClick: (Inquilino) -> Unit, onPagarClick: (Inquilino) -> Unit) {
    val state by vm.pagosState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarPagos() }

    when (val s = state) {
        is UiState.Success -> {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.data, key = { it.idPago }) { inquilino ->
                    val colores = inquilino.colores()
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onCardClick(inquilino) },
                        colors = CardDefaults.cardColors(containerColor = colores.fondo),
                        border = BorderStroke(1.dp, colores.borde.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(colores.borde), contentAlignment = Alignment.Center) {
                                Text(inquilino.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(inquilino.nombre, fontWeight = FontWeight.Bold, color = colores.texto, fontSize = 16.sp)
                                Text(inquilino.etiquetaDias, fontSize = 12.sp, color = colores.texto, fontWeight = FontWeight.ExtraBold)
                            }
                            Button(
                                onClick = { onPagarClick(inquilino) },
                                colors = ButtonDefaults.buttonColors(containerColor = colores.borde),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("S/ ${"%.2f".format(inquilino.monto)}", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleBottomSheet(inquilino: Inquilino, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Inquilino", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Nombre", fontSize = 11.sp, color = Color.Gray); Text(inquilino.nombre, fontWeight = FontWeight.Bold) }
            }
            if (!inquilino.celular.isNullOrBlank()) {
                Row(
                    Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Celular", fontSize = 11.sp, color = Color.Gray)
                        Text(inquilino.celular, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AzulPrimario)
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${inquilino.celular}"))
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Phone, "Llamar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                            .clickable {
                                val num = inquilino.celular.replace(Regex("[^\\d]"), "")
                                val waNum = if (num.length == 9) "51$num" else num
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum"))
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Habitación", fontSize = 11.sp, color = Color.Gray); Text(inquilino.habitacion, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Payments, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Saldo Pendiente", fontSize = 11.sp, color = Color.Gray); Text("S/ ${inquilino.monto}", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

@Composable
fun SeccionPagados(vm: PagosViewModel) {
    val state by vm.pagosRecientesState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarPagosRecientes() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pagos realizados recientemente", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Sin pagos en los últimos 10 días", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.data) { pago ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pago.nombre, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Pagado: S/ ${"%.2f".format(pago.montoOriginal)}",
                                            color = Color(0xFF388E3C),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(onClick = { vm.revertirPago(pago.idPago) }) {
                                        Text("Revertir", color = Color(0xFFE65100))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(s.message, color = Color.Red)
            }
            else -> Unit
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN INQUILINOS
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun SeccionInquilinos(vm: PagosViewModel) {
    val state       by vm.inquilinosState.collectAsStateWithLifecycle()
    val retiroState by vm.retiroState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarInquilinos() }

    var inquilinoSeleccionado by remember { mutableStateOf<InquilinoMobile?>(null) }
    var inquilinoARetirar     by remember { mutableStateOf<InquilinoMobile?>(null) }
    var filtroNombre by remember { mutableStateOf("") }

    // Cierra el bottom sheet y limpia el estado cuando la acción de retiro termina
    LaunchedEffect(retiroState) {
        if (retiroState is UiState.Success) {
            inquilinoSeleccionado = null
            vm.resetRetiroState()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inquilinos activos", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = filtroNombre,
            onValueChange = { filtroNombre = it },
            placeholder = { Text("Buscar por nombre…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            trailingIcon = {
                if (filtroNombre.isNotEmpty()) {
                    IconButton(onClick = { filtroNombre = "" }) {
                        Icon(Icons.Default.Close, "Limpiar", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AzulPrimario,
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UiState.Success -> {
                val filtrados = if (filtroNombre.isBlank()) s.data
                else s.data.filter {
                    "${it.nombre} ${it.apellidos}".contains(filtroNombre, ignoreCase = true)
                }
                if (filtrados.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (s.data.isEmpty()) "No hay inquilinos activos"
                            else "Sin resultados para \"$filtroNombre\"",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtrados, key = { it.idInquilino }) { inq ->
                            val esPendiente = inq.estado == "pendiente_retiro"
                            val bgColor     = if (esPendiente) Color(0xFFFFCDD2) else Color(0xFFC8E6C9)
                            val borderColor = if (esPendiente) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            val textColor   = if (esPendiente) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { inquilinoSeleccionado = inq },
                                colors = CardDefaults.cardColors(containerColor = bgColor),
                                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(borderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(inq.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${inq.nombre} ${inq.apellidos}", fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
                                        Text("${inq.casa} · Cuarto ${inq.nroCuarto}", fontSize = 12.sp, color = Color.DarkGray)
                                        if (esPendiente) {
                                            Text(
                                                "Retiro en ${inq.diasParaRetiro ?: 0} día(s)",
                                                fontSize = 11.sp, color = textColor, fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error   -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
            else -> Unit
        }
    }

    // Bottom sheet con detalle + acciones
    if (inquilinoSeleccionado != null) {
        InquilinoBottomSheet(
            inquilino   = inquilinoSeleccionado!!,
            retiroState = retiroState,
            onRetirar        = { inquilinoARetirar = it },
            onCancelarRetiro = { vm.cancelarRetiro(it.idInquilino) },
            onDismiss        = { inquilinoSeleccionado = null; vm.resetRetiroState() }
        )
    }

    // Confirmación antes de iniciar retiro
    if (inquilinoARetirar != null) {
        AlertDialog(
            onDismissRequest = { inquilinoARetirar = null },
            confirmButton = {
                Button(
                    onClick = {
                        vm.iniciarRetiro(inquilinoARetirar!!.idInquilino)
                        inquilinoARetirar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Sí, retirar") }
            },
            dismissButton = { TextButton(onClick = { inquilinoARetirar = null }) { Text("Cancelar") } },
            title = { Text("Confirmar Retiro") },
            text  = { Text("¿Está seguro de retirar a ${inquilinoARetirar?.nombre}? Tendrá 24 horas para cancelar la acción antes de que sea definitivo.") }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN CUARTOS LIBRES
// ──────────────────────────────────────────────────────────────────────────────

private val coloresCuartoLibre = EstadoColores(Color(0xFFBBDEFB), Color(0xFF1565C0), Color(0xFF0D47A1))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionCuartosLibres(vm: PagosViewModel) {
    val state by vm.cuartosLibresState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarCuartosLibres() }

    var cuartoSeleccionado by remember { mutableStateOf<CuartoLibre?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cuartos disponibles", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No hay cuartos libres",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF388E3C)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "¡Todos los cuartos están ocupados!",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.data, key = { it.idCuarto }) { cuarto ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { cuartoSeleccionado = cuarto },
                                colors = CardDefaults.cardColors(containerColor = coloresCuartoLibre.fondo),
                                border = BorderStroke(1.dp, coloresCuartoLibre.borde.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(coloresCuartoLibre.borde),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MeetingRoom, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Cuarto ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold, color = coloresCuartoLibre.texto, fontSize = 16.sp)
                                        Text("${cuarto.casa} · ${cuarto.piso}", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                    val precio = cuarto.precio?.toDoubleOrNull()
                                    if (precio != null) {
                                        Text(
                                            "S/ ${"%.2f".format(precio)}",
                                            fontWeight = FontWeight.Black,
                                            color = coloresCuartoLibre.borde,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error   -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
            else -> Unit
        }
    }

    if (cuartoSeleccionado != null) {
        CuartoBottomSheet(cuarto = cuartoSeleccionado!!, onDismiss = { cuartoSeleccionado = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuartoBottomSheet(cuarto: CuartoLibre, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Cuarto", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.MeetingRoom, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cuarto", fontSize = 11.sp, color = Color.Gray)
                    Text("Nro. ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ubicación", fontSize = 11.sp, color = Color.Gray)
                    Text("${cuarto.casa} · ${cuarto.piso}", fontWeight = FontWeight.Bold)
                }
            }
            val precio = cuarto.precio?.toDoubleOrNull()
            if (precio != null) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Payments, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Precio mensual", fontSize = 11.sp, color = Color.Gray)
                        Text("S/ ${"%.2f".format(precio)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            val garantia = cuarto.garantia?.toDoubleOrNull()
            if (garantia != null && garantia > 0) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Shield, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Garantía", fontSize = 11.sp, color = Color.Gray)
                        Text("S/ ${"%.2f".format(garantia)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!cuarto.descripcion.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Info, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Descripción", fontSize = 11.sp, color = Color.Gray)
                        Text(cuarto.descripcion, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
                    Spacer(Modifier.width(8.dp))
                    Text("Disponible", fontSize = 14.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN SERVICIOS
// ──────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionServicios(vm: PagosViewModel, onPagarClick: (ServicioCasa) -> Unit) {
    val state by vm.serviciosState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarServicios() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Servicios pendientes", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarServicios() },
            state        = pullState,
            modifier     = Modifier.weight(1f)
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay servicios pendientes.\nDesliza hacia abajo para actualizar.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { "${it.idServicio}-${it.mes}-${it.anio}" }) { srv ->
                                val colores = srv.colores()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = colores.fondo),
                                    border = BorderStroke(1.dp, colores.borde.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(colores.borde),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(srv.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.nombre, fontWeight = FontWeight.Bold, color = colores.texto, fontSize = 16.sp)
                                            Text(srv.etiquetaDias, fontSize = 12.sp, color = colores.texto, fontWeight = FontWeight.ExtraBold)
                                            Text("${srv.nombreMes} ${srv.anio} · Día ${srv.dia}", fontSize = 11.sp, color = Color.DarkGray)
                                        }
                                        if (srv.pagado) {
                                            Icon(Icons.Default.CheckCircle, null, tint = colores.borde, modifier = Modifier.size(32.dp))
                                        } else {
                                            Button(
                                                onClick = { onPagarClick(srv) },
                                                colors = ButtonDefaults.buttonColors(containerColor = colores.borde),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text("S/ ${"%.2f".format(srv.montoReferencial.toDoubleOrNull() ?: 0.0)}", fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InquilinoBottomSheet(
    inquilino:        InquilinoMobile,
    retiroState:      UiState<String>,
    onRetirar:        (InquilinoMobile) -> Unit,
    onCancelarRetiro: (InquilinoMobile) -> Unit,
    onDismiss:        () -> Unit
) {
    val esPendiente = inquilino.estado == "pendiente_retiro"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Inquilino", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Nombre", fontSize = 11.sp, color = Color.Gray)
                    Text("${inquilino.nombre} ${inquilino.apellidos}", fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Habitación", fontSize = 11.sp, color = Color.Gray)
                    Text("${inquilino.casa} · ${inquilino.piso} · Cuarto ${inquilino.nroCuarto}", fontWeight = FontWeight.Bold)
                }
            }
            if (inquilino.celular != null) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Celular", fontSize = 11.sp, color = Color.Gray)
                        Text(inquilino.celular, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (esPendiente) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Le quedan ${inquilino.diasParaRetiro ?: 0} día(s) para que el inquilino sea eliminado definitivamente",
                            fontSize = 13.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { onCancelarRetiro(inquilino) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    enabled  = retiroState !is UiState.Loading
                ) {
                    if (retiroState is UiState.Loading)
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("Cancelar Retiro")
                }
            } else {
                Button(
                    onClick  = { onRetirar(inquilino) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    enabled  = retiroState !is UiState.Loading
                ) {
                    if (retiroState is UiState.Loading)
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("Retirar Inquilino")
                }
            }
            if (retiroState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((retiroState as UiState.Error).message, color = Color.Red, fontSize = 13.sp)
            }
        }
    }
}
