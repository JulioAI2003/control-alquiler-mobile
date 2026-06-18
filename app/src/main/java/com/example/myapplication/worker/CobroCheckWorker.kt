package com.example.myapplication.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

/**
 * Worker diario que comprueba cobros de inquilinos y servicios pendientes/vencidos.
 * Solo genera notificaciones para pagos vencidos (dias < 0) o que vencen hoy (dias == 0).
 * Pagos futuros no se notifican.
 */
class CobroCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val dataStore = SessionDataStore(applicationContext)
            val userId = dataStore.userId.first() ?: return Result.success()

            val hoy = LocalDate.now()

            // ── Cobros de inquilinos ───────────────────────────────────────
            val pagos = AlquilerApiClient.service.getPagosPendientes(userId)
            pagos.forEach { pago ->
                val fechaVenc = LocalDate.of(pago.anio, pago.mes, pago.dia)
                val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
                if (dias <= 0L) {
                    val etiqueta = if (dias == 0L) "vence HOY" else "vencido hace ${-dias}d"
                    notificar(
                        id     = pago.idPago.hashCode(),
                        titulo = "Cobro pendiente",
                        texto  = "${pago.nombre} ${pago.apellidos} · S/ ${pago.montoTotal} ($etiqueta)"
                    )
                }
            }

            // ── Servicios de casa ─────────────────────────────────────────
            val servicios = AlquilerApiClient.service.getServicios(userId)
            servicios.forEach { srv ->
                if (!srv.pagado && srv.diasRestantes <= 0) {
                    val etiqueta = if (srv.diasRestantes == 0) "vence HOY" else "vencido hace ${-srv.diasRestantes}d"
                    notificar(
                        id     = srv.idServicio.hashCode(),
                        titulo = "Servicio pendiente",
                        texto  = "${srv.nombre} · S/ ${srv.montoReferencial} ($etiqueta)"
                    )
                }
            }

            // ── Garantías pendientes ─────────────────────────────────────
            val inquilinos = AlquilerApiClient.service.getInquilinos(userId)
            inquilinos.forEach { inq ->
                if (inq.fechaGarantia == null && inq.fechaEsperadaGarantia != null) {
                    val fechaEsperada = LocalDate.parse(inq.fechaEsperadaGarantia)
                    if (!hoy.isBefore(fechaEsperada)) {
                        notificar(
                            id     = "garantia-${inq.idInquilino}".hashCode(),
                            titulo = "Garantía pendiente",
                            texto  = "${inq.nombre} ${inq.apellidos} aún no paga la garantía"
                        )
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
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

    companion object {
        const val CHANNEL_ID = "cobros_channel"
        const val WORK_NAME  = "cobro_diario"
    }
}
