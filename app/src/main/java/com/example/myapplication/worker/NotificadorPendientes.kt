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

    /**
     * Lee la sesión, consulta los pendientes según el **rol** y avisa. No hace nada si no hay sesión.
     *
     * Cobertura por tipo de usuario (todos reciben avisos de cobros/pagos):
     *  • **Administrador** → suscripciones de los usuarios **por cobrar** (vencidas/de hoy).
     *  • **Propietario (Usuario)** → cobros de inquilinos (ingresos), servicios de casa (gastos),
     *    garantías pendientes y **su propia suscripción por pagar**.
     *  • **Individual** → ingresos/gastos recurrentes (movimientos) y **su propia suscripción por pagar**.
     */
    suspend fun revisar(context: Context) {
        val dataStore = SessionDataStore(context)
        val userId = dataStore.userId.first() ?: return
        val usarAlarma = dataStore.tipoAviso.first() == "alarma"

        crearCanales(context)

        val hoy = LocalDate.now()
        val rol = dataStore.rol.first()
        val pospuestos = obtenerPospuestos(userId)

        // Se recolectan TODOS los pendientes; luego se entregan como una alarma-resumen única
        // (modo alarma) o como una notificación por ítem (modo notificación).
        val avisos = mutableListOf<Aviso>()

        when (rol) {
            // ── ADMINISTRADOR: suscripciones por COBRAR a los usuarios ──────
            "Administrador" -> {
                val suscripciones = try { AlquilerApiClient.service.getPagosUsuarios() } catch (_: Exception) { emptyList() }
                suscripciones.forEach { p ->
                    val clave = "suscripcion:${p.idPagoUsuario}"
                    if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                    val vence = parsearFecha(p.fechaFacturacion) ?: return@forEach
                    if (!hoy.isBefore(vence)) {
                        avisos += Aviso(
                            clave.hashCode(), "Suscripción por cobrar",
                            "${p.nombres ?: "Usuario"} · S/ ${p.monto ?: "0"} (${etiquetaVencida(hoy, vence)})",
                            esCobro = true, monto = p.monto?.toDoubleOrNull() ?: 0.0
                        )
                    }
                }
            }

            // ── INDIVIDUAL: ingresos/gastos recurrentes + suscripción propia ─
            "Individual" -> {
                val movimientos = try { AlquilerApiClient.service.getMovimientosIndividuales(userId, "") } catch (_: Exception) { emptyList() }
                movimientos.forEach { mov ->
                    if (mov.conciliado || mov.diasRestantes > 0) return@forEach
                    val clave = "movimiento:${mov.idConcepto}:${mov.anio}-${mov.mes}"
                    if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                    val etiqueta = if (mov.diasRestantes == 0) "vence HOY" else "vencido hace ${-mov.diasRestantes}d"
                    avisos += Aviso(
                        clave.hashCode(), if (mov.esIngreso) "Cobro pendiente" else "Pago pendiente",
                        "${mov.nombre} · S/ ${"%.2f".format(mov.montoMostrar)} ($etiqueta)",
                        esCobro = mov.esIngreso, monto = mov.montoMostrar
                    )
                }
                agregarSuscripcionPropia(avisos, userId, hoy, pospuestos)
            }

            // ── PROPIETARIO (Usuario): inquilinos + servicios + garantías + suscripción propia ─
            else -> {
                // Cobros de inquilinos (ingresos)
                val pagos = try { AlquilerApiClient.service.getPagosPendientes(userId) } catch (_: Exception) { emptyList() }
                pagos.forEach { pago ->
                    val clave = "pago:${pago.idPago}"
                    if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                    val fechaVenc = LocalDate.of(pago.anio, pago.mes, pago.dia)
                    val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
                    if (dias <= 0L) {
                        val etiqueta = if (dias == 0L) "vence HOY" else "vencido hace ${-dias}d"
                        avisos += Aviso(
                            clave.hashCode(), "Cobro pendiente",
                            "${pago.nombre} ${pago.apellidos} · S/ ${pago.montoTotal} ($etiqueta)",
                            esCobro = true, monto = pago.montoTotal.toDoubleOrNull() ?: 0.0
                        )
                    }
                }

                // Servicios de casa (gastos)
                val servicios = try { AlquilerApiClient.service.getServicios(userId) } catch (_: Exception) { emptyList() }
                servicios.forEach { srv ->
                    if (srv.pagado || srv.diasRestantes > 0) return@forEach
                    val clave = "servicio:${srv.idServicio}:${srv.anio}-${srv.mes}"
                    if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                    val etiqueta = if (srv.diasRestantes == 0) "vence HOY" else "vencido hace ${-srv.diasRestantes}d"
                    avisos += Aviso(
                        clave.hashCode(), "Servicio pendiente",
                        "${srv.nombre} · S/ ${srv.montoReferencial} ($etiqueta)",
                        esCobro = false, monto = srv.montoReferencial.toDoubleOrNull() ?: 0.0
                    )
                }

                // Garantías pendientes
                val inquilinos = try { AlquilerApiClient.service.getInquilinos(userId) } catch (_: Exception) { emptyList() }
                inquilinos.forEach { inq ->
                    if (inq.fechaGarantia != null || inq.fechaEsperadaGarantia == null) return@forEach
                    val fechaEsperada = parsearFecha(inq.fechaEsperadaGarantia) ?: return@forEach
                    if (hoy.isBefore(fechaEsperada)) return@forEach
                    val clave = "garantia:${inq.idInquilino}"
                    if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                    avisos += Aviso(
                        clave.hashCode(), "Garantía pendiente",
                        "${inq.nombre} ${inq.apellidos} aún no paga la garantía",
                        esCobro = true, monto = inq.montoGarantia?.toDoubleOrNull() ?: 0.0
                    )
                }

                agregarSuscripcionPropia(avisos, userId, hoy, pospuestos)
            }
        }

        if (avisos.isEmpty()) return

        if (usarAlarma) {
            // Una sola alarma general con el resumen de cobros y pagos.
            val (titulo, texto) = construirResumen(avisos)
            dispararAlarma(context, ID_RESUMEN, titulo, texto)
        } else {
            // Una notificación por ítem (se apilan en la bandeja).
            avisos.forEach { notificar(context, it.id, it.titulo, it.texto) }
        }
    }

    /** Datos mínimos de un aviso recolectado. */
    private data class Aviso(
        val id: Int,
        val titulo: String,
        val texto: String,
        val esCobro: Boolean,
        val monto: Double
    )

    /** Suscripción propia (propietario/individual) que el usuario debe pagar a la plataforma. */
    private suspend fun agregarSuscripcionPropia(
        avisos: MutableList<Aviso>, userId: String, hoy: LocalDate, pospuestos: Map<String, LocalDate>
    ) {
        try {
            val propias = AlquilerApiClient.service.getPagosUsuarios(userId)
            propias.forEach { p ->
                val clave = "suscripcion:${p.idPagoUsuario}"
                if (estaPospuesto(clave, pospuestos, hoy)) return@forEach
                val vence = parsearFecha(p.fechaFacturacion) ?: return@forEach
                if (!hoy.isBefore(vence)) {
                    avisos += Aviso(
                        clave.hashCode(), "Suscripción por pagar",
                        "Plan ${p.nombrePlan ?: ""} · S/ ${p.monto ?: "0"} (${etiquetaVencida(hoy, vence)})",
                        esCobro = false, monto = p.monto?.toDoubleOrNull() ?: 0.0
                    )
                }
            }
        } catch (_: Exception) { /* no interrumpe el resto de avisos */ }
    }

    /** Construye el título y texto resumen de la alarma general, en líneas separadas para que
     *  visualmente quede ordenado (etiqueta y su detalle en renglones distintos). */
    private fun construirResumen(avisos: List<Aviso>): Pair<String, String> {
        val cobros = avisos.filter { it.esCobro }
        val pagos = avisos.filter { !it.esCobro }
        val lineas = mutableListOf<String>()
        if (cobros.isNotEmpty()) {
            lineas += "Por cobrar:"
            lineas += "${cobros.size} (S/ ${"%.2f".format(cobros.sumOf { it.monto })})"
        }
        if (pagos.isNotEmpty()) {
            lineas += "Por pagar:"
            lineas += "${pagos.size} (S/ ${"%.2f".format(pagos.sumOf { it.monto })})"
        }
        return "Tienes pendientes hoy" to lineas.joinToString("\n")
    }

    /** Mapa clave→fecha de los recibos cuyo recordatorio fue pospuesto (vacío ante error). */
    private suspend fun obtenerPospuestos(userId: String): Map<String, LocalDate> =
        try {
            AlquilerApiClient.service.getRecordatoriosPospuestos(userId)
                .mapNotNull { r -> parsearFecha(r.pospuestoHasta)?.let { r.clave to it } }
                .toMap()
        } catch (_: Exception) { emptyMap() }

    /** True si el recibo está pospuesto: hoy es anterior a la fecha hasta la que se aplazó. */
    private fun estaPospuesto(clave: String, pospuestos: Map<String, LocalDate>, hoy: LocalDate): Boolean {
        val hasta = pospuestos[clave] ?: return false
        return hoy.isBefore(hasta)
    }

    /** Convierte fechas tipo "2026-06-22" o "2026-06-22T..." a LocalDate; null si no se puede. */
    private fun parsearFecha(fecha: String?): LocalDate? {
        if (fecha.isNullOrBlank()) return null
        return try { LocalDate.parse(fecha.take(10)) } catch (_: Exception) { null }
    }

    /** Etiqueta para una fecha ya vencida o que vence hoy (vence <= hoy). */
    private fun etiquetaVencida(hoy: LocalDate, vence: LocalDate): String {
        val dias = ChronoUnit.DAYS.between(hoy, vence)
        return if (dias == 0L) "vence HOY" else "vencido hace ${-dias}d"
    }

    private val ID_RESUMEN = "resumen-diario".hashCode()

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
