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

    @POST("mobile/inquilino/pagar-garantia")
    suspend fun pagarGarantia(@Body body: IdInquilinoRequest): PagoRegistradoResponse

    // --- SECCIÓN CUARTOS LIBRES ---

    @GET("mobile/cuartos-libres")
    suspend fun getCuartosLibres(@Query("id_usuario") idUsuario: String): List<CuartoLibre>

    // --- SECCIÓN SERVICIOS ---

    @GET("mobile/servicios")
    suspend fun getServicios(@Query("id_usuario") idUsuario: String): List<ServicioCasa>

    @GET("mobile/servicios/realizados")
    suspend fun getServiciosRealizados(@Query("id_usuario") idUsuario: String): List<ServicioCasa>

    @POST("mobile/servicios/pagar")
    suspend fun pagarServicio(@Body body: PagarServicioRequest): PagoRegistradoResponse

    @POST("servicios/revertir/{id_pago}")
    suspend fun revertirServicio(@Path("id_pago") idPago: String): PagoRegistradoResponse

    // --- ADMIN: USUARIOS ---

    @GET("usuarios")
    suspend fun getUsuarios(
        @Header("id_rol") idRol: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): UsuariosResponse

    @PUT("mobile/usuarios/{id_usuario}/estado")
    suspend fun cambiarEstadoUsuario(
        @Path("id_usuario") idUsuario: String,
        @Header("id_rol") idRol: String,
        @Body body: CambiarEstadoUsuarioRequest
    ): PagoRegistradoResponse

    // --- ADMIN: PAGOS DE SUSCRIPCIÓN ---

    @GET("pagos-usuarios")
    suspend fun getPagosUsuarios(): List<PagoUsuario>

    @GET("pagos-usuarios/realizados")
    suspend fun getPagosUsuariosRealizados(): List<PagoUsuario>

    @PUT("pagos-usuarios")
    suspend fun confirmarPagoUsuario(@Body body: ConfirmarPagoUsuarioRequest): PagoRegistradoResponse

    @PUT("pagos-usuarios/revertir")
    suspend fun revertirPagoUsuario(@Body body: RevertirPagoUsuarioRequest): PagoRegistradoResponse

    // --- CAMBIO DE CONTRASEÑA ---

    @POST("cambiar-password")
    suspend fun cambiarPassword(@Body body: CambiarPasswordRequest): CambiarPasswordResponse
}
