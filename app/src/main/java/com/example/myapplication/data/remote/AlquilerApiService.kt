package com.example.myapplication.data.remote

import com.example.myapplication.data.model.*
import retrofit2.http.*

interface AlquilerApiService {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("pagos")
    suspend fun getPagosPendientes(@Query("id_usuario") idUsuario: String): List<PagoBackend>

    @POST("pagos/{id_pago}")
    suspend fun registrarPago(@Path("id_pago") idPago: String, @Body request: PagoRequest): PagoRegistradoResponse

    // --- NUEVOS ENDPOINTS ---
    @GET("pagos/recientes")
    suspend fun getPagosRecientes(@Query("id_usuario") idUsuario: String): List<PagoBackend>

    @POST("pagos/revertir/{id_pago}")
    suspend fun revertirPago(@Path("id_pago") idPago: String): PagoRegistradoResponse

    // --- SECCIÓN INQUILINOS ---

    @GET("mobile/inquilinos")
    suspend fun getInquilinos(@Query("id_usuario") idUsuario: String): List<InquilinoMobile>

    @POST("mobile/inquilino/iniciar-retiro")
    suspend fun iniciarRetiro(@Body body: IdInquilinoRequest): PagoRegistradoResponse

    @POST("mobile/inquilino/cancelar-retiro")
    suspend fun cancelarRetiro(@Body body: IdInquilinoRequest): PagoRegistradoResponse

    // --- SECCIÓN SERVICIOS ---

    @GET("mobile/servicios")
    suspend fun getServicios(@Query("id_usuario") idUsuario: String): List<ServicioCasa>

    @POST("mobile/servicios/pagar")
    suspend fun pagarServicio(@Body body: PagarServicioRequest): PagoRegistradoResponse
}
