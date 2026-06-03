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
    val token:    String,
    @SerialName("id_usuario") val idUsuario: String,
    val nombre:   String,
    val rol:      String
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
    @SerialName("fecha_garantia")        val fechaGarantia:     String? = null
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
    val periodoAnio:      Int
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
    @SerialName("id_inquilino")    val idInquilino:    String,
    val nombre:                                        String,
    val apellidos:                                     String,
    val celular:                                       String? = null,
    @SerialName("nro_cuarto")      val nroCuarto:      Int,
    val piso:                                          String,
    val casa:                                          String,
    val estado:                                        String,          // "activo" | "pendiente_retiro"
    @SerialName("dias_para_retiro") val diasParaRetiro: Int? = null
)

@Serializable
data class IdInquilinoRequest(
    @SerialName("id_inquilino") val idInquilino: String
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
