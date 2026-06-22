package com.example.myapplication.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Agenda el aviso diario con una **alarma exacta** ([AlarmManager.setExactAndAllowWhileIdle]),
 * que se dispara a la hora elegida incluso con el dispositivo en Doze (precisión real).
 *
 * El disparo llega a [RecordatorioReceiver] (acción [ACTION_AVISO_DIARIO]), que ejecuta el
 * chequeo de pendientes y reagenda la alarma del día siguiente.
 */
object RecordatorioScheduler {

    const val ACTION_AVISO_DIARIO = "com.example.myapplication.action.AVISO_DIARIO"
    private const val REQUEST_CODE = 73101

    /** Agenda (o reagenda) la alarma para la próxima ocurrencia de [horaMinuto] ("HH:mm")
     *  en la hora **local del dispositivo** (la del país del usuario, p. ej. Perú). */
    fun programar(context: Context, horaMinuto: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = proximaOcurrencia(horaMinuto)
        val pi = pendingIntent(context)

        val puedeExacta =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

        try {
            if (puedeExacta) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Sin permiso de alarmas exactas: aviso que igual atraviesa Doze, pero aproximado.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // Por si el permiso se revoca entre el chequeo y la llamada.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelar(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordatorioReceiver::class.java).setAction(ACTION_AVISO_DIARIO)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Milisegundos (epoch) de la próxima ocurrencia de la hora dada; si ya pasó hoy → mañana. */
    private fun proximaOcurrencia(horaMinuto: String): Long {
        val partes = horaMinuto.split(":")
        val hora = partes.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minuto = partes.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val ahora = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(ahora)) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
}
