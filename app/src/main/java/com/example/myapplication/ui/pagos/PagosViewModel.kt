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

    private val _inquilinosState = MutableStateFlow<UiState<List<InquilinoMobile>>>(UiState.Idle)
    val inquilinosState: StateFlow<UiState<List<InquilinoMobile>>> = _inquilinosState.asStateFlow()

    private val _retiroState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val retiroState: StateFlow<UiState<String>> = _retiroState.asStateFlow()

    private val _serviciosState = MutableStateFlow<UiState<List<ServicioCasa>>>(UiState.Idle)
    val serviciosState: StateFlow<UiState<List<ServicioCasa>>> = _serviciosState.asStateFlow()

    private val _pagarServicioState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pagarServicioState: StateFlow<UiState<String>> = _pagarServicioState.asStateFlow()

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

    fun cargarInquilinos() {
        viewModelScope.launch {
            _inquilinosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val lista = AlquilerApiClient.service.getInquilinos(idUsuario)
                _inquilinosState.value = UiState.Success(lista)
            } catch (e: Exception) {
                _inquilinosState.value = UiState.Error(e.message ?: "Error al cargar inquilinos")
            }
        }
    }

    fun iniciarRetiro(idInquilino: String) {
        viewModelScope.launch {
            _retiroState.value = UiState.Loading
            try {
                AlquilerApiClient.service.iniciarRetiro(IdInquilinoRequest(idInquilino))
                _retiroState.value = UiState.Success("Retiro iniciado")
                cargarInquilinos()
            } catch (e: Exception) {
                _retiroState.value = UiState.Error(e.message ?: "Error al iniciar retiro")
            }
        }
    }

    fun cancelarRetiro(idInquilino: String) {
        viewModelScope.launch {
            _retiroState.value = UiState.Loading
            try {
                AlquilerApiClient.service.cancelarRetiro(IdInquilinoRequest(idInquilino))
                _retiroState.value = UiState.Success("Retiro cancelado")
                cargarInquilinos()
            } catch (e: Exception) {
                _retiroState.value = UiState.Error(e.message ?: "Error al cancelar retiro")
            }
        }
    }

    fun resetRetiroState() {
        _retiroState.value = UiState.Idle
    }

    fun cargarServicios() {
        viewModelScope.launch {
            _serviciosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _serviciosState.value = UiState.Success(AlquilerApiClient.service.getServicios(idUsuario))
            } catch (e: Exception) {
                _serviciosState.value = UiState.Error(e.message ?: "Error al cargar servicios")
            }
        }
    }

    fun pagarServicio(idServicio: String, idPago: String? = null, montoPagado: Double? = null) {
        viewModelScope.launch {
            _pagarServicioState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val resp = AlquilerApiClient.service.pagarServicio(
                    PagarServicioRequest(idServicio, idUsuario, idPago, montoPagado)
                )
                _pagarServicioState.value = UiState.Success(resp.message)
                cargarServicios()
            } catch (e: Exception) {
                _pagarServicioState.value = UiState.Error(e.message ?: "Error al pagar servicio")
            }
        }
    }

    fun resetPagarServicioState() {
        _pagarServicioState.value = UiState.Idle
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
            habitacion = "$casa · $piso · Cuarto $nroCuarto",
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
