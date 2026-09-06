package com.example.myapplication.ui.pagos

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.appear
import com.example.myapplication.ui.theme.bounceClick
import com.example.myapplication.ui.onboarding.CoachStep
import com.example.myapplication.ui.onboarding.CoachmarkOverlay
import com.example.myapplication.ui.onboarding.coachAnchor
import com.example.myapplication.ui.onboarding.rememberCoachmarkState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EstadoPago
import com.example.myapplication.data.model.Inquilino
import com.example.myapplication.data.model.CuartoLibre
import com.example.myapplication.util.aMontoOrNull
import com.example.myapplication.util.formatearFecha
import com.example.myapplication.util.DescargasPdf
import com.example.myapplication.data.model.InquilinoMobile
import com.example.myapplication.data.model.ServicioCasa
import com.example.myapplication.data.model.ServicioConcepto
import com.example.myapplication.data.model.UiState
import com.example.myapplication.data.model.UsuarioAdmin
import com.example.myapplication.data.model.PagoUsuario
import com.example.myapplication.data.model.GuardarAjustesRequest
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.remote.AlquilerApiClient
import com.example.myapplication.worker.RecordatorioScheduler
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

// 🎨 PALETA DE COLORES (SEMÁFORO RESTAURADO)
// Los tonos salen de AppTheme.colores, que ya resuelve claro/oscuro por token.
private data class EstadoColores(val fondo: Color, val borde: Color, val texto: Color)

@Composable
private fun coloresVencido(): EstadoColores = with(AppTheme.colores) {
    EstadoColores(peligroContenedor, peligro, peligroTexto)
}

@Composable
private fun coloresPorVencer(): EstadoColores = with(AppTheme.colores) {
    EstadoColores(advertenciaContenedorAlt, advertenciaBorde, advertencia)
}

@Composable
private fun coloresAlDia(): EstadoColores = with(AppTheme.colores) {
    EstadoColores(exitoContenedor, exito, exitoTexto)
}

@Composable
private fun Inquilino.colores(): EstadoColores = when (estadoPago) {
    EstadoPago.VENCIDO    -> coloresVencido()
    EstadoPago.POR_VENCER -> coloresPorVencer()
    EstadoPago.AL_DIA     -> coloresAlDia()
}

@Composable
private fun ServicioCasa.colores(): EstadoColores = when {
    pagado             -> coloresAlDia()
    // diasRestantes == 0 (vence hoy) cuenta como vencido: hoy es la fecha límite.
    diasRestantes <= 0 -> coloresVencido()
    diasRestantes <= 5 -> coloresPorVencer()
    else               -> coloresAlDia()
}

/** Dorado de marca. Es una propiedad composable para seguir el tema activo. */
private val AzulPrimario: Color
    @Composable get() = AppTheme.colores.dorado

/**
 * Niveles de tamaño de letra que se ofrecen en Ajustes: multiplicador → nombre.
 *
 * El rango se queda en 85 %–130 % a propósito. El multiplicador solo afecta a los
 * `sp` (el texto), no a los `dp` (los recuadros), así que pasado ese punto los
 * textos largos empiezan a desbordar las tarjetas en pantallas angostas.
 */
private val NIVELES_TEXTO = listOf(
    0.85f to "Pequeña",
    1.00f to "Normal",
    1.15f to "Grande",
    1.30f to "Muy grande",
)

/** Convierte una hora guardada en "HH:mm" (24h) a formato 12 horas con AM/PM (ej. "8:00 a. m."). */
private fun horaEn12h(hm: String): String {
    val partes = hm.split(":")
    val h = partes.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val m = partes.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val sufijo = if (h < 12) "a. m." else "p. m."
    val h12 = when {
        h == 0  -> 12
        h > 12  -> h - 12
        else    -> h
    }
    return "%d:%02d %s".format(h12, m, sufijo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit, onCambiarPassword: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MyApplication
    val vm: PagosViewModel = viewModel(factory = PagosViewModel.factory(app))

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val rol by app.sessionDataStore.rol.collectAsStateWithLifecycle(initialValue = null)
    val nombreUsuario by app.sessionDataStore.nombre.collectAsStateWithLifecycle(initialValue = null)
    val userId by app.sessionDataStore.userId.collectAsStateWithLifecycle(initialValue = null)
    val esAdmin = rol == "Administrador"
    val esIndividual = rol == "Individual"

    // Indicaciones de ayuda (coach-marks). El efecto que las dispara está más abajo,
    // tras declarar `currentScreen` (cada vista tiene su propio tutorial).
    val coach = rememberCoachmarkState()
    var currentScreen by remember { mutableStateOf(if (esAdmin) "admin_usuarios" else "pendientes") }
    LaunchedEffect(esAdmin) { if (esAdmin && currentScreen == "pendientes") currentScreen = "admin_usuarios" }
    LaunchedEffect(esIndividual) { if (esIndividual && currentScreen == "pendientes") currentScreen = "individual_ingresos" }

    // Cada vista tiene su propio tutorial, mostrado una sola vez por usuario. La vista
    // principal usa el flag de onboarding; las demás secciones, su propio flag.
    LaunchedEffect(currentScreen, userId, rol) {
        val uid = userId ?: return@LaunchedEffect
        val r = rol ?: return@LaunchedEffect
        if (currentScreen in SECCIONES_CON_TUTORIAL) {
            if (!app.sessionDataStore.haVistoTutorial(uid, currentScreen).first()) {
                delay(500)
                coach.start(pasosDeAyuda(currentScreen, r))
                app.sessionDataStore.marcarTutorialVisto(uid, currentScreen)
            }
        } else {
            if (!app.sessionDataStore.haVistoOnboarding(uid).first()) {
                delay(800) // deja que se midan las posiciones de los botones
                coach.start(pasosDeAyuda(currentScreen, r))
                app.sessionDataStore.marcarOnboardingVisto(uid)
            }
        }
    }

    // Estados para Modales
    var inquilinoDetalle by remember { mutableStateOf<Inquilino?>(null) }
    var inquilinoAConfirmar by remember { mutableStateOf<Inquilino?>(null) }
    var servicioAConfirmar by remember { mutableStateOf<ServicioCasa?>(null) }
    var montoIngresadoServicio by remember { mutableStateOf("") }
    var mensajeExito by remember { mutableStateOf<String?>(null) }
    // Mensaje de error de acción (registrar pago/servicio/retiro): antes era silencioso.
    var mensajeError by remember { mutableStateOf<String?>(null) }

    // Reset monto al abrir/cerrar el diálogo de servicio
    LaunchedEffect(servicioAConfirmar) { montoIngresadoServicio = "" }

    val pagoRapidoState by vm.pagoRapidoState.collectAsStateWithLifecycle()
    val pagarServicioState by vm.pagarServicioState.collectAsStateWithLifecycle()
    val retiroStateGlobal by vm.retiroState.collectAsStateWithLifecycle()

    LaunchedEffect(pagoRapidoState) {
        when (val s = pagoRapidoState) {
            is UiState.Success -> { mensajeExito = s.data; vm.resetPagoRapidoState() }
            is UiState.Error -> { mensajeError = s.message; vm.resetPagoRapidoState() }
            else -> Unit
        }
    }

    LaunchedEffect(pagarServicioState) {
        when (val s = pagarServicioState) {
            is UiState.Success -> { mensajeExito = s.data; vm.resetPagarServicioState() }
            is UiState.Error -> { mensajeError = s.message; vm.resetPagarServicioState() }
            else -> Unit
        }
    }

    LaunchedEffect(retiroStateGlobal) {
        val s = retiroStateGlobal
        if (s is UiState.Success && inquilinoDetalle != null) {
            mensajeExito = s.data
            inquilinoDetalle = null
            vm.resetRetiroState()
        } else if (s is UiState.Error) {
            mensajeError = s.message
            vm.resetRetiroState()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = AppTheme.colores.superficie) {
                Spacer(Modifier.height(16.dp))
                Text("Cobros App", Modifier.padding(24.dp), fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
                // Estado del submenú "Pagos registrados" (agrupa cobros y servicios pagados).
                // Empieza abierto si ya estás viendo una de esas secciones.
                var pagosMenuExpandido by remember {
                    mutableStateOf(currentScreen == "pagados" || currentScreen == "servicios_pagados")
                }
                if (!esAdmin && !esIndividual) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Inicio") },
                        selected = currentScreen in listOf("pendientes", "servicios", "cuartos"),
                        onClick = { currentScreen = "pendientes"; scope.launch { drawerState.close() } },
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
                        label = { Text("Cuartos") },
                        selected = currentScreen == "cuartos_todos",
                        onClick = { currentScreen = "cuartos_todos"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CleaningServices, null) },
                        label = { Text("Limpieza") },
                        selected = currentScreen == "limpieza",
                        onClick = { currentScreen = "limpieza"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BarChart, null) },
                        label = { Text("Estadísticas") },
                        selected = currentScreen == "estadisticas",
                        onClick = { currentScreen = "estadisticas"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    // Submenú "Pagos registrados": agrupa el histórico de cobros de
                    // inquilinos y el de servicios (antes eran dos ítems sueltos).
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.History, null) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Pagos registrados", modifier = Modifier.weight(1f))
                                Icon(
                                    if (pagosMenuExpandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        },
                        selected = currentScreen in listOf("pagados", "servicios_pagados"),
                        onClick = { pagosMenuExpandido = !pagosMenuExpandido },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    if (pagosMenuExpandido) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Group, null) },
                            label = { Text("Inquilinos Pagados") },
                            selected = currentScreen == "pagados",
                            onClick = { currentScreen = "pagados"; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(start = 20.dp).padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.ReceiptLong, null) },
                            label = { Text("Servicios Pagados") },
                            selected = currentScreen == "servicios_pagados",
                            onClick = { currentScreen = "servicios_pagados"; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(start = 20.dp).padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Ajustes") },
                        selected = currentScreen == "ajustes",
                        onClick = { currentScreen = "ajustes"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (esIndividual) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Inicio") },
                        selected = currentScreen in listOf("individual_ingresos", "individual_gastos", "individual_resumen"),
                        onClick = { currentScreen = "individual_ingresos"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Ajustes") },
                        selected = currentScreen == "ajustes",
                        onClick = { currentScreen = "ajustes"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (esAdmin) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.People, null) },
                        label = { Text("Usuarios") },
                        selected = currentScreen == "admin_usuarios",
                        onClick = { currentScreen = "admin_usuarios"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Payment, null) },
                        label = { Text("Pagos Pendientes") },
                        selected = currentScreen == "admin_pagos",
                        onClick = { currentScreen = "admin_pagos"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text("Pagos Registrados") },
                        selected = currentScreen == "admin_pagos_realizados",
                        onClick = { currentScreen = "admin_pagos_realizados"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 24.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, null) },
                    label = { Text("Cambiar contraseña") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onCambiarPassword() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, null, tint = AppTheme.colores.error) },
                    label = { Text("Cerrar Sesión", color = AppTheme.colores.error) },
                    selected = false,
                    onClick = {
                        app.cerrarSesion()   // borra token (memoria) + sesión persistida
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        val tabScreens = if (esIndividual) listOf("individual_ingresos", "individual_gastos", "individual_resumen")
                         else listOf("pendientes", "servicios", "cuartos")
        val tabTitles  = if (esIndividual) listOf("Ingresos", "Gastos", "Resumen")
                         else listOf("Cobros", "Servicios", "Cuartos Libres")
        val selectedTab = tabScreens.indexOf(currentScreen).coerceAtLeast(0)
        val showTabs = !esAdmin && currentScreen in tabScreens

        val screenTitle = when (currentScreen) {
            "pendientes" -> "Cobros"; "pagados" -> "Pagados"; "servicios" -> "Servicios"
            "servicios_pagados" -> "Servicios Pagados"; "cuartos" -> "Cuartos Libres"; "cuartos_todos" -> "Cuartos"
            "admin_usuarios" -> "Usuarios"; "admin_pagos" -> "Pagos Pendientes"
            "admin_pagos_realizados" -> "Pagos Registrados"; "ajustes" -> "Ajustes"
            "estadisticas" -> "Estadísticas"; "limpieza" -> "Limpieza"
            "individual_ingresos" -> "Ingresos"; "individual_gastos" -> "Gastos"; "individual_resumen" -> "Resumen"
            else -> "Inquilinos"
        }

        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            val saludo = "Bienvenido, ${nombreUsuario ?: "Usuario"}"
                            Text(if (showTabs) saludo else screenTitle, fontWeight = FontWeight.ExtraBold, fontSize = if (showTabs) 18.sp else 20.sp)
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.coachAnchor(coach, "menu")
                            ) {
                                Icon(Icons.Default.Menu, "Menú")
                            }
                        },
                        actions = {
                            // Botón para volver a ver las indicaciones (junto al saludo).
                            IconButton(
                                onClick = { coach.start(pasosDeAyuda(currentScreen, rol ?: "")) },
                                modifier = Modifier.coachAnchor(coach, "help")
                            ) {
                                Icon(Icons.Default.HelpOutline, "Ver indicaciones", tint = AzulPrimario)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = AppTheme.colores.superficie, titleContentColor = AzulPrimario
                        )
                    )
                    if (showTabs) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = AppTheme.colores.superficie,
                            contentColor = AzulPrimario
                        ) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick  = { currentScreen = tabScreens[index] },
                                    modifier = Modifier.coachAnchor(coach, "tab_$index"),
                                    text     = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(AppTheme.colores.fondo)) {
                when (currentScreen) {
                    "pendientes" -> ListaPendientes(
                        vm = vm,
                        onCardClick = { inquilinoDetalle = it },
                        onPagarClick = { inquilinoAConfirmar = it }
                    )
                    "pagados"        -> SeccionPagados(vm)
                    "cuartos"        -> SeccionCuartosLibres(vm)
                    "cuartos_todos"  -> SeccionCuartos(vm)
                    "estadisticas"   -> SeccionEstadisticas(vm)
                    "limpieza"       -> SeccionLimpieza(vm)
                    "servicios"          -> SeccionServicios(vm, onPagarClick = { servicioAConfirmar = it })
                    "servicios_pagados"  -> SeccionServiciosPagados(vm)
                    "admin_usuarios"         -> SeccionAdminUsuarios(vm)
                    "admin_pagos"            -> SeccionAdminPagos(vm)
                    "admin_pagos_realizados" -> SeccionAdminPagosRealizados(vm)
                    "ajustes"                -> SeccionAjustes()
                    "individual_ingresos"    -> SeccionIndividual(vm, "ingreso")
                    "individual_gastos"      -> SeccionIndividual(vm, "gasto")
                    "individual_resumen"     -> SeccionResumenIndividual(vm)
                    else                     -> SeccionInquilinos(vm)
                }
            }
        }
    }

    // --- MODALES ---

    // 1. Detalle Deslizable (Click en Tarjeta)
    if (inquilinoDetalle != null) {
        DetalleBottomSheet(inquilinoDetalle!!, vm = vm, onDismiss = { inquilinoDetalle = null })
    }

    // 2a. Confirmación pago de inquilino (con opción de pago por partes)
    if (inquilinoAConfirmar != null) {
        DialogoCobrarInquilino(
            inquilino = inquilinoAConfirmar!!,
            onConfirm = { monto, fechaCompromiso ->
                vm.registrarPagoInquilino(inquilinoAConfirmar!!, monto, fechaCompromiso)
                inquilinoAConfirmar = null
            },
            onDismiss = { inquilinoAConfirmar = null }
        )
    }

    // 2b. Confirmación pago de servicio
    if (servicioAConfirmar != null) {
        val srv = servicioAConfirmar!!
        val esPrecioFijo = srv.precioFijo
        val montoValido = esPrecioFijo || montoIngresadoServicio.aMontoOrNull() != null

        AlertDialog(
            onDismissRequest = { servicioAConfirmar = null },
            confirmButton = {
                Button(
                    onClick = {
                        val monto = if (esPrecioFijo) null else montoIngresadoServicio.aMontoOrNull()
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
                            fontSize = 13.sp, color = AppTheme.colores.textoSuave
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = montoIngresadoServicio,
                            onValueChange = { v ->
                                montoIngresadoServicio = v.filter { it.isDigit() || it == '.' || it == ',' }
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
            icon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), AppTheme.colores.exitoBrillante) },
            title = { Text(if (mensajeExito!!.contains("revertido", ignoreCase = true)) "¡Pago Revertido!" else "¡Pago Registrado!") },
            text = { Text(mensajeExito!!, fontSize = 16.sp) }
        )
    }

    // Error de acción (antes silencioso): el usuario ahora sabe que algo falló.
    if (mensajeError != null) {
        AlertDialog(
            onDismissRequest = { mensajeError = null },
            confirmButton = { Button(onClick = { mensajeError = null }, Modifier.fillMaxWidth()) { Text("Entendido") } },
            icon = { Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), AppTheme.colores.peligro) },
            title = { Text("No se pudo completar") },
            text = { Text(mensajeError!!, fontSize = 16.sp) }
        )
    }

    // Overlay de indicaciones (encima de todo). Se activa solo o con el botón de ayuda.
    CoachmarkOverlay(coach) { }
}

