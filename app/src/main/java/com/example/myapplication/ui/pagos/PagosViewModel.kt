// ─── ui/pagos/PagosViewModel.kt ──────────────────────────────────────────────
package com.example.myapplication.ui.pagos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.*
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * ViewModel del Dashboard de Pagos Pendientes.
 *
 * Responsabilidades:
 *  - Cargar la lista de pagos pendientes desde el backend.
 *  - Transformar cada [PagoBackend] en un [Inquilino] de UI con:
 *      · [EstadoPago] calculado según días restantes hacia la fecha de vencimiento.
 *      · Ordenamiento: VENCIDO (Rojo) → POR_VENCER (Amarillo) → AL_DIA (Verde).
 *  - Ejecutar [registrarPagoRapido] con un solo toque (sin pedir datos al usuario).
 */
class PagosViewModel(private val app: MyApplication) : ViewModel() {

    // ── Estado de la lista ────────────────────────────────────────────────────

    private val _pagosState = MutableStateFlow<UiState<List<Inquilino>>>(UiState.Idle)
    val pagosState: StateFlow<UiState<List<Inquilino>>> = _pagosState.asStateFlow()

    // ── Estado del pago rápido (feedback instantáneo por tarjeta) ─────────────

    private val _pagoRapidoState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pagoRapidoState: StateFlow<UiState<String>> = _pagoRapidoState.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGA DE PAGOS PENDIENTES
    // ─────────────────────────────────────────────────────────────────────────

    fun cargarPagos() {
        viewModelScope.launch {
            _pagosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first()
                    ?: return@launch run {
                        _pagosState.value = UiState.Error("Sesión inválida. Vuelve a iniciar sesión.")
                    }

                val pagosBackend = AlquilerApiClient.service.getPagosPendientes(idUsuario)
                val hoy          = LocalDate.now()

                val inquilinos = pagosBackend.map { pago -> pago.toInquilinoUi(hoy) }
                    .sortedWith(
                        // 1° VENCIDO (Rojo), 2° POR_VENCER (Amarillo), 3° AL_DIA (Verde)
                        // Se usa descending porque en el enum AL_DIA=0 y VENCIDO=2
                        compareByDescending<Inquilino> { it.estadoPago.ordinal }
                            .thenBy { it.diasRestantes }
                    )

                _pagosState.value = UiState.Success(inquilinos)

            } catch (e: HttpException) {
                _pagosState.value = UiState.Error(httpErrorMessage(e.code()))
            } catch (e: Exception) {
                _pagosState.value = UiState.Error(
                    e.message ?: "Sin conexión al servidor. Verifica que el backend esté activo."
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PAGO RÁPIDO (un toque, valores por defecto automatizados)
    // ─────────────────────────────────────────────────────────────────────────

    fun registrarPagoRapido(inquilino: Inquilino) {
        viewModelScope.launch {
            _pagoRapidoState.value = UiState.Loading
            try {
                val request = PagoRequest(
                    montoPagado  = inquilino.monto,
                    metodoPago   = "Yape",
                    descripcion  = "Mensualidad Automatizada App",
                    fechaCompromiso = null
                )
                val respuesta = AlquilerApiClient.service.registrarPago(inquilino.idPago, request)
                _pagoRapidoState.value = UiState.Success(respuesta.message)

                cargarPagos()

            } catch (e: HttpException) {
                _pagoRapidoState.value = UiState.Error(httpErrorMessage(e.code()))
            } catch (e: Exception) {
                _pagoRapidoState.value = UiState.Error(
                    e.message ?: "Error al registrar el pago"
                )
            }
        }
    }

    fun resetPagoRapidoState() {
        _pagoRapidoState.value = UiState.Idle
    }

    private fun PagoBackend.toInquilinoUi(hoy: LocalDate): Inquilino {
        val fechaVenc    = LocalDate.of(anio, mes, dia)
        val dias         = ChronoUnit.DAYS.between(hoy, fechaVenc)
        val estadoPago   = when {
            dias < 0   -> EstadoPago.VENCIDO
            dias <= 5  -> EstadoPago.POR_VENCER
            else       -> EstadoPago.AL_DIA
        }
        return Inquilino(
            idPago           = idPago,
            idInquilino      = idInquilino,
            nombre           = "$nombre $apellidos".trim(),
            habitacion       = "$casa · $piso · Cuarto $nroCuarto",
            celular          = celular,
            monto            = montoTotal.toDoubleOrNull()   ?: 0.0,
            montoOriginal    = montoOriginal.toDoubleOrNull() ?: 0.0,
            esParcial        = esPagoParcial,
            fechaVencimiento = fechaVenc,
            diasRestantes    = dias,
            estadoPago       = estadoPago,
            periodoMes       = mes,
            periodoAnio      = anio
        )
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401  -> "Sesión expirada. Vuelve a iniciar sesión."
        403  -> "Sin permiso para esta acción."
        404  -> "Recurso no encontrado en el servidor."
        500  -> "Error interno del servidor."
        else -> "Error HTTP $code."
    }

    companion object {
        fun factory(app: MyApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return PagosViewModel(app) as T
                }
            }
    }
}
