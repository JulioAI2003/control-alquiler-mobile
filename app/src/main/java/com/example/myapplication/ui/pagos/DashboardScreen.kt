package com.example.myapplication.ui.pagos

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.myapplication.data.model.InquilinoMobile
import com.example.myapplication.data.model.ServicioCasa
import com.example.myapplication.data.model.UiState
import com.example.myapplication.data.model.UsuarioAdmin
import com.example.myapplication.data.model.PagoUsuario
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.worker.RecordatorioScheduler
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

private val AzulPrimario = Color(0xFF8A6A12)

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

    // Reset monto al abrir/cerrar el diálogo de servicio
    LaunchedEffect(servicioAConfirmar) { montoIngresadoServicio = "" }

    val pagoRapidoState by vm.pagoRapidoState.collectAsStateWithLifecycle()
    val pagarServicioState by vm.pagarServicioState.collectAsStateWithLifecycle()
    val retiroStateGlobal by vm.retiroState.collectAsStateWithLifecycle()

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

    LaunchedEffect(retiroStateGlobal) {
        if (retiroStateGlobal is UiState.Success && inquilinoDetalle != null) {
            mensajeExito = (retiroStateGlobal as UiState.Success<String>).data
            inquilinoDetalle = null
            vm.resetRetiroState()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                Spacer(Modifier.height(16.dp))
                Text("Cobros App", Modifier.padding(24.dp), fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
                if (!esAdmin && !esIndividual) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Inicio") },
                        selected = currentScreen in listOf("pendientes", "servicios", "cuartos"),
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
                        label = { Text("Cuartos") },
                        selected = currentScreen == "cuartos_todos",
                        onClick = { currentScreen = "cuartos_todos"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.ReceiptLong, null) },
                        label = { Text("Servicios Pagados") },
                        selected = currentScreen == "servicios_pagados",
                        onClick = { currentScreen = "servicios_pagados"; scope.launch { drawerState.close() } },
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
                    icon = { Icon(Icons.Default.Logout, null, tint = Color.Red) },
                    label = { Text("Cerrar Sesión", color = Color.Red) },
                    selected = false,
                    onClick = { onLogout() },
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
                            containerColor = Color.White, titleContentColor = AzulPrimario
                        )
                    )
                    if (showTabs) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.White,
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
            Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
                when (currentScreen) {
                    "pendientes" -> ListaPendientes(
                        vm = vm,
                        onCardClick = { inquilinoDetalle = it },
                        onPagarClick = { inquilinoAConfirmar = it }
                    )
                    "pagados"        -> SeccionPagados(vm)
                    "cuartos"        -> SeccionCuartosLibres(vm)
                    "cuartos_todos"  -> SeccionCuartos(vm)
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

    // Overlay de indicaciones (encima de todo). Se activa solo o con el botón de ayuda.
    CoachmarkOverlay(coach) { }
}

