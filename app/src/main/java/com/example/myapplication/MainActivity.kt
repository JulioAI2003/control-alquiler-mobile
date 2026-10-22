// ─── MainActivity.kt ─────────────────────────────────────────────────────────
package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.auth.CambiarPasswordScreen
import com.example.myapplication.ui.auth.LoginScreen
import com.example.myapplication.ui.auth.PermisosDialog
import com.example.myapplication.ui.auth.faltanPermisos
import com.example.myapplication.ui.pagos.DashboardScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.ImagenCompartidaBus
import com.example.myapplication.worker.AlarmFullScreenActivity
import com.example.myapplication.worker.AlarmSoundService
import com.example.myapplication.worker.RecordatorioScheduler
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.imePadding

/**
 * Único Activity de la app.
 *
 * Flujo de inicio:
 *  • Si hay token JWT en cache → navega directo a [DashboardScreen].
 *  • Si no hay token           → muestra [LoginScreen].
 *
 * El botón "Cerrar Sesión" de [DashboardScreen] borra el token y vuelve a Login.
 */
class MainActivity : ComponentActivity() {

    /** Si el Intent es "compartir imagen" (ej. captura de Yape desde la Galería), lo publica
     *  en [ImagenCompartidaBus] para que el Dashboard lo recoja y abra "Pagos extra". */
    private fun procesarIntentCompartido(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        ImagenCompartidaBus.emitir(uri)
    }

    // launchMode="singleTask": si la app ya está en primer plano, Android reutiliza esta
    // instancia y entrega el nuevo Intent aquí en vez de recrear la Activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        procesarIntentCompartido(intent)
    }

    private fun agendarRecordatorioDiario() {
        // Limpia el trabajo periódico de versiones anteriores (migración a alarma exacta).
        WorkManager.getInstance(this).cancelUniqueWork("cobro_diario")
        // Agenda la alarma exacta a la hora local elegida por el usuario (Configuración).
        val hora = runBlocking {
            (application as MyApplication).sessionDataStore.horaNotificacion.first()
        }
        RecordatorioScheduler.programar(this, hora)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen nativa (API 31+, backport hasta API 23 vía SplashScreen compat)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyApplication

        // Si la alarma está sonando al entrar (o empieza a sonar con la app abierta), se
        // muestra su pantalla para poder leer los pendientes y apagarla. El flujo emite el
        // valor actual al empezar a observar, así que cubre también el regreso a la app.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmSoundService.sonando.collect { sonando ->
                    if (sonando && AlarmSoundService.estaActiva) {
                        startActivity(
                            Intent(this@MainActivity, AlarmFullScreenActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra(AlarmSoundService.EXTRA_TITULO, AlarmSoundService.tituloActivo)
                                putExtra(AlarmSoundService.EXTRA_DESCRIPCION, AlarmSoundService.descripcionActiva)
                            }
                        )
                    }
                }
            }
        }

        // Arranque en frío compartiendo una imagen (ej. captura de Yape) directamente.
        procesarIntentCompartido(intent)

        // Agenda el recordatorio diario solo si hay sesión activa
        if (!app.cachedToken.isNullOrBlank()) agendarRecordatorioDiario()

        // Decide la pantalla inicial en función del token persistido
        val startDestination = if (!app.cachedToken.isNullOrBlank()) "dashboard" else "login"

        setContent {
            // Tema elegido en Ajustes; si nunca eligió, sigue al ajuste del sistema.
            val temaOscuro  by app.temaOscuro.collectAsStateWithLifecycle()
            val escalaTexto by app.escalaTexto.collectAsStateWithLifecycle()

            MyApplicationTheme(
                oscuro      = temaOscuro ?: isSystemInDarkTheme(),
                escalaTexto = escalaTexto
            ) {
                Surface(
                    // imePadding: con enableEdgeToEdge la ventana ya no se achica
                    // sola al abrirse el teclado. Esto reserva su altura, el área
                    // de contenido se encoge y las listas y formularios —que ya son
                    // desplazables— llevan solos el campo enfocado a la vista.
                    modifier = Modifier.fillMaxSize().imePadding(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Al primer arranque (o si faltan), pedir los permisos que la app necesita.
                    var mostrarPermisos by remember { mutableStateOf(faltanPermisos(this@MainActivity)) }

                    // Se vuelve a validar en cada regreso a la app: si el usuario revocó un
                    // permiso (o salió de Ajustes sin darlo), el diálogo reaparece y se
                    // sigue pidiendo hasta que estén todos autorizados.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val obs = LifecycleEventObserver { _, e ->
                            if (e == Lifecycle.Event.ON_RESUME) {
                                val faltan = faltanPermisos(this@MainActivity)
                                // Reprograma con los permisos recién concedidos (ej. alarmas
                                // exactas), así se aplican sin reiniciar la app.
                                if (mostrarPermisos && !faltan && !app.cachedToken.isNullOrBlank()) {
                                    agendarRecordatorioDiario()
                                }
                                mostrarPermisos = faltan
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(obs)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                    }

                    if (mostrarPermisos) {
                        PermisosDialog(onDismiss = {
                            // "Continuar" solo se habilita con todo concedido; se revalida igual.
                            mostrarPermisos = faltanPermisos(this@MainActivity)
                            if (!mostrarPermisos && !app.cachedToken.isNullOrBlank()) agendarRecordatorioDiario()
                        })
                    }

                    // Navega a Login cuando el servidor invalida el token (contraseña cambiada).
                    LaunchedEffect(Unit) {
                        app.sessionExpired.collect {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    NavHost(
                        navController    = navController,
                        startDestination = startDestination
                    ) {

                        // ── LOGIN ─────────────────────────────────────────────
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ── DASHBOARD ─────────────────────────────────────────
                        composable("dashboard") {
                            DashboardScreen(
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                },
                                onCambiarPassword = {
                                    navController.navigate("cambiar-password")
                                }
                            )
                        }

                        // ── CAMBIAR CONTRASEÑA ────────────────────────────────
                        composable("cambiar-password") {
                            CambiarPasswordScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
