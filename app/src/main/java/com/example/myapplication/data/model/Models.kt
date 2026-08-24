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
    // Todos los campos tienen default: con coerceInputValues, si el backend manda un
    // null o falta un campo, cae al default en vez de tumbar TODA la lista de cobros.
    @SerialName("id_pago")               val idPago:            String = "",
    @SerialName("id_inquilino")          val idInquilino:       String = "",
    val mes:                                                     Int = 1,
    val anio:                                                    Int = 2000,
    val dia:                                                     Int = 1,
    val monto:                                                   String = "0",
    @SerialName("monto_total")           val montoTotal:        String = "0",
    @SerialName("monto_original_mensual")val montoOriginal:     String = "0",
    val nombre:                                                  String = "",
    val apellidos:                                               String = "",
    @SerialName("nro_cuarto")            val nroCuarto:         Int = 0,
    val piso:                                                    String = "",
    val casa:                                                    String = "",
    @SerialName("es_pago_parcial")       val esPagoParcial:     Boolean = false,
    @SerialName("mensualidad_pagada")    val mensualidadPagada: Boolean = false,
    val celular:                                                 String? = null,
    val garantia:                                                Double? = null,
    @SerialName("fecha_garantia")          val fechaGarantia:         String? = null,
    @SerialName("fecha_esperada_garantia") val fechaEsperadaGarantia: String? = null,
    // Fecha/hora en que se registró el pago (timestamp del backend). Se usa para
    // mostrar "cuándo se registró el cobro" en la sección de pagos realizados.
    @SerialName("fecha_pago")              val fechaPago:             String? = null
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
    val garantiaPagada:   Boolean = true,
    // Fecha (ISO) en que se registró el pago; solo se llena en el historial de pagos.
    val fechaPago:        String? = null
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
    @SerialName("id_inquilino")            val idInquilino:           String = "",
    val nombre:                                                       String = "",
    val apellidos:                                                    String = "",
    val celular:                                                      String? = null,
    val dni:                                                          String? = null,
    val email:                                                        String? = null,
    @SerialName("nro_cuarto")              val nroCuarto:             Int = 0,
    @SerialName("id_piso")                 val idPiso:                String = "",
    val piso:                                                         String = "",
    val casa:                                                         String = "",
    val estado:                                                       String = "",
    @SerialName("dias_para_retiro")        val diasParaRetiro:        Int? = null,
    @SerialName("fecha_garantia")          val fechaGarantia:         String? = null,
    @SerialName("fecha_esperada_garantia") val fechaEsperadaGarantia: String? = null,
    @SerialName("monto_garantia")          val montoGarantia:         String? = null
)

@Serializable
data class IdInquilinoRequest(
    @SerialName("id_inquilino") val idInquilino: String,
    /** true retira a la vez todos los cuartos que alquila esa misma persona. */
    @SerialName("todos_los_cuartos") val todosLosCuartos: Boolean = false
)

/**
 * Inquilino ya registrado, para el selector de "inquilino existente" al dar de
 * alta un cuarto adicional. Se agrupa por persona, no por contrato.
 */
@Serializable
data class InquilinoSeleccionable(
    @SerialName("id_persona") val idPersona: String = "",
    val nombre:                              String = "",
    val apellidos:                           String = "",
    val dni:                                 String? = null,
    val celular:                             String? = null,
    val email:                               String? = null,
    /** Cuántos cuartos alquila hoy. */
    val contratos:                           Int = 0,
    /** Números de esos cuartos, ya formateados ("3, 7"). */
    val cuartos:                             String? = null
) {
    val nombreCompleto: String get() = "$nombre $apellidos".trim()
}

// ═════════════════════════════════════════════════════════════════════════════
//  ESTADÍSTICAS (SECCIÓN MOBILE)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Potencial de ingresos de un piso, calculado sobre el precio de sus cuartos.
 * Siempre se cumple que `potencial = actual + muerto`.
 */