// Secciones (abiertas desde el menú) que tienen su propio tutorial, distinto del
// de la vista principal.
private val SECCIONES_CON_TUTORIAL = setOf(
    "pagados", "inquilinos", "cuartos_todos", "servicios", "servicios_pagados", "ajustes", "limpieza",
    "estadisticas", "admin_pagos", "admin_pagos_realizados"
)

// Devuelve el tutorial correspondiente a la VISTA actual. Las vistas principales
// (pestañas / panel admin) usan la guía por rol; cada sección tiene la suya.
private fun pasosDeAyuda(screen: String, rol: String): List<CoachStep> {
    val repasar = CoachStep("help", "Repasar cuando quieras", "¿Necesitas volver a ver esto? Toca este botón en cualquier momento.")

    // Tutorial propio de cada sección (explica para qué sirve la vista).
    val seccion: List<CoachStep>? = when (screen) {
        "pagados" -> listOf(
            CoachStep(null, "Inquilinos Pagados", "Aquí ves los pagos que tus inquilinos realizaron en los últimos 10 días. Si registraste uno por error, usa \"Revertir\" para deshacerlo."),
            repasar
        )
        "inquilinos" -> listOf(
            CoachStep(null, "Inquilinos", "Lista de tus inquilinos activos. Si tienes varios pisos, los botones de arriba filtran la lista, y el número junto al título te dice cuántos estás viendo."),
            CoachStep(null, "Detalle del inquilino", "Toca una tarjeta para ver su detalle: ahí puedes contactarlo por llamada o WhatsApp, descargar su contrato, editar sus datos personales, trasladarlo a otro cuarto o iniciar su retiro."),
            CoachStep(null, "Editar y trasladar", "\"Editar datos personales\" cambia nombre, apellidos, DNI, celular y correo sin tocar el contrato. \"Trasladar a otro cuarto\" lo mueve a un cuarto libre y puede ajustar el recibo pendiente al precio del cuarto nuevo."),
            repasar
        )
        "cuartos_todos" -> listOf(
            CoachStep(null, "Cuartos", "Todos tus cuartos en un solo lugar. Toca uno para ver su detalle y editar su número, precio, garantía o descripción. Los botones de arriba filtran entre alquilados y sin alquilar, y cada uno lleva su conteo."),
            repasar
        )
        "servicios" -> listOf(
            CoachStep("tab_1", "Servicios de la casa", "Aquí gestionas los servicios de tu propiedad (luz, agua, gas, internet…). Tiene dos sub-pestañas: \"Pendientes\" y \"Conceptos\"."),
            CoachStep(null, "Pendientes", "Los recibos del mes por pagar. Toca el monto para registrar el pago cuando lo realices; el color (rojo/amarillo/verde) indica qué tan cerca está el vencimiento."),
            CoachStep(null, "Conceptos", "El catálogo de tus servicios fijos. Crea uno nuevo, edita su monto o su día de vencimiento (el cambio aplica desde el recibo en curso) o elimínalo. Al eliminar tienes 24 h para deshacerlo y deja de generar recibos."),
            repasar
        )
        "servicios_pagados" -> listOf(
            CoachStep(null, "Servicios Pagados", "Historial de los servicios de la casa que ya pagaste (luz, agua, etc.). Puedes revertir un pago si te equivocaste."),
            repasar
        )
        "limpieza" -> listOf(
            CoachStep(null, "Horario de limpieza", "Cada piso reparte los siete días de la semana entre sus inquilinos: uno limpia cada día. Aquí ves de un vistazo quién tiene cada día y qué días están libres."),
            CoachStep(null, "Cambiar un día", "Toca a un inquilino para asignarle otro día. Los días que ya tiene alguien de ese mismo piso aparecen bloqueados, con el nombre de quien lo ocupa."),
            CoachStep(null, "Conflictos", "Un día marcado en rojo tiene más de un inquilino asignado. Suele venir de datos antiguos, o de un piso con más de siete cuartos ocupados. Toca a cualquiera de ellos para moverlo a un día libre."),
        )
        "ajustes" -> listOf(
            CoachStep(null, "Ajustes", "Cambia entre modo claro y modo oscuro, ajusta el tamaño de letra a tu gusto, y elige cómo recibir los avisos: notificación silenciosa o alarma con sonido. También defines a qué hora del día llega el recordatorio diario de cobros y servicios pendientes."),
            repasar
        )
        "estadisticas" -> listOf(
            CoachStep(null, "Estadísticas", "Cuánto rinden tus cuartos. Las tres cifras salen del precio de cada cuarto (no de lo ya cobrado), y siempre cuadran entre sí: lo potencial es la suma de lo actual más lo muerto."),
            CoachStep(null, "Las tres cifras", "\"Ingresos actuales\" es lo que generan hoy los cuartos alquilados. \"Ingresos potenciales\" es lo que darían todos si estuvieran ocupados. \"Ingresos muertos\" es lo que dejas de ganar por los cuartos vacíos."),
            CoachStep(null, "Aprovechamiento y pisos", "La barra muestra qué parte del potencial estás cobrando. Más abajo tienes el desglose por piso: los que tienen cuartos vacíos se marcan en naranja, para ver de un vistazo dónde puedes mejorar."),
            repasar
        )
        "admin_pagos" -> listOf(
            CoachStep(null, "Pagos Pendientes", "Suscripciones de los usuarios por confirmar. Revisa cada pago y confírmalo para activar el plan del usuario."),
            repasar
        )
        "admin_pagos_realizados" -> listOf(
            CoachStep(null, "Pagos Registrados", "Historial de las suscripciones ya confirmadas. Si confirmaste un pago por error, puedes revertirlo desde aquí."),
            repasar
        )
        else -> null
    }
    if (seccion != null) return seccion

    // Vista principal: guía por rol (menú + pestañas).
    return when (rol) {
        "Individual" -> listOf(
            CoachStep("menu", "Menú", "Desde aquí entras a Ingresos, Gastos, Resumen y Ajustes."),
            CoachStep("tab_0", "Ingresos", "Lo que cobras cada mes: alquileres, sueldos, mensualidades, etc."),
            CoachStep("tab_1", "Gastos", "Lo que pagas cada mes: cuotas del préstamo al banco, servicios (luz, agua, internet) y suscripciones."),
            CoachStep("tab_2", "Resumen", "El balance del mes: cuánto entró, cuánto salió y tu resultado."),
            CoachStep(null, "Las 3 sub-pestañas", "Dentro de Ingresos y de Gastos verás: \"Pendientes\" (lo que falta cobrar o pagar), \"Realizados\" (lo ya registrado, que puedes Revertir si te equivocas) y \"Conceptos\" (crea, edita o elimina tus ingresos y gastos fijos; tienes 24 h para deshacer un borrado)."),
            CoachStep(null, "Registrar y recordar", "En \"Pendientes\" toca Cobrar o Pagar y confirma. En Ingresos, si aún no toca cobrar, puedes usar \"Posponer\" para aplazar el aviso. La app te recordará cada pago e ingreso para que nunca se te olvide una cuota."),
            repasar
        )
        "Administrador" -> listOf(
            CoachStep("menu", "Menú de administración", "Aquí gestionas Usuarios, Pagos Pendientes y Pagos Registrados."),
            repasar
        )
        else -> listOf(
            CoachStep("menu", "Menú", "Aquí abres el menú: Inquilinos, Cuartos, Inquilinos Pagados, Servicios Pagados y Ajustes."),
            CoachStep("tab_0", "Cobros", "Cobros pendientes de tus inquilinos. Toca el monto para registrar el pago; toca la tarjeta para ver el detalle."),
            CoachStep(null, "Pago por partes", "Al registrar un cobro puedes escribir un monto menor al total: el inquilino abona una parte y eliges la fecha en que se compromete a pagar el resto. El recibo se marca como \"Pago por partes\" y su deuda se actualiza sola."),
            CoachStep(null, "Botón \"PP\"", "En el detalle del inquilino, el botón circular \"PP\" (esquina superior derecha) lista los pagos por partes de ese recibo y te deja revertir el último si te equivocaste."),
            CoachStep(null, "Botón \"RP\"", "Al lado del \"PP\" está \"RP\" (regularizar pago): corrige cuánto se debe de ese mes, ya sea bajando el monto por un descuento acordado o subiéndolo por un recargo de retraso. Pide un motivo, queda en el historial y puedes revertirlo. Solo está activo mientras el recibo tenga saldo."),
            CoachStep("tab_1", "Servicios", "Servicios de la casa (luz, agua, etc.). Tiene dos sub-pestañas: \"Pendientes\" y \"Conceptos\"; al abrir Servicios te explico cada una."),
            CoachStep("tab_2", "Cuartos Libres", "Cuartos disponibles. Si tienes varios pisos puedes filtrarlos para ver qué hay libre en cada uno. Toca uno para ver su detalle y usa \"Alquilar\" para registrar un inquilino; ahí puedes marcar \"Inquilino existente\" si le alquilas un cuarto más a alguien que ya tienes registrado."),
            repasar
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaPendientes(vm: PagosViewModel, onCardClick: (Inquilino) -> Unit, onPagarClick: (Inquilino) -> Unit) {
    val state by vm.pagosState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarPagos() }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { vm.cargarPagos() },
        state        = pullState,
        modifier     = Modifier.fillMaxSize(),
        indicator    = {}
    ) {
        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("¡No tienes cobros pendientes! 🎉", color = AppTheme.colores.textoSuave)
                    }
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(s.data, key = { _, it -> it.idPago }) { index, inquilino ->
                        val colores = inquilino.colores()
                        Card(
                            modifier = Modifier.fillMaxWidth().appear(index).bounceClick { onCardClick(inquilino) },
                            colors = CardDefaults.cardColors(containerColor = colores.fondo),
                            border = BorderStroke(1.dp, colores.borde.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(colores.borde), contentAlignment = Alignment.Center) {
                                        Text(inquilino.nombre.take(1), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                    }
                                    if (!inquilino.garantiaPagada && (inquilino.montoGarantia ?: 0.0) > 0) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(AppTheme.colores.ambar)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(inquilino.nombre, fontWeight = FontWeight.Bold, color = colores.texto, fontSize = 16.sp)
                                    Text(inquilino.etiquetaDias, fontSize = 12.sp, color = colores.texto, fontWeight = FontWeight.ExtraBold)
                                    if (inquilino.esParcial) {
                                        Box(
                                            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                                                .background(AppTheme.colores.advertencia).padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Pago por partes", fontSize = 10.sp, color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                        }
                                    }
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
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.message, color = AppTheme.colores.error, modifier = Modifier.padding(24.dp))
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleBottomSheet(inquilino: Inquilino, vm: PagosViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val retiroState by vm.retiroState.collectAsStateWithLifecycle()
    var posponerOpen by remember { mutableStateOf(false) }
    var ppOpen by remember { mutableStateOf(false) }
    var rpOpen by remember { mutableStateOf(false) }
    if (posponerOpen) {
        PosponerRecordatorioDialog(
            clave = "pago:${inquilino.idPago}",
            titulo = inquilino.nombre,
            onDismiss = { posponerOpen = false }
        )
    }
    if (ppOpen) {
        PagosPorPartesDialog(vm = vm, idPago = inquilino.idPago, onDismiss = { ppOpen = false })
    }
    if (rpOpen) {
        RegularizarPagoDialog(vm = vm, inquilino = inquilino, onDismiss = { rpOpen = false })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 20.sp (antes 22) para que el título y los dos botones circulares
                // quepan en una línea incluso con el tamaño de letra al máximo.
                Text("Detalle del Inquilino", fontWeight = FontWeight.Black, fontSize = 20.sp, color = AzulPrimario, modifier = Modifier.weight(1f))
                // Botón circular "RP": regulariza el monto del recibo (sube o baja).
                // Solo con saldo pendiente: un recibo cancelado ya no se reajusta.
                val puedeRegularizar = inquilino.monto > 0
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(
                            if (puedeRegularizar) AppTheme.colores.oliva
                            else AppTheme.colores.superficieTenue
                        )
                        .clickable(enabled = puedeRegularizar) { rpOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "RP",
                        color = if (puedeRegularizar) AppTheme.colores.textoSobreAcento
                                else AppTheme.colores.textoSuave,
                        fontWeight = FontWeight.Black, fontSize = 15.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Botón circular "PP": pagos por partes registrados de este recibo.
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (inquilino.esParcial) AppTheme.colores.advertencia else AzulPrimario)
                        .clickable { ppOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("PP", color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Nombre", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(inquilino.nombre, fontWeight = FontWeight.Bold) }
            }
            if (!inquilino.celular.isNullOrBlank()) {
                Row(
                    Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Celular", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
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
                        Icon(Icons.Default.Phone, "Llamar", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colores.whatsapp)
                            .clickable {
                                val num = inquilino.celular.replace(Regex("[^\\d]"), "")
                                val waNum = if (num.length == 9) "51$num" else num
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum"))
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Habitación", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(inquilino.habitacion, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Payments, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Saldo Pendiente", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text("S/ ${inquilino.monto}", fontWeight = FontWeight.Bold) }
            }

            // ── Garantía ──
            val montoGar = inquilino.montoGarantia
            if (montoGar != null && montoGar > 0) {
                Spacer(Modifier.height(8.dp))
                if (inquilino.garantiaPagada) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AppTheme.colores.exito)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = AppTheme.colores.exitoFuerte)
                                Text("Pagado", fontSize = 12.sp, color = AppTheme.colores.exito)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.advertenciaContenedor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = AppTheme.colores.advertencia)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = AppTheme.colores.advertencia)
                                Text("Pendiente de pago", fontSize = 12.sp, color = AppTheme.colores.advertenciaTexto)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.pagarGarantia(inquilino.idInquilino) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito),
                        enabled = retiroState !is UiState.Loading
                    ) { Text("Pagar Garantía") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { posponerOpen = true },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.botonNeutro, contentColor = AppTheme.colores.botonNeutroTexto)
            ) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Posponer recordatorio")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

// ── Diálogo: registrar cobro de inquilino (admite pago por partes) ────────────
// Si el monto ingresado es menor al saldo, se registra como pago por partes y se
// pide la fecha en que el inquilino se compromete a pagar lo restante.
@Composable
private fun DialogoCobrarInquilino(
    inquilino: Inquilino,
    onConfirm: (monto: Double, fechaCompromiso: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val saldo = inquilino.monto
    var montoTxt by remember { mutableStateOf("%.2f".format(saldo)) }
    var fechaCompromiso by remember { mutableStateOf<String?>(null) }

    val monto = montoTxt.aMontoOrNull()
    val esParcial = monto != null && monto > 0 && monto < saldo
    val montoValido = monto != null && monto > 0 && monto <= saldo + 0.001
    val puedeConfirmar = montoValido && (!esParcial || fechaCompromiso != null)

    fun abrirSelectorFecha() {
        val hoy = java.util.Calendar.getInstance()
        val picker = android.app.DatePickerDialog(
            context,
            { _, y, m, d -> fechaCompromiso = "%04d-%02d-%02d".format(y, m + 1, d) },
            hoy.get(java.util.Calendar.YEAR), hoy.get(java.util.Calendar.MONTH), hoy.get(java.util.Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.minDate = hoy.timeInMillis
        picker.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(monto!!, if (esParcial) fechaCompromiso else null) },
                enabled = puedeConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario)
            ) { Text(if (esParcial) "Registrar pago por partes" else "Sí, registrar pago") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Registrar Pago") },
        text = {
            Column {
                Text(inquilino.nombre, fontWeight = FontWeight.Bold)
                Text("Saldo pendiente: S/ ${"%.2f".format(saldo)}", fontSize = 13.sp, color = AppTheme.colores.textoSuave)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = montoTxt,
                    onValueChange = { v -> montoTxt = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Monto pagado (S/)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (esParcial && monto != null) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.advertenciaContenedor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Pago por partes · quedará S/ ${"%.2f".format(saldo - monto)} de saldo",
                                fontSize = 12.sp, color = AppTheme.colores.advertenciaTexto, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { abrirSelectorFecha() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulPrimario)
                            ) {
                                Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(fechaCompromiso?.let { "Compromiso: $it" } ?: "Fecha de compromiso del saldo")
                            }
                        }
                    }
                }
            }
        }
    )
}

// ── Diálogo: regularizar el monto de un recibo ("RP") ─────────────────────────
// Sirve para corregir lo que se debe de un mes concreto: un descuento acordado o
// un recargo por retraso. No registra un pago — cambia el saldo — y por eso exige
// un motivo, que queda en el historial junto al monto y la fecha.
@Composable
private fun RegularizarPagoDialog(
    vm: PagosViewModel,
    inquilino: Inquilino,
    onDismiss: () -> Unit
) {
    val listaState  by vm.reajustesState.collectAsStateWithLifecycle()
    val accionState by vm.reajusteAccionState.collectAsStateWithLifecycle()
    val pagosState  by vm.pagosState.collectAsStateWithLifecycle()

    LaunchedEffect(inquilino.idPago) { vm.cargarReajustes(inquilino.idPago) }

    val reajustes = (listaState as? UiState.Success)?.data.orEmpty()

    // El saldo se relee tras cada reajuste. Primero de la lista de cobros ya
    // recargada; si el recibo salió de ella (quedó en cero), del último reajuste.
    val saldo = (pagosState as? UiState.Success)?.data
        ?.firstOrNull { it.idPago == inquilino.idPago }?.monto
        ?: reajustes.firstOrNull()?.montoDespues?.toDoubleOrNull()
        ?: inquilino.monto

    var montoTxt by remember(saldo) { mutableStateOf("%.2f".format(saldo)) }
    var motivo   by remember { mutableStateOf("") }

    val nuevoMonto = montoTxt.aMontoOrNull()
    // Positiva = descuento (el saldo baja); negativa = recargo (sube).
    val diferencia = nuevoMonto?.let { saldo - it }
    val hayCambio  = diferencia != null && kotlin.math.abs(diferencia) >= 0.005
    val guardando  = accionState is UiState.Loading
    val puedeAplicar = nuevoMonto != null && nuevoMonto >= 0 && hayCambio &&
                       motivo.isNotBlank() && !guardando

    fun cerrar() { vm.resetReajustesState(); onDismiss() }

    AlertDialog(
        onDismissRequest = { if (!guardando) cerrar() },
        confirmButton = {
            Button(
                onClick = {
                    vm.aplicarReajuste(inquilino.idPago, nuevoMonto!!, motivo)
                    motivo = ""
                },
                enabled = puedeAplicar,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.oliva)
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = AppTheme.colores.textoSobreAcento, strokeWidth = 2.dp
                    )
                } else {
                    Text(if (diferencia != null && diferencia < 0) "Aplicar recargo" else "Aplicar descuento")
                }
            }
        },
        dismissButton = { TextButton(onClick = { cerrar() }, enabled = !guardando) { Text("Cerrar") } },
        title = { Text("Regularizar pago") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(inquilino.nombre, fontWeight = FontWeight.Bold)
                Text(
                    "${inquilino.habitacion} · saldo actual S/ ${"%.2f".format(saldo)}",
                    fontSize = 12.sp, color = AppTheme.colores.textoSuave
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = montoTxt,
                    onValueChange = { v -> montoTxt = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Nuevo saldo (S/)") },
                    singleLine = true,
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Escribe cuánto debe quedar debiendo, no la diferencia.",
                    fontSize = 11.sp, color = AppTheme.colores.textoSuave,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Resumen del efecto: sin esto es fácil confundir el saldo final
                // con el importe del ajuste.
                if (diferencia != null && hayCambio) {
                    val esDescuento = diferencia > 0
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (esDescuento) AppTheme.colores.exitoContenedorTenue
                                             else AppTheme.colores.advertenciaContenedor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                (if (esDescuento) "Descuento de S/ " else "Recargo de S/ ") +
                                    "%.2f".format(kotlin.math.abs(diferencia)),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (esDescuento) AppTheme.colores.exitoTexto
                                        else AppTheme.colores.advertenciaTexto
                            )
                            Text(
                                "El saldo pasa de S/ ${"%.2f".format(saldo)} a S/ ${"%.2f".format(nuevoMonto!!)}",
                                fontSize = 12.sp, color = AppTheme.colores.textoMedio
                            )
                            if (nuevoMonto <= 0.0) {
                                Text(
                                    "Al quedar en cero, el recibo se marcará como cancelado.",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = AppTheme.colores.advertenciaTexto,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { if (it.length <= 120) motivo = it },
                    label = { Text("Motivo *") },
                    enabled = !guardando,
                    minLines = 2,
                    supportingText = { Text("${motivo.length}/120") },
                    modifier = Modifier.fillMaxWidth()
                )

                when (val a = accionState) {
                    is UiState.Error   -> Text(a.message, color = AppTheme.colores.error, fontSize = 12.sp)
                    is UiState.Success -> Text(a.data, color = AppTheme.colores.exito, fontSize = 12.sp)
                    else -> Unit
                }

                // ── Historial ──
                Spacer(Modifier.height(16.dp))
                Text("Reajustes aplicados", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                when (val l = listaState) {
                    is UiState.Loading -> Box(
                        Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }

                    is UiState.Error -> Text(l.message, color = AppTheme.colores.error, fontSize = 12.sp)

                    is UiState.Success -> {
                        if (reajustes.isEmpty()) {
                            Text(
                                "Este recibo aún no tiene reajustes.",
                                fontSize = 12.sp, color = AppTheme.colores.textoSuave
                            )
                        } else {
                            reajustes.forEachIndexed { i, r ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (r.esRecargo) "+S/ " else "−S/ ") +
                                                "%.2f".format(kotlin.math.abs(r.monto)),
                                            fontWeight = FontWeight.Bold,
                                            color = if (r.esRecargo) AppTheme.colores.advertencia
                                                    else AppTheme.colores.exito
                                        )
                                        r.descripcion?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                        }
                                        r.fechaReajuste?.take(10)?.let {
                                            Text(it, fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                                        }
                                    }
                                    TextButton(
                                        onClick = { vm.revertirReajuste(inquilino.idPago, r.idReajuste) },
                                        enabled = !guardando
                                    ) {
                                        Text(
                                            "Revertir",
                                            color = AppTheme.colores.advertencia,
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (i != reajustes.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    )
}

// ── Diálogo: lista de pagos por partes (abonos) de un recibo, con revertir ─────
@Composable
private fun PagosPorPartesDialog(vm: PagosViewModel, idPago: String, onDismiss: () -> Unit) {
    val state by vm.abonosState.collectAsStateWithLifecycle()
    LaunchedEffect(idPago) { vm.cargarAbonos(idPago) }

    fun cerrar() { vm.resetAbonosState(); onDismiss() }

    AlertDialog(
        onDismissRequest = { cerrar() },
        confirmButton = { TextButton(onClick = { cerrar() }) { Text("Cerrar") } },
        title = { Text("Pagos por partes") },
        text = {
            when (val s = state) {
                is UiState.Success -> {
                    // El más reciente primero; el backend solo permite revertir ese.
                    val abonos = s.data.sortedByDescending { it.fechaAbono ?: "" }
                    if (abonos.isEmpty()) {
                        Text("Aún no hay pagos por partes registrados para este recibo.", color = AppTheme.colores.textoSuave)
                    } else {
                        Column {
                            abonos.forEachIndexed { i, ab ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("S/ ${"%.2f".format(ab.montoAbonado.toDoubleOrNull() ?: 0.0)}", fontWeight = FontWeight.Bold)
                                        ab.fechaAbono?.take(10)?.let { Text("Abonado: $it", fontSize = 11.sp, color = AppTheme.colores.textoSuave) }
                                        if (!ab.fechaCompromiso.isNullOrBlank()) {
                                            Text("Compromiso saldo: ${ab.fechaCompromiso.take(10)}", fontSize = 11.sp, color = AppTheme.colores.advertenciaTexto)
                                        }
                                    }
                                    if (i == 0) {
                                        TextButton(onClick = { vm.revertirAbono(ab.idAbono, idPago) }) {
                                            Text("Revertir", color = AppTheme.colores.advertencia, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (i != abonos.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Text(s.message, color = AppTheme.colores.error)
                else -> Unit
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionPagados(vm: PagosViewModel) {
    val state by vm.pagosRecientesState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarPagosRecientes() }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pagos realizados recientemente", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarPagosRecientes() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sin pagos en los últimos 10 días", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(s.data) { index, pago ->
                                Card(Modifier.fillMaxWidth().appear(index), colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie)) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(pago.nombre, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Pagado: S/ ${"%.2f".format(pago.montoOriginal)}",
                                                color = AppTheme.colores.exito,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val fechaCobro = formatearFecha(pago.fechaPago)
                                            if (fechaCobro.isNotBlank()) {
                                                Text(
                                                    "Registrado: $fechaCobro",
                                                    fontSize = 11.sp,
                                                    color = AppTheme.colores.textoMedio
                                                )
                                            }
                                        }
                                        TextButton(onClick = { vm.revertirPago(pago.idPago) }) {
                                            Text("Revertir", color = AppTheme.colores.advertencia)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = AppTheme.colores.error)
                }
                else -> Unit
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN INQUILINOS
// ──────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionInquilinos(vm: PagosViewModel) {
    val state         by vm.inquilinosState.collectAsStateWithLifecycle()
    val retiroState   by vm.retiroState.collectAsStateWithLifecycle()
    val contratoState by vm.contratoState.collectAsStateWithLifecycle()
    val editarState   by vm.editarDatosState.collectAsStateWithLifecycle()
    val trasladoState by vm.trasladoState.collectAsStateWithLifecycle()
    val cuartosLibres by vm.cuartosLibresState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { vm.cargarInquilinos() }

    var inquilinoSeleccionado by remember { mutableStateOf<InquilinoMobile?>(null) }
    var inquilinoARetirar     by remember { mutableStateOf<InquilinoMobile?>(null) }
    var inquilinoAEditar      by remember { mutableStateOf<InquilinoMobile?>(null) }
    var inquilinoATrasladar   by remember { mutableStateOf<InquilinoMobile?>(null) }
    var filtroNombre by remember { mutableStateOf("") }
    var filtroPiso   by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(retiroState) {
        if (retiroState is UiState.Success) {
            inquilinoSeleccionado = null
            vm.resetRetiroState()
        }
    }

    // Contrato listo: queda guardado en Descargas y se abre con el visor de PDF.
    LaunchedEffect(contratoState) {
        val estado = contratoState
        if (estado is UiState.Success) {
            aviso = if (DescargasPdf.abrir(contexto, estado.data))
                "Contrato guardado en Descargas"
            else
                "Contrato guardado en Descargas (no hay app para abrir PDF)"
            vm.resetContratoState()
        }
    }

    LaunchedEffect(aviso) {
        if (aviso != null) {
            kotlinx.coroutines.delay(2500)
            aviso = null
        }
    }

    // Lista cargada (null mientras carga o si falló) y su filtrado por piso y nombre.
    // Se calculan aquí, fuera del `when`, porque alimentan el contador, el total del
    // piso y la lista de abajo: así los tres no pueden discrepar.
    val inquilinos = (state as? UiState.Success)?.data

    // Pisos disponibles, en el mismo orden en que el backend devuelve los inquilinos.
    val pisos = remember(inquilinos) {
        inquilinos.orEmpty()
            .map { PisoFiltro(it.idPiso, "${it.casa} · ${it.piso}") }
            .distinctBy { it.idPiso }
    }
    // Si el piso elegido desaparece (se retiró su último inquilino), se vuelve a "Todos".
    LaunchedEffect(pisos) {
        if (filtroPiso != null && pisos.none { it.idPiso == filtroPiso }) filtroPiso = null
    }

    val porPiso = inquilinos?.filter { filtroPiso == null || it.idPiso == filtroPiso }
    val filtrados = porPiso?.let { lista ->
        if (filtroNombre.isBlank()) lista
        else lista.filter { "${it.nombre} ${it.apellidos}".contains(filtroNombre, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        EncabezadoLista("Inquilinos activos", total = inquilinos?.size, visibles = filtrados?.size)
        Spacer(Modifier.height(8.dp))

        // ── Filtro por piso ──
        FiltroPisos(pisos, filtroPiso) { filtroPiso = it }
        if (pisos.size > 1) Spacer(Modifier.height(8.dp))

        // El total en soles por piso vive en la sección Estadísticas: aquí duplicaba
        // esa información y restaba espacio a la lista, que es lo que se viene a ver.
        OutlinedTextField(
            value = filtroNombre,
            onValueChange = { filtroNombre = it },
            placeholder = { Text("Buscar por nombre…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppTheme.colores.textoSuave) },
            trailingIcon = {
                if (filtroNombre.isNotEmpty()) {
                    IconButton(onClick = { filtroNombre = "" }) {
                        Icon(Icons.Default.Close, "Limpiar", tint = AppTheme.colores.textoSuave)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AzulPrimario,
                unfocusedBorderColor = AppTheme.colores.bordeCampo,
                focusedContainerColor = AppTheme.colores.superficie,
                unfocusedContainerColor = AppTheme.colores.superficie
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarInquilinos() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    val visibles = filtrados.orEmpty()
                    if (visibles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (s.data.isEmpty()) "No hay inquilinos activos"
                                else "Sin resultados para \"$filtroNombre\"",
                                color = AppTheme.colores.textoSuave
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibles, key = { it.idInquilino }) { inq ->
                                val esPendiente = inq.estado == "pendiente_retiro"
                                val bgColor     = if (esPendiente) AppTheme.colores.peligroContenedor else AppTheme.colores.exitoContenedor
                                val borderColor = if (esPendiente) AppTheme.colores.peligro else AppTheme.colores.exito
                                val textColor   = if (esPendiente) AppTheme.colores.peligroTexto else AppTheme.colores.exitoTexto
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { inquilinoSeleccionado = inq },
                                    colors = CardDefaults.cardColors(containerColor = bgColor),
                                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(
                                                Modifier.size(44.dp).clip(CircleShape).background(borderColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(inq.nombre.take(1), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                            }
                                            if (inq.fechaGarantia == null && (inq.montoGarantia?.toDoubleOrNull() ?: 0.0) > 0) {
                                                Box(
                                                    Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(AppTheme.colores.ambar)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${inq.nombre} ${inq.apellidos}", fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
                                            Text("${inq.casa} · Cuarto ${inq.nroCuarto}", fontSize = 12.sp, color = AppTheme.colores.textoMedio)
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
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error   -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    // Bottom sheet con detalle + acciones
    if (inquilinoSeleccionado != null) {
        InquilinoBottomSheet(
            inquilino     = inquilinoSeleccionado!!,
            retiroState   = retiroState,
            contratoState = contratoState,
            onRetirar        = { inquilinoARetirar = it },
            onCancelarRetiro = { vm.cancelarRetiro(it.idInquilino) },
            onPagarGarantia  = { vm.pagarGarantia(it.idInquilino) },
            onContrato       = { vm.descargarContrato(it) },
            // Se cierra el detalle al abrir el formulario: dos hojas apiladas
            // dejarían un doble oscurecido de fondo.
            onEditarDatos    = { inquilinoAEditar = it; inquilinoSeleccionado = null },
            onTrasladar      = { inquilinoATrasladar = it; inquilinoSeleccionado = null },
            onDismiss        = { inquilinoSeleccionado = null; vm.resetRetiroState(); vm.resetContratoState() }
        )
    }

    // Formulario de datos personales (nombre, apellidos, celular, DNI, correo)
    inquilinoAEditar?.let { inq ->
        EditarDatosInquilinoSheet(
            inquilino = inq,
            estado    = editarState,
            onGuardar = { nombre, apellidos, celular, dni, email ->
                vm.editarDatosPersonales(inq.idInquilino, nombre, apellidos, celular, dni, email)
            },
            onDismiss = { inquilinoAEditar = null; vm.resetEditarDatosState() }
        )
    }

    // Traslado a otro cuarto
    inquilinoATrasladar?.let { inq ->
        TrasladarInquilinoSheet(
            inquilino    = inq,
            cuartosState = cuartosLibres,
            estado       = trasladoState,
            onRecargar   = { vm.cargarCuartosLibres() },
            onConfirmar  = { idCuartoNuevo, aplicarPrecio ->
                vm.trasladarInquilino(inq.idInquilino, idCuartoNuevo, aplicarPrecio)
            },
            onDismiss    = { inquilinoATrasladar = null; vm.resetTrasladoState() }
        )
    }

    // Guardado correcto: se cierran el formulario y el detalle (sus datos ya son
    // viejos, la lista se recargó) y se confirma con el aviso de la sección.
    LaunchedEffect(editarState) {
        if (editarState is UiState.Success) {
            inquilinoAEditar = null
            inquilinoSeleccionado = null
            aviso = "Datos del inquilino actualizados"
            vm.resetEditarDatosState()
        }
    }

    LaunchedEffect(trasladoState) {
        if (trasladoState is UiState.Success) {
            inquilinoATrasladar = null
            aviso = "Inquilino trasladado de cuarto"
            vm.resetTrasladoState()
        }
    }

    // Confirmación antes de iniciar retiro
    inquilinoARetirar?.let { retirando ->
        // Un inquilino puede alquilar varios cuartos. Solo cuando es el caso se
        // ofrece elegir: preguntarlo siempre sería ruido para la mayoría.
        val otrosCuartos = inquilinos.orEmpty().filter {
            it.dni != null && it.dni == retirando.dni && it.idInquilino != retirando.idInquilino
        }
        var retirarTodos by remember(retirando.idInquilino) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { inquilinoARetirar = null },
            confirmButton = {
                Button(
                    onClick = {
                        vm.iniciarRetiro(retirando.idInquilino, todosLosCuartos = retirarTodos)
                        inquilinoARetirar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.peligro)
                ) { Text("Sí, retirar") }
            },
            dismissButton = { TextButton(onClick = { inquilinoARetirar = null }) { Text("Cancelar") } },
            title = { Text("Confirmar Retiro") },
            text = {
                Column {
                    Text(
                        "¿Está seguro de retirar a ${retirando.nombre}? Tendrá 24 horas " +
                            "para cancelar la acción antes de que sea definitivo."
                    )

                    if (otrosCuartos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Este inquilino alquila ${otrosCuartos.size + 1} cuartos.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        OpcionRetiro(
                            seleccionada = !retirarTodos,
                            titulo = "Solo el cuarto ${retirando.nroCuarto}",
                            detalle = "Sigue alquilando los demás.",
                            onClick = { retirarTodos = false }
                        )
                        OpcionRetiro(
                            seleccionada = retirarTodos,
                            titulo = "Todos sus cuartos",
                            detalle = "Se retiran los ${otrosCuartos.size + 1} contratos a la vez.",
                            onClick = { retirarTodos = true }
                        )
                    }
                }
            }
        )
    }

    if (aviso != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { aviso = null }) { Text("OK", color = AppTheme.colores.textoSobreAcento) } },
            containerColor = AppTheme.colores.exito
        ) { Text(aviso!!, color = AppTheme.colores.textoSobreAcento) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN CUARTOS LIBRES
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun coloresCuartoLibre(): EstadoColores = with(AppTheme.colores) {
    EstadoColores(ocupadoContenedor, dorado, ocupadoTexto)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionCuartosLibres(vm: PagosViewModel) {
    val state by vm.cuartosLibresState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarCuartosLibres() }

    var cuartoSeleccionado by remember { mutableStateOf<CuartoLibre?>(null) }
    var cuartoAAlquilar by remember { mutableStateOf<CuartoLibre?>(null) }
    var mensajeExito by remember { mutableStateOf<String?>(null) }
    var filtroPiso by remember { mutableStateOf<String?>(null) }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    // Lista cargada y su filtrado por piso: alimentan el contador y la lista, así
    // que se calculan fuera del `when` para que no puedan discrepar.
    val cuartos = (state as? UiState.Success)?.data
    val pisos = remember(cuartos) {
        cuartos.orEmpty()
            .map { PisoFiltro(it.idPiso, "${it.casa} · ${it.piso}") }
            .distinctBy { it.idPiso }
    }
    // Si se alquila el último cuarto libre de un piso, ese piso desaparece de la
    // lista y el filtro vuelve solo a "Todos".
    LaunchedEffect(pisos) {
        if (filtroPiso != null && pisos.none { it.idPiso == filtroPiso }) filtroPiso = null
    }
    val visibles = cuartos?.filter { filtroPiso == null || it.idPiso == filtroPiso }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        EncabezadoLista("Cuartos disponibles", total = cuartos?.size, visibles = visibles?.size)
        Spacer(Modifier.height(8.dp))

        FiltroPisos(pisos, filtroPiso) { filtroPiso = it }
        if (pisos.size > 1) Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(4.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarCuartosLibres() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    val lista = visibles.orEmpty()
                    if (lista.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AppTheme.colores.exitoBrillante,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (s.data.isEmpty()) "No hay cuartos libres"
                                    else "Este piso no tiene cuartos libres",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = AppTheme.colores.exito
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (s.data.isEmpty()) "¡Todos los cuartos están ocupados!"
                                    else "Prueba con otro piso o quita el filtro.",
                                    fontSize = 14.sp,
                                    color = AppTheme.colores.textoSuave
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(lista, key = { _, it -> it.idCuarto }) { index, cuarto ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().appear(index).bounceClick { cuartoSeleccionado = cuarto },
                                    colors = CardDefaults.cardColors(containerColor = coloresCuartoLibre().fondo),
                                    border = BorderStroke(1.dp, coloresCuartoLibre().borde.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(coloresCuartoLibre().borde),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MeetingRoom, null, tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Cuarto ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold, color = coloresCuartoLibre().texto, fontSize = 16.sp)
                                            Text("${cuarto.casa} · ${cuarto.piso}", fontSize = 12.sp, color = AppTheme.colores.textoMedio)
                                        }
                                        val precio = cuarto.precio?.toDoubleOrNull()
                                        if (precio != null) {
                                            Text(
                                                "S/ ${"%.2f".format(precio)}",
                                                fontWeight = FontWeight.Black,
                                                color = coloresCuartoLibre().borde,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error   -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (cuartoSeleccionado != null) {
        CuartoBottomSheet(
            cuarto = cuartoSeleccionado!!,
            onDismiss = { cuartoSeleccionado = null },
            onAlquilar = { c -> cuartoSeleccionado = null; cuartoAAlquilar = c }
        )
    }

    cuartoAAlquilar?.let { c ->
        RegistrarInquilinoWizard(
            vm = vm,
            cuarto = c,
            onDismiss = { cuartoAAlquilar = null },
            onSuccess = { msg ->
                cuartoAAlquilar = null
                vm.resetRegistrarInquilinoState()
                mensajeExito = msg
            }
        )
    }

    mensajeExito?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensajeExito = null },
            confirmButton = { TextButton(onClick = { mensajeExito = null }) { Text("OK") } },
            title = { Text("Listo") },
            text = { Text(msg) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuartoBottomSheet(cuarto: CuartoLibre, onDismiss: () -> Unit, onAlquilar: (CuartoLibre) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Cuarto", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.MeetingRoom, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cuarto", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    Text("Nro. ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ubicación", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    Text("${cuarto.casa} · ${cuarto.piso}", fontWeight = FontWeight.Bold)
                }
            }
            val precio = cuarto.precio?.toDoubleOrNull()
            if (precio != null) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Payments, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Precio mensual", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
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
                        Text("Garantía", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text("S/ ${"%.2f".format(garantia)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!cuarto.descripcion.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Info, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Descripción", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text(cuarto.descripcion, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppTheme.colores.exito)
                    Spacer(Modifier.width(8.dp))
                    Text("Disponible", fontSize = 14.sp, color = AppTheme.colores.exitoTexto, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onAlquilar(cuarto) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exitoFuerte)
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Alquilar", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  SECCIÓN SERVICIOS
// ──────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionServicios(vm: PagosViewModel, onPagarClick: (ServicioCasa) -> Unit) {
    var subtab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subtab, containerColor = AppTheme.colores.superficie, contentColor = AzulPrimario) {
            listOf("Pendientes", "Conceptos").forEachIndexed { i, t ->
                Tab(
                    selected = subtab == i,
                    onClick = { subtab = i },
                    text = { Text(t, fontWeight = if (subtab == i) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(AppTheme.colores.fondo)) {
            when (subtab) {
                0 -> ServiciosPendientesTab(vm, onPagarClick)
                else -> ServiciosConceptosTab(vm)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServiciosPendientesTab(vm: PagosViewModel, onPagarClick: (ServicioCasa) -> Unit) {
    val state by vm.serviciosState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarServicios() }

    var servicioARevertir by remember { mutableStateOf<ServicioCasa?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Servicios pendientes", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarServicios() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay servicios pendientes.\nDesliza hacia abajo para actualizar.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(s.data, key = { _, it -> "${it.idServicio}-${it.mes}-${it.anio}" }) { index, srv ->
                                val colores = srv.colores()
                                Card(
                                    modifier = Modifier.fillMaxWidth().appear(index),
                                    colors = CardDefaults.cardColors(containerColor = colores.fondo),
                                    border = BorderStroke(1.dp, colores.borde.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(colores.borde),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(srv.nombre.take(1), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.nombre, fontWeight = FontWeight.Bold, color = colores.texto, fontSize = 16.sp)
                                            Text(srv.etiquetaDias, fontSize = 12.sp, color = colores.texto, fontWeight = FontWeight.ExtraBold)
                                            Text("${srv.nombreMes} ${srv.anio} · Día ${srv.dia}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                        }
                                        if (srv.pagado) {
                                            TextButton(onClick = { servicioARevertir = srv }) {
                                                Text("Revertir", color = AppTheme.colores.advertencia, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
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
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (servicioARevertir != null) {
        val srv = servicioARevertir!!
        AlertDialog(
            onDismissRequest = { servicioARevertir = null },
            confirmButton = {
                Button(
                    onClick = {
                        srv.idPago?.let { vm.revertirServicio(it) }
                        servicioARevertir = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.advertencia)
                ) { Text("Sí, revertir") }
            },
            dismissButton = { TextButton(onClick = { servicioARevertir = null }) { Text("Cancelar") } },
            title = { Text("Revertir Pago") },
            text = { Text("¿Deseas revertir el pago de ${srv.nombre} (${srv.nombreMes} ${srv.anio})?") }
        )
    }
}

// ── Subpestaña "Conceptos": crea, edita y elimina los servicios de la casa ─────
@Composable
private fun ServiciosConceptosTab(vm: PagosViewModel) {
    val state  by vm.conceptosServicioState.collectAsStateWithLifecycle()
    val accion by vm.accionServicioState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarServiciosConceptos() }

    var nuevo       by remember { mutableStateOf(false) }
    var aEditar     by remember { mutableStateOf<ServicioConcepto?>(null) }
    var aEliminar   by remember { mutableStateOf<ServicioConcepto?>(null) }
    var mensaje     by remember { mutableStateOf<String?>(null) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accion) {
        when (val a = accion) {
            is UiState.Success -> { mensaje = a.data; vm.resetAccionServicioState() }
            is UiState.Error   -> { errorMsg = a.message; vm.resetAccionServicioState() }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { nuevo = true },
            colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nuevo servicio", fontWeight = FontWeight.Bold)
        }

        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay servicios configurados.", color = AppTheme.colores.textoSuave)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(s.data, key = { _, it -> it.idServicio }) { index, cpt ->
                            Box(Modifier.appear(index)) {
                                if (cpt.eliminado) ServicioConceptoEliminadoCard(cpt) { vm.restaurarServicioConcepto(it.idServicio) }
                                else ServicioConceptoCard(cpt, onEditar = { aEditar = it }, onEliminar = { aEliminar = it })
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error   -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
            else -> Unit
        }
    }

    if (nuevo) {
        DialogoServicioConcepto(
            conceptoExistente = null,
            onConfirm = { nombre, monto, dia, precioFijo -> vm.crearServicioConcepto(nombre, monto, dia, precioFijo); nuevo = false },
            onDismiss = { nuevo = false }
        )
    }
    aEditar?.let { cpt ->
        DialogoServicioConcepto(
            conceptoExistente = cpt,
            onConfirm = { nombre, monto, dia, precioFijo -> vm.editarServicioConcepto(cpt.idServicio, nombre, monto, dia, precioFijo); aEditar = null },
            onDismiss = { aEditar = null }
        )
    }
    aEliminar?.let { cpt ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            confirmButton = {
                Button(
                    onClick = { vm.eliminarServicioConcepto(cpt.idServicio); aEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.peligroFuerte)
                ) { Text("Sí, eliminar") }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } },
            title = { Text("Eliminar servicio") },
            text = { Text("¿Eliminar \"${cpt.nombre}\"? Dejará de generar recibos. Tienes 24 h para deshacerlo.") }
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
private fun ServicioConceptoCard(
    cpt: ServicioConcepto,
    onEditar: (ServicioConcepto) -> Unit,
    onEliminar: (ServicioConcepto) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(AzulPrimario), contentAlignment = Alignment.Center) {
                Text(cpt.nombre.take(1).uppercase(), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(cpt.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("S/ ${cpt.montoReferencial} · Día ${cpt.diaVencimiento}", fontSize = 12.sp, color = AzulPrimario, fontWeight = FontWeight.Bold)
                if (!cpt.precioFijo) Text("Precio variable", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
            }
            IconButton(onClick = { onEditar(cpt) }) {
                Icon(Icons.Default.Edit, "Editar", tint = AzulPrimario, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onEliminar(cpt) }) {
                Icon(Icons.Default.Delete, "Eliminar", tint = AppTheme.colores.peligroFuerte, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ServicioConceptoEliminadoCard(cpt: ServicioConcepto, onRestaurar: (ServicioConcepto) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficieTenue),
        border = BorderStroke(1.dp, AppTheme.colores.bordeTenue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cpt.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTheme.colores.textoSuave)
                Text("Eliminado · ${textoCuentaRegresivaServicio(cpt.minutosParaBorrado ?: 0)}", fontSize = 11.sp, color = AppTheme.colores.peligroFuerte)
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
private fun textoCuentaRegresivaServicio(minIniciales: Int): String {
    var restante by remember(minIniciales) { mutableStateOf(minIniciales) }
    LaunchedEffect(minIniciales) {
        while (restante > 0) { delay(60_000); restante -= 1 }
    }
    val h = restante / 60
    val m = restante % 60
    return when {
        restante <= 0 -> "se eliminará en breve"
        h > 0         -> "se borra en ${h}h ${m}m"
        else          -> "se borra en ${m}m"
    }
}

@Composable
private fun DialogoServicioConcepto(
    conceptoExistente: ServicioConcepto?,
    onConfirm: (nombre: String, monto: Double, dia: Int, precioFijo: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val esEdicion = conceptoExistente != null
    var nombre by remember { mutableStateOf(conceptoExistente?.nombre ?: "") }
    var monto  by remember { mutableStateOf(conceptoExistente?.montoReferencial ?: "") }
    var dia    by remember { mutableStateOf(conceptoExistente?.diaVencimiento?.toString() ?: "") }
    var esFijo by remember { mutableStateOf(conceptoExistente?.precioFijo ?: true) }
    val valido = nombre.isNotBlank() && monto.toDoubleOrNull() != null && (dia.toIntOrNull() ?: 0) in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(nombre, monto.toDouble(), dia.toInt(), esFijo) },
                enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario)
            ) { Text(if (esEdicion) "Actualizar" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(if (esEdicion) "Editar servicio" else "Nuevo servicio") },
        text = {
            Column {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre (Ej. Luz, Agua)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = monto, onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto mensual (S/)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dia, onValueChange = { dia = it.filter { c -> c.isDigit() } },
                    label = { Text("Día de vencimiento (1-31)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = esFijo, onCheckedChange = { esFijo = it })
                    Text("El precio es fijo")
                }
                if (!esFijo) {
                    Text("Al pagar podrás ingresar el monto real de cada mes.", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                }
            }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  SECCIÓN SERVICIOS PAGADOS
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionServiciosPagados(vm: PagosViewModel) {
    val state by vm.serviciosRealizadosState.collectAsStateWithLifecycle()
    val pagarServicioState by vm.pagarServicioState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarServiciosRealizados() }

    var servicioARevertir by remember { mutableStateOf<ServicioCasa?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Servicios pagados recientemente", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarServiciosRealizados() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay servicios pagados.\nDesliza hacia abajo para actualizar.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { "${it.idPago}-${it.idServicio}-${it.mes}-${it.anio}" }) { srv ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                                    border = BorderStroke(1.dp, AppTheme.colores.exito.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colores.exito),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(srv.nombre.take(1), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            val montoMostrar = srv.montoPagado?.toDoubleOrNull() ?: srv.montoReferencial.toDoubleOrNull() ?: 0.0
                                            Text("S/ ${"%.2f".format(montoMostrar)}", fontSize = 12.sp, color = AppTheme.colores.exitoFuerte, fontWeight = FontWeight.Bold)
                                            Text("${srv.nombreMes} ${srv.anio} · Día ${srv.dia}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                            val fechaServicio = formatearFecha(srv.fechaPago)
                                            if (fechaServicio.isNotBlank()) {
                                                Text("Registrado: $fechaServicio", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                            }
                                        }
                                        TextButton(onClick = { servicioARevertir = srv }) {
                                            Text("Revertir", color = AppTheme.colores.advertencia, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (servicioARevertir != null) {
        val srv = servicioARevertir!!
        AlertDialog(
            onDismissRequest = { servicioARevertir = null },
            confirmButton = {
                Button(
                    onClick = {
                        srv.idPago?.let { vm.revertirServicio(it) }
                        servicioARevertir = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.advertencia)
                ) { Text("Sí, revertir") }
            },
            dismissButton = { TextButton(onClick = { servicioARevertir = null }) { Text("Cancelar") } },
            title = { Text("Revertir Pago") },
            text = { Text("¿Deseas revertir el pago de ${srv.nombre} (${srv.nombreMes} ${srv.anio})?") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InquilinoBottomSheet(
    inquilino:        InquilinoMobile,
    retiroState:      UiState<String>,
    contratoState:    UiState<Uri>,
    onRetirar:        (InquilinoMobile) -> Unit,
    onCancelarRetiro: (InquilinoMobile) -> Unit,
    onPagarGarantia:  (InquilinoMobile) -> Unit,
    onContrato:       (InquilinoMobile) -> Unit,
    onEditarDatos:    (InquilinoMobile) -> Unit,
    onTrasladar:      (InquilinoMobile) -> Unit,
    onDismiss:        () -> Unit
) {
    val esPendiente = inquilino.estado == "pendiente_retiro"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Inquilino", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Nombre", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    Text("${inquilino.nombre} ${inquilino.apellidos}", fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Home, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Habitación", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    Text("${inquilino.casa} · ${inquilino.piso} · Cuarto ${inquilino.nroCuarto}", fontWeight = FontWeight.Bold)
                }
            }
            if (!inquilino.celular.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Celular", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text(inquilino.celular, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!inquilino.dni.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Badge, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("DNI", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text(inquilino.dni, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!inquilino.email.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Email, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Correo", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                        Text(inquilino.email, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Editar datos personales ──
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick  = { onEditarDatos(inquilino) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, null, tint = AzulPrimario, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editar datos personales")
            }

            // ── Trasladar de cuarto ──
            // Solo mientras el contrato sigue activo: trasladar a alguien que ya
            // está en proceso de retiro dejaría un cuarto ocupado por nadie.
            if (!esPendiente) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { onTrasladar(inquilino) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, null, tint = AzulPrimario, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Trasladar a otro cuarto")
                }
            }

            // ── Garantía ──
            val montoGar = inquilino.montoGarantia?.toDoubleOrNull()
            if (montoGar != null && montoGar > 0) {
                Spacer(Modifier.height(8.dp))
                if (inquilino.fechaGarantia != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AppTheme.colores.exito)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = AppTheme.colores.exitoFuerte)
                                Text("Pagado", fontSize = 12.sp, color = AppTheme.colores.exito)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colores.advertenciaContenedor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = AppTheme.colores.advertencia)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = AppTheme.colores.advertencia)
                                Text("Pendiente de pago", fontSize = 12.sp, color = AppTheme.colores.advertenciaTexto)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPagarGarantia(inquilino) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito),
                        enabled = retiroState !is UiState.Loading
                    ) { Text("Pagar Garantía") }
                }
            }

            // ── Contrato PDF ──
            // El backend lo arma con los datos ya registrados del inquilino y su
            // cuarto; aquí solo se descarga y se abre con el visor del teléfono.
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick  = { onContrato(inquilino) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = contratoState !is UiState.Loading
            ) {
                if (contratoState is UiState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = AzulPrimario, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generando contrato…")
                } else {
                    Icon(Icons.Default.Description, null, tint = AzulPrimario)
                    Spacer(Modifier.width(8.dp))
                    Text("Contrato")
                }
            }
            if (contratoState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(contratoState.message, color = AppTheme.colores.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            if (esPendiente) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colores.peligroContenedorTenue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = AppTheme.colores.peligro)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Le quedan ${inquilino.diasParaRetiro ?: 0} día(s) para que el inquilino sea eliminado definitivamente",
                            fontSize = 13.sp, color = AppTheme.colores.peligroTexto, fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { onCancelarRetiro(inquilino) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito),
                    enabled  = retiroState !is UiState.Loading
                ) {
                    if (retiroState is UiState.Loading)
                        CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colores.textoSobreAcento, strokeWidth = 2.dp)
                    else
                        Text("Cancelar Retiro")
                }
            } else {
                Button(
                    onClick  = { onRetirar(inquilino) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.peligro),
                    enabled  = retiroState !is UiState.Loading
                ) {
                    if (retiroState is UiState.Loading)
                        CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colores.textoSobreAcento, strokeWidth = 2.dp)
                    else
                        Text("Retirar Inquilino")
                }
            }
            if (retiroState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((retiroState as UiState.Error).message, color = AppTheme.colores.error, fontSize = 13.sp)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  EDITAR DATOS PERSONALES DEL INQUILINO
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Formulario de datos personales. Solo edita a la persona: el cuarto, las fechas
 * del contrato y los montos no se tocan desde aquí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarDatosInquilinoSheet(
    inquilino: InquilinoMobile,
    estado:    UiState<String>,
    onGuardar: (nombre: String, apellidos: String, celular: String, dni: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    // `inquilino.idInquilino` como clave: si se abre otro inquilino, los campos
    // se recargan en vez de conservar lo tecleado para el anterior.
    var nombre    by remember(inquilino.idInquilino) { mutableStateOf(inquilino.nombre) }
    var apellidos by remember(inquilino.idInquilino) { mutableStateOf(inquilino.apellidos) }
    var celular   by remember(inquilino.idInquilino) { mutableStateOf(inquilino.celular.orEmpty()) }
    var dni       by remember(inquilino.idInquilino) { mutableStateOf(inquilino.dni.orEmpty()) }
    var email     by remember(inquilino.idInquilino) { mutableStateOf(inquilino.email.orEmpty()) }

    // Se validan al intentar guardar, no mientras se escribe: marcar en rojo un
    // campo que aún se está llenando resulta molesto.
    var validar by remember(inquilino.idInquilino) { mutableStateOf(false) }

    val guardando = estado is UiState.Loading
    val nombreMal    = nombre.isBlank()
    val apellidosMal = apellidos.isBlank()
    val dniMal       = dni.isBlank()
    // El correo es opcional, pero si se escribe algo debe parecer un correo.
    val emailMal     = email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val hayErrores   = nombreMal || apellidosMal || dniMal || emailMal

    ModalBottomSheet(onDismissRequest = { if (!guardando) onDismiss() }, containerColor = AppTheme.colores.superficie) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text("Editar Datos", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Text(
                "Cambia los datos personales del inquilino. El cuarto, las fechas y los montos no se modifican.",
                fontSize = 13.sp, color = AppTheme.colores.textoSuave
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                singleLine = true,
                isError = validar && nombreMal,
                supportingText = if (validar && nombreMal) ({ Text("El nombre es obligatorio") }) else null,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                label = { Text("Apellidos") },
                singleLine = true,
                isError = validar && apellidosMal,
                supportingText = if (validar && apellidosMal) ({ Text("Los apellidos son obligatorios") }) else null,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = dni,
                // Mismos límites que el asistente de registro: 8 dígitos.
                onValueChange = { nuevo -> dni = nuevo.filter(Char::isDigit).take(8) },
                label = { Text("DNI") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validar && dniMal,
                supportingText = if (validar && dniMal) ({ Text("El DNI es obligatorio") }) else null,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = celular,
                onValueChange = { nuevo -> celular = nuevo.filter(Char::isDigit).take(9) },
                // Opcional a propósito: hay inquilinos registrados desde la web sin
                // celular, y no debe bloquearse corregirles el nombre por eso.
                label = { Text("Celular (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = validar && emailMal,
                supportingText = if (validar && emailMal) ({ Text("Escribe un correo válido o deja el campo vacío") }) else null,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )

            if (estado is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text(estado.message, color = AppTheme.colores.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !guardando,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }

                Button(
                    onClick = {
                        validar = true
                        if (!hayErrores) onGuardar(nombre, apellidos, celular, dni, email)
                    },
                    enabled = !guardando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (guardando) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = AppTheme.colores.textoSobreAcento,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  TRASLADAR INQUILINO DE CUARTO
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Mueve al inquilino a otro cuarto disponible.
 *
 * El backend libera el cuarto anterior y ocupa el nuevo dentro de una misma
 * transacción. Si se activa "aplicar el precio del cuarto nuevo", además ajusta
 * el recibo que esté pendiente; los ya pagados nunca se tocan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrasladarInquilinoSheet(
    inquilino:    InquilinoMobile,
    cuartosState: UiState<List<CuartoLibre>>,
    estado:       UiState<String>,
    onRecargar:   () -> Unit,
    onConfirmar:  (idCuartoNuevo: String, aplicarPrecioNuevo: Boolean) -> Unit,
    onDismiss:    () -> Unit
) {
    var cuartoElegido      by remember(inquilino.idInquilino) { mutableStateOf<CuartoLibre?>(null) }
    var aplicarPrecioNuevo by remember(inquilino.idInquilino) { mutableStateOf(true) }
    var confirmando        by remember { mutableStateOf(false) }

    // La lista de cuartos libres pudo quedar vieja (o nunca cargarse si el usuario
    // no entró a esa pestaña), así que se pide al abrir.
    LaunchedEffect(inquilino.idInquilino) { onRecargar() }

    val trasladando = estado is UiState.Loading

    ModalBottomSheet(
        onDismissRequest = { if (!trasladando) onDismiss() },
        containerColor   = AppTheme.colores.superficie
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text("Trasladar Inquilino", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(4.dp))
            Text(
                "${inquilino.nombre} ${inquilino.apellidos} está ahora en " +
                    "${inquilino.casa} · ${inquilino.piso} · Cuarto ${inquilino.nroCuarto}.",
                fontSize = 13.sp, color = AppTheme.colores.textoSuave
            )

            Spacer(Modifier.height(20.dp))
            Text("Cuarto de destino", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))

            when (val s = cuartosState) {
                is UiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UiState.Error -> Text(s.message, color = AppTheme.colores.error, fontSize = 13.sp)

                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colores.advertenciaContenedor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = AppTheme.colores.advertencia)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "No tienes cuartos disponibles. Libera uno antes de trasladar.",
                                    fontSize = 13.sp, color = AppTheme.colores.advertenciaTexto
                                )
                            }
                        }
                    } else {
                        // Column y no LazyColumn: esta hoja ya tiene scroll propio y
                        // anidar dos scrolls verticales rompe el gesto.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.data.forEach { cuarto ->
                                val elegido = cuartoElegido?.idCuarto == cuarto.idCuarto
                                Card(
                                    modifier = Modifier.fillMaxWidth().bounceClick { cuartoElegido = cuarto },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (elegido) AppTheme.colores.doradoContenedor
                                                         else AppTheme.colores.superficie
                                    ),
                                    border = BorderStroke(
                                        if (elegido) 2.dp else 1.dp,
                                        if (elegido) AzulPrimario else AppTheme.colores.borde
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = elegido,
                                            onClick  = { cuartoElegido = cuarto },
                                            colors   = RadioButtonDefaults.colors(selectedColor = AzulPrimario)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Cuarto ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(
                                                "${cuarto.casa} · ${cuarto.piso}",
                                                fontSize = 12.sp, color = AppTheme.colores.textoMedio
                                            )
                                        }
                                        cuarto.precio?.aMontoOrNull()?.let { precio ->
                                            Text(
                                                "S/ ${"%.2f".format(precio)}",
                                                fontWeight = FontWeight.Bold,
                                                color = AzulPrimario,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }

            // ── Precio del cuarto nuevo ──
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficieTenue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cobrar el precio del cuarto nuevo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (aplicarPrecioNuevo)
                                "Se ajustará el recibo pendiente al precio del cuarto de destino."
                            else
                                "El recibo pendiente mantendrá el precio del cuarto actual.",
                            fontSize = 12.sp, color = AppTheme.colores.textoSuave
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = aplicarPrecioNuevo,
                        onCheckedChange = { aplicarPrecioNuevo = it },
                        enabled = !trasladando,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppTheme.colores.textoSobreAcento,
                            checkedTrackColor = AzulPrimario
                        )
                    )
                }
            }

            if (estado is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text(estado.message, color = AppTheme.colores.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick  = onDismiss,
                    enabled  = !trasladando,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }

                Button(
                    onClick  = { confirmando = true },
                    enabled  = cuartoElegido != null && !trasladando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (trasladando) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = AppTheme.colores.textoSobreAcento,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Trasladar")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // El traslado mueve al inquilino y puede cambiar lo que debe: se confirma antes.
    if (confirmando) {
        val destino = cuartoElegido
        AlertDialog(
            onDismissRequest = { confirmando = false },
            confirmButton = {
                Button(onClick = {
                    confirmando = false
                    destino?.let { onConfirmar(it.idCuarto, aplicarPrecioNuevo) }
                }) { Text("Sí, trasladar") }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text("Cancelar") } },
            title = { Text("Confirmar traslado") },
            text = {
                Text(
                    "${inquilino.nombre} ${inquilino.apellidos} pasará del cuarto " +
                        "${inquilino.nroCuarto} al ${destino?.nroCuarto ?: ""} " +
                        "(${destino?.casa ?: ""} · ${destino?.piso ?: ""}).\n\n" +
                        if (aplicarPrecioNuevo)
                            "El recibo pendiente se ajustará al precio del cuarto nuevo."
                        else
                            "El recibo pendiente no cambiará de monto."
                )
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SECCIÓN ADMIN — USUARIOS
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionAdminUsuarios(vm: PagosViewModel) {
    val state by vm.usuariosState.collectAsStateWithLifecycle()
    val adminAction by vm.adminActionState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarUsuarios() }

    var usuarioSeleccionado by remember { mutableStateOf<UsuarioAdmin?>(null) }
    var mensajeExitoAdmin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(adminAction) {
        if (adminAction is UiState.Success) {
            mensajeExitoAdmin = (adminAction as UiState.Success<String>).data
            usuarioSeleccionado = null
            vm.resetAdminActionState()
        }
    }

    LaunchedEffect(mensajeExitoAdmin) {
        if (mensajeExitoAdmin != null) {
            kotlinx.coroutines.delay(1000)
            mensajeExitoAdmin = null
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lista de usuarios registrados", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarUsuarios() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay usuarios registrados.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { it.idUsuario }) { usr ->
                                val colorEstado = when (usr.estado) {
                                    "activo"    -> AppTheme.colores.exito
                                    "pendiente" -> AppTheme.colores.advertencia
                                    else        -> AppTheme.colores.textoSuave
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { usuarioSeleccionado = usr },
                                    colors = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
                                    border = BorderStroke(1.dp, colorEstado.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(AzulPrimario),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(usr.nombre.take(1).uppercase(), color = AppTheme.colores.textoSobreAcento, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${usr.nombre} ${usr.apellido ?: ""}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text(usr.email ?: "Sin email", fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                                            Text("Plan: ${usr.plan ?: "Sin plan"} · Activos: ${usr.inquilinosActivos ?: 0}/${usr.planCapacidad ?: "∞"}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                        }
                                        Text(
                                            (usr.estado ?: "?").replaceFirstChar { it.uppercase() },
                                            color = colorEstado,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (usuarioSeleccionado != null) {
        UsuarioDetalleBottomSheet(
            usuario = usuarioSeleccionado!!,
            adminAction = adminAction,
            onCambiarEstado = { usr, estado -> vm.cambiarEstadoUsuario(usr.idUsuario, estado) },
            onDismiss = { usuarioSeleccionado = null; vm.resetAdminActionState() }
        )
    }

    if (mensajeExitoAdmin != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { mensajeExitoAdmin = null }) { Text("OK", color = AppTheme.colores.textoSobreAcento) } },
            containerColor = AppTheme.colores.exito
        ) { Text(mensajeExitoAdmin!!, color = AppTheme.colores.textoSobreAcento) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioDetalleBottomSheet(
    usuario: UsuarioAdmin,
    adminAction: UiState<String>,
    onCambiarEstado: (UsuarioAdmin, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colores.superficie) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Usuario", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Nombre", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text("${usuario.nombre} ${usuario.apellido ?: ""}", fontWeight = FontWeight.Bold) }
            }
            if (!usuario.email.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Email, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Email", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(usuario.email, fontWeight = FontWeight.Bold) }
                }
            }
            if (!usuario.celular.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Celular", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(usuario.celular, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AzulPrimario)
                            .clickable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${usuario.celular}"))) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Phone, "Llamar", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colores.whatsapp)
                            .clickable {
                                val num = usuario.celular.replace(Regex("[^\\d]"), "")
                                val waNum = if (num.length == 9) "51$num" else num
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum")))
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = AppTheme.colores.textoSobreAcento, modifier = Modifier.size(20.dp)) }
                }
            }
            if (!usuario.dni.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Badge, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("DNI", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text(usuario.dni, fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.WorkspacePremium, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Plan", fontSize = 11.sp, color = AppTheme.colores.textoSuave)
                    Text("${usuario.plan ?: "Sin plan"} · Activos: ${usuario.inquilinosActivos ?: 0}/${usuario.planCapacidad ?: "∞"}", fontWeight = FontWeight.Bold)
                }
            }

            val colorEstado = when (usuario.estado) { "activo" -> AppTheme.colores.exito; "pendiente" -> AppTheme.colores.advertencia; else -> AppTheme.colores.textoSuave }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Circle, null, tint = colorEstado, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text("Estado", fontSize = 11.sp, color = AppTheme.colores.textoSuave); Text((usuario.estado ?: "?").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, color = colorEstado) }
            }

            Spacer(Modifier.height(16.dp))

            if (usuario.estado == "activo") {
                Button(
                    onClick = { onCambiarEstado(usuario, "inactivo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.peligro),
                    enabled = adminAction !is UiState.Loading
                ) {
                    if (adminAction is UiState.Loading) CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colores.textoSobreAcento, strokeWidth = 2.dp)
                    else Text("Inactivar Usuario")
                }
            } else {
                Button(
                    onClick = { onCambiarEstado(usuario, "activo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito),
                    enabled = adminAction !is UiState.Loading
                ) {
                    if (adminAction is UiState.Loading) CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colores.textoSobreAcento, strokeWidth = 2.dp)
                    else Text("Activar Usuario")
                }
            }

            if (adminAction is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((adminAction as UiState.Error).message, color = AppTheme.colores.error, fontSize = 13.sp)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SECCIÓN ADMIN — PAGOS PENDIENTES DE SUSCRIPCIÓN
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionAdminPagos(vm: PagosViewModel) {
    val state by vm.pagosUsuariosState.collectAsStateWithLifecycle()
    val adminAction by vm.adminActionState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarPagosUsuarios() }

    var pagoAConfirmar by remember { mutableStateOf<PagoUsuario?>(null) }

    LaunchedEffect(adminAction) {
        if (adminAction is UiState.Success) {
            pagoAConfirmar = null
            vm.resetAdminActionState()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pagos de suscripción pendientes", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarPagosUsuarios() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay pagos pendientes.\nDesliza hacia abajo para actualizar.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { it.idPagoUsuario }) { pago ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = AppTheme.colores.advertenciaContenedor),
                                    border = BorderStroke(1.dp, AppTheme.colores.advertencia.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colores.advertencia),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Payment, null, tint = AppTheme.colores.textoSobreAcento)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pago.nombres ?: "Usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("DNI: ${pago.dni ?: "—"}", fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                                            Text("Plan: ${pago.nombrePlan ?: "—"}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                            if (pago.fechaFacturacion != null) {
                                                Text("Facturado: ${pago.fechaFacturacion.take(10)}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                            }
                                        }
                                        Button(
                                            onClick = { pagoAConfirmar = pago },
                                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("S/ ${pago.monto ?: "0"}", fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (pagoAConfirmar != null) {
        val pago = pagoAConfirmar!!
        AlertDialog(
            onDismissRequest = { pagoAConfirmar = null },
            confirmButton = {
                Button(
                    onClick = { vm.confirmarPagoUsuario(pago.idPagoUsuario); pagoAConfirmar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.exito)
                ) { Text("Sí, confirmar") }
            },
            dismissButton = { TextButton(onClick = { pagoAConfirmar = null }) { Text("Cancelar") } },
            title = { Text("Confirmar Pago") },
            text = { Text("¿Confirmar pago de S/ ${pago.monto ?: "0"} de ${pago.nombres ?: "usuario"}?") }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SECCIÓN ADMIN — PAGOS REGISTRADOS (HISTORIAL + REVERTIR)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionAdminPagosRealizados(vm: PagosViewModel) {
    val state by vm.pagosRealizadosState.collectAsStateWithLifecycle()
    val adminAction by vm.adminActionState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarPagosRealizados() }

    var pagoARevertir by remember { mutableStateOf<PagoUsuario?>(null) }

    LaunchedEffect(adminAction) {
        if (adminAction is UiState.Success) {
            pagoARevertir = null
            vm.resetAdminActionState()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pagos de suscripción realizados", fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarPagosRealizados() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay pagos registrados.\nDesliza hacia abajo para actualizar.", color = AppTheme.colores.textoSuave)
                        }
                    } else {
                        // Solo se puede revertir el ÚLTIMO pago registrado de cada usuario
                        // (para corregir errores de registro sin romper la secuencia mensual).
                        val idsUltimoPorUsuario = s.data
                            .sortedByDescending { it.fechaRegistro ?: "" }
                            .distinctBy { it.idUsuario }
                            .map { it.idPagoUsuario }
                            .toSet()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { it.idPagoUsuario }) { pago ->
                                val esUltimo = pago.idPagoUsuario in idsUltimoPorUsuario
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = AppTheme.colores.exitoContenedorTenue),
                                    border = BorderStroke(1.dp, AppTheme.colores.exito.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colores.exito),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = AppTheme.colores.textoSobreAcento)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pago.nombres ?: "Usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Plan: ${pago.nombrePlan ?: "—"}", fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                                            Text("S/ ${pago.monto ?: "0"}", fontSize = 12.sp, color = AppTheme.colores.exitoFuerte, fontWeight = FontWeight.Bold)
                                            if (pago.fechaRegistro != null) {
                                                Text("Pagado: ${pago.fechaRegistro.take(10)}", fontSize = 11.sp, color = AppTheme.colores.textoMedio)
                                            }
                                        }
                                        if (esUltimo) {
                                            TextButton(onClick = { pagoARevertir = pago }) {
                                                Text("Revertir", color = AppTheme.colores.advertencia, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        } else {
                                            Text(
                                                "Solo se revierte\nel último",
                                                fontSize = 10.sp,
                                                color = AppTheme.colores.textoSuave,
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = AppTheme.colores.error) }
                else -> Unit
            }
        }
    }

    if (pagoARevertir != null) {
        val pago = pagoARevertir!!
        AlertDialog(
            onDismissRequest = { pagoARevertir = null },
            confirmButton = {
                Button(
                    onClick = { vm.revertirPagoUsuario(pago.idPagoUsuario); pagoARevertir = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colores.advertencia)
                ) { Text("Sí, revertir") }
            },
            dismissButton = { TextButton(onClick = { pagoARevertir = null }) { Text("Cancelar") } },
            title = { Text("Revertir Pago") },
            text = { Text("¿Deseas revertir el pago de S/ ${pago.monto ?: "0"} de ${pago.nombres ?: "usuario"}?") }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN AJUSTES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SeccionAjustes() {
    val context = LocalContext.current
    val dataStore = remember { SessionDataStore(context) }
    val tipoAviso by dataStore.tipoAviso.collectAsStateWithLifecycle(initialValue = "notificacion")
    val horaNotif by dataStore.horaNotificacion.collectAsStateWithLifecycle(initialValue = "08:00")
    val escalaTexto by dataStore.escalaTexto.collectAsStateWithLifecycle(initialValue = 1f)
    val scope = rememberCoroutineScope()

    // Tema actual: el que ya está pintado en pantalla. Si el usuario todavía no
    // eligió, refleja el ajuste del sistema, que es justo lo que se está viendo.
    val esOscuro = AppTheme.colores.esOscuro

    /** Cambia entre claro y oscuro. Se guarda al instante y todo el árbol se repinta. */
    fun cambiarTema(oscuro: Boolean) {
        scope.launch { dataStore.guardarTemaOscuro(oscuro) }
    }

    // Índice del nivel de letra actual dentro de NIVELES_TEXTO. Se compara con
    // tolerancia porque el valor viaja como Float por DataStore.
    val nivelActual = NIVELES_TEXTO
        .indexOfFirst { kotlin.math.abs(it.first - escalaTexto) < 0.01f }
        .coerceAtLeast(0)

    /** Guarda el nivel de letra. Es solo visual: no toca ningún dato ni ajuste del servidor. */
    fun cambiarNivelTexto(nivel: Int) {
        val destino = NIVELES_TEXTO.getOrNull(nivel) ?: return
        scope.launch { dataStore.guardarEscalaTexto(destino.first) }
    }

    // Guarda el ajuste localmente (efecto inmediato) y lo sincroniza con el backend
    // en segundo plano: así, si el usuario reinstala la app o cambia de dispositivo,
    // el próximo login recupera este mismo ajuste en vez de volver al valor por defecto.
    fun actualizarTipoAviso(tipo: String) {
        scope.launch {
            dataStore.guardarTipoAviso(tipo)
            runCatching {
                val uid = dataStore.userId.first() ?: return@runCatching
                AlquilerApiClient.service.guardarAjustes(GuardarAjustesRequest(idUsuario = uid, tipoAviso = tipo))
            }
        }
    }

    fun actualizarHora(hora: String) {
        scope.launch {
            dataStore.guardarHoraNotificacion(hora)
            // Reprograma la alarma exacta para la próxima ocurrencia de la nueva hora.
            RecordatorioScheduler.programar(context, hora)
            runCatching {
                val uid = dataStore.userId.first() ?: return@runCatching
                AlquilerApiClient.service.guardarAjustes(GuardarAjustesRequest(idUsuario = uid, horaNotificacion = hora))
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── Apariencia ─────────────────────────────────────────────────────────
        Text("Apariencia", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AzulPrimario)

        Text(
            "Cambia entre el modo claro y el modo oscuro. Tu elección se guarda en " +
                "este dispositivo y se mantiene aunque cierres sesión.",
            fontSize = 14.sp, color = AppTheme.colores.textoSuave
        )

        Card(
            modifier = Modifier.fillMaxWidth().bounceClick { cambiarTema(!esOscuro) },
            colors = CardDefaults.cardColors(
                containerColor = if (esOscuro) AppTheme.colores.doradoContenedor
                                 else AppTheme.colores.superficie
            ),
            border = BorderStroke(2.dp, if (esOscuro) AzulPrimario else AppTheme.colores.borde),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (esOscuro) Icons.Default.DarkMode else Icons.Default.LightMode,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = if (esOscuro) AzulPrimario else AppTheme.colores.ambar
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (esOscuro) "Modo oscuro" else "Modo claro",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    Text(
                        if (esOscuro) "Fondos oscuros, más cómodo de noche."
                        else "Fondos claros, más legible con mucha luz.",
                        fontSize = 13.sp, color = AppTheme.colores.textoSuave
                    )
                }
                Switch(
                    checked = esOscuro,
                    onCheckedChange = { cambiarTema(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = AppTheme.colores.textoSobreAcento,
                        checkedTrackColor   = AzulPrimario,
                        uncheckedThumbColor = AppTheme.colores.superficie,
                        uncheckedTrackColor = AppTheme.colores.bordeTenue
                    )
                )
            }
        }

        // ── Tamaño de letra ────────────────────────────────────────────────────
        Text(
            "Agranda o reduce el texto de toda la app. Es solo visual: no cambia " +
                "ningún monto, fecha ni cálculo.",
            fontSize = 14.sp, color = AppTheme.colores.textoSuave
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
            border   = BorderStroke(2.dp, AppTheme.colores.borde),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FormatSize, null,
                        modifier = Modifier.size(40.dp),
                        tint = AzulPrimario
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tamaño de letra", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Se guarda en este dispositivo, igual que el tema.",
                            fontSize = 13.sp, color = AppTheme.colores.textoSuave
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Selector paso a paso en vez de una fila de opciones: con la letra
                // al máximo cuatro etiquetas no entran en pantallas angostas.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val puedeReducir = nivelActual > 0
                    IconButton(
                        onClick  = { cambiarNivelTexto(nivelActual - 1) },
                        enabled  = puedeReducir,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (puedeReducir) AppTheme.colores.doradoContenedor
                                else AppTheme.colores.superficieTenue
                            )
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Reducir el tamaño de letra",
                            tint = if (puedeReducir) AzulPrimario else AppTheme.colores.textoSuave
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            NIVELES_TEXTO[nivelActual].second,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            maxLines   = 1
                        )
                        Text(
                            "%d %%".format((NIVELES_TEXTO[nivelActual].first * 100).toInt()),
                            fontSize = 12.sp,
                            color    = AppTheme.colores.textoSuave
                        )
                    }

                    val puedeAgrandar = nivelActual < NIVELES_TEXTO.lastIndex
                    IconButton(
                        onClick  = { cambiarNivelTexto(nivelActual + 1) },
                        enabled  = puedeAgrandar,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (puedeAgrandar) AppTheme.colores.doradoContenedor
                                else AppTheme.colores.superficieTenue
                            )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Agrandar el tamaño de letra",
                            tint = if (puedeAgrandar) AzulPrimario else AppTheme.colores.textoSuave
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // La muestra usa los mismos tamaños que las tarjetas de inquilinos,
                // así que se ve exactamente cómo va a quedar la app antes de salir.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colores.superficieTenue)
                        .padding(14.dp)
                ) {
                    Text(
                        "VISTA PREVIA",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = AppTheme.colores.textoSuave
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Juan Pérez", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Cuarto 3 · vence hoy · S/ 300.00",
                        fontSize = 14.sp, color = AppTheme.colores.textoMedio
                    )
                }
            }
        }

        // ── Tipo de aviso ──────────────────────────────────────────────────────
        HorizontalDivider(Modifier.padding(top = 8.dp))

        Text("Tipo de Aviso", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AzulPrimario)

        Text(
            "Elige cómo deseas recibir los avisos cuando tengas cobros o servicios vencidos.",
            fontSize = 14.sp, color = AppTheme.colores.textoSuave
        )

        Card(
            modifier = Modifier.fillMaxWidth().bounceClick {
                actualizarTipoAviso("notificacion")
            },
            colors = CardDefaults.cardColors(
                containerColor = if (tipoAviso == "notificacion") AppTheme.colores.doradoContenedor else AppTheme.colores.superficie
            ),
            border = BorderStroke(
                2.dp,
                if (tipoAviso == "notificacion") AzulPrimario else AppTheme.colores.borde
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications, null,
                    modifier = Modifier.size(40.dp),
                    tint = if (tipoAviso == "notificacion") AzulPrimario else AppTheme.colores.textoSuave
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Notificación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Recibes una notificación silenciosa en la barra superior.", fontSize = 13.sp, color = AppTheme.colores.textoSuave)
                }
                RadioButton(
                    selected = tipoAviso == "notificacion",
                    onClick  = { actualizarTipoAviso("notificacion") },
                    colors   = RadioButtonDefaults.colors(selectedColor = AzulPrimario)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().bounceClick {
                actualizarTipoAviso("alarma")
            },
            colors = CardDefaults.cardColors(
                containerColor = if (tipoAviso == "alarma") AppTheme.colores.advertenciaContenedor else AppTheme.colores.superficie
            ),
            border = BorderStroke(
                2.dp,
                if (tipoAviso == "alarma") AppTheme.colores.advertencia else AppTheme.colores.borde
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Alarm, null,
                    modifier = Modifier.size(40.dp),
                    tint = if (tipoAviso == "alarma") AppTheme.colores.advertencia else AppTheme.colores.textoSuave
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alarma", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Suena una alarma con sonido fuerte y pantalla completa.", fontSize = 13.sp, color = AppTheme.colores.textoSuave)
                }
                RadioButton(
                    selected = tipoAviso == "alarma",
                    onClick  = { actualizarTipoAviso("alarma") },
                    colors   = RadioButtonDefaults.colors(selectedColor = AppTheme.colores.advertencia)
                )
            }
        }

        // ── Hora del aviso diario ──────────────────────────────────────────────
        HorizontalDivider(Modifier.padding(top = 8.dp))

        Text("Hora del Aviso Diario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AzulPrimario)
        Text(
            "Elige a qué hora (hora local de tu país) quieres recibir el aviso diario de cobros y servicios pendientes.",
            fontSize = 14.sp, color = AppTheme.colores.textoSuave
        )

        Card(
            modifier = Modifier.fillMaxWidth().clickable {
                val partes = horaNotif.split(":")
                val h = partes.getOrNull(0)?.toIntOrNull() ?: 8
                val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(
                    context,
                    { _, hora, minuto ->
                        // Se guarda en 24h ("HH:mm") internamente; el selector entrega 0-23.
                        val nuevaHora = "%02d:%02d".format(hora, minuto)
                        actualizarHora(nuevaHora)
                    },
                    h, m, false // false = selector en formato 12 horas (AM/PM)
                ).show()
            },
            colors = CardDefaults.cardColors(containerColor = AppTheme.colores.olivaContenedor),
            border = BorderStroke(2.dp, AppTheme.colores.oliva),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule, null,
                    modifier = Modifier.size(40.dp),
                    tint = AppTheme.colores.oliva
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hora del aviso", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Toca para cambiar la hora local del aviso diario.", fontSize = 13.sp, color = AppTheme.colores.textoSuave)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    horaEn12h(horaNotif),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = AppTheme.colores.olivaTexto,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
