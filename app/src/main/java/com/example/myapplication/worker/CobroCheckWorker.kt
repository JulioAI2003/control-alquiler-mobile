package com.example.myapplication.worker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CobroCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val dataStore = SessionDataStore(applicationContext)
            val userId = dataStore.userId.first() ?: return Result.success()
            val tipoAviso = dataStore.tipoAviso.first()
            val usarAlarma = tipoAviso == "alarma"

            crearCanales()

            val hoy = LocalDate.now()
            val rol = dataStore.rol.first()

            // ── Usuario Individual: ingresos/gastos recurrentes ────────────
            if (rol == "Individual") {
                val movimientos = AlquilerApiClient.service.getMovimientosIndividuales(userId, "")
                movimientos.forEach { mov ->
                    if (!mov.conciliado && mov.diasRestantes <= 0) {
                        val etiqueta = if (mov.diasRestantes == 0) "vence HOY" else "vencido hace ${-mov.diasRestantes}d"
                        val verbo = if (mov.esIngreso) "Cobro" else "Pago"
                        val texto = "${mov.nombre} · S/ ${"%.2f".format(mov.montoMostrar)} ($etiqueta)"
                        avisar((mov.idMovimiento ?: mov.idConcepto).hashCode(), "$verbo pendiente", texto, usarAlarma)
                    }
                }
                return Result.success()
            }

            // ── Cobros de inquilinos ───────────────────────────────────────
            val pagos = AlquilerApiClient.service.getPagosPendientes(userId)
            pagos.forEach { pago ->
                val fechaVenc = LocalDate.of(pago.anio, pago.mes, pago.dia)
                val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
                if (dias <= 0L) {
                    val etiqueta = if (dias == 0L) "vence HOY" else "vencido hace ${-dias}d"
                    val texto = "${pago.nombre} ${pago.apellidos} · S/ ${pago.montoTotal} ($etiqueta)"
                    avisar(pago.idPago.hashCode(), "Cobro pendiente", texto, usarAlarma)
                }
            }

            // ── Servicios de casa ─────────────────────────────────────────
            val servicios = AlquilerApiClient.service.getServicios(userId)
            servicios.forEach { srv ->
                if (!srv.pagado && srv.diasRestantes <= 0) {
                    val etiqueta = if (srv.diasRestantes == 0) "vence HOY" else "vencido hace ${-srv.diasRestantes}d"
                    val texto = "${srv.nombre} · S/ ${srv.montoReferencial} ($etiqueta)"
                    avisar(srv.idServicio.hashCode(), "Servicio pendiente", texto, usarAlarma)
                }
            }

            // ── Garantías pendientes ─────────────────────────────────────
            val inquilinos = AlquilerApiClient.service.getInquilinos(userId)
            inquilinos.forEach { inq ->
                if (inq.fechaGarantia == null && inq.fechaEsperadaGarantia != null) {
                    val fechaEsperada = LocalDate.parse(inq.fechaEsperadaGarantia)
                    if (!hoy.isBefore(fechaEsperada)) {
                        val texto = "${inq.nombre} ${inq.apellidos} aún no paga la garantía"
                        avisar("garantia-${inq.idInquilino}".hashCode(), "Garantía pendiente", texto, usarAlarma)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun avisar(id: Int, titulo: String, texto: String, usarAlarma: Boolean) {
        if (usarAlarma) dispararAlarma(id, titulo, texto) else notificar(id, titulo, texto)
    }

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
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

    private fun notificar(id: Int, titulo: String, texto: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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

    private fun dispararAlarma(id: Int, titulo: String, texto: String) {
        val intent = Intent(applicationContext, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TITULO, titulo)
            putExtra(AlarmReceiver.EXTRA_DESCRIPCION, texto)
            putExtra(AlarmReceiver.EXTRA_NOTIF_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = applicationContext.getSystemService(AlarmManager::class.java) ?: return
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

    companion object {
        const val CHANNEL_ID = "cobros_channel"
        const val WORK_NAME  = "cobro_diario"
    }
}
