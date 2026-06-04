// ─── MainActivity.kt ─────────────────────────────────────────────────────────
package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.ui.auth.LoginScreen
import com.example.myapplication.ui.pagos.DashboardScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.worker.CobroCheckWorker
import java.util.concurrent.TimeUnit

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

    private fun agendarWorkerDiario() {
        val request = PeriodicWorkRequestBuilder<CobroCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CobroCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen nativa (API 31+, backport hasta API 23 vía SplashScreen compat)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyApplication

        // Solicitar permiso de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        // Agenda el worker diario solo si hay sesión activa
        if (!app.cachedToken.isNullOrBlank()) agendarWorkerDiario()

        // Decide la pantalla inicial en función del token persistido
        val startDestination = if (!app.cachedToken.isNullOrBlank()) "dashboard" else "login"

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController    = navController,
                        startDestination = startDestination
                    ) {

                        // ── LOGIN ─────────────────────────────────────────────
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    // Sustituye Login por Dashboard en el back-stack
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
                                    // Sustituye Dashboard por Login en el back-stack
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
