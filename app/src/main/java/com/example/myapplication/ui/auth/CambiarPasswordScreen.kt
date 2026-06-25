// ─── ui/auth/CambiarPasswordScreen.kt ────────────────────────────────────────
package com.example.myapplication.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.CambiarPasswordRequest
import com.example.myapplication.data.model.UiState
import com.example.myapplication.data.remote.AlquilerApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

// ─── ViewModel ────────────────────────────────────────────────────────────────

class CambiarPasswordViewModel(private val app: MyApplication) : ViewModel() {

    private val _state = MutableStateFlow<UiState<String>>(UiState.Idle)
    val state: StateFlow<UiState<String>> = _state

    fun cambiarPassword(passwordActual: String, passwordNuevo: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            // Leer el id_usuario guardado en DataStore al momento del login
            val idUsuario = app.sessionDataStore.userId.first().orEmpty()
            try {
                val resp = AlquilerApiClient.service.cambiarPassword(
                    CambiarPasswordRequest(idUsuario, passwordActual, passwordNuevo)
                )
                // Persiste el nuevo token — este dispositivo no pierde la sesión.
                app.sessionDataStore.actualizarToken(resp.token)
                app.updateToken(resp.token)
                _state.value = UiState.Success(resp.message)
            } catch (e: HttpException) {
                // Intenta leer el mensaje de error que devuelve el servidor
                val serverMsg = try {
                    e.response()?.errorBody()?.string()
                        ?.let { body ->
                            errorJson.parseToJsonElement(body)
                                .jsonObject["message"]?.jsonPrimitive?.content
                        }
                } catch (_: Exception) { null }

                val msg = when {
                    e.code() == 401 -> "Contraseña actual incorrecta."
                    !serverMsg.isNullOrBlank() -> serverMsg
                    else -> "Error al cambiar la contraseña (${e.code()})."
                }
                _state.value = UiState.Error(msg)
            } catch (e: Exception) {
                _state.value = UiState.Error("Sin conexión. Intenta nuevamente.")
            }
        }
    }

    fun resetState() { _state.value = UiState.Idle }

    companion object {
        private val errorJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun factory(app: MyApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                CambiarPasswordViewModel(app) as T
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CambiarPasswordScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MyApplication
    val vm: CambiarPasswordViewModel = viewModel(factory = CambiarPasswordViewModel.factory(app))

    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var passwordActual  by remember { mutableStateOf("") }
    var passwordNuevo   by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    var mostrarActual  by remember { mutableStateOf(false) }
    var mostrarNuevo   by remember { mutableStateOf(false) }
    var mostrarConfirm by remember { mutableStateOf(false) }

    val longitudError = passwordNuevo.isNotBlank() && passwordNuevo.length < 6
    val confirmError  = passwordConfirm.isNotBlank() && passwordNuevo != passwordConfirm

    LaunchedEffect(state) {
        when (state) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar((state as UiState.Success<String>).data)
                vm.resetState()
                onBack()
            }
            is UiState.Error -> {
                snackbarHostState.showSnackbar((state as UiState.Error).message)
                vm.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cambiar contraseña", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF8A6A12),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = passwordActual,
                onValueChange = { passwordActual = it },
                label = { Text("Contraseña actual") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (mostrarActual) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { mostrarActual = !mostrarActual }) {
                        Icon(
                            if (mostrarActual) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                }
            )

            OutlinedTextField(
                value = passwordNuevo,
                onValueChange = { passwordNuevo = it },
                label = { Text("Nueva contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = longitudError,
                supportingText = { if (longitudError) Text("Mínimo 6 caracteres") },
                visualTransformation = if (mostrarNuevo) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { mostrarNuevo = !mostrarNuevo }) {
                        Icon(
                            if (mostrarNuevo) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                }
            )

            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text("Confirmar nueva contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = confirmError,
                supportingText = { if (confirmError) Text("Las contraseñas no coinciden") },
                visualTransformation = if (mostrarConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { mostrarConfirm = !mostrarConfirm }) {
                        Icon(
                            if (mostrarConfirm) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.cambiarPassword(passwordActual, passwordNuevo) },
                enabled = passwordActual.isNotBlank()
                        && passwordNuevo.length >= 6
                        && passwordNuevo == passwordConfirm
                        && state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A6A12))
            ) {
                if (state is UiState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar cambios", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
