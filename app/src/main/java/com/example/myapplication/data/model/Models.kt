// ─── data/model/Models.kt ────────────────────────────────────────────────────
package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

// ═════════════════════════════════════════════════════════════════════════════
//  AUTH
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class LoginRequest(
    val email:    String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token:    String? = null,
    @SerialName("id_usuario") val idUsuario: String? = null,
    val nombre:   String? = null,
    val rol:      String? = null,
    @SerialName("id_rol") val idRol: String = "",
    val error:    String? = null,
    val estado:   String? = null
)

// ═════════════════════════════════════════════════════════════════════════════
//  PAGOS PENDIENTES — respuesta del backend (GET /api/pagos)
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class PagoBackend(
    @SerialName("id_pago")               val idPago:            String,
    @SerialName("id_inquilino")          val idInquilino:       String,
    val mes:                                                     Int,
    val anio:                                                    Int,
    val dia:                                                     Int,
    val monto:                                                   String,
    @SerialName("monto_total")           val montoTotal:        String,
    @SerialName("monto_original_mensual")val montoOriginal:     String,
    val nombre:                                                  String,
    val apellidos:                                               String,
    @SerialName("nro_cuarto")            val nroCuarto:         Int,
    val piso:                                                    String,
    val casa:                                                    String,
    @SerialName("es_pago_parcial")       val esPagoParcial:     Boolean,
    @SerialName("mensualidad_pagada")    val mensualidadPagada: Boolean,
    val celular:                                                 String? = null,
    val garantia:                                                Double? = null,
    @SerialName("fecha_garantia")          val fechaGarantia:         String? = null,
    @SerialName("fecha_esperada_garantia") val fechaEsperadaGarantia: String? = null
)

// ═════════════════════════════════════════════════════════════════════════════
//  REGISTRO DE PAGO — body del POST /api/pagos/{id_pago}
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class PagoRequest(
    val accion:                                          String = "pagar_mensual",
    @SerialName("monto_pagado")     val montoPagado:    Double,
    @SerialName("metodo_pago")      val metodoPago:     String,
    val descripcion:                                     String? = null,
    @SerialName("fecha_compromiso") val fechaCompromiso: String? = null
)

@Serializable
data class PagoRegistradoResponse(
    val message: String
)

// ═════════════════════════════════════════════════════════════════════════════
//  MODELO DE UI — lo que ven las pantallas Compose
// ═════════════════════════════════════════════════════════════════════════════

/** Estado de vencimiento del pago (determina el color de la tarjeta). */
enum class EstadoPago { AL_DIA, POR_VENCER, VENCIDO }

/**
 * Modelo enriquecido para la UI.
 * Se construye en [PagosViewModel] mapeando [PagoBackend] con cálculos de fecha.
 */
