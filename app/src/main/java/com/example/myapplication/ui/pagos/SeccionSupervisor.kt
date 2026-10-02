// ─── ui/pagos/SeccionSupervisor.kt ───────────────────────────────────────────
// Pantallas del rol Supervisor: administra las cuentas de varios propietarios.
//
// Solo ve cobros y servicios de las cuentas que le concedieron acceso Y que él
// marcó. No hay inquilinos, cuartos, estadísticas ni ajustes de esas cuentas: su
// trabajo es cobrar y pagar, no administrar la propiedad ajena.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.CuentaSupervisada
import com.example.myapplication.data.model.ResumenCuenta
import com.example.myapplication.data.model.UiState
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.bounceClick

private val SupDorado: Color
    @Composable get() = AppTheme.colores.dorado

// ═════════════════════════════════════════════════════════════════════════════
//  INICIO · una tarjeta por cuenta administrada
// ═════════════════════════════════════════════════════════════════════════════

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionSupervisorInicio(
    vm: PagosViewModel,
    onAbrirCuenta: (CuentaSupervisada) -> Unit,
    onIrACuentas: () -> Unit
) {
    val state by vm.resumenSupervisorState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarResumenSupervisor() }

    val pullState = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val cuentas = (state as? UiState.Success)?.data.orEmpty()
        EncabezadoLista("Cuentas que administras", total = cuentas.size.takeIf { state is UiState.Success })
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca una cuenta para ver sus cobros y servicios pendientes.",
            fontSize = 12.sp, color = AppTheme.colores.textoSuave
        )
        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = state is UiState.Loading,
            onRefresh    = { vm.cargarResumenSupervisor() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = AppTheme.colores.error, modifier = Modifier.padding(24.dp))
                }
                is UiState.Success -> {
                    if (cuentas.isEmpty()) {
                        SinCuentas(onIrACuentas)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(cuentas, key = { it.idUsuario }) { resumen ->
                                TarjetaCuenta(resumen) {
                                    onAbrirCuenta(
                                        CuentaSupervisada(
                                            idUsuario = resumen.idUsuario,
                                            nombre    = resumen.nombre,
                                            apellido  = resumen.apellido,
                                            activo    = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SinCuentas(onIrACuentas: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Groups, null,
                modifier = Modifier.size(56.dp), tint = AppTheme.colores.textoSuave
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Todavía no administras ninguna cuenta",
                fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "El propietario tiene que escribir tu correo en sus Ajustes. Cuando lo haga, " +
                    "su cuenta aparecerá en \"Administrar cuentas\" para que la marques.",
                fontSize = 13.sp, color = AppTheme.colores.textoSuave
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onIrACuentas) { Text("Ir a administrar cuentas") }
        }
    }
}

@Composable
private fun TarjetaCuenta(resumen: ResumenCuenta, onClick: () -> Unit) {
    // El borde se enciende en rojo si algo está vencido: es lo que hay que mirar primero.
    val hayVencidos = resumen.vencidos > 0
    val acento = if (hayVencidos) AppTheme.colores.peligro else AppTheme.colores.borde

    Card(
        modifier = Modifier.fillMaxWidth().bounceClick { onClick() },
        colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border   = BorderStroke(if (hayVencidos) 2.dp else 1.dp, acento.copy(alpha = 0.6f)),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(SupDorado),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        resumen.nombre.take(1).uppercase(),
                        color = AppTheme.colores.textoSobreAcento,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(resumen.nombreCompleto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (resumen.pendientes == 0) "Sin pendientes"
                        else "${resumen.pendientes} pendiente(s)" +
                            if (hayVencidos) " · ${resumen.vencidos} vencido(s)" else "",
                        fontSize = 12.sp,
                        fontWeight = if (hayVencidos) FontWeight.Bold else FontWeight.Normal,
                        color = if (hayVencidos) AppTheme.colores.peligroTexto
                                else AppTheme.colores.textoSuave
                    )
                }
                if (hayVencidos) {
                    Icon(Icons.Default.Warning, null, tint = AppTheme.colores.peligro)
                }
            }

            HorizontalDivider(color = AppTheme.colores.borde)

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Contador(
                    icono = Icons.Default.CallReceived,
                    etiqueta = "Cobros",
                    cantidad = resumen.cobros,
                    monto = resumen.totalCobrosNum,
                    vencidos = resumen.cobrosVencidos,
                    acento = AppTheme.colores.exito,
                    modifier = Modifier.weight(1f)
                )
                Contador(
                    icono = Icons.Default.CallMade,
                    etiqueta = "Pagos",
                    cantidad = resumen.servicios,
                    monto = resumen.totalServiciosNum,
                    vencidos = resumen.serviciosVencidos,
                    acento = AppTheme.colores.advertencia,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Contador(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    etiqueta: String,
    cantidad: Int,
    monto: Double,
    vencidos: Int,
    acento: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, null, tint = acento, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(etiqueta, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = acento)
        }
        Text("$cantidad", fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            "S/ ${"%.2f".format(monto)}",
            fontSize = 12.sp, color = AppTheme.colores.textoMedio
        )
        if (vencidos > 0) {
            Text(
                "$vencidos vencido(s)",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = AppTheme.colores.peligroTexto
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  ADMINISTRAR CUENTAS · marcar cuáles se trabajan
// ═════════════════════════════════════════════════════════════════════════════

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionCuentasSupervisadas(vm: PagosViewModel) {
    val state  by vm.cuentasSupervisadasState.collectAsStateWithLifecycle()
    val accion by vm.accionCuentaState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.cargarCuentasSupervisadas() }

    val pullState = rememberPullToRefreshState()
    val cuentas = (state as? UiState.Success)?.data.orEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        EncabezadoLista(
            "Cuentas disponibles",
            total = cuentas.size.takeIf { state is UiState.Success },
            visibles = cuentas.count { it.activo }.takeIf { state is UiState.Success }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Aquí solo salen las cuentas que escribieron tu correo en sus Ajustes. " +
                "Marca las que quieras administrar.",
            fontSize = 12.sp, color = AppTheme.colores.textoSuave
        )

        (accion as? UiState.Error)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.message, color = AppTheme.colores.error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = state is UiState.Loading,
            onRefresh    = { vm.cargarCuentasSupervisadas() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = AppTheme.colores.error, modifier = Modifier.padding(24.dp))
                }
                is UiState.Success -> {
                    if (cuentas.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Ninguna cuenta te ha dado acceso todavía.",
                                color = AppTheme.colores.textoSuave,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(cuentas, key = { it.idUsuario }) { cuenta ->
                                FilaCuenta(cuenta, habilitado = accion !is UiState.Loading) {
                                    vm.marcarCuenta(cuenta.idUsuario, !cuenta.activo)
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun FilaCuenta(cuenta: CuentaSupervisada, habilitado: Boolean, onAlternar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (cuenta.activo) AppTheme.colores.doradoContenedor
                             else AppTheme.colores.superficie
        ),
        border = BorderStroke(
            if (cuenta.activo) 2.dp else 1.dp,
            if (cuenta.activo) SupDorado else AppTheme.colores.borde
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = cuenta.activo,
                onCheckedChange = { if (habilitado) onAlternar() },
                enabled = habilitado,
                colors = CheckboxDefaults.colors(checkedColor = SupDorado)
            )
            Column(Modifier.weight(1f)) {
                Text(cuenta.nombreCompleto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(cuenta.email, fontSize = 12.sp, color = AppTheme.colores.textoSuave)
                if (cuenta.estado != "activo") {
                    Text(
                        "Cuenta ${cuenta.estado}",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = AppTheme.colores.advertenciaTexto
                    )
                }
            }
        }
    }
}
