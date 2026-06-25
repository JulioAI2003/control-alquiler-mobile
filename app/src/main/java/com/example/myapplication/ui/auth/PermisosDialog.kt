package com.example.myapplication.ui.auth

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val PermAzul = Color(0xFF8A6A12)
private val PermVerde = Color(0xFF2E7D32)

// ── Chequeos de permisos ─────────────────────────────────────────────────────

private fun notifConcedido(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun alarmasExactasConcedido(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

private fun overlayConcedido(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

private fun fullScreenConcedido(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    return ctx.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
}

/** True si falta al menos un permiso necesario para que las alarmas funcionen bien. */
fun faltanPermisos(ctx: Context): Boolean =
    !notifConcedido(ctx) || !alarmasExactasConcedido(ctx) || !overlayConcedido(ctx) || !fullScreenConcedido(ctx)

private fun abrirAjuste(ctx: Context, action: String, conPaquete: Boolean) {
    val intent = Intent(action).apply {
        if (conPaquete) data = Uri.parse("package:${ctx.packageName}")
    }
    runCatching { ctx.startActivity(intent) }
        .onFailure {
            // Fallback: abrir el detalle de la app si el ajuste específico no existe en el dispositivo.
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
}

/**
 * Diálogo de permisos que se muestra al iniciar si falta alguno. Lista cada permiso necesario
 * con su estado y un botón para concederlo (abre el ajuste correspondiente). Se re-evalúa al
 * volver de Ajustes (observa ON_RESUME).
 */
@Composable
fun PermisosDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Re-evaluar estados al volver de Ajustes.
    var tick by remember { mutableStateOf(0) }
    if (activity != null) {
        DisposableEffect(activity) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
            activity.lifecycle.addObserver(obs)
            onDispose { activity.lifecycle.removeObserver(obs) }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val notifOk = remember(tick) { notifConcedido(context) }
    val alarmaOk = remember(tick) { alarmasExactasConcedido(context) }
    val overlayOk = remember(tick) { overlayConcedido(context) }
    val fullOk = remember(tick) { fullScreenConcedido(context) }
    val todoOk = notifOk && alarmaOk && overlayOk && fullOk

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {

                // Encabezado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(PermAzul.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Security, null, tint = PermAzul, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Permisos necesarios", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PermAzul)
                        Text("Para recibir tus cobros y pagos a tiempo", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(18.dp))

                PermisoFila(
                    "Notificaciones",
                    "Avisos de cobros y pagos pendientes.",
                    notifOk
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else
                        abrirAjuste(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                }

                PermisoFila(
                    "Alarmas exactas",
                    "Que la alarma suene a la hora exacta que elijas.",
                    alarmaOk
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        abrirAjuste(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, false)
                }

                PermisoFila(
                    "Mostrar sobre otras apps",
                    "Ver la alarma sobre otras apps y abrir ventanas en segundo plano.",
                    overlayOk
                ) {
                    abrirAjuste(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PermisoFila(
                        "Pantalla completa",
                        "Mostrar la alarma a pantalla completa y en la pantalla de bloqueo.",
                        fullOk
                    ) {
                        abrirAjuste(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "En algunos teléfonos (Xiaomi, etc.) activa también, en \"Otros permisos\", " +
                        "\"Mostrar en pantalla de bloqueo\" y \"Abrir nuevas ventanas en segundo plano\".",
                    fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { abrirAjuste(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("Abrir ajustes de la app", color = PermAzul, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (todoOk) PermVerde else Color.Black,
                        contentColor = Color.White
                    )
                ) { Text(if (todoOk) "¡Listo! Continuar" else "Continuar") }
            }
        }
    }
}

@Composable
private fun PermisoFila(titulo: String, descripcion: String, concedido: Boolean, onConceder: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle, null,
            tint = if (concedido) PermVerde else Color(0xFFBDBDBD),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(descripcion, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (concedido) {
            Text("Activo", color = PermVerde, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Button(
                onClick = onConceder,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("Conceder", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