@Serializable
data class EstadisticaPiso(
    @SerialName("id_piso")            val idPiso:            String = "",
    val piso:                                                String = "",
    val casa:                                                String = "",
    val cuartos:                                             Int = 0,
    @SerialName("cuartos_alquilados") val cuartosAlquilados: Int = 0,
    @SerialName("cuartos_libres")     val cuartosLibres:     Int = 0,
    /** Lo que daría el piso entero si estuviera lleno. */
    val potencial:                                           Double = 0.0,
    /** Lo que genera hoy (cuartos alquilados). */
    val actual:                                              Double = 0.0,
    /** Lo que se deja de ganar por los cuartos vacíos. */
    val muerto:                                              Double = 0.0
)

@Serializable
data class EstadisticasMobile(
    val potencial:                                           Double = 0.0,
    val actual:                                              Double = 0.0,
    val muerto:                                              Double = 0.0,
    val cuartos:                                             Int = 0,
    @SerialName("cuartos_alquilados") val cuartosAlquilados: Int = 0,
    @SerialName("porPiso")            val porPiso:           List<EstadisticaPiso> = emptyList()
)

/**
 * Traslado de un inquilino a otro cuarto (mismo endpoint que usa la web).
 *
 * [aplicarPrecioNuevo] ajusta el recibo pendiente al precio del cuarto de destino.
 * Los recibos ya pagados nunca se tocan.
 */
@Serializable
data class CambiarCuartoRequest(
    @SerialName("id_inquilino")        val idInquilino:        String,
    @SerialName("id_cuarto_nuevo")     val idCuartoNuevo:      String,
    @SerialName("aplicar_precio_nuevo") val aplicarPrecioNuevo: Boolean
)

/**
 * Edición de los datos personales del inquilino (tabla `persona`).
 *
 * No toca el contrato (cuarto, fechas ni montos): para eso el backend tiene otro
 * endpoint. El email viaja siempre, y vacío significa "borrar el correo".
 */
@Serializable
data class EditarDatosPersonalesRequest(
    @SerialName("id_inquilino") val idInquilino: String,
    val nombre:                                  String,
    val apellidos:                               String,
    val celular:                                 String,
    val dni:                                     String,
    val email:                                   String
)

// ═════════════════════════════════════════════════════════════════════════════
//  CUARTOS LIBRES (SECCIÓN MOBILE)
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class CuartoLibre(
    @SerialName("id_cuarto")  val idCuarto:    String = "",
    @SerialName("nro_cuarto") val nroCuarto:   String = "",
    val precio:                                String? = null,
    val descripcion:                           String? = null,
    val garantia:                              String? = null,
    @SerialName("id_piso")    val idPiso:      String = "",
    val piso:                                  String = "",
    val casa:                                  String = ""
)

// ── Registro de inquilino (alquilar un cuarto) ────────────────────────────────
// Mismo contrato que el formulario web (POST /api/inquilino).
@Serializable
data class RegistrarInquilinoRequest(
    @SerialName("id_usuario")              val idUsuario:            String,
    @SerialName("id_cuarto")               val idCuarto:             String,
    val nombre:                                                      String,
    val apellidos:                                                  String,
    val dni:                                                        String,
    val celular:                                                    String,
    val email:                                                      String? = null,
    @SerialName("fecha_pago")              val fechaPago:            Int,
    @SerialName("dia_limpieza")            val diaLimpieza:          String,
    val descripcion:                                                String? = null,
    // esnuevo=true → primera deuda en el mes actual; false → mes siguiente.
    val esnuevo:                                                    Boolean,
    @SerialName("garantia_pagada")         val garantiaPagada:       Boolean,
    @SerialName("fecha_esperada_garantia") val fechaEsperadaGarantia: String? = null,
    // Fecha de ingreso real (YYYY-MM-DD), opcional. Si es de un mes anterior, el backend
    // genera una deuda pendiente por cada mes desde el ingreso hasta el actual.
    @SerialName("fecha_ingreso")           val fechaIngreso:         String? = null,
    // Cuarto adicional para alguien que ya es inquilino: el backend reutiliza su
    // persona en vez de rechazar el DNI repetido.
    @SerialName("inquilino_existente")     val inquilinoExistente:   Boolean = false,
    @SerialName("id_persona_existente")    val idPersonaExistente:   String? = null
)

