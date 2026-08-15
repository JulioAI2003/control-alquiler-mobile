package com.example.myapplication.ui.pagos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.*
import com.example.myapplication.data.remote.AlquilerApiClient
import com.example.myapplication.data.remote.NetworkError
import com.example.myapplication.util.DescargasPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Contrato PDF del inquilino: Success lleva el Uri del archivo ya guardado.
    private val _contratoState = MutableStateFlow<UiState<Uri>>(UiState.Idle)
    val contratoState: StateFlow<UiState<Uri>> = _contratoState.asStateFlow()

    private val _serviciosState = MutableStateFlow<UiState<List<ServicioCasa>>>(UiState.Idle)
    val serviciosState: StateFlow<UiState<List<ServicioCasa>>> = _serviciosState.asStateFlow()

    private val _serviciosRealizadosState = MutableStateFlow<UiState<List<ServicioCasa>>>(UiState.Idle)
    val serviciosRealizadosState: StateFlow<UiState<List<ServicioCasa>>> = _serviciosRealizadosState.asStateFlow()

    private val _cuartosLibresState = MutableStateFlow<UiState<List<CuartoLibre>>>(UiState.Idle)
    val cuartosLibresState: StateFlow<UiState<List<CuartoLibre>>> = _cuartosLibresState.asStateFlow()

    private val _registrarInquilinoState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val registrarInquilinoState: StateFlow<UiState<String>> = _registrarInquilinoState.asStateFlow()

    private val _cuartosState = MutableStateFlow<UiState<List<CuartoDetalle>>>(UiState.Idle)
    val cuartosState: StateFlow<UiState<List<CuartoDetalle>>> = _cuartosState.asStateFlow()

    private val _editarCuartoState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val editarCuartoState: StateFlow<UiState<String>> = _editarCuartoState.asStateFlow()

    private val _pagarServicioState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pagarServicioState: StateFlow<UiState<String>> = _pagarServicioState.asStateFlow()

    // Conceptos de servicio (subpestaña "Conceptos" del arrendador)
    private val _conceptosServicioState = MutableStateFlow<UiState<List<ServicioConcepto>>>(UiState.Idle)
    val conceptosServicioState: StateFlow<UiState<List<ServicioConcepto>>> = _conceptosServicioState.asStateFlow()

    private val _accionServicioState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val accionServicioState: StateFlow<UiState<String>> = _accionServicioState.asStateFlow()

    // Abonos / pagos por partes de un recibo de inquilino
    private val _abonosState = MutableStateFlow<UiState<List<AbonoPago>>>(UiState.Idle)
    val abonosState: StateFlow<UiState<List<AbonoPago>>> = _abonosState.asStateFlow()

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
                _pagosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar pagos"))
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
                _pagosRecientesState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar historial"))
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
                _pagoRapidoState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al registrar pago"))
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
                _pagoRapidoState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al revertir pago"))
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
                _inquilinosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar inquilinos"))
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
                _retiroState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al iniciar retiro"))
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
                _retiroState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al registrar garantía"))
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
                _retiroState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cancelar retiro"))
            }
        }
    }

    fun resetRetiroState() {
        _retiroState.value = UiState.Idle
    }

    /**
     * Pide al backend el contrato del inquilino y lo guarda en Descargas.
     * Los datos del contrato (nombre, DNI, baño, fecha de ingreso, precio,
     * garantía y detalles adicionales) los arma el backend con lo ya registrado.
     */
    fun descargarContrato(inquilino: InquilinoMobile) {
        viewModelScope.launch {
            _contratoState.value = UiState.Loading
            try {
                val cuerpo = AlquilerApiClient.service.descargarContrato(inquilino.idInquilino)
                // Leer el PDF y escribirlo en disco son operaciones de bloqueo.
                val uri = withContext(Dispatchers.IO) {
                    val bytes = cuerpo.use { it.bytes() }
                    DescargasPdf.guardarEnDescargas(
                        app,
                        "contrato_${inquilino.nombre}_${inquilino.apellidos}",
                        bytes
                    )
                }
                _contratoState.value = UiState.Success(uri)
            } catch (e: Exception) {
                _contratoState.value =
                    UiState.Error(NetworkError.toUserMessage(e, "Error al generar el contrato"))
            }
        }
    }

    fun resetContratoState() {
        _contratoState.value = UiState.Idle
    }

    fun cargarCuartosLibres() {
        viewModelScope.launch {
            _cuartosLibresState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val lista = AlquilerApiClient.service.getCuartosLibres(idUsuario)
                _cuartosLibresState.value = UiState.Success(lista)
            } catch (e: Exception) {
                _cuartosLibresState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar cuartos libres"))
            }
        }
    }

    /**
     * Registra un inquilino en un cuarto (réplica del flujo web): primero crea el
     * inquilino con POST /inquilino y luego, si hay servicios adicionales, los agrega
     * uno a uno con POST /inquilino/servicio (cada uno actualiza el recibo pendiente).
     */
    fun registrarInquilino(
        idCuarto: String,
        nombre: String, apellidos: String, dni: String, celular: String, email: String?,
        fechaPago: Int, diaLimpieza: String, descripcion: String?,
        esnuevo: Boolean, garantiaPagada: Boolean, fechaEsperadaGarantia: String?,
        servicios: List<ServicioNuevo>, fechaIngreso: String? = null
    ) {
        viewModelScope.launch {
            _registrarInquilinoState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val resp = AlquilerApiClient.service.registrarInquilino(
                    RegistrarInquilinoRequest(
                        idUsuario = idUsuario, idCuarto = idCuarto,
                        nombre = nombre, apellidos = apellidos, dni = dni, celular = celular,
                        email = email, fechaPago = fechaPago, diaLimpieza = diaLimpieza,
                        descripcion = descripcion, esnuevo = esnuevo,
                        garantiaPagada = garantiaPagada, fechaEsperadaGarantia = fechaEsperadaGarantia,
                        fechaIngreso = fechaIngreso
                    )
                )
                val idInquilino = resp.inquilino?.idInquilino
                if (idInquilino != null) {
                    for (s in servicios) {
                        AlquilerApiClient.service.agregarServicioInquilino(
                            AgregarServicioInquilinoRequest(idInquilino, s.nombre, s.monto)
                        ).close()
                    }
                }
                _registrarInquilinoState.value = UiState.Success(resp.message ?: "Inquilino registrado correctamente")
                cargarCuartosLibres()
            } catch (e: Exception) {
                _registrarInquilinoState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al registrar inquilino"))
            }
        }
    }

    fun resetRegistrarInquilinoState() { _registrarInquilinoState.value = UiState.Idle }

    fun cargarCuartos() {
        viewModelScope.launch {
            _cuartosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _cuartosState.value = UiState.Success(AlquilerApiClient.service.getCuartos(idUsuario))
            } catch (e: Exception) {
                _cuartosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar los cuartos"))
            }
        }
    }

    /**
     * Edita un cuarto (nro, precio, garantía, descripción). [estemes] solo aplica
     * cuando el cuarto está alquilado y cambió el precio: true = afecta el recibo
     * ya generado del mes; false = solo el siguiente.
     */
    fun editarCuarto(
        idCuarto: String, nroCuarto: String, precio: Double, garantia: Double,
        idPiso: String, descripcion: String?, estemes: Boolean
    ) {
        viewModelScope.launch {
            _editarCuartoState.value = UiState.Loading
            try {
                AlquilerApiClient.service.editarCuarto(
                    idCuarto,
                    EditarCuartoRequest(nroCuarto, precio, garantia, idPiso, descripcion, estemes)
                ).close()
                _editarCuartoState.value = UiState.Success("Cuarto actualizado correctamente")
                cargarCuartos()
            } catch (e: Exception) {
                _editarCuartoState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al actualizar el cuarto"))
            }
        }
    }

    fun resetEditarCuartoState() { _editarCuartoState.value = UiState.Idle }

    fun cargarServicios() {
        viewModelScope.launch {
            _serviciosState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _serviciosState.value = UiState.Success(AlquilerApiClient.service.getServicios(idUsuario))
            } catch (e: Exception) {
                _serviciosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar servicios"))
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
                _serviciosRealizadosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar servicios pagados"))
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
                _pagarServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al pagar servicio"))
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
                _pagarServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al revertir pago"))
            }
        }
    }

    fun resetPagarServicioState() {
        _pagarServicioState.value = UiState.Idle
    }

    // ── CONCEPTOS DE SERVICIO (CRUD + borrado diferido 24h) ───────────────────
    fun cargarServiciosConceptos() {
        viewModelScope.launch {
            _conceptosServicioState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                _conceptosServicioState.value = UiState.Success(
                    AlquilerApiClient.service.getServiciosConceptos(idUsuario)
                )
            } catch (e: Exception) {
                _conceptosServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar los servicios"))
            }
        }
    }

    fun crearServicioConcepto(nombre: String, monto: Double, diaVencimiento: Int, precioFijo: Boolean) {
        viewModelScope.launch {
            _accionServicioState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                AlquilerApiClient.service.crearServicioConcepto(
                    CrearServicioConceptoRequest(idUsuario, nombre, monto, diaVencimiento, precioFijo)
                )
                _accionServicioState.value = UiState.Success("Servicio creado")
                cargarServiciosConceptos()
                cargarServicios()
            } catch (e: Exception) {
                _accionServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al crear el servicio"))
            }
        }
    }

    fun editarServicioConcepto(idServicio: String, nombre: String, monto: Double, diaVencimiento: Int, precioFijo: Boolean) {
        viewModelScope.launch {
            _accionServicioState.value = UiState.Loading
            try {
                AlquilerApiClient.service.editarServicioConcepto(
                    EditarServicioConceptoRequest(idServicio, nombre, monto, diaVencimiento, precioFijo)
                )
                _accionServicioState.value = UiState.Success("Servicio actualizado")
                cargarServiciosConceptos()
                cargarServicios()
            } catch (e: Exception) {
                _accionServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al actualizar el servicio"))
            }
        }
    }

    fun eliminarServicioConcepto(idServicio: String) {
        viewModelScope.launch {
            _accionServicioState.value = UiState.Loading
            try {
                AlquilerApiClient.service.eliminarServicioConcepto(idServicio)
                _accionServicioState.value = UiState.Success("Servicio eliminado. Puedes deshacerlo en 24 h.")
                cargarServiciosConceptos()
                cargarServicios()
            } catch (e: Exception) {
                _accionServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al eliminar el servicio"))
            }
        }
    }

    fun restaurarServicioConcepto(idServicio: String) {
        viewModelScope.launch {
            _accionServicioState.value = UiState.Loading
            try {
                AlquilerApiClient.service.restaurarServicioConcepto(idServicio)
                _accionServicioState.value = UiState.Success("Eliminación deshecha")
                cargarServiciosConceptos()
                cargarServicios()
            } catch (e: Exception) {
                _accionServicioState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al deshacer la eliminación"))
            }
        }
    }

    fun resetAccionServicioState() { _accionServicioState.value = UiState.Idle }

    // ── PAGO POR PARTES (abonos) de un recibo de inquilino ────────────────────
    /**
     * Registra un pago de inquilino. Si [montoPagado] es menor a la deuda, queda como
     * pago por partes y [fechaCompromiso] ("YYYY-MM-DD") es el plazo del saldo restante.
     */
    fun registrarPagoInquilino(inquilino: Inquilino, montoPagado: Double, fechaCompromiso: String?) {
        viewModelScope.launch {
            _pagoRapidoState.value = UiState.Loading
            try {
                val request = PagoRequest(
                    montoPagado = montoPagado,
                    metodoPago = "Yape",
                    descripcion = "Pago desde App Mobile",
                    fechaCompromiso = fechaCompromiso
                )
                val respuesta = AlquilerApiClient.service.registrarPago(inquilino.idPago, request)
                _pagoRapidoState.value = UiState.Success(respuesta.message)
                cargarPagos()
            } catch (e: Exception) {
                _pagoRapidoState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al registrar pago"))
            }
        }
    }

    fun cargarAbonos(idPago: String) {
        viewModelScope.launch {
            _abonosState.value = UiState.Loading
            try {
                _abonosState.value = UiState.Success(AlquilerApiClient.service.getHistorialPago(idPago))
            } catch (e: Exception) {
                _abonosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar los pagos por partes"))
            }
        }
    }

    fun revertirAbono(idAbono: String, idPago: String) {
        viewModelScope.launch {
            try {
                AlquilerApiClient.service.eliminarAbono(idAbono)
                cargarAbonos(idPago)
                cargarPagos()
            } catch (e: Exception) {
                _abonosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al revertir el pago por partes"))
            }
        }
    }

    fun resetAbonosState() { _abonosState.value = UiState.Idle }

    fun cargarUsuarios() {
        viewModelScope.launch {
            _usuariosState.value = UiState.Loading
            try {
                val idRol = app.sessionDataStore.idRol.first() ?: return@launch
                val resp = AlquilerApiClient.service.getUsuarios(idRol)
                _usuariosState.value = UiState.Success(resp.data)
            } catch (e: Exception) {
                _usuariosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar usuarios"))
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
                _pagosUsuariosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar pagos"))
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
                _pagosRealizadosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar pagos realizados"))
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
                _adminActionState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cambiar estado"))
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
                _adminActionState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al confirmar pago"))
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
                _adminActionState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al revertir pago"))
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
                _movIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar movimientos"))
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
                _movIndividualRealizadosState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar movimientos"))
            }
        }
    }

    fun registrarMovimiento(
        idConcepto: String, idMovimiento: String?, montoPagado: Double?,
        metodoPago: String, descripcion: String?, tipo: String, celular: String? = null
    ) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                val resp = AlquilerApiClient.service.registrarMovimiento(
                    RegistrarMovimientoRequest(idConcepto, idUsuario, idMovimiento, montoPagado, metodoPago, descripcion, celular)
                )
                _accionIndividualState.value = UiState.Success(resp.message)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al registrar"))
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
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al revertir"))
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
                _conceptosIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar conceptos"))
            }
        }
    }

    fun crearConcepto(
        tipo: String, nombre: String, descripcion: String?, monto: Double,
        diaVencimiento: Int, precioFijo: Boolean = true, celular: String? = null
    ) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                val idUsuario = app.sessionDataStore.userId.first() ?: return@launch
                AlquilerApiClient.service.crearConcepto(
                    CrearConceptoRequest(idUsuario, tipo, nombre, descripcion, monto, diaVencimiento, precioFijo, celular)
                )
                _accionIndividualState.value = UiState.Success("Concepto creado")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al crear concepto"))
            }
        }
    }

    fun editarConcepto(
        idConcepto: String, tipo: String, nombre: String, descripcion: String?, monto: Double,
        diaVencimiento: Int, precioFijo: Boolean = true, celular: String? = null
    ) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                AlquilerApiClient.service.editarConcepto(
                    EditarConceptoRequest(idConcepto, nombre, descripcion, monto, diaVencimiento, precioFijo, celular)
                )
                _accionIndividualState.value = UiState.Success("Concepto actualizado")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al actualizar concepto"))
            }
        }
    }

    fun eliminarConcepto(idConcepto: String, tipo: String) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                AlquilerApiClient.service.eliminarConcepto(idConcepto)
                _accionIndividualState.value = UiState.Success("Concepto eliminado. Puedes deshacerlo en 24 h.")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al eliminar concepto"))
            }
        }
    }

    fun restaurarConcepto(idConcepto: String, tipo: String) {
        viewModelScope.launch {
            _accionIndividualState.value = UiState.Loading
            try {
                AlquilerApiClient.service.restaurarConcepto(idConcepto)
                _accionIndividualState.value = UiState.Success("Eliminación deshecha")
                cargarConceptos(tipo)
                cargarMovimientos(tipo)
            } catch (e: Exception) {
                _accionIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al deshacer la eliminación"))
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
                _resumenIndividualState.value = UiState.Error(NetworkError.toUserMessage(e, "Error al cargar resumen"))
            }
        }
    }

    fun resetAccionIndividualState() { _accionIndividualState.value = UiState.Idle }

    private fun PagoBackend.toInquilinoUi(hoy: LocalDate): Inquilino {
        // fechaSegura evita el crash si el día de pago no existe en el mes (ej. 31 en abril).
        val fechaVenc = com.example.myapplication.util.fechaSegura(anio, mes, dia)
        val dias = ChronoUnit.DAYS.between(hoy, fechaVenc)
        val estadoPago = when {
            // dias == 0 (vence hoy) cuenta como vencido: hoy es la fecha límite, no "por vencer".
            dias <= 0 -> EstadoPago.VENCIDO
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
            garantiaPagada = fechaGarantia != null,
            fechaPago = fechaPago
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