// Secciones (abiertas desde el menú) que tienen su propio tutorial, distinto del
// de la vista principal.
private val SECCIONES_CON_TUTORIAL = setOf(
    "pagados", "inquilinos", "cuartos_todos", "servicios_pagados", "ajustes",
    "admin_pagos", "admin_pagos_realizados"
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
            CoachStep(null, "Inquilinos", "Lista de tus inquilinos activos. Toca una tarjeta para ver su detalle, contactarlo por llamada/WhatsApp o iniciar su retiro."),
            repasar
        )
        "cuartos_todos" -> listOf(
            CoachStep(null, "Cuartos", "Todos tus cuartos en un solo lugar. Toca uno para ver su detalle y editar su número, precio, garantía o descripción."),
            repasar
        )
        "servicios_pagados" -> listOf(
            CoachStep(null, "Servicios Pagados", "Historial de los servicios de la casa que ya pagaste (luz, agua, etc.). Puedes revertir un pago si te equivocaste."),
            repasar
        )
        "ajustes" -> listOf(
            CoachStep(null, "Ajustes", "Elige cómo recibir los avisos: notificación silenciosa o alarma con sonido. También defines a qué hora del día llega el recordatorio diario de cobros y servicios pendientes."),
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
            CoachStep(null, "Registrar y recordar", "En \"Pendientes\" toca Cobrar o Pagar y confirma; si aún no toca, usa \"Posponer\". La app te recordará cada pago e ingreso para que nunca se te olvide una cuota."),
            repasar
        )
        "Administrador" -> listOf(
            CoachStep("menu", "Menú de administración", "Aquí gestionas Usuarios, Pagos Pendientes y Pagos Registrados."),
            repasar
        )
        else -> listOf(
            CoachStep("menu", "Menú", "Aquí abres el menú: Inquilinos, Cuartos, Inquilinos Pagados, Servicios Pagados y Ajustes."),
            CoachStep("tab_0", "Cobros", "Cobros pendientes de tus inquilinos. Toca el monto para registrar el pago; toca la tarjeta para ver el detalle."),
            CoachStep("tab_1", "Servicios", "Servicios de la casa (luz, agua, etc.). Regístralos cuando los pagues."),
            CoachStep("tab_2", "Cuartos Libres", "Cuartos disponibles. Toca uno para ver su detalle y usa \"Alquilar\" para registrar un inquilino."),
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
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        Text(inquilino.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    if (!inquilino.garantiaPagada && (inquilino.montoGarantia ?: 0.0) > 0) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color(0xFFFFC107))
                                        )
                                    }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleBottomSheet(inquilino: Inquilino, vm: PagosViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val retiroState by vm.retiroState.collectAsStateWithLifecycle()
    var posponerOpen by remember { mutableStateOf(false) }
    if (posponerOpen) {
        PosponerRecordatorioDialog(
            clave = "pago:${inquilino.idPago}",
            titulo = inquilino.nombre,
            onDismiss = { posponerOpen = false }
        )
    }
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

            // ── Garantía ──
            val montoGar = inquilino.montoGarantia
            if (montoGar != null && montoGar > 0) {
                Spacer(Modifier.height(8.dp))
                if (inquilino.garantiaPagada) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("Pagado", fontSize = 12.sp, color = Color(0xFF388E3C))
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Text("Pendiente de pago", fontSize = 12.sp, color = Color(0xFFBF360C))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.pagarGarantia(inquilino.idInquilino) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        enabled = retiroState !is UiState.Loading
                    ) { Text("Pagar Garantía") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { posponerOpen = true },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionPagados(vm: PagosViewModel) {
    val state by vm.pagosRecientesState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarPagosRecientes() }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pagos realizados recientemente", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("Sin pagos en los últimos 10 días", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(s.data) { index, pago ->
                                Card(Modifier.fillMaxWidth().appear(index), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color.Red)
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
    val state       by vm.inquilinosState.collectAsStateWithLifecycle()
    val retiroState by vm.retiroState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarInquilinos() }

    var inquilinoSeleccionado by remember { mutableStateOf<InquilinoMobile?>(null) }
    var inquilinoARetirar     by remember { mutableStateOf<InquilinoMobile?>(null) }
    var filtroNombre by remember { mutableStateOf("") }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarInquilinos() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    val filtrados = if (filtroNombre.isBlank()) s.data
                    else s.data.filter {
                        "${it.nombre} ${it.apellidos}".contains(filtroNombre, ignoreCase = true)
                    }
                    if (filtrados.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (s.data.isEmpty()) "No hay inquilinos activos"
                                else "Sin resultados para \"$filtroNombre\"",
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
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
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(
                                                Modifier.size(44.dp).clip(CircleShape).background(borderColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(inq.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            if (inq.fechaGarantia == null && (inq.montoGarantia?.toDoubleOrNull() ?: 0.0) > 0) {
                                                Box(
                                                    Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color(0xFFFFC107))
                                                )
                                            }
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
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error   -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
                else -> Unit
            }
        }
    }

    // Bottom sheet con detalle + acciones
    if (inquilinoSeleccionado != null) {
        InquilinoBottomSheet(
            inquilino   = inquilinoSeleccionado!!,
            retiroState = retiroState,
            onRetirar        = { inquilinoARetirar = it },
            onCancelarRetiro = { vm.cancelarRetiro(it.idInquilino) },
            onPagarGarantia  = { vm.pagarGarantia(it.idInquilino) },
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

private val coloresCuartoLibre = EstadoColores(Color(0xFFEFE2BC), Color(0xFF8A6A12), Color(0xFF6E5410))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionCuartosLibres(vm: PagosViewModel) {
    val state by vm.cuartosLibresState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarCuartosLibres() }

    var cuartoSeleccionado by remember { mutableStateOf<CuartoLibre?>(null) }
    var cuartoAAlquilar by remember { mutableStateOf<CuartoLibre?>(null) }
    var mensajeExito by remember { mutableStateOf<String?>(null) }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cuartos disponibles", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarCuartosLibres() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(s.data, key = { _, it -> it.idCuarto }) { index, cuarto ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().appear(index).bounceClick { cuartoSeleccionado = cuarto },
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
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error   -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
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
            Button(
                onClick = { onAlquilar(cuarto) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
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
    val state by vm.serviciosState.collectAsStateWithLifecycle()
    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) { vm.cargarServicios() }

    var servicioARevertir by remember { mutableStateOf<ServicioCasa?>(null) }
    var servicioAPosponer by remember { mutableStateOf<ServicioCasa?>(null) }

    servicioAPosponer?.let { srv ->
        PosponerRecordatorioDialog(
            clave = "servicio:${srv.idServicio}:${srv.anio}-${srv.mes}",
            titulo = srv.nombre,
            onDismiss = { servicioAPosponer = null }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Servicios pendientes", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("No hay servicios pendientes.\nDesliza hacia abajo para actualizar.", color = Color.Gray)
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
                                            Text(srv.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.nombre, fontWeight = FontWeight.Bold, color = colores.texto, fontSize = 16.sp)
                                            Text(srv.etiquetaDias, fontSize = 12.sp, color = colores.texto, fontWeight = FontWeight.ExtraBold)
                                            Text("${srv.nombreMes} ${srv.anio} · Día ${srv.dia}", fontSize = 11.sp, color = Color.DarkGray)
                                        }
                                        if (srv.pagado) {
                                            TextButton(onClick = { servicioARevertir = srv }) {
                                                Text("Revertir", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        } else {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Button(
                                                    onClick = { onPagarClick(srv) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colores.borde),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                                ) {
                                                    Text("S/ ${"%.2f".format(srv.montoReferencial.toDoubleOrNull() ?: 0.0)}", fontWeight = FontWeight.Black)
                                                }
                                                Button(
                                                    onClick = { servicioAPosponer = srv },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Posponer", fontSize = 11.sp)
                                                }
                                            }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) { Text("Sí, revertir") }
            },
            dismissButton = { TextButton(onClick = { servicioARevertir = null }) { Text("Cancelar") } },
            title = { Text("Revertir Pago") },
            text = { Text("¿Deseas revertir el pago de ${srv.nombre} (${srv.nombreMes} ${srv.anio})?") }
        )
    }
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
        Text("Servicios pagados recientemente", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("No hay servicios pagados.\nDesliza hacia abajo para actualizar.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { "${it.idPago}-${it.idServicio}-${it.mes}-${it.anio}" }) { srv ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    border = BorderStroke(1.dp, Color(0xFF388E3C).copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF388E3C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(srv.nombre.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            val montoMostrar = srv.montoPagado?.toDoubleOrNull() ?: srv.montoReferencial.toDoubleOrNull() ?: 0.0
                                            Text("S/ ${"%.2f".format(montoMostrar)}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            Text("${srv.nombreMes} ${srv.anio} · Día ${srv.dia}", fontSize = 11.sp, color = Color.DarkGray)
                                            if (srv.fechaPago != null) {
                                                Text("Pagado: ${srv.fechaPago.take(10)}", fontSize = 11.sp, color = Color.DarkGray)
                                            }
                                        }
                                        TextButton(onClick = { servicioARevertir = srv }) {
                                            Text("Revertir", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
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
    onRetirar:        (InquilinoMobile) -> Unit,
    onCancelarRetiro: (InquilinoMobile) -> Unit,
    onPagarGarantia:  (InquilinoMobile) -> Unit,
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
            // ── Garantía ──
            val montoGar = inquilino.montoGarantia?.toDoubleOrNull()
            if (montoGar != null && montoGar > 0) {
                Spacer(Modifier.height(8.dp))
                if (inquilino.fechaGarantia != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("Pagado", fontSize = 12.sp, color = Color(0xFF388E3C))
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Garantía: S/ ${"%.2f".format(montoGar)}", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Text("Pendiente de pago", fontSize = 12.sp, color = Color(0xFFBF360C))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPagarGarantia(inquilino) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        enabled = retiroState !is UiState.Loading
                    ) { Text("Pagar Garantía") }
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
        Text("Lista de usuarios registrados", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("No hay usuarios registrados.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { it.idUsuario }) { usr ->
                                val colorEstado = when (usr.estado) {
                                    "activo"    -> Color(0xFF388E3C)
                                    "pendiente" -> Color(0xFFE65100)
                                    else        -> Color.Gray
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { usuarioSeleccionado = usr },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, colorEstado.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(AzulPrimario),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(usr.nombre.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${usr.nombre} ${usr.apellido ?: ""}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text(usr.email ?: "Sin email", fontSize = 12.sp, color = Color.Gray)
                                            Text("Plan: ${usr.plan ?: "Sin plan"} · Activos: ${usr.inquilinosActivos ?: 0}/${usr.planCapacidad ?: "∞"}", fontSize = 11.sp, color = Color.DarkGray)
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
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
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
            action = { TextButton(onClick = { mensajeExitoAdmin = null }) { Text("OK", color = Color.White) } },
            containerColor = Color(0xFF388E3C)
        ) { Text(mensajeExitoAdmin!!, color = Color.White) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Usuario", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AzulPrimario)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column { Text("Nombre", fontSize = 11.sp, color = Color.Gray); Text("${usuario.nombre} ${usuario.apellido ?: ""}", fontWeight = FontWeight.Bold) }
            }
            if (!usuario.email.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Email, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Email", fontSize = 11.sp, color = Color.Gray); Text(usuario.email, fontWeight = FontWeight.Bold) }
                }
            }
            if (!usuario.celular.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Celular", fontSize = 11.sp, color = Color.Gray); Text(usuario.celular, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AzulPrimario)
                            .clickable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${usuario.celular}"))) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Phone, "Llamar", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF25D366))
                            .clickable {
                                val num = usuario.celular.replace(Regex("[^\\d]"), "")
                                val waNum = if (num.length == 9) "51$num" else num
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNum")))
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_whatsapp), "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            if (!usuario.dni.isNullOrBlank()) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Badge, null, tint = AzulPrimario)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("DNI", fontSize = 11.sp, color = Color.Gray); Text(usuario.dni, fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.WorkspacePremium, null, tint = AzulPrimario)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Plan", fontSize = 11.sp, color = Color.Gray)
                    Text("${usuario.plan ?: "Sin plan"} · Activos: ${usuario.inquilinosActivos ?: 0}/${usuario.planCapacidad ?: "∞"}", fontWeight = FontWeight.Bold)
                }
            }

            val colorEstado = when (usuario.estado) { "activo" -> Color(0xFF388E3C); "pendiente" -> Color(0xFFE65100); else -> Color.Gray }
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Circle, null, tint = colorEstado, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text("Estado", fontSize = 11.sp, color = Color.Gray); Text((usuario.estado ?: "?").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, color = colorEstado) }
            }

            Spacer(Modifier.height(16.dp))

            if (usuario.estado == "activo") {
                Button(
                    onClick = { onCambiarEstado(usuario, "inactivo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    enabled = adminAction !is UiState.Loading
                ) {
                    if (adminAction is UiState.Loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Inactivar Usuario")
                }
            } else {
                Button(
                    onClick = { onCambiarEstado(usuario, "activo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    enabled = adminAction !is UiState.Loading
                ) {
                    if (adminAction is UiState.Loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Activar Usuario")
                }
            }

            if (adminAction is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((adminAction as UiState.Error).message, color = Color.Red, fontSize = 13.sp)
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
        Text("Pagos de suscripción pendientes", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("No hay pagos pendientes.\nDesliza hacia abajo para actualizar.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.data, key = { it.idPagoUsuario }) { pago ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE65100)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Payment, null, tint = Color.White)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pago.nombres ?: "Usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("DNI: ${pago.dni ?: "—"}", fontSize = 12.sp, color = Color.Gray)
                                            Text("Plan: ${pago.nombrePlan ?: "—"}", fontSize = 11.sp, color = Color.DarkGray)
                                            if (pago.fechaFacturacion != null) {
                                                Text("Facturado: ${pago.fechaFacturacion.take(10)}", fontSize = 11.sp, color = Color.DarkGray)
                                            }
                                        }
                                        Button(
                                            onClick = { pagoAConfirmar = pago },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
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
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
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
        Text("Pagos de suscripción realizados", fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            Text("No hay pagos registrados.\nDesliza hacia abajo para actualizar.", color = Color.Gray)
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
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    border = BorderStroke(1.dp, Color(0xFF388E3C).copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF388E3C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pago.nombres ?: "Usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Plan: ${pago.nombrePlan ?: "—"}", fontSize = 12.sp, color = Color.Gray)
                                            Text("S/ ${pago.monto ?: "0"}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            if (pago.fechaRegistro != null) {
                                                Text("Pagado: ${pago.fechaRegistro.take(10)}", fontSize = 11.sp, color = Color.DarkGray)
                                            }
                                        }
                                        if (esUltimo) {
                                            TextButton(onClick = { pagoARevertir = pago }) {
                                                Text("Revertir", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        } else {
                                            Text(
                                                "Solo se revierte\nel último",
                                                fontSize = 10.sp,
                                                color = Color.Gray,
                                                textAlign = TextAlign.End
                                            )
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

    if (pagoARevertir != null) {
        val pago = pagoARevertir!!
        AlertDialog(
            onDismissRequest = { pagoARevertir = null },
            confirmButton = {
                Button(
                    onClick = { vm.revertirPagoUsuario(pago.idPagoUsuario); pagoARevertir = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
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
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Tipo de Aviso", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AzulPrimario)

        Text(
            "Elige cómo deseas recibir los avisos cuando tengas cobros o servicios vencidos.",
            fontSize = 14.sp, color = Color.Gray
        )

        Card(
            modifier = Modifier.fillMaxWidth().bounceClick {
                scope.launch { dataStore.guardarTipoAviso("notificacion") }
            },
            colors = CardDefaults.cardColors(
                containerColor = if (tipoAviso == "notificacion") Color(0xFFF7EFD8) else Color.White
            ),
            border = BorderStroke(
                2.dp,
                if (tipoAviso == "notificacion") AzulPrimario else Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications, null,
                    modifier = Modifier.size(40.dp),
                    tint = if (tipoAviso == "notificacion") AzulPrimario else Color.Gray
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Notificación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Recibes una notificación silenciosa en la barra superior.", fontSize = 13.sp, color = Color.Gray)
                }
                RadioButton(
                    selected = tipoAviso == "notificacion",
                    onClick  = { scope.launch { dataStore.guardarTipoAviso("notificacion") } },
                    colors   = RadioButtonDefaults.colors(selectedColor = AzulPrimario)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().bounceClick {
                scope.launch { dataStore.guardarTipoAviso("alarma") }
            },
            colors = CardDefaults.cardColors(
                containerColor = if (tipoAviso == "alarma") Color(0xFFFFF3E0) else Color.White
            ),
            border = BorderStroke(
                2.dp,
                if (tipoAviso == "alarma") Color(0xFFE65100) else Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Alarm, null,
                    modifier = Modifier.size(40.dp),
                    tint = if (tipoAviso == "alarma") Color(0xFFE65100) else Color.Gray
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alarma", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Suena una alarma con sonido fuerte y pantalla completa.", fontSize = 13.sp, color = Color.Gray)
                }
                RadioButton(
                    selected = tipoAviso == "alarma",
                    onClick  = { scope.launch { dataStore.guardarTipoAviso("alarma") } },
                    colors   = RadioButtonDefaults.colors(selectedColor = Color(0xFFE65100))
                )
            }
        }

        // ── Hora del aviso diario ──────────────────────────────────────────────
        HorizontalDivider(Modifier.padding(top = 8.dp))

        Text("Hora del Aviso Diario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AzulPrimario)
        Text(
            "Elige a qué hora (hora local de tu país) quieres recibir el aviso diario de cobros y servicios pendientes.",
            fontSize = 14.sp, color = Color.Gray
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
                        scope.launch { dataStore.guardarHoraNotificacion(nuevaHora) }
                        // Reprograma la alarma exacta para la próxima ocurrencia de la nueva hora.
                        RecordatorioScheduler.programar(context, nuevaHora)
                    },
                    h, m, false // false = selector en formato 12 horas (AM/PM)
                ).show()
            },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
            border = BorderStroke(2.dp, Color(0xFF558B2F)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule, null,
                    modifier = Modifier.size(40.dp),
                    tint = Color(0xFF558B2F)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hora del aviso", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Toca para cambiar la hora local del aviso diario.", fontSize = 13.sp, color = Color.Gray)
                }
                Text(
                    horaEn12h(horaNotif),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF33691E)
                )
            }
        }
    }
}
