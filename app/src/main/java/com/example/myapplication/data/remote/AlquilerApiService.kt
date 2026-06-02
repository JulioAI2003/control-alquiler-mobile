// ─── data/remote/AlquilerApiService.kt ───────────────────────────────────────
package com.example.myapplication.data.remote

import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.PagoBackend
import com.example.myapplication.data.model.PagoRegistradoResponse
import com.example.myapplication.data.model.PagoRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contrato Retrofit del backend Node.js (control-alquiler-backend).
 * La baseUrl ya incluye "/api/" → las rutas aquí son relativas a eso.
 */
interface AlquilerApiService {

    // ── Autenticación ─────────────────────────────────────────────────────────
    /** POST /api/login  →  {email, password}  →  {token, id_usuario, nombre, rol} */
    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // ── Pagos pendientes ──────────────────────────────────────────────────────
    /** GET /api/pagos?id_usuario=X  →  [ PagoBackend, … ] */
    @GET("pagos")
    suspend fun getPagosPendientes(
        @Query("id_usuario") idUsuario: String
    ): List<PagoBackend>

    // ── Registrar pago ────────────────────────────────────────────────────────
    /**
     * POST /api/pagos/{id_pago}
     * Body: { accion, monto_pagado, metodo_pago, descripcion, fecha_compromiso }
     * El pago rápido envía metodo_pago = "Yape" y descripcion = "Mensualidad Automatizada App".
     */
    @POST("pagos/{id_pago}")
    suspend fun registrarPago(
        @Path("id_pago") idPago:   String,
        @Body           request:   PagoRequest
    ): PagoRegistradoResponse
}
