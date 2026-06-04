// ─── MyApplication.kt ────────────────────────────────────────────────────────
package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.local.SessionDataStore
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application principal.
 *
 * Responsabilidades:
 *  1. Inicializa [SessionDataStore] para persistir el JWT en DataStore.
 *  2. Carga el token guardado al arrancar (sincrónicamente, solo una vez).
 *  3. Inicializa [AlquilerApiClient] con un proveedor de token en memoria
 *     para que el interceptor siempre inyecte el token actualizado.
 *  4. Expone [updateToken] para que Login y Logout mantengan el token sincronizado.
 */
class MyApplication : Application() {

    /** DataStore con persistencia de sesión JWT */
    lateinit var sessionDataStore: SessionDataStore
        private set

    /**
     * Token en memoria: evita leer DataStore en cada petición HTTP.
     * Se sincroniza con DataStore en onCreate, login y logout.
     */
    @Volatile var cachedToken: String? = null
        private set

    override fun onCreate() {
        super.onCreate()

        sessionDataStore = SessionDataStore(this)

        // Carga el token persistido de forma síncrona solo al arrancar.
        // Si el token existe pero está expirado, se limpia la sesión y el usuario verá Login.
        runBlocking {
            val token = sessionDataStore.token.first()
            when {
                token.isNullOrBlank()    -> { /* sin token → arranca en Login */ }
                isTokenExpired(token)    -> { sessionDataStore.limpiarSesion() }
                else                     -> { cachedToken = token }
            }
        }

        // Retrofit usa esta lambda en cada petición: siempre lee el token actual.
        AlquilerApiClient.init { cachedToken }
    }

    /** Decodifica el payload JWT y comprueba si el campo `exp` ya pasó. */
    private fun isTokenExpired(token: String): Boolean {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return true
            val decoded = android.util.Base64.decode(
                payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            )
            val json = String(decoded)
            val exp = Regex("\"exp\":(\\d+)").find(json)
                ?.groupValues?.get(1)?.toLongOrNull() ?: return true
            System.currentTimeMillis() / 1000 >= exp
        } catch (e: Exception) {
            true
        }
    }

    /** Llamar después de un login exitoso o un logout. */
    fun updateToken(token: String?) {
        cachedToken = token
    }
}
