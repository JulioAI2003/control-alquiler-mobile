// ─── data/remote/AlquilerApiClient.kt ────────────────────────────────────────
package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Singleton que provee [AlquilerApiService].
 *
 * Ciclo de vida:
 *  1. [MyApplication.onCreate] llama a [init] con el proveedor de token.
 *  2. Cualquier parte de la app accede a [service] para hacer peticiones.
 */
object AlquilerApiClient {

    private var _service: AlquilerApiService? = null

    /**
     * Debe llamarse UNA sola vez en [MyApplication.onCreate].
     * [tokenProvider] es una lambda que devuelve el JWT en memoria;
     * esto permite que el interceptor siempre lea el token actualizado.
     */
    fun init(tokenProvider: () -> String?) {
        if (_service != null) return   // idempotente

        // JSON permisivo: ignora campos desconocidos del backend.
        // encodeDefaults = true es CRÍTICO: sin esto, Kotlinx Serialization omite
        // campos con valor por defecto (como `accion = "pagar_mensual"` en PagoRequest)
        // y el backend rechaza la petición con 400 "Acción no válida".
        val json = Json {
            ignoreUnknownKeys = true
            isLenient         = true
            coerceInputValues = true
            encodeDefaults    = true   // ← serializa campos con valor default
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        _service = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(
                json.asConverterFactory("application/json; charset=UTF-8".toMediaType())
            )
            .build()
            .create(AlquilerApiService::class.java)
    }

    val service: AlquilerApiService
        get() = _service ?: error(
            "AlquilerApiClient no inicializado. Llama a AlquilerApiClient.init() en MyApplication.onCreate()."
        )
}
