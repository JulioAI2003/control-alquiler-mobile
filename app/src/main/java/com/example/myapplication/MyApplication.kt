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
        // runBlocking aquí es seguro porque es en Application.onCreate (hilo principal, antes de UI).
        runBlocking {
            cachedToken = sessionDataStore.token.first()
        }

        // Retrofit usa esta lambda en cada petición: siempre lee el token actual.
        AlquilerApiClient.init { cachedToken }
    }

    /** Llamar después de un login exitoso o un logout. */
    fun updateToken(token: String?) {
        cachedToken = token
    }
}
