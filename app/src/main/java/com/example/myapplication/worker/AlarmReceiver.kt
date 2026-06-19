package com.example.myapplication.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) return

        val titulo      = intent.getStringExtra(EXTRA_TITULO) ?: "Cobro pendiente"
        val descripcion = intent.getStringExtra(EXTRA_DESCRIPCION) ?: ""
        val notifId     = intent.getIntExtra(EXTRA_NOTIF_ID, 0)

        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            putExtra(AlarmSoundService.EXTRA_TITULO, titulo)
            putExtra(AlarmSoundService.EXTRA_DESCRIPCION, descripcion)
            putExtra(AlarmSoundService.EXTRA_NOTIF_ID, notifId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val EXTRA_TITULO      = "titulo"
        const val EXTRA_DESCRIPCION = "descripcion"
        const val EXTRA_NOTIF_ID    = "notif_id"
    }
}
