// ─── ui/pagos/SeccionLimpieza.kt ─────────────────────────────────────────────
// Horario de limpieza: qué inquilino limpia cada día, agrupado por piso.
//
// La regla del negocio es "un inquilino por día en cada piso", pero se aplica solo
// al asignar. Los datos existentes ya traen días repetidos (y un piso puede tener
// más de 7 cuartos ocupados, con lo que la regla es imposible de cumplir), así que
// la pantalla los MUESTRA marcados como conflicto en vez de esconderlos: es la
// única forma de que el usuario sepa que están ahí y pueda resolverlos.
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
import androidx.compose.material.icons.filled.CleaningServices
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
import com.example.myapplication.data.model.LimpiezaInquilino
import com.example.myapplication.data.model.UiState
import com.example.myapplication.ui.theme.AppTheme

/**
 * Los siete días, en el orden y con la escritura que espera el backend.
 * Es la única lista: el asistente de alta también la usa, para que no puedan
 * separarse y acabar mandando un día que el servidor rechace.
 */
internal val DIAS_SEMANA = listOf(
    "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
)

private val LimpDorado: Color
    @Composable get() = AppTheme.colores.dorado

/** Un piso con sus inquilinos repartidos por día. */
private data class PisoLimpieza(
    val idPiso:   String,
    val etiqueta: String,
    /** día → inquilinos que lo tienen. Más de uno = conflicto. */
    val porDia:   Map<String, List<LimpiezaInquilino>>,
    val sinDia:   List<LimpiezaInquilino>
) {
    val diasAsignados: Int get() = DIAS_SEMANA.count { !porDia[it].isNullOrEmpty() }
    val conflictos:    Int get() = DIAS_SEMANA.count { (porDia[it]?.size ?: 0) > 1 }
}

private fun agrupar(lista: List<LimpiezaInquilino>): List<PisoLimpieza> =
    lista.groupBy { it.idPiso }
        .map { (idPiso, delPiso) ->
            val asignados = delPiso.filter { it.diaLimpieza.isNotBlank() }
            PisoLimpieza(
                idPiso   = idPiso,
                etiqueta = delPiso.first().let { "${it.casa} · ${it.piso}" },
                porDia   = asignados.groupBy { it.diaLimpieza },
                sinDia   = delPiso.filter { it.diaLimpieza.isBlank() }
            )
        }
        .sortedBy { it.etiqueta }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeccionLimpieza(vm: PagosViewModel) {
    val state  by vm.limpiezaState.collectAsStateWithLifecycle()
    val accion by vm.limpiezaAccionState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.cargarLimpieza() }

    // Inquilino cuyo día se está cambiando (null = no hay diálogo abierto).
    var editando by remember { mutableStateOf<LimpiezaInquilino?>(null) }

    // El diálogo se cierra solo cuando el guardado sale bien; si falla (p. ej. el
    // día ya está tomado) se queda abierto mostrando el motivo.
    LaunchedEffect(accion) { if (accion is UiState.Success) editando = null }

    val pisos = remember(state) {
        agrupar((state as? UiState.Success)?.data.orEmpty())
    }

    editando?.let { inq ->
        val piso = pisos.firstOrNull { it.idPiso == inq.idPiso }
        DialogoDiaLimpieza(
            inquilino = inq,
            // Quién ocupa cada día en ese piso, sin contar al propio inquilino.
            ocupados = DIAS_SEMANA.mapNotNull { dia ->
                piso?.porDia?.get(dia)
                    ?.firstOrNull { it.idInquilino != inq.idInquilino }
                    ?.let { dia to it }
            }.toMap(),
            estadoAccion = accion,
            onElegir  = { dia -> vm.guardarDiaLimpieza(inq.idInquilino, dia) },
            onDismiss = { editando = null; vm.resetLimpiezaAccionState() }
        )
    }

    val isRefreshing = state is UiState.Loading
    val pullState = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        EncabezadoLista(
            "Horario de limpieza",
            total = (state as? UiState.Success)?.data?.size
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Cada piso reparte los 7 días entre sus inquilinos. Toca a un inquilino para cambiarle el día.",
            fontSize = 12.sp, color = AppTheme.colores.textoSuave
        )
        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { vm.cargarLimpieza() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {}
        ) {
            when (val s = state) {
                is UiState.Success -> {
                    if (pisos.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Aún no tienes inquilinos activos.",
                                color = AppTheme.colores.textoSuave
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(pisos, key = { it.idPiso }) { piso ->
                                TarjetaPiso(piso) { editando = it; vm.resetLimpiezaAccionState() }
                            }
                        }
                    }
                }
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = AppTheme.colores.error, modifier = Modifier.padding(24.dp))
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun TarjetaPiso(piso: PisoLimpieza, onTocarInquilino: (LimpiezaInquilino) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie),
        border   = BorderStroke(1.dp, AppTheme.colores.borde),
        shape    = RoundedCornerShape(16.dp)
    ) {
        // ── Cabecera del piso ──
        Row(
            Modifier.fillMaxWidth()
                .background(AppTheme.colores.doradoContenedor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CleaningServices, null,
                tint = LimpDorado, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    piso.etiqueta,
                    fontWeight = FontWeight.Black, fontSize = 14.sp,
                    color = AppTheme.colores.doradoContenedorTexto
                )
                Text(
                    buildString {
                        append("${piso.diasAsignados} de 7 días asignados")
                        if (piso.conflictos > 0) {
                            append(" · ${piso.conflictos} ")
                            append(if (piso.conflictos == 1) "conflicto" else "conflictos")
                        }
                    },
                    fontSize = 11.sp,
                    fontWeight = if (piso.conflictos > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (piso.conflictos > 0) AppTheme.colores.peligroTexto
                            else AppTheme.colores.doradoContenedorTexto
                )
            }
        }

        // ── Los siete días ──
        DIAS_SEMANA.forEachIndexed { i, dia ->
            if (i > 0) HorizontalDivider(color = AppTheme.colores.borde)
            val delDia = piso.porDia[dia].orEmpty()
            FilaDia(dia = dia, inquilinos = delDia, onTocar = onTocarInquilino)
        }

        // ── Sin día asignado ──
        if (piso.sinDia.isNotEmpty()) {
            HorizontalDivider(color = AppTheme.colores.borde)
            Column(
                Modifier.fillMaxWidth()
                    .background(AppTheme.colores.advertenciaContenedor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "Sin día asignado",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = AppTheme.colores.advertenciaTexto
                )
                piso.sinDia.forEach { inq ->
                    ChipInquilino(inq, resaltado = false, onTocar = onTocarInquilino)
                }
            }
        }
    }
}

