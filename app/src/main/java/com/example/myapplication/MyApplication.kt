// ─── MyApplication.kt ────────────────────────────────────────────────────────
package com.example.myapplication

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.remote.AlquilerApiClient
import com.example.myapplication.worker.NotificadorPendientes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MyApplication : Application() {

    lateinit var sessionDataStore: SessionDataStore
        private set

    @Volatile var cachedToken: String? = null
        private set

    /**
     * Tema elegido por el usuario (`true` oscuro, `false` claro, `null` seguir al
     * sistema). Se siembra de forma síncrona en [onCreate] y luego se mantiene
     * al día con DataStore, para que la primera composición ya pinte el tema
     * correcto y no haya un parpadeo claro→oscuro al abrir la app.
     */
    private val _temaOscuro = MutableStateFlow<Boolean?>(null)
    val temaOscuro: StateFlow<Boolean?> = _temaOscuro.asStateFlow()

    // Emite un evento cuando el servidor rechaza el token (contraseña cambiada externamente).
    // MainActivity lo observa para navegar a Login automáticamente.
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // Scope de aplicación (no atado a ninguna pantalla) para tareas que deben
    // terminar aunque la UI se destruya, como limpiar la sesión al cerrar sesión.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        sessionDataStore = SessionDataStore(this)

        // Carga el token persistido. El token ya no expira por tiempo —
        // se invalida solo cuando el servidor responde 401 (contraseña cambiada).
        runBlocking {
            val token = sessionDataStore.token.first()
            if (!token.isNullOrBlank()) cachedToken = token
            _temaOscuro.value = sessionDataStore.temaOscuro.first()
        }

        // Mantiene el tema sincronizado cuando el usuario lo cambia en Ajustes.
        appScope.launch { sessionDataStore.temaOscuro.collect { _temaOscuro.value = it } }

        AlquilerApiClient.init(
            context        = this,
            tokenProvider  = { cachedToken },
            onUnauthorized = {
                // Llamado desde el hilo de OkHttp cuando el servidor responde 401.
                // Limpia la sesión y notifica a la UI para navegar a Login.
                runBlocking { sessionDataStore.limpiarSesion() }
                updateToken(null)
                _sessionExpired.tryEmit(Unit)
            }
        )

        crearCanalNotificaciones()
    }

    private fun crearCanalNotificaciones() {
        val canal = NotificationChannel(
            NotificadorPendientes.CHANNEL_ID,
            "Cobros y Servicios",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de cobros de inquilinos y servicios pendientes o vencidos"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun updateToken(token: String?) {
        cachedToken = token
    }

    /**
     * Cierra sesión por completo: borra el token en memoria (inmediato, para que el
     * arranque ya no entre al dashboard) y limpia la sesión persistida en DataStore
     * en un scope de aplicación que sobrevive a la navegación.
     */
    fun cerrarSesion() {
        cachedToken = null
        appScope.launch { sessionDataStore.limpiarSesion() }
    }
}
