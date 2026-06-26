// ─── ui/pagos/CuartosScreen.kt ────────────────────────────────────────────────
// Sección "Cuartos": lista todos los cuartos del propietario con su detalle y
// permite editar nro de cuarto, precio, garantía y descripción (igual que la web).
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.myapplication.ui.theme.appear
import com.example.myapplication.ui.theme.bounceClick
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.CuartoDetalle
import com.example.myapplication.data.model.UiState

private val CtAzul = Color(0xFF8A6A12)

private fun coloresEstado(estado: String?): Pair<Color, Color> = when (estado) {
    "Disponible" -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
    "Alquilado"  -> Color(0xFFEFE2BC) to Color(0xFF6E5410)
    else         -> Color(0xFFEEEEEE) to Color(0xFF616161)
}

// Holder para recordar la edición pendiente mientras se pregunta por `estemes`.
private data class EdicionPendiente(
    val cuarto: CuartoDetalle, val nro: String, val precio: Double, val garantia: Double, val descripcion: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeccionCuartos(vm: PagosViewModel) {
    val state by vm.cuartosState.collectAsStateWithLifecycle()
    val editState by vm.editarCuartoState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarCuartos() }

    var detalle by remember { mutableStateOf<CuartoDetalle?>(null) }
    var editar by remember { mutableStateOf<CuartoDetalle?>(null) }
    var pendiente by remember { mutableStateOf<EdicionPendiente?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editState) {
        if (editState is UiState.Success) {
            mensaje = (editState as UiState.Success<String>).data
            editar = null
            pendiente = null
            vm.resetEditarCuartoState()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Todos los cuartos", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tienes cuartos registrados.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(s.data, key = { _, it -> it.idCuarto }) { index, cuarto ->
                            CuartoCard(cuarto, index = index, onClick = { detalle = cuarto })
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = Color.Red) }
            else -> Unit
        }
    }

    // Detalle (bottom sheet) con botón Editar
    detalle?.let { c ->
        CuartoDetalleSheet(c, onEditar = { detalle = null; editar = c }, onDismiss = { detalle = null })
    }

    // Diálogo de edición
    editar?.let { c ->
        EditarCuartoDialog(
            cuarto = c,
            isLoading = editState is UiState.Loading,
            onConfirm = { nro, precio, garantia, desc ->
                val precioAnterior = c.precio?.toDoubleOrNull()
                val cambioPrecio = precioAnterior == null || precio != precioAnterior
                if (c.estado == "Alquilado" && cambioPrecio) {
                    pendiente = EdicionPendiente(c, nro, precio, garantia, desc)
                } else {
                    vm.editarCuarto(c.idCuarto, nro, precio, garantia, c.idPiso, desc, false)
                }
            },
            onDismiss = { editar = null; vm.resetEditarCuartoState() }
        )
    }

    // ¿Aplicar el nuevo precio al recibo del mes actual? (solo cuarto alquilado + cambio de precio)
    pendiente?.let { p ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("Cambio de precio") },
            text = { Text("El cuarto tiene un inquilino activo. ¿Aplicar el nuevo precio al recibo del mes ya generado o solo a partir del siguiente recibo?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        vm.editarCuarto(p.cuarto.idCuarto, p.nro, p.precio, p.garantia, p.cuarto.idPiso, p.descripcion, true)
                    }) { Text("Recibo actual") }
                    TextButton(onClick = {
                        vm.editarCuarto(p.cuarto.idCuarto, p.nro, p.precio, p.garantia, p.cuarto.idPiso, p.descripcion, false)
                    }) { Text("Solo siguiente") }
                }
            },
            dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } }
        )
    }

    // Éxito
    mensaje?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensaje = null },
            confirmButton = { TextButton(onClick = { mensaje = null }) { Text("OK") } },
            title = { Text("Listo") },
            text = { Text(msg) }
        )
    }
}

@Composable
private fun CuartoCard(cuarto: CuartoDetalle, index: Int = 0, onClick: () -> Unit) {
    val (fondoEstado, textoEstado) = coloresEstado(cuarto.estado)
    Card(
        modifier = Modifier.fillMaxWidth().appear(index).bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(CtAzul),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MeetingRoom, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Cuarto ${cuarto.nroCuarto}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CtAzul)
                Text("${cuarto.casa ?: ""} · ${cuarto.piso ?: ""}", fontSize = 12.sp, color = Color.DarkGray)
                cuarto.precio?.toDoubleOrNull()?.let {
                    Text("S/ ${"%.2f".format(it)} · Garantía S/ ${cuarto.garantia?.toDoubleOrNull()?.let { g -> "%.2f".format(g) } ?: "—"}",
                        fontSize = 12.sp, color = Color.Gray)
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(fondoEstado).padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(cuarto.estado ?: "—", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textoEstado) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuartoDetalleSheet(cuarto: CuartoDetalle, onEditar: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Detalle del Cuarto", fontWeight = FontWeight.Black, fontSize = 22.sp, color = CtAzul)
            Spacer(Modifier.height(16.dp))
            DetalleFila(Icons.Default.MeetingRoom, "Cuarto", "Nro. ${cuarto.nroCuarto}")
            DetalleFila(Icons.Default.Home, "Ubicación", "${cuarto.casa ?: ""} · ${cuarto.piso ?: ""}")
            DetalleFila(Icons.Default.Payments, "Precio mensual", "S/ ${cuarto.precio?.toDoubleOrNull()?.let { "%.2f".format(it) } ?: "—"}")
            DetalleFila(Icons.Default.Shield, "Garantía", "S/ ${cuarto.garantia?.toDoubleOrNull()?.let { "%.2f".format(it) } ?: "—"}")
            DetalleFila(Icons.Default.Info, "Estado", cuarto.estado ?: "—")
            if (!cuarto.descripcion.isNullOrBlank()) {
                DetalleFila(Icons.Default.Description, "Descripción", cuarto.descripcion)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onEditar, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtAzul)
            ) {
                Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Editar", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

@Composable
private fun DetalleFila(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, valor: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = CtAzul)
        Spacer(Modifier.width(12.dp))
        Column { Text(label, fontSize = 11.sp, color = Color.Gray); Text(valor, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EditarCuartoDialog(
    cuarto: CuartoDetalle,
    isLoading: Boolean,
    onConfirm: (nro: String, precio: Double, garantia: Double, descripcion: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var nro by remember { mutableStateOf(cuarto.nroCuarto) }
    var precio by remember { mutableStateOf(cuarto.precio ?: "") }
    var garantia by remember { mutableStateOf(cuarto.garantia ?: "") }
    var descripcion by remember { mutableStateOf(cuarto.descripcion ?: "") }
    val valido = nro.isNotBlank() && precio.toDoubleOrNull() != null && garantia.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Editar Cuarto ${cuarto.nroCuarto}") },
        text = {
            Column {
                OutlinedTextField(
                    nro, { nro = it.filter(Char::isDigit) }, label = { Text("N° de cuarto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    precio, { precio = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Precio mensual (S/)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    garantia, { garantia = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Garantía (S/)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    descripcion, { if (it.length <= 90) descripcion = it }, label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nro, precio.toDouble(), garantia.toDouble(), descripcion.ifBlank { null }) },
                enabled = valido && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CtAzul)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") } }
    )
}
