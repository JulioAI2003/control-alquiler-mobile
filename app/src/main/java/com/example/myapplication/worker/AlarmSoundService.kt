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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio en primer plano que hace sonar la alarma de cobros.
 *
 * Distingue dos acciones que antes eran una sola:
 *  • [ACTION_SILENCIAR] — calla el sonido pero deja la alarma en pie: la pantalla
 *    y la notificación siguen visibles para poder leer los pendientes con calma.
 *  • [ACTION_STOP]      — apaga la alarma por completo y se detiene el servicio.
 */
class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    // Datos del aviso en curso. Se guardan porque al silenciar hay que reconstruir
    // la notificación (cambian las acciones que ofrece).
    private var titulo      = "Cobro pendiente"
    private var descripcion = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP      -> { detenerAlarma(); return START_NOT_STICKY }
            ACTION_SILENCIAR -> { silenciar();     return START_NOT_STICKY }
        }

        titulo      = intent?.getStringExtra(EXTRA_TITULO) ?: "Cobro pendiente"
        descripcion = intent?.getStringExtra(EXTRA_DESCRIPCION) ?: ""

        estaActiva        = true
        descripcionActiva = descripcion
        _sonando.value    = true

        mostrarNotificacion(sonando = true)
        reproducirSonido()

        return START_NOT_STICKY
    }

    private fun mostrarNotificacion(sonando: Boolean) {
        fun piServicio(accion: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode,
            Intent(this, AlarmSoundService::class.java).apply { action = accion },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tocar la notificación (o el aviso a pantalla completa) abre la pantalla de la alarma
        // para poder detenerla — NO entra a la app.
        val fullScreenPi = PendingIntent.getActivity(
            this, 10_000,
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
            .apply {
                if (sonando) {
                    addAction(
                        android.R.drawable.ic_lock_silent_mode, "Silenciar",
                        piServicio(ACTION_SILENCIAR, 1)
                    )
                } else {
                    setSubText("Silenciada")
                }
                addAction(
                    android.R.drawable.ic_media_pause, "Detener alarma",
                    piServicio(ACTION_STOP, 2)
                )
            }
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

    private fun liberarReproductor() {
        try { mediaPlayer?.stop() } catch (_: Exception) { }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Calla el sonido dejando la alarma activa. La notificación se reconstruye sin
     * la acción "Silenciar" para que quede claro que ya no está sonando.
     */
    private fun silenciar() {
        liberarReproductor()
        _sonando.value = false
        // Si el aviso ya no estaba en pie (el servicio revivió solo para esta acción),
        // no hay nada que mantener en primer plano.
        if (estaActiva) runCatching { mostrarNotificacion(sonando = false) } else stopSelf()
    }

    private fun detenerAlarma() {
        estaActiva     = false
        _sonando.value = false
        liberarReproductor()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        estaActiva     = false
        _sonando.value = false
        liberarReproductor()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP         = "com.example.myapplication.STOP_ALARM"
        const val ACTION_SILENCIAR    = "com.example.myapplication.SILENCE_ALARM"
        const val EXTRA_TITULO        = "titulo"
        const val EXTRA_DESCRIPCION   = "descripcion"
        const val EXTRA_NOTIF_ID      = "notif_id"
        const val FOREGROUND_NOTIF_ID = 9001
        const val CHANNEL_ALARM_ID    = "alarma_cobros_channel"

        /** true mientras el aviso sigue en pie (aunque esté silenciado). */
        @Volatile var estaActiva: Boolean = false
        @Volatile var descripcionActiva: String = ""

        /** true solo mientras el sonido está reproduciéndose. La UI lo observa
         *  para pintar el botón de silenciar como activo o ya silenciado. */
        private val _sonando = MutableStateFlow(false)
        val sonando: StateFlow<Boolean> = _sonando.asStateFlow()
    }
}
