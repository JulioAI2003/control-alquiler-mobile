// ─── ui/auth/LoginScreen.kt ──────────────────────────────────────────────────
package com.example.myapplication.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.MyApplication
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.R
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.UiState
import com.example.myapplication.data.remote.AlquilerApiClient
import com.example.myapplication.data.remote.NetworkError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ═════════════════════════════════════════════════════════════════════════════
//  LOGIN VIEWMODEL (inline en el mismo archivo — simple y sin repo extra)
// ═════════════════════════════════════════════════════════════════════════════

class LoginViewModel(private val app: MyApplication) : ViewModel() {

    private val _state = MutableStateFlow<UiState<LoginResponse>>(UiState.Idle)
    val state: StateFlow<UiState<LoginResponse>> = _state.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = UiState.Error("Completa todos los campos.")
            return
        }

        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val response = AlquilerApiClient.service.login(
                    LoginRequest(email = email.trim(), password = password)
                )

                if (response.error != null) {
                    _state.value = when (response.estado) {
                        "inactivo"  -> UiState.Error("Su usuario está inactivo, contactarse con el administrador.")
                        "pendiente" -> UiState.Error("Su usuario está pendiente de aprobación, contactarse con el administrador.")
                        else        -> UiState.Error(response.error)
                    }
                    return@launch
                }

                app.sessionDataStore.guardarSesion(
                    token  = response.token!!,
                    userId = response.idUsuario!!,
                    nombre = response.nombre!!,
                    rol    = response.rol!!,
                    idRol  = response.idRol
                )
                app.updateToken(response.token)

                // Restaura desde el backend el tipo de aviso y la hora del recordatorio
                // configurados previamente (p. ej. tras reinstalar la app o en un
                // dispositivo nuevo). Si falla (sin conexión), se conservan los valores
                // por defecto locales; no debe bloquear el login.
                runCatching {
                    val ajustes = AlquilerApiClient.service.getAjustes(response.idUsuario)
                    app.sessionDataStore.guardarTipoAviso(ajustes.tipoAviso)
                    app.sessionDataStore.guardarHoraNotificacion(ajustes.horaNotificacion)
                }

                // Agenda el recordatorio diario ya con la hora restaurada (o la que hubiera
                // por defecto). Antes esto solo ocurría en el siguiente arranque en frío de
                // la app, dejando la primera sesión sin alarma programada.
                val horaVigente = app.sessionDataStore.horaNotificacion.first()
                com.example.myapplication.worker.RecordatorioScheduler.programar(app, horaVigente)

                _state.value = UiState.Success(response)

            } catch (e: HttpException) {
                _state.value = UiState.Error(
                    when (e.code()) {
                        404  -> "El usuario no existe."
                        401  -> "El usuario no existe."
                        500  -> "Error en el servidor. Intenta más tarde."
                        else -> "Error ${e.code()}."
                    }
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(
                    NetworkError.toUserMessage(e, "Sin conexión. Verifica que el servidor esté activo.")
                )
            }
        }
    }

    fun resetState() { _state.value = UiState.Idle }

    companion object {
        fun factory(app: MyApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST") return LoginViewModel(app) as T
                }
            }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  LOGIN SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val app   = LocalContext.current.applicationContext as MyApplication
    val vm    = viewModel<LoginViewModel>(factory = LoginViewModel.factory(app))
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    // Campos del formulario
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    // Navegar en cuanto el login tenga éxito
    LaunchedEffect(state) {
        if (state is UiState.Success) onLoginSuccess()
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(
                // Degradado de marca: se mantiene igual en claro y en oscuro.
                Brush.verticalGradient(
                    listOf(Color(0xFFCBA85A), Color(0xFF8A6A12), Color(0xFF4A3A0C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors    = CardDefaults.cardColors(containerColor = AppTheme.colores.superficie)
        ) {
            Column(
                modifier            = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Logo oficial e Introducción ────────────────────────────────
                Image(
                    painter            = painterResource(R.drawable.logo),
                    contentDescription = "Gestia",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(26.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text       = "Control de Alquileres",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = AppTheme.colores.dorado
                )
                Text(
                    text     = "Inicia sesión para continuar",
                    color    = AppTheme.colores.textoSuave,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(28.dp))

                // ── Email ──────────────────────────────────────────────────────
                OutlinedTextField(
                    value         = email,
                    onValueChange = {
                        email = it
                        if (state is UiState.Error) vm.resetState()
                    },
                    label       = { Text("Correo electrónico") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    singleLine  = true,
                    isError     = state is UiState.Error,
                    modifier    = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focus.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(Modifier.height(12.dp))

                // ── Contraseña ─────────────────────────────────────────────────
                OutlinedTextField(
                    value         = password,
                    onValueChange = {
                        password = it
                        if (state is UiState.Error) vm.resetState()
                    },
                    label       = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                imageVector        = if (showPass) Icons.Default.Visibility
                                                     else          Icons.Default.VisibilityOff,
                                contentDescription = if (showPass) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None
                                           else          PasswordVisualTransformation(),
                    singleLine  = true,
                    isError     = state is UiState.Error,
                    modifier    = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focus.clearFocus(); vm.login(email, password) }
                    )
                )

                // ── Mensaje de error ───────────────────────────────────────────
                if (state is UiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text     = (state as UiState.Error).message,
                            color    = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Botón Ingresar ─────────────────────────────────────────────
                Button(
                    onClick  = { focus.clearFocus(); vm.login(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape   = RoundedCornerShape(14.dp),
                    enabled = state !is UiState.Loading,
                    contentPadding = PaddingValues(),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    // Degradado dorado metalizado con texto oscuro (look premium).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFE6CF8B), Color(0xFFC8A24B), Color(0xFF8A6A12))
                                ),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(22.dp),
                                color       = AppTheme.colores.tinta,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Login, null,
                                    tint = AppTheme.colores.tinta,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Ingresar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 16.sp,
                                    color      = AppTheme.colores.tinta
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