data class Inquilino(
    val idPago:           String,
    val idInquilino:      String,
    // Datos personales
    val nombre:           String,        // nombre completo
    val habitacion:       String,        // "Casa X · Piso Y · Cuarto Z"
    val celular:          String?,
    // Montos
    val monto:            Double,        // saldo pendiente actual
    val montoOriginal:    Double,        // total facturado del mes
    val esParcial:        Boolean,
    // Fecha de vencimiento calculada a partir de (anio, mes, dia)
    val fechaVencimiento: LocalDate,
    val diasRestantes:    Long,          // negativo = vencido
    val estadoPago:       EstadoPago,
    // Período de cobro para mostrar en la tarjeta
    val periodoMes:       Int,
    val periodoAnio:      Int,
    // Garantía
    val montoGarantia:    Double? = null,
    val garantiaPagada:   Boolean = true
) {
    /** Texto descriptivo del vencimiento, listo para mostrar en la UI. */
    val etiquetaDias: String get() = when {
        diasRestantes < 0   -> "Vencido hace ${-diasRestantes}d"
        diasRestantes == 0L -> "¡Vence HOY!"
        diasRestantes <= 5  -> "${diasRestantes}d para vencer"
        else                -> "${diasRestantes}d restantes"
    }

    val nombreMes: String get() = listOf(
        "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    ).getOrElse(periodoMes) { periodoMes.toString() }
}

// ═════════════════════════════════════════════════════════════════════════════
//  INQUILINOS (SECCIÓN MOBILE)
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class InquilinoMobile(
    @SerialName("id_inquilino")            val idInquilino:           String,
    val nombre:                                                       String,
    val apellidos:                                                    String,
    val celular:                                                      String? = null,
    @SerialName("nro_cuarto")              val nroCuarto:             Int,
    val piso:                                                         String,
    val casa:                                                         String,
    val estado:                                                       String,
    @SerialName("dias_para_retiro")        val diasParaRetiro:        Int? = null,
    @SerialName("fecha_garantia")          val fechaGarantia:         String? = null,
    @SerialName("fecha_esperada_garantia") val fechaEsperadaGarantia: String? = null,
    @SerialName("monto_garantia")          val montoGarantia:         String? = null
)

@Serializable
data class IdInquilinoRequest(
    @SerialName("id_inquilino") val idInquilino: String
)

// ═════════════════════════════════════════════════════════════════════════════
//  CUARTOS LIBRES (SECCIÓN MOBILE)
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class CuartoLibre(
    @SerialName("id_cuarto")  val idCuarto:    String,
    @SerialName("nro_cuarto") val nroCuarto:   String,
    val precio:                                String? = null,
    val descripcion:                           String? = null,
    val garantia:                              String? = null,
    val piso:                                  String,
    val casa:                                  String
)

// ═════════════════════════════════════════════════════════════════════════════
//  SERVICIOS DE CASA (LUZ, AGUA, GAS, ETC.)
// ═════════════════════════════════════════════════════════════════════════════

/** Recibo mensual de un servicio de casa (luz, agua, gas, etc.).
 *  Arquitectura idéntica a [Inquilino]: el cron lo genera, el usuario lo paga. */
@Serializable
data class ServicioCasa(
    @SerialName("id_pago")                val idPago:          String? = null,
    @SerialName("id_servicio")            val idServicio:      String,
    val nombre:                                                String,
    @SerialName("monto_referencial")      val montoReferencial: String,
    @SerialName("monto_original_mensual") val montoOriginal:   String? = null,
    @SerialName("monto_pagado")          val montoPagado:     String? = null,
    val dia:                                                   Int,
    @SerialName("mes_correspondiente")    val mes:             Int,
    @SerialName("anio_correspondiente")   val anio:            Int,
    val pagado:                                               Boolean,
    @SerialName("precio_fijo")            val precioFijo:      Boolean = true,
    @SerialName("fecha_pago")             val fechaPago:       String? = null,
    @SerialName("dias_restantes")         val diasRestantes:   Int
) {
    val nombreMes: String get() = listOf(
        "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    ).getOrElse(mes) { mes.toString() }

    val etiquetaDias: String get() = when {
        pagado              -> "Pagado este mes ✓"
        diasRestantes < 0   -> "Vencido hace ${-diasRestantes}d"
        diasRestantes == 0  -> "¡Vence HOY!"
        diasRestantes <= 5  -> "${diasRestantes}d para vencer"
        else                -> "${diasRestantes}d restantes"
    }
}

@Serializable
data class PagarServicioRequest(
    @SerialName("id_servicio")   val idServicio:  String,
    @SerialName("id_usuario")    val idUsuario:   String,
    @SerialName("id_pago")       val idPago:      String? = null,
    @SerialName("monto_pagado")  val montoPagado: Double? = null
)

// ═════════════════════════════════════════════════════════════════════════════
//  MÓDULO INDIVIDUAL (ingresos / gastos recurrentes)
// ═════════════════════════════════════════════════════════════════════════════

/** Movimiento mensual (recibo) de un concepto individual. tipo = ingreso | gasto. */
@Serializable
data class MovimientoIndividual(
    @SerialName("id_movimiento")          val idMovimiento:     String? = null,
    @SerialName("id_concepto")            val idConcepto:       String,
    val nombre:                                                 String,
    val tipo:                                                   String,
    val descripcion:                                            String? = null,
    @SerialName("monto_referencial")      val montoReferencial: String? = null,
    @SerialName("monto_total")            val montoTotal:       String? = null,
    @SerialName("monto_original_mensual") val montoOriginal:    String? = null,
    @SerialName("monto_pagado")           val montoPagado:      String? = null,
    val dia:                                                    Int,
    @SerialName("mes_correspondiente")    val mes:              Int,
    @SerialName("anio_correspondiente")   val anio:             Int,
    val conciliado:                                             Boolean = false,
    @SerialName("metodo_pago")            val metodoPago:       String? = null,
    @SerialName("fecha_registro")         val fechaRegistro:    String? = null,
    @SerialName("dias_restantes")         val diasRestantes:    Int = 0
) {
    val esIngreso: Boolean get() = tipo == "ingreso"

    val montoMostrar: Double get() =
        (montoTotal ?: montoOriginal ?: montoReferencial)?.toDoubleOrNull() ?: 0.0

    val nombreMes: String get() = listOf(
        "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    ).getOrElse(mes) { mes.toString() }

    val etiquetaDias: String get() = when {
        conciliado          -> "Registrado ✓"
        diasRestantes < 0   -> "Vencido hace ${-diasRestantes}d"
        diasRestantes == 0  -> "¡Vence HOY!"
        diasRestantes <= 5  -> "${diasRestantes}d para vencer"
        else                -> "${diasRestantes}d restantes"
    }
}

/** Plantilla de concepto recurrente (ingreso o gasto). */
@Serializable
data class ConceptoIndividual(
    @SerialName("id_concepto")     val idConcepto:     String,
    val tipo:                                          String,
    val nombre:                                        String,
    val descripcion:                                   String? = null,
    val monto:                                         String,
    @SerialName("dia_vencimiento") val diaVencimiento: Int,
    val activo:                                        Boolean = true
)

@Serializable
data class RegistrarMovimientoRequest(
    @SerialName("id_concepto")   val idConcepto:   String,
    @SerialName("id_usuario")    val idUsuario:    String,
    @SerialName("id_movimiento") val idMovimiento: String? = null,
    @SerialName("monto_pagado")  val montoPagado:  Double? = null,
    @SerialName("metodo_pago")   val metodoPago:   String? = null,
    val descripcion:                               String? = null
)

@Serializable
data class CrearConceptoRequest(
    @SerialName("id_usuario")      val idUsuario:      String,
    val tipo:                                          String,
    val nombre:                                        String,
    val descripcion:                                   String? = null,
    val monto:                                         Double,
    @SerialName("dia_vencimiento") val diaVencimiento: Int
)

@Serializable
data class ResumenIndividual(
    val mes:  Int = 0,
    val anio: Int = 0,
    val ingresos: Double = 0.0,
    val gastos:   Double = 0.0,
    val balance:  Double = 0.0,
    @SerialName("num_ingresos") val numIngresos: Int = 0,
    @SerialName("num_gastos")   val numGastos:   Int = 0
)

// ═════════════════════════════════════════════════════════════════════════════
//  CAMBIO DE CONTRASEÑA
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class CambiarPasswordRequest(
    @SerialName("id_usuario")      val idUsuario:      String,
    @SerialName("password_actual") val passwordActual: String,
    @SerialName("password_nuevo")  val passwordNuevo:  String
)

@Serializable
data class CambiarPasswordResponse(
    val token:   String,
    val message: String
)

// ═════════════════════════════════════════════════════════════════════════════
//  ADMIN — USUARIOS Y PAGOS DE SUSCRIPCIÓN
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class UsuarioAdmin(
    val nombre:                                                     String,
    val apellido:                                                   String? = null,
    val dni:                                                        String? = null,
    val celular:                                                    String? = null,
    val email:                                                      String? = null,
    val estado:                                                     String? = null,
    @SerialName("fecha_registro")       val fechaRegistro:          String? = null,
    @SerialName("id_usuario")           val idUsuario:              String,
    val plan:                                                       String? = null,
    @SerialName("plan_capacidad")       val planCapacidad:          Int? = null,
    @SerialName("inquilinos_registrados") val inquilinosRegistrados: Int? = null,
    @SerialName("inquilinos_activos")   val inquilinosActivos:      Int? = null
)

@Serializable
data class UsuariosResponse(
    val data:  List<UsuarioAdmin>,
    val total: Int,
    val page:  Int,
    val limit: Int
)

@Serializable
data class PagoUsuario(
    @SerialName("id_pagousuario")      val idPagoUsuario:    String,
    @SerialName("id_usuario")          val idUsuario:        String,
    val monto:                                               String? = null,
    @SerialName("nombre_plan")         val nombrePlan:       String? = null,
    @SerialName("fecha_facturacion")   val fechaFacturacion: String? = null,
    @SerialName("fecha_registro")      val fechaRegistro:    String? = null,
    @SerialName("mensualidad_pagada")  val pagada:           Boolean,
    @SerialName("metodo_pago")         val metodoPago:       String? = null,
    val nombres:                                             String? = null,
    val dni:                                                 String? = null,
    val celular:                                             String? = null,
    val estado:                                              String? = null
)

@Serializable
data class ConfirmarPagoUsuarioRequest(
    @SerialName("id_pagousuario") val idPagoUsuario: String,
    @SerialName("metodo_pago")    val metodoPago:    String
)

@Serializable
data class RevertirPagoUsuarioRequest(
    @SerialName("id_pagousuario") val idPagoUsuario: String
)

@Serializable
data class CambiarEstadoUsuarioRequest(
    val estado: String
)

// ═════════════════════════════════════════════════════════════════════════════
//  ESTADO GENÉRICO DE UI
// ═════════════════════════════════════════════════════════════════════════════

sealed class UiState<out T> {
    data object Idle    : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String)  : UiState<Nothing>()
}
