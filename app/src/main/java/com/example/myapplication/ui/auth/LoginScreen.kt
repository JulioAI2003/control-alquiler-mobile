// ─── ui/auth/LoginScreen.kt ──────────────────────────────────────────────────
package com.example.myapplication.ui.auth

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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.UiState
import com.example.myapplication.data.remote.AlquilerApiClient
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
                // Persiste la sesión en DataStore
                app.sessionDataStore.guardarSesion(
                    token  = response.token,
                    userId = response.idUsuario,
                    nombre = response.nombre,
                    rol    = response.rol
                )
                // Actualiza el token en memoria para el interceptor
                app.updateToken(response.token)

                _state.value = UiState.Success(response)

            } catch (e: HttpException) {
                _state.value = UiState.Error(
                    when (e.code()) {
                        400  -> "Datos incorrectos."
                        401  -> "Correo o contraseña incorrectos."
                        403  -> "Cuenta inactiva o pendiente. Contacta al administrador."
                        500  -> "Error en el servidor. Intenta más tarde."
                        else -> "Error ${e.code()}."
                    }
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(
                    e.message ?: "Sin conexión. Verifica que el servidor esté activo."
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
                Brush.verticalGradient(
                    listOf(Color(0xFF1565C0), Color(0xFF003C8F))
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
            colors    = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier            = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Ícono e Introducción ───────────────────────────────────────
                Icon(
                    imageVector        = Icons.Default.Home,
                    contentDescription = null,
                    tint               = Color(0xFF1565C0),
                    modifier           = Modifier.size(60.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "Control de Alquileres",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color(0xFF1A237E)
                )
                Text(
                    text     = "Inicia sesión para continuar",
                    color    = Color(0xFF757575),
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
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1565C0),
                        disabledContainerColor = Color(0xFF1565C0).copy(alpha = 0.6f)
                    )
                ) {
                    if (state is UiState.Loading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            color       = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Login, null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ingresar",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }
            }
        }
    }
}
