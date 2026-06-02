// ─── ui/pagos/DashboardScreen.kt ─────────────────────────────────────────────
package com.example.myapplication.ui.pagos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

// ═════════════════════════════════════════════════════════════════════════════
//  PALETA DE COLORES POR ESTADO
// ═════════════════════════════════════════════════════════════════════════════

private data class EstadoColores(
    val fondo: Color,
    val borde: Color,
    val texto: Color
)

private val coloresVencido    = EstadoColores(Color(0xFFFFCDD2), Color(0xFFD32F2F), Color(0xFFB71C1C))
private val coloresPorVencer  = EstadoColores(Color(0xFFFFF9C4), Color(0xFFF57F17), Color(0xFFE65100))
private val coloresAlDia      = EstadoColores(Color(0xFFC8E6C9), Color(0xFF388E3C), Color(0xFF1B5E20))

private fun Inquilino.colores(): EstadoColores = when (estadoPago) {
    EstadoPago.VENCIDO    -> coloresVencido
    EstadoPago.POR_VENCER -> coloresPorVencer
    EstadoPago.AL_DIA     -> coloresAlDia
}

// ═════════════════════════════════════════════════════════════════════════════
//  DASHBOARD SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val app = LocalContext.current.applicationContext as MyApplication
    val vm: PagosViewModel = viewModel(factory = PagosViewModel.factory(app))

    val pagosState      by vm.pagosState.collectAsStateWithLifecycle()
    val pagoRapidoState by vm.pagoRapidoState.collectAsStateWithLifecycle()

    // Estado local de la pantalla
    var inquilinoDetalle    by remember { mutableStateOf<Inquilino?>(null) }
    var showLogoutDialog    by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.cargarPagos() }

    // Feedback del pago rápido via Snackbar
    LaunchedEffect(pagoRapidoState) {
        when (val s = pagoRapidoState) {
            is UiState.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message  = "✅ ${s.data}",
                        duration = SnackbarDuration.Short
                    )
                }
                vm.resetPagoRapidoState()
            }
            is UiState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message  = "❌ ${s.message}",
                        duration = SnackbarDuration.Short
                    )
                }
                vm.resetPagoRapidoState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Pagos Pendientes",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Text(
                            text     = "Control de Alquileres",
                            color    = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.cargarPagos() }) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, "Cerrar sesión", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1565C0))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when (val state = pagosState) {

                // ── Cargando ──────────────────────────────────────────────────
                is UiState.Loading -> {
                    Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1565C0))
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando pagos...", color = Color(0xFF757575))
                    }
                }

                // ── Error de red ──────────────────────────────────────────────
                is UiState.Error -> {
                    Column(
                        modifier            = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.WifiOff, null,
                            modifier = Modifier.size(64.dp),
                            tint     = Color(0xFFBDBDBD)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text       = "Sin conexión",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text     = state.message,
                            color    = Color(0xFF757575),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { vm.cargarPagos() }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                    }
                }

                // ── Lista de pagos ────────────────────────────────────────────
                is UiState.Success -> {
                    val lista = state.data

                    if (lista.isEmpty()) {
                        Column(
                            modifier            = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉", fontSize = 52.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "¡Sin pagos pendientes!",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp
                            )
                            Text(
                                "Todo al día",
                                color    = Color(0xFF757575),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding      = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cabecera con resumen de colores
                            item { ResumenHeader(lista) }

                            // Tarjetas de inquilinos
                            items(lista, key = { it.idPago }) { inquilino ->
                                InquilinoCard(
                                    inquilino    = inquilino,
                                    isPagando    = pagoRapidoState is UiState.Loading,
                                    onPagar      = { vm.registrarPagoRapido(it) },
                                    onVerDetalle = { inquilinoDetalle = it }
                                )
                            }

                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    // ── Bottom Sheet de detalle ───────────────────────────────────────────────
    inquilinoDetalle?.let { detalle ->
        DetalleBottomSheet(
            inquilino = detalle,
            onDismiss = { inquilinoDetalle = null }
        )
    }

    // ── Diálogo de logout ─────────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon   = { Icon(Icons.Default.Logout, null, tint = Color(0xFFD32F2F)) },
            title  = { Text("¿Cerrar sesión?") },
            text   = { Text("Se eliminará la sesión guardada en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        app.sessionDataStore.limpiarSesion()
                        app.updateToken(null)
                    }
                    onLogout()
                }) {
                    Text("Salir", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  RESUMEN DE CABECERA
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ResumenHeader(lista: List<Inquilino>) {
    val vencidos  = lista.count { it.estadoPago == EstadoPago.VENCIDO    }
    val proximos  = lista.count { it.estadoPago == EstadoPago.POR_VENCER }
    val alDia     = lista.count { it.estadoPago == EstadoPago.AL_DIA     }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResumenChip("$vencidos vencidos",  coloresVencido.borde,   Modifier.weight(1f))
        ResumenChip("$proximos próximos",  coloresPorVencer.borde, Modifier.weight(1f))
        ResumenChip("$alDia al día",       coloresAlDia.borde,     Modifier.weight(1f))
    }
}

@Composable
private fun ResumenChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(50.dp),
        color    = color.copy(alpha = 0.12f),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text       = label,
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .fillMaxWidth()
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  TARJETA DE INQUILINO
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun InquilinoCard(
    inquilino:    Inquilino,
    isPagando:    Boolean,
    onPagar:      (Inquilino) -> Unit,
    onVerDetalle: (Inquilino) -> Unit
) {
    val colores  = inquilino.colores()
    val iniciales = inquilino.nombre.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        border    = BorderStroke(2.dp, colores.borde),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors    = CardDefaults.cardColors(containerColor = colores.fondo)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Fila superior: Avatar + Nombre + Período + Badge días ──────────
            Row(
                verticalAlignment  = Alignment.CenterVertically,
                modifier           = Modifier.fillMaxWidth()
            ) {
                // Avatar circular con iniciales
                Box(
                    modifier         = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colores.borde),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = iniciales,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = inquilino.nombre,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = colores.texto
                    )
                    Text(
                        text     = inquilino.habitacion,
                        fontSize = 12.sp,
                        color    = Color(0xFF616161)
                    )
                    Text(
                        text     = "${inquilino.nombreMes} ${inquilino.periodoAnio}",
                        fontSize = 11.sp,
                        color    = Color(0xFF757575)
                    )
                }

                // Badge de días restantes
                DiasBadge(inquilino)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colores.borde.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            // ── Montos ────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Bottom
            ) {
                Column {
                    Text("Saldo pendiente", fontSize = 11.sp, color = Color(0xFF757575))
                    Text(
                        text       = "S/ ${"%.2f".format(inquilino.monto)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp,
                        color      = colores.borde
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total mes", fontSize = 11.sp, color = Color(0xFF757575))
                    Text(
                        text     = "S/ ${"%.2f".format(inquilino.montoOriginal)}",
                        fontSize = 14.sp,
                        color    = Color(0xFF424242)
                    )
                    if (inquilino.esParcial) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colores.borde.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Pago parcial",
                                color    = colores.borde,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Botones ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ─ Botón PAGAR (pago rápido, un toque) ───────────────────────
                Button(
                    onClick  = { onPagar(inquilino) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape   = RoundedCornerShape(10.dp),
                    enabled = !isPagando,
                    colors  = ButtonDefaults.buttonColors(containerColor = colores.borde)
                ) {
                    AnimatedVisibility(visible = isPagando, enter = fadeIn(), exit = fadeOut()) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    AnimatedVisibility(visible = !isPagando, enter = fadeIn(), exit = fadeOut()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pagar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // ─ Botón VER DETALLE ──────────────────────────────────────────
                OutlinedButton(
                    onClick  = { onVerDetalle(inquilino) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, colores.borde),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colores.borde)
                ) {
                    Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver Detalle", fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Badge de días ────────────────────────────────────────────────────────────

@Composable
private fun DiasBadge(inquilino: Inquilino) {
    val colores = inquilino.colores()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colores.borde.copy(alpha = 0.18f)
    ) {
        Text(
            text       = inquilino.etiquetaDias,
            color      = colores.texto,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  BOTTOM SHEET DE DETALLE
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleBottomSheet(inquilino: Inquilino, onDismiss: () -> Unit) {
    val colores    = inquilino.colores()
    val formatter  = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Encabezado del sheet
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colores.borde),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        inquilino.nombre.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                            .joinToString(""),
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        inquilino.nombre,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp
                    )
                    Text(
                        "${inquilino.nombreMes} ${inquilino.periodoAnio}",
                        color    = Color(0xFF757575),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Filas de detalle
            DetalleRow(Icons.Outlined.Home,          "Habitación",      inquilino.habitacion)
            DetalleRow(Icons.Outlined.Payments,      "Saldo pendiente", "S/ ${"%.2f".format(inquilino.monto)}")
            DetalleRow(Icons.Outlined.Receipt,       "Total del mes",   "S/ ${"%.2f".format(inquilino.montoOriginal)}")
            DetalleRow(Icons.Outlined.CalendarToday, "Vencimiento",     inquilino.fechaVencimiento.format(formatter))
            if (!inquilino.celular.isNullOrBlank()) {
                DetalleRow(Icons.Outlined.Phone, "Celular", inquilino.celular)
            }

            Spacer(Modifier.height(12.dp))

            // Badge de estado con color completo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                color    = colores.fondo,
                border   = BorderStroke(1.5.dp, colores.borde)
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icono, etiqueta) = when (inquilino.estadoPago) {
                        EstadoPago.VENCIDO    -> Icons.Default.Warning to "VENCIDO — ${inquilino.etiquetaDias}"
                        EstadoPago.POR_VENCER -> Icons.Default.AccessTime to "POR VENCER — ${inquilino.etiquetaDias}"
                        EstadoPago.AL_DIA     -> Icons.Default.CheckCircle to "AL DÍA — ${inquilino.etiquetaDias}"
                    }
                    Icon(icono, null, tint = colores.borde, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(etiqueta, color = colores.texto, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp)
            ) { Text("Cerrar", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetalleRow(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF9E9E9E))
            Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
}
