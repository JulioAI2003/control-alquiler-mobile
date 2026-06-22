package com.example.myapplication.worker

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            detenerAlarma()
            return START_NOT_STICKY
        }

        val titulo      = intent?.getStringExtra(EXTRA_TITULO) ?: "Cobro pendiente"
        val descripcion = intent?.getStringExtra(EXTRA_DESCRIPCION) ?: ""
        val notifId     = intent?.getIntExtra(EXTRA_NOTIF_ID, 0) ?: 0

        estaActiva = true
        descripcionActiva = descripcion
        mostrarNotificacion(titulo, descripcion, notifId)
        reproducirSonido()

        return START_NOT_STICKY
    }

    private fun mostrarNotificacion(titulo: String, descripcion: String, notifId: Int) {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmSoundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Tocar la notificación (o el aviso a pantalla completa) abre la pantalla de la alarma
        // para poder detenerla — NO entra a la app.
        val fullScreenPi = PendingIntent.getActivity(
            this, notifId + 10_000,
            Intent(this, AlarmFullScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_DESCRIPCION, descripcion)
                putExtra(EXTRA_TITULO, titulo)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titulo)
            .setContentText(descripcion)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descripcion))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(android.R.drawable.ic_media_pause, "Detener alarma", stopPi)
            .build()

        startForeground(FOREGROUND_NOTIF_ID, notif)
    }

    private fun reproducirSonido() {
        if (mediaPlayer?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    private fun detenerAlarma() {
        estaActiva = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        estaActiva = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP         = "com.example.myapplication.STOP_ALARM"
        const val EXTRA_TITULO        = "titulo"
        const val EXTRA_DESCRIPCION   = "descripcion"
        const val EXTRA_NOTIF_ID      = "notif_id"
        const val FOREGROUND_NOTIF_ID = 9001
        const val CHANNEL_ALARM_ID    = "alarma_cobros_channel"

        @Volatile var estaActiva: Boolean = false
        @Volatile var descripcionActiva: String = ""
    }
}
