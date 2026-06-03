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

class PagosViewModel(private val app: MyApplication) : ViewModel() {

    private val _pagosState = MutableStateFlow<UiState<List<Inquilino>>>(UiState.Idle)
    val pagosState: StateFlow<UiState<List<Inquilino>>> = _pagosState.asStateFlow()

    private val _pagosRecientesState = MutableStateFlow<UiState<List<Inquilino>>>(UiState.Idle)
    val pagosRecientesState: StateFlow<UiState<List<Inquilino>>> = _pagosRecientesState.asStateFlow()

    private val _pagoRapidoState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pagoRapidoState: StateFlow<UiState<String>> = _pagoRapidoState.asStateFlow()

    fun cargarPagos() {
        viewModelScope.launch {
            _pagosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val pagosBackend = AlquilerApiClient.service.getPagosPendientes(idUsuario)
                val hoy = LocalDate.now()
                val inquilinos = pagosBackend.map { it.toInquilinoUi(hoy) }
                    .sortedWith(compareByDescending<Inquilino> { it.estadoPago.ordinal }.thenBy { it.diasRestantes })
                _pagosState.value = UiState.Success(inquilinos)
            } catch (e: Exception) {
                _pagosState.value = UiState.Error(e.message ?: "Error al cargar pagos")
            }
        }
    }

    fun cargarPagosRecientes() {
        viewModelScope.launch {
            _pagosRecientesState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val pagosBackend = AlquilerApiClient.service.getPagosRecientes(idUsuario)
                val hoy = LocalDate.now()
                val inquilinos = pagosBackend.map { it.toInquilinoUi(hoy) }
                _pagosRecientesState.value = UiState.Success(inquilinos)
            } catch (e: Exception) {
                _pagosRecientesState.value = UiState.Error(e.message ?: "Error al cargar historial")
            }
        }
    }

    fun registrarPagoRapido(inquilino: Inquilino) {
        viewModelScope.launch {
            _pagoRapidoState.value = UiState.Loading
            try {
                val request = PagoRequest(
                    montoPagado = inquilino.monto,
                    metodoPago = "Yape",
                    descripcion = "Pago desde App Mobile",
                    fechaCompromiso = null
                )
                val respuesta = AlquilerApiClient.service.registrarPago(inquilino.idPago, request)
                _pagoRapidoState.value = UiState.Success("Pago de ${inquilino.nombre} registrado con éxito.")
                cargarPagos()
            } catch (e: Exception) {
                _pagoRapidoState.value = UiState.Error(e.message ?: "Error al registrar pago")
            }
        }
    }

    fun revertirPago(idPago: String) {
        viewModelScope.launch {
            _pagoRapidoState.value = UiState.Loading
            try {
                AlquilerApiClient.service.revertirPago(idPago)
                _pagoRapidoState.value = UiState.Success("Pago revertido correctamente.")
                cargarPagosRecientes()
                cargarPagos()
            } catch (e: Exception) {
                _pagoRapidoState.value = UiState.Error(e.message ?: "Error al revertir pago")
            }
        }
    }

    fun resetPagoRapidoState() {
        _pagoRapidoState.value = UiState.Idle
    }

    private fun PagoBackend.toInquilinoUi(hoy: LocalDate): Inquilino {
        val fechaVenc = LocalDate.of(anio, mes, dia)
        val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
        val estadoPago = when {
            dias < 0 -> EstadoPago.VENCIDO
            dias <= 5 -> EstadoPago.POR_VENCER
            else -> EstadoPago.AL_DIA
        }
        return Inquilino(
            idPago = idPago,
            idInquilino = idInquilino,
            nombre = "$nombre $apellidos".trim(),
            habitacion = "$casa · Cuarto $nroCuarto",
            celular = celular,
            monto = montoTotal.toDoubleOrNull() ?: 0.0,
            montoOriginal = montoOriginal.toDoubleOrNull() ?: 0.0,
            esParcial = esPagoParcial,
            fechaVencimiento = fechaVenc,
            diasRestantes = dias,
            estadoPago = estadoPago,
            periodoMes = mes,
            periodoAnio = anio
        )
    }

    companion object {
        fun factory(app: MyApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PagosViewModel(app) as T
                }
            }
    }
}