@Serializable
data class InquilinoCreado(@SerialName("id_inquilino") val idInquilino: String)

@Serializable
data class RegistroInquilinoResponse(
    val message:    String? = null,
    val inquilino:  InquilinoCreado? = null
)

// Servicio adicional a registrar tras crear el inquilino (POST /api/inquilino/servicio).
@Serializable
data class AgregarServicioInquilinoRequest(
    @SerialName("id_inquilino") val idInquilino: String,
    val nombre:                                  String,
    val monto:                                   Double
)

/** Holder en memoria para los servicios que el usuario agrega en el wizard (no se serializa). */
data class ServicioNuevo(val nombre: String, val monto: Double)

// ── Sección "Cuartos" (listar todos + editar) ─────────────────────────────────
@Serializable
data class CuartoDetalle(
    @SerialName("id_cuarto")  val idCuarto:    String = "",
    @SerialName("nro_cuarto") val nroCuarto:   String = "",
    val precio:                                String? = null,
    val garantia:                              String? = null,
    val descripcion:                           String? = null,
    val estado:                                String? = null,
    @SerialName("id_piso")    val idPiso:      String = "",
    val piso:                                  String? = null,
    val casa:                                  String? = null
)

@Serializable
data class EditarCuartoRequest(
    @SerialName("nro_cuarto") val nroCuarto:   String,
    val precio:                                Double,
    val garantia:                              Double,
    @SerialName("id_piso")    val idPiso:      String,
    val descripcion:                           String? = null,
    // estemes=true → aplicar el nuevo precio también al último recibo ya generado
    // del inquilino activo; false → solo afecta a partir del siguiente recibo.
    val estemes:                               Boolean = false
)

// ═════════════════════════════════════════════════════════════════════════════
//  SERVICIOS DE CASA (LUZ, AGUA, GAS, ETC.)
// ═════════════════════════════════════════════════════════════════════════════

/** Recibo mensual de un servicio de casa (luz, agua, gas, etc.).
 *  Arquitectura idéntica a [Inquilino]: el cron lo genera, el usuario lo paga. */
