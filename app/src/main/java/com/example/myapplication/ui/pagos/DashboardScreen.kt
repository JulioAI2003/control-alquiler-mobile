package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EstadoPago
import com.example.myapplication.data.model.Inquilino
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

private val AzulPrimario = Color(0xFF1A237E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val app = LocalContext.current.applicationContext as MyApplication
    val vm: PagosViewModel = viewModel(factory = PagosViewModel.factory(app))
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("pendientes") }

    // Estados para Modales
    var inquilinoDetalle by remember { mutableStateOf<Inquilino?>(null) }
    var inquilinoAConfirmar by remember { mutableStateOf<Inquilino?>(null) }
    var mensajeExito by remember { mutableStateOf<String?>(null) }

    val pagoRapidoState by vm.pagoRapidoState.collectAsStateWithLifecycle()

    LaunchedEffect(pagoRapidoState) {
        if (pagoRapidoState is UiState.Success) {
            mensajeExito = (pagoRapidoState as UiState.Success<String>).data
            vm.resetPagoRapidoState()
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 24.dp))
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
                    title = { Text(if(currentScreen == "pendientes") "Cobros" else "Pagados", fontWeight = FontWeight.ExtraBold) },
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
                if (currentScreen == "pendientes") {
                    ListaPendientes(
                        vm = vm, 
                        onCardClick = { inquilinoDetalle = it },
                        onPagarClick = { inquilinoAConfirmar = it }
                    )
                } else {
                    SeccionPagados(vm)
                }
            }
        }
    }

    // --- MODALES ---

    // 1. Detalle Deslizable (Click en Tarjeta)
    if (inquilinoDetalle != null) {
        DetalleBottomSheet(inquilinoDetalle!!, onDismiss = { inquilinoDetalle = null })
    }

    // 2. Confirmación (Click en Botón S/)
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
                                Text(inquilino.habitacion, fontSize = 11.sp, color = Color.DarkGray)
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Inquilino", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Nombre", fontSize = 11.sp, color = Color.Gray); Text(inquilino.nombre, fontWeight = FontWeight.Bold) }
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
