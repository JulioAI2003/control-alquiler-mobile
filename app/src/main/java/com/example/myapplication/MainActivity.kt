// ─── MainActivity.kt ─────────────────────────────────────────────────────────
package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.auth.LoginScreen
import com.example.myapplication.ui.pagos.DashboardScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen nativa (API 31+, backport hasta API 23 vía SplashScreen compat)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyApplication

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
