// ─── data/remote/AuthInterceptor.kt ─────────────────────────────────────────
package com.example.myapplication.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Inyecta el JWT en cada petición HTTP.
 * El token se obtiene en tiempo de ejecución via [tokenProvider] para
 * leer siempre el valor actual sin capturar un token obsoleto al construir el cliente.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}
