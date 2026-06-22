package com.example.myapplication.worker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Revisa los pendientes (cobros, servicios, garantías, movimientos) y dispara
 * notificaciones/alarmas locales.
 *
 * Antes esta lógica vivía dentro de `CobroCheckWorker` (WorkManager). Se extrajo aquí para
 * poder ejecutarla **directamente desde [RecordatorioReceiver]** durante la ventana que abre
 * `AlarmManager.setExactAndAllowWhileIdle`, evitando que el modo Doze de Android difiera el
 * aviso a una "ventana de mantenimiento" (lo que rompía la precisión de la hora elegida).
 */
object NotificadorPendientes {

    const val CHANNEL_ID = "cobros_channel"

    /** Lee la sesión, consulta los pendientes y avisa. No hace nada si no hay sesión. */
    suspend fun revisar(context: Context) {
        val dataStore = SessionDataStore(context)
        val userId = dataStore.userId.first() ?: return
        val usarAlarma = dataStore.tipoAviso.first() == "alarma"

        crearCanales(context)

        val hoy = LocalDate.now()
        val rol = dataStore.rol.first()

        // ── Usuario Individual: ingresos/gastos recurrentes ────────────────
        if (rol == "Individual") {
            val movimientos = AlquilerApiClient.service.getMovimientosIndividuales(userId, "")
            movimientos.forEach { mov ->
                if (!mov.conciliado && mov.diasRestantes <= 0) {
                    val etiqueta = if (mov.diasRestantes == 0) "vence HOY" else "vencido hace ${-mov.diasRestantes}d"
                    val verbo = if (mov.esIngreso) "Cobro" else "Pago"
                    val texto = "${mov.nombre} · S/ ${"%.2f".format(mov.montoMostrar)} ($etiqueta)"
                    avisar(context, (mov.idMovimiento ?: mov.idConcepto).hashCode(), "$verbo pendiente", texto, usarAlarma)
                }
            }
            return
        }

        // ── Cobros de inquilinos ───────────────────────────────────────────
        val pagos = AlquilerApiClient.service.getPagosPendientes(userId)
        pagos.forEach { pago ->
            val fechaVenc = LocalDate.of(pago.anio, pago.mes, pago.dia)
            val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
            if (dias <= 0L) {
                val etiqueta = if (dias == 0L) "vence HOY" else "vencido hace ${-dias}d"
                val texto = "${pago.nombre} ${pago.apellidos} · S/ ${pago.montoTotal} ($etiqueta)"
                avisar(context, pago.idPago.hashCode(), "Cobro pendiente", texto, usarAlarma)
            }
        }

        // ── Servicios de casa ─────────────────────────────────────────────
        val servicios = AlquilerApiClient.service.getServicios(userId)
        servicios.forEach { srv ->
            if (!srv.pagado && srv.diasRestantes <= 0) {
                val etiqueta = if (srv.diasRestantes == 0) "vence HOY" else "vencido hace ${-srv.diasRestantes}d"
                val texto = "${srv.nombre} · S/ ${srv.montoReferencial} ($etiqueta)"
                avisar(context, srv.idServicio.hashCode(), "Servicio pendiente", texto, usarAlarma)
            }
        }

        // ── Garantías pendientes ───────────────────────────────────────────
        val inquilinos = AlquilerApiClient.service.getInquilinos(userId)
        inquilinos.forEach { inq ->
            if (inq.fechaGarantia == null && inq.fechaEsperadaGarantia != null) {
                val fechaEsperada = LocalDate.parse(inq.fechaEsperadaGarantia)
                if (!hoy.isBefore(fechaEsperada)) {
                    val texto = "${inq.nombre} ${inq.apellidos} aún no paga la garantía"
                    avisar(context, "garantia-${inq.idInquilino}".hashCode(), "Garantía pendiente", texto, usarAlarma)
                }
            }
        }
    }

    private fun avisar(context: Context, id: Int, titulo: String, texto: String, usarAlarma: Boolean) {
        if (usarAlarma) dispararAlarma(context, id, titulo, texto) else notificar(context, id, titulo, texto)
    }

    private fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cobros pendientes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notificaciones de cobros y servicios pendientes"
                    enableVibration(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(AlarmSoundService.CHANNEL_ALARM_ID, "Alarma de cobros", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alarma sonora para cobros pendientes"
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
            )
        }
    }

    private fun notificar(context: Context, id: Int, titulo: String, texto: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(id, notif)
    }

    private fun dispararAlarma(context: Context, id: Int, titulo: String, texto: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TITULO, titulo)
            putExtra(AlarmReceiver.EXTRA_DESCRIPCION, texto)
            putExtra(AlarmReceiver.EXTRA_NOTIF_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + 500

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10_000L, pi)
                }
            }
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
