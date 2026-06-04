package com.example.myapplication.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notificar(id: Int, titulo: String, texto: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notif)
    }

    companion object {
        const val CHANNEL_ID = "cobros_channel"
        const val WORK_NAME  = "cobro_diario"
    }
}