@Composable
private fun FilaDia(
    dia: String,
    inquilinos: List<LimpiezaInquilino>,
    onTocar: (LimpiezaInquilino) -> Unit
) {
    val enConflicto = inquilinos.size > 1

    Row(
        Modifier.fillMaxWidth()
            .background(
                if (enConflicto) AppTheme.colores.peligroContenedorTenue else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            dia,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enConflicto) AppTheme.colores.peligroTexto else AppTheme.colores.texto,
            modifier = Modifier.width(88.dp)
        )
        Column(Modifier.weight(1f)) {
            when {
                inquilinos.isEmpty() -> Text(
                    "Libre",
                    fontSize = 13.sp, color = AppTheme.colores.textoSuave
                )
                else -> {
                    if (enConflicto) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning, null,
                                tint = AppTheme.colores.peligro, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${inquilinos.size} inquilinos el mismo día",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = AppTheme.colores.peligroTexto
                            )
                        }
                    }
                    inquilinos.forEach { inq ->
                        ChipInquilino(inq, resaltado = enConflicto, onTocar = onTocar)
                    }
                }
            }
        }
    }
}

/** Un inquilino dentro de la grilla, tocable para cambiarle el día. */
@Composable
private fun ChipInquilino(
    inquilino: LimpiezaInquilino,
    resaltado: Boolean,
    onTocar: (LimpiezaInquilino) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTocar(inquilino) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (resaltado) AppTheme.colores.peligro else LimpDorado),
            contentAlignment = Alignment.Center
        ) {
            Text(
                inquilino.nombre.take(1).uppercase(),
                color = AppTheme.colores.textoSobreAcento,
                fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(inquilino.nombreCompleto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "Cuarto ${inquilino.nroCuarto}" +
                    if (inquilino.estado == "pendiente_retiro") " · en retiro" else "",
                fontSize = 11.sp, color = AppTheme.colores.textoSuave
            )
        }
        Text("Cambiar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LimpDorado)
    }
}

/**
 * Elección del día de un inquilino.
 *
 * Los días que ya tiene otro inquilino del MISMO piso salen deshabilitados con el
 * nombre de quien los ocupa: así la regla se explica sola en vez de aparecer como
 * un error después de guardar. El backend la vuelve a validar igualmente.
 */
@Composable
private fun DialogoDiaLimpieza(
    inquilino: LimpiezaInquilino,
    ocupados: Map<String, LimpiezaInquilino>,
    estadoAccion: UiState<String>,
    onElegir: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val guardando = estadoAccion is UiState.Loading

    AlertDialog(
        onDismissRequest = { if (!guardando) onDismiss() },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) { Text("Cerrar") }
        },
        title = { Text("Día de limpieza") },
        text = {
            Column {
                Text(inquilino.nombreCompleto, fontWeight = FontWeight.Bold)
                Text(
                    "${inquilino.casa} · ${inquilino.piso} · Cuarto ${inquilino.nroCuarto}",
                    fontSize = 12.sp, color = AppTheme.colores.textoSuave
                )
                Spacer(Modifier.height(12.dp))

                DIAS_SEMANA.forEach { dia ->
                    val ocupadoPor = ocupados[dia]
                    val libre = ocupadoPor == null
                    val esSuyo = inquilino.diaLimpieza == dia

                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = libre && !guardando && !esSuyo) { onElegir(dia) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = esSuyo,
                            onClick  = { onElegir(dia) },
                            enabled  = libre && !guardando && !esSuyo
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                dia,
                                fontWeight = if (esSuyo) FontWeight.Bold else FontWeight.Normal,
                                color = if (libre) AppTheme.colores.texto else AppTheme.colores.textoSuave
                            )
                            ocupadoPor?.let {
                                Text(
                                    "Ocupado por ${it.nombreCompleto} · cuarto ${it.nroCuarto}",
                                    fontSize = 11.sp, color = AppTheme.colores.peligroTexto
                                )
                            }
                        }
                    }
                }

                if (inquilino.diaLimpieza.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onElegir("") }, enabled = !guardando) {
                        Text(
                            "Quitar el día asignado",
                            color = AppTheme.colores.advertencia, fontSize = 13.sp
                        )
                    }
                }

                if (guardando) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                (estadoAccion as? UiState.Error)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.message, color = AppTheme.colores.error, fontSize = 12.sp)
                }
            }
        }
    )
}
