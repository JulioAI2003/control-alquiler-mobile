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

    // Registrar un inquilino en un cuarto (mismo endpoint que la web).
    @POST("inquilino")
    suspend fun registrarInquilino(@Body body: RegistrarInquilinoRequest): RegistroInquilinoResponse

    // Agregar un servicio adicional al inquilino recién creado (actualiza el recibo).
    @POST("inquilino/servicio")
    suspend fun agregarServicioInquilino(@Body body: AgregarServicioInquilinoRequest): okhttp3.ResponseBody

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

    // Sin id_usuario → todas las suscripciones pendientes (vista admin = por cobrar).
    // Con id_usuario  → solo las del usuario indicado (su suscripción por pagar).
    @GET("pagos-usuarios")
    suspend fun getPagosUsuarios(@Query("id_usuario") idUsuario: String? = null): List<PagoUsuario>

    @GET("pagos-usuarios/realizados")
    suspend fun getPagosUsuariosRealizados(): List<PagoUsuario>

    @PUT("pagos-usuarios")
    suspend fun confirmarPagoUsuario(@Body body: ConfirmarPagoUsuarioRequest): PagoRegistradoResponse

    @PUT("pagos-usuarios/revertir")
    suspend fun revertirPagoUsuario(@Body body: RevertirPagoUsuarioRequest): PagoRegistradoResponse

    // --- RECORDATORIOS POSPUESTOS (aplazar avisos por recibo) ---

    @GET("recordatorios/pospuestos")
    suspend fun getRecordatoriosPospuestos(@Query("id_usuario") idUsuario: String): List<RecordatorioPospuesto>

    @POST("recordatorios/posponer")
    suspend fun posponerRecordatorio(@Body body: PosponerRequest): PagoRegistradoResponse

    // --- CAMBIO DE CONTRASEÑA ---

    @POST("cambiar-password")
    suspend fun cambiarPassword(@Body body: CambiarPasswordRequest): CambiarPasswordResponse

    // --- MÓDULO INDIVIDUAL (ingresos / gastos) ---

    @GET("individual/movimientos")
    suspend fun getMovimientosIndividuales(
        @Query("id_usuario") idUsuario: String,
        @Query("tipo") tipo: String = ""
    ): List<MovimientoIndividual>

    @GET("individual/movimientos/realizados")
    suspend fun getMovimientosIndividualesRealizados(
        @Query("id_usuario") idUsuario: String,
        @Query("tipo") tipo: String = ""
    ): List<MovimientoIndividual>

    @POST("individual/movimientos/registrar")
    suspend fun registrarMovimiento(@Body body: RegistrarMovimientoRequest): PagoRegistradoResponse

    @POST("individual/movimientos/revertir/{id_movimiento}")
    suspend fun revertirMovimiento(@Path("id_movimiento") idMovimiento: String): PagoRegistradoResponse

    @GET("individual/conceptos")
    suspend fun getConceptosIndividuales(
        @Query("id_usuario") idUsuario: String,
        @Query("tipo") tipo: String = ""
    ): List<ConceptoIndividual>

    @POST("individual/concepto")
    suspend fun crearConcepto(@Body body: CrearConceptoRequest): ConceptoIndividual

    @PUT("individual/concepto")
    suspend fun editarConcepto(@Body body: EditarConceptoRequest): ConceptoIndividual

    @DELETE("individual/concepto")
    suspend fun eliminarConcepto(@Query("id_concepto") idConcepto: String): PagoRegistradoResponse

    @POST("individual/concepto/restaurar")
    suspend fun restaurarConcepto(@Query("id_concepto") idConcepto: String): PagoRegistradoResponse

    @GET("individual/resumen")
    suspend fun getResumenIndividual(@Query("id_usuario") idUsuario: String): ResumenIndividual
}