@Serializable
data class ServicioCasa(
    @SerialName("id_pago")                val idPago:          String? = null,
    @SerialName("id_servicio")            val idServicio:      String = "",
    val nombre:                                                String = "",
    @SerialName("monto_referencial")      val montoReferencial: String = "0",
    @SerialName("monto_original_mensual") val montoOriginal:   String? = null,
    @SerialName("monto_pagado")          val montoPagado:     String? = null,
    val dia:                                                   Int = 1,
    @SerialName("mes_correspondiente")    val mes:             Int = 1,
    @SerialName("anio_correspondiente")   val anio:            Int = 2000,
    val pagado:                                               Boolean = false,
    @SerialName("precio_fijo")            val precioFijo:      Boolean = true,
    @SerialName("fecha_pago")             val fechaPago:       String? = null,
    @SerialName("dias_restantes")         val diasRestantes:   Int = 0
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

// ── Conceptos de servicio (subpestaña "Conceptos" del arrendador) ─────────────
/** Plantilla de servicio recurrente de la casa (luz, agua, etc.). Borrado diferido 24h. */
@Serializable
data class ServicioConcepto(
    @SerialName("id_servicio")     val idServicio:     String = "",
    val nombre:                                        String = "",
    @SerialName("monto_referencial") val montoReferencial: String = "0",
    @SerialName("dia_vencimiento") val diaVencimiento: Int = 1,
    @SerialName("precio_fijo")     val precioFijo:     Boolean = true,
    val activo:                                        Boolean = true,
    // Papelera: si eliminado=true puede deshacerse mientras queden minutos_para_borrado (<24h).
    val eliminado:                                     Boolean = false,
    @SerialName("minutos_para_borrado") val minutosParaBorrado: Int? = null
)

@Serializable
data class CrearServicioConceptoRequest(
    @SerialName("id_usuario")        val idUsuario:        String,
    val nombre:                                            String,
    @SerialName("monto_referencial") val montoReferencial: Double,
    @SerialName("dia_vencimiento")   val diaVencimiento:   Int,
    @SerialName("precio_fijo")       val precioFijo:       Boolean = true
)

@Serializable
data class EditarServicioConceptoRequest(
    @SerialName("id_servicio")       val idServicio:       String,
    val nombre:                                            String,
    @SerialName("monto_referencial") val montoReferencial: Double,
    @SerialName("dia_vencimiento")   val diaVencimiento:   Int,
    @SerialName("precio_fijo")       val precioFijo:       Boolean = true
)

// ── Abonos / pagos por partes de un recibo de inquilino ───────────────────────
/** Un abono parcial registrado contra un recibo mensual (GET /historial-pago). */
@Serializable
data class AbonoPago(
    @SerialName("id_abono")            val idAbono:        String = "",
    @SerialName("id_pago_mensual")     val idPagoMensual:  String = "",
    @SerialName("monto_abonado")       val montoAbonado:   String = "0",
    @SerialName("metodo_pago")         val metodoPago:     String? = null,
    @SerialName("descripcion_abono")   val descripcion:    String? = null,
    @SerialName("fecha_abono")         val fechaAbono:     String? = null,
    @SerialName("fecha_compromiso_restante") val fechaCompromiso: String? = null
)

// ═════════════════════════════════════════════════════════════════════════════
//  MÓDULO INDIVIDUAL (ingresos / gastos recurrentes)
// ═════════════════════════════════════════════════════════════════════════════

/** Movimiento mensual (recibo) de un concepto individual. tipo = ingreso | gasto. */
@Serializable
data class MovimientoIndividual(
    @SerialName("id_movimiento")          val idMovimiento:     String? = null,
    @SerialName("id_concepto")            val idConcepto:       String = "",
    val nombre:                                                 String = "",
    val tipo:                                                   String = "",
    val descripcion:                                            String? = null,
    @SerialName("monto_referencial")      val montoReferencial: String? = null,
    @SerialName("monto_total")            val montoTotal:       String? = null,
    @SerialName("monto_original_mensual") val montoOriginal:    String? = null,
    @SerialName("monto_pagado")           val montoPagado:      String? = null,
    val dia:                                                    Int = 1,
    @SerialName("mes_correspondiente")    val mes:              Int = 1,
    @SerialName("anio_correspondiente")   val anio:             Int = 2000,
    val conciliado:                                             Boolean = false,
    @SerialName("metodo_pago")            val metodoPago:       String? = null,
    @SerialName("fecha_registro")         val fechaRegistro:    String? = null,
    @SerialName("dias_restantes")         val diasRestantes:    Int = 0,
    @SerialName("precio_fijo")            val precioFijo:       Boolean = true,
    val celular:                                                String? = null
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
    @SerialName("id_concepto")     val idConcepto:     String = "",
    val tipo:                                          String = "",
    val nombre:                                        String = "",
    val descripcion:                                   String? = null,
    val monto:                                         String = "0",
    @SerialName("dia_vencimiento") val diaVencimiento: Int = 1,
    @SerialName("precio_fijo")     val precioFijo:     Boolean = true,
    val celular:                                       String? = null,
    val activo:                                        Boolean = true,
    // Borrado diferido: si está en papelera (eliminado=true) puede deshacerse
    // mientras queden minutos_para_borrado (< 24h desde la eliminación).
    val eliminado:                                     Boolean = false,
    @SerialName("minutos_para_borrado") val minutosParaBorrado: Int? = null
)

@Serializable
data class EditarConceptoRequest(
    @SerialName("id_concepto")     val idConcepto:     String,
    val nombre:                                        String,
    val descripcion:                                   String? = null,
    val monto:                                         Double,
    @SerialName("dia_vencimiento") val diaVencimiento: Int,
    @SerialName("precio_fijo")     val precioFijo:     Boolean = true,
    val celular:                                       String? = null
)

@Serializable
data class RegistrarMovimientoRequest(
    @SerialName("id_concepto")   val idConcepto:   String,
    @SerialName("id_usuario")    val idUsuario:    String,
    @SerialName("id_movimiento") val idMovimiento: String? = null,
    @SerialName("monto_pagado")  val montoPagado:  Double? = null,
    @SerialName("metodo_pago")   val metodoPago:   String? = null,
    val descripcion:                               String? = null,
    val celular:                                   String? = null
)

@Serializable
data class CrearConceptoRequest(
    @SerialName("id_usuario")      val idUsuario:      String,
    val tipo:                                          String,
    val nombre:                                        String,
    val descripcion:                                   String? = null,
    val monto:                                         Double,
    @SerialName("dia_vencimiento") val diaVencimiento: Int,
    @SerialName("precio_fijo")     val precioFijo:     Boolean = true,
    val celular:                                       String? = null
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
//  AJUSTES DE LA APP MÓVIL (tipo de aviso + hora del recordatorio diario)
//  Se guardan en el backend para restaurarse tras reinstalar la app o cambiar de
//  dispositivo, en vez de volver siempre a los valores por defecto.
// ═════════════════════════════════════════════════════════════════════════════

@Serializable
data class AjustesResponse(
    @SerialName("tipo_aviso")        val tipoAviso:        String = "notificacion",
    @SerialName("hora_notificacion") val horaNotificacion: String = "08:00"
)

@Serializable
data class GuardarAjustesRequest(
    @SerialName("id_usuario")        val idUsuario:        String,
    @SerialName("tipo_aviso")        val tipoAviso:        String? = null,
    @SerialName("hora_notificacion") val horaNotificacion: String? = null
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
    val nombre:                                                     String = "",
    val apellido:                                                   String? = null,
    val dni:                                                        String? = null,
    val celular:                                                    String? = null,
    val email:                                                      String? = null,
    val estado:                                                     String? = null,
    @SerialName("fecha_registro")       val fechaRegistro:          String? = null,
    @SerialName("id_usuario")           val idUsuario:              String = "",
    val plan:                                                       String? = null,
    @SerialName("plan_capacidad")       val planCapacidad:          Int? = null,
    @SerialName("inquilinos_registrados") val inquilinosRegistrados: Int? = null,
    @SerialName("inquilinos_activos")   val inquilinosActivos:      Int? = null
)

@Serializable
data class UsuariosResponse(
    val data:  List<UsuarioAdmin> = emptyList(),
    val total: Int = 0,
    val page:  Int = 1,
    val limit: Int = 0
)

@Serializable
data class PagoUsuario(
    @SerialName("id_pagousuario")      val idPagoUsuario:    String = "",
    @SerialName("id_usuario")          val idUsuario:        String = "",
    val monto:                                               String? = null,
    @SerialName("nombre_plan")         val nombrePlan:       String? = null,
    @SerialName("fecha_facturacion")   val fechaFacturacion: String? = null,
    @SerialName("fecha_registro")      val fechaRegistro:    String? = null,
    @SerialName("mensualidad_pagada")  val pagada:           Boolean = false,
    @SerialName("metodo_pago")         val metodoPago:       String? = null,
    val nombres:                                             String? = null,
    val dni:                                                 String? = null,
    val celular:                                             String? = null,
    val estado:                                              String? = null
)

// ═════════════════════════════════════════════════════════════════════════════
//  RECORDATORIOS POSPUESTOS (aplazar el aviso de un recibo unos días)
// ═════════════════════════════════════════════════════════════════════════════

/** Recibo cuyo recordatorio fue pospuesto hasta una fecha ("YYYY-MM-DD"). */
@Serializable
data class RecordatorioPospuesto(
    val clave:                            String = "",
    @SerialName("pospuesto_hasta")        val pospuestoHasta: String = ""
)

/** Body para posponer el recordatorio de un recibo. */
@Serializable
data class PosponerRequest(
    @SerialName("id_usuario")      val idUsuario:      String,
    val clave:                                         String,
    @SerialName("pospuesto_hasta") val pospuestoHasta: String
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
