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

    private val _serviciosRealizadosState = MutableStateFlow<UiState<List<ServicioCasa>>>(UiState.Idle)
    val serviciosRealizadosState: StateFlow<UiState<List<ServicioCasa>>> = _serviciosRealizadosState.asStateFlow()

    private val _cuartosLibresState = MutableStateFlow<UiState<List<CuartoLibre>>>(UiState.Idle)
    val cuartosLibresState: StateFlow<UiState<List<CuartoLibre>>> = _cuartosLibresState.asStateFlow()

    private val _pagarServicioState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pagarServicioState: StateFlow<UiState<String>> = _pagarServicioState.asStateFlow()

    private val _usuariosState = MutableStateFlow<UiState<List<UsuarioAdmin>>>(UiState.Idle)
    val usuariosState: StateFlow<UiState<List<UsuarioAdmin>>> = _usuariosState.asStateFlow()

    private val _pagosUsuariosState = MutableStateFlow<UiState<List<PagoUsuario>>>(UiState.Idle)
    val pagosUsuariosState: StateFlow<UiState<List<PagoUsuario>>> = _pagosUsuariosState.asStateFlow()

    // ── MÓDULO INDIVIDUAL ─────────────────────────────────────────────────────
    private val _movIndividualState = MutableStateFlow<UiState<List<MovimientoIndividual>>>(UiState.Idle)
    val movIndividualState: StateFlow<UiState<List<MovimientoIndividual>>> = _movIndividualState.asStateFlow()

    private val _movIndividualRealizadosState = MutableStateFlow<UiState<List<MovimientoIndividual>>>(UiState.Idle)
    val movIndividualRealizadosState: StateFlow<UiState<List<MovimientoIndividual>>> = _movIndividualRealizadosState.asStateFlow()

    private val _conceptosIndividualState = MutableStateFlow<UiState<List<ConceptoIndividual>>>(UiState.Idle)
    val conceptosIndividualState: StateFlow<UiState<List<ConceptoIndividual>>> = _conceptosIndividualState.asStateFlow()

    private val _resumenIndividualState = MutableStateFlow<UiState<ResumenIndividual>>(UiState.Idle)
    val resumenIndividualState: StateFlow<UiState<ResumenIndividual>> = _resumenIndividualState.asStateFlow()

    private val _accionIndividualState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val accionIndividualState: StateFlow<UiState<String>> = _accionIndividualState.asStateFlow()

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

    fun pagarGarantia(idInquilino: String) {
        viewModelScope.launch {
            _retiroState.value = UiState.Loading
            try {
                AlquilerApiClient.service.pagarGarantia(IdInquilinoRequest(idInquilino))
                _retiroState.value = UiState.Success("Garantía registrada como pagada")
                cargarInquilinos()
                cargarPagos()
            } catch (e: Exception) {
                _retiroState.value = UiState.Error(e.message ?: "Error al registrar garantía")
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

    fun cargarCuartosLibres() {
        viewModelScope.launch {
            _cuartosLibresState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val lista = AlquilerApiClient.service.getCuartosLibres(idUsuario)
                _cuartosLibresState.value = UiState.Success(lista)
            } catch (e: Exception) {
                _cuartosLibresState.value = UiState.Error(e.message ?: "Error al cargar cuartos libres")
            }
        }
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

    fun cargarServiciosRealizados() {
        viewModelScope.launch {
            _serviciosRealizadosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _serviciosRealizadosState.value = UiState.Success(AlquilerApiClient.service.getServiciosRealizados(idUsuario))
            } catch (e: Exception) {
                _serviciosRealizadosState.value = UiState.Error(e.message ?: "Error al cargar servicios pagados")
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

    fun revertirServicio(idPago: String) {
        viewModelScope.launch {
            _pagarServicioState.value = UiState.Loading
            try {
                val resp = AlquilerApiClient.service.revertirServicio(idPago)
                _pagarServicioState.value = UiState.Success(resp.message)
                cargarServicios()
                cargarServiciosRealizados()
            } catch (e: Exception) {
                _pagarServicioState.value = UiState.Error(e.message ?: "Error al revertir pago")
            }
        }
    }

    fun resetPagarServicioState() {
        _pagarServicioState.value = UiState.Idle
    }

    fun cargarUsuarios() {
        viewModelScope.launch {
            _usuariosState.value = UiState.Loading
            try {
                val idRol = app.sessionDataStore.idRol.first() ?: return@launch
                val resp = AlquilerApiClient.service.getUsuarios(idRol)
                _usuariosState.value = UiState.Success(resp.data)
            } catch (e: Exception) {
                _usuariosState.value = UiState.Error(e.message ?: "Error al cargar usuarios")
            }
        }
    }

    fun cargarPagosUsuarios() {
        viewModelScope.launch {
            _pagosUsuariosState.value = UiState.Loading
            try {
                val lista = AlquilerApiClient.service.getPagosUsuarios()
                _pagosUsuariosState.value = UiState.Success(lista)
            } catch (e: Exception) {
                _pagosUsuariosState.value = UiState.Error(e.message ?: "Error al cargar pagos")
            }
        }
    }

    private val _pagosRealizadosState = MutableStateFlow<UiState<List<PagoUsuario>>>(UiState.Idle)
    val pagosRealizadosState: StateFlow<UiState<List<PagoUsuario>>> = _pagosRealizadosState.asStateFlow()

    private val _adminActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val adminActionState: StateFlow<UiState<String>> = _adminActionState.asStateFlow()

    fun cargarPagosRealizados() {
        viewModelScope.launch {
            _pagosRealizadosState.value = UiState.Loading
            try {
                val lista = AlquilerApiClient.service.getPagosUsuariosRealizados()
                _pagosRealizadosState.value = UiState.Success(lista)
            } catch (e: Exception) {
                _pagosRealizadosState.value = UiState.Error(e.message ?: "Error al cargar pagos realizados")
            }
        }
    }

    fun cambiarEstadoUsuario(idUsuario: String, nuevoEstado: String) {
        viewModelScope.launch {
            _adminActionState.value = UiState.Loading
            try {
                val idRol = app.sessionDataStore.idRol.first() ?: return@launch
                AlquilerApiClient.service.cambiarEstadoUsuario(idUsuario, idRol, CambiarEstadoUsuarioRequest(nuevoEstado))
                _adminActionState.value = UiState.Success("Usuario ${if (nuevoEstado == "activo") "activado" else "inactivado"}")
                cargarUsuarios()
            } catch (e: Exception) {
                _adminActionState.value = UiState.Error(e.message ?: "Error al cambiar estado")
            }
        }
    }

    fun confirmarPagoUsuario(idPagoUsuario: String) {
        viewModelScope.launch {
            _adminActionState.value = UiState.Loading
            try {
                AlquilerApiClient.service.confirmarPagoUsuario(ConfirmarPagoUsuarioRequest(idPagoUsuario, "Yape"))
                _adminActionState.value = UiState.Success("Pago confirmado")
                cargarPagosUsuarios()
            } catch (e: Exception) {
                _adminActionState.value = UiState.Error(e.message ?: "Error al confirmar pago")
            }
        }
    }

    fun revertirPagoUsuario(idPagoUsuario: String) {
        viewModelScope.launch {
            _adminActionState.value = UiState.Loading
            try {
                AlquilerApiClient.service.revertirPagoUsuario(RevertirPagoUsuarioRequest(idPagoUsuario))
                _adminActionState.value = UiState.Success("Pago revertido")
                cargarPagosRealizados()
                cargarPagosUsuarios()
            } catch (e: Exception) {
                _adminActionState.value = UiState.Error(e.message ?: "Error al revertir pago")
            }
        }
    }

    fun resetAdminActionState() { _adminActionState.value = UiState.Idle }

    // ── MÓDULO INDIVIDUAL ─────────────────────────────────────────────────────

    fun cargarMovimientos(tipo: String) {
        viewModelScope.launch {
            _movIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _movIndividualState.value = UiState.Success(
                    AlquilerApiClient.service.getMovimientosIndividuales(idUsuario, tipo)
                )
            } catch (e: Exception) {
                _movIndividualState.value = UiState.Error(e.message ?: "Error al cargar movimientos")
            }
        }
    }

    fun cargarMovimientosRealizados(tipo: String) {
        viewModelScope.launch {
            _movIndividualRealizadosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _movIndividualRealizadosState.value = UiState.Success(
                    AlquilerApiClient.service.getMovimientosIndividualesRealizados(idUsuario, tipo)
                )
            } catch (e: Exception) {
                _movIndividualRealizadosState.value = UiState.Error(e.message ?: "Error al cargar movimientos")
            }
        }
    }

    fun registrarMovimiento(
        idConcepto: String, idMovimiento: String?, montoPagado: Double?,
        metodoPago: String, descripcion: String?, tipo: String
    ) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val resp = AlquilerApiClient.service.registrarMovimiento(
                    RegistrarMovimientoRequest(idConcepto, idUsuario, idMovimiento, montoPagado, metodoPago, descripcion)
                )
                _accionIndividualState.value = UiState.Success(resp.message)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(e.message ?: "Error al registrar")
            }
        }
    }

    fun revertirMovimiento(idMovimiento: String, tipo: String) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                val resp = AlquilerApiClient.service.revertirMovimiento(idMovimiento)
                _accionIndividualState.value = UiState.Success(resp.message)
                cargarMovimientosRealizados(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(e.message ?: "Error al revertir")
            }
        }
    }

    fun cargarConceptos(tipo: String) {
        viewModelScope.launch {
            _conceptosIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _conceptosIndividualState.value = UiState.Success(
                    AlquilerApiClient.service.getConceptosIndividuales(idUsuario, tipo)
                )
            } catch (e: Exception) {
                _conceptosIndividualState.value = UiState.Error(e.message ?: "Error al cargar conceptos")
            }
        }
    }

    fun crearConcepto(tipo: String, nombre: String, descripcion: String?, monto: Double, diaVencimiento: Int) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                AlquilerApiClient.service.crearConcepto(
                    CrearConceptoRequest(idUsuario, tipo, nombre, descripcion, monto, diaVencimiento)
                )
                _accionIndividualState.value = UiState.Success("Concepto creado")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(e.message ?: "Error al crear concepto")
            }
        }
    }

    fun eliminarConcepto(idConcepto: String, tipo: String) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                AlquilerApiClient.service.eliminarConcepto(idConcepto)
                _accionIndividualState.value = UiState.Success("Concepto eliminado")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(e.message ?: "Error al eliminar concepto")
            }
        }
    }

    fun cargarResumenIndividual() {
        viewModelScope.launch {
            _resumenIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _resumenIndividualState.value = UiState.Success(
                    AlquilerApiClient.service.getResumenIndividual(idUsuario)
                )
            } catch (e: Exception) {
                _resumenIndividualState.value = UiState.Error(e.message ?: "Error al cargar resumen")
            }
        }
    }

    fun resetAccionIndividualState() { _accionIndividualState.value = UiState.Idle }

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
            periodoAnio = anio,
            montoGarantia = garantia,
            garantiaPagada = fechaGarantia != null
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
