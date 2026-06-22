package com.example.myapplication.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.data.local.SessionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Recibe el disparo de la alarma exacta diaria y los eventos del sistema que borran/alteran
 * las alarmas programadas (reinicio, actualización de la app, cambio de hora/zona).
 *
 *  • [RecordatorioScheduler.ACTION_AVISO_DIARIO] → reagenda la alarma del día siguiente y
 *    ejecuta el chequeo de pendientes dentro de la ventana de `setExactAndAllowWhileIdle`
 *    (usando `goAsync` para mantener vivo el receptor mientras corre la corrutina).
 *  • BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED → reagenda la alarma
 *    (las alarmas exactas se pierden al reiniciar y deben recalcularse si cambia la hora local).
 */
class RecordatorioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                reprogramar(appContext)
            }

            RecordatorioScheduler.ACTION_AVISO_DIARIO -> {
                // 1) Reagenda primero (rápido) por si el chequeo se interrumpe.
                reprogramar(appContext)
                // 2) Ejecuta el chequeo durante la ventana de allow-while-idle.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        NotificadorPendientes.revisar(appContext)
                    } catch (_: Exception) {
                        // Silencioso: el siguiente disparo diario lo reintenta.
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /** Reagenda la alarma con la hora guardada, solo si hay una sesión activa. */
    private fun reprogramar(context: Context) {
        val hora = runBlocking {
            val ds = SessionDataStore(context)
            val userId = ds.userId.first()
            if (userId.isNullOrBlank()) null else ds.horaNotificacion.first()
        } ?: return
        RecordatorioScheduler.programar(context, hora)
    }
}
