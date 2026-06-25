package com.example.myapplication.ui.pagos

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.model.PosponerRequest
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.launch
import java.util.Calendar

private val PosponerAzul = Color(0xFF8A6A12)

/**
 * Diálogo para posponer (aplazar) el recordatorio/alarma de un recibo concreto unos días,
 * sin cambiar su fecha real. Es autocontenido: lee el id_usuario de la sesión y llama al
 * backend (`/recordatorios/posponer`). El mes siguiente del recibo conserva su fecha original.
 *
 * @param clave identificador lógico del recibo (ej. "pago:<id>", "servicio:<id>:<anio>-<mes>").
 * @param titulo nombre legible del recibo, solo para mostrar.
 */
@Composable
fun PosponerRecordatorioDialog(
    clave: String,
    titulo: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val idUsuario by remember { SessionDataStore(context).userId }
        .collectAsStateWithLifecycle(initialValue = null)
    var enviando by remember { mutableStateOf(false) }

    fun posponerHasta(fechaIso: String) {
        val uid = idUsuario ?: return
        scope.launch {
            enviando = true
            try {
                AlquilerApiClient.service.posponerRecordatorio(PosponerRequest(uid, clave, fechaIso))
                Toast.makeText(context, "Recordatorio pospuesto hasta $fechaIso", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo posponer el recordatorio", Toast.LENGTH_SHORT).show()
            } finally {
                enviando = false
            }
        }
    }

    fun posponerDias(dias: Int) {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, dias) }
        posponerHasta(isoDe(c))
    }

    fun abrirSelectorFecha() {
        val hoy = Calendar.getInstance()
        val picker = DatePickerDialog(
            context,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                posponerHasta(isoDe(c))
            },
            hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.minDate = hoy.timeInMillis
        picker.show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(24.dp)) {

                // ── Encabezado: ícono + título ──────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(PosponerAzul.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = PosponerAzul, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Posponer recordatorio", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PosponerAzul)
                        Text(titulo, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Aplaza el aviso de este recibo sin cambiar su fecha real. El próximo mes mantiene su fecha.",
                    fontSize = 13.sp, color = Color(0xFF555555), lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))
                Text("Aplazar:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.DarkGray)
                Spacer(Modifier.height(10.dp))

                // ── Opciones rápidas (chips) ────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipDias("+1 día", !enviando, Modifier.weight(1f)) { posponerDias(1) }
                    ChipDias("+3 días", !enviando, Modifier.weight(1f)) { posponerDias(3) }
                    ChipDias("+7 días", !enviando, Modifier.weight(1f)) { posponerDias(7) }
                }

                Spacer(Modifier.height(12.dp))

                // ── Selector de fecha ───────────────────────────────────────
                OutlinedButton(
                    onClick = { abrirSelectorFecha() },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, PosponerAzul),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosponerAzul)
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir otra fecha", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                // ── Pie: progreso o botón cancelar ──────────────────────────
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (enviando) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = PosponerAzul)
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Chip de opción rápida de días, con estilo del botón sólido negro de la app. */
@Composable
private fun ChipDias(texto: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Formatea un Calendar a "YYYY-MM-DD". */
private fun isoDe(c: Calendar): String =
    "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
