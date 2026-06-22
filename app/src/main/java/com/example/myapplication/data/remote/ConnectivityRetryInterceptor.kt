// ─── data/remote/ConnectivityRetryInterceptor.kt ─────────────────────────────
package com.example.myapplication.data.remote

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor de robustez de red:
 *
 *  1. Corta de inmediato si el dispositivo no tiene internet (lanza
 *     [NoConnectivityException]) en vez de esperar el timeout completo.
 *  2. Reintenta automáticamente las peticiones GET ante fallos típicos del
 *     "cold start" del backend serverless en Vercel: timeouts de red y
 *     respuestas 5xx/504 temporales. Así el primer acceso tras un rato de
 *     inactividad —que es justo el que fallaba— se recupera solo.
 *
 * Solo reintenta GET (idempotente). NUNCA reintenta POST/PUT/DELETE para no
 * duplicar pagos u operaciones.
 */
class ConnectivityRetryInterceptor(
    private val context: Context,
    private val maxRetries: Int = 1,
    private val retryDelayMs: Long = 1200,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!NetworkError.isOnline(context)) throw NoConnectivityException()

        val request = chain.request()
        val canRetry = request.method.equals("GET", ignoreCase = true)

        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                // Éxito, método no reintentável, error no temporal, o sin reintentos
                // restantes → devolvemos la respuesta tal cual.
                if (response.isSuccessful || !canRetry ||
                    response.code !in 500..599 || attempt >= maxRetries
                ) {
                    return response
                }
                response.close() // descarta el 5xx temporal antes de reintentar
            } catch (e: IOException) {
                if (!canRetry || attempt >= maxRetries) throw e
            }
            attempt++
            try {
                Thread.sleep(retryDelayMs * attempt) // backoff lineal: 1.2s, 2.4s...
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
