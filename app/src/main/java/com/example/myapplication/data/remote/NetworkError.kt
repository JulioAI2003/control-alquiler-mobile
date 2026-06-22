// ─── data/remote/NetworkError.kt ─────────────────────────────────────────────
package com.example.myapplication.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * IOException que lanza [ConnectivityRetryInterceptor] cuando el dispositivo no
 * tiene red. Permite distinguir "no hay internet" de "el servidor no responde".
 */
class NoConnectivityException(
    message: String = "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
) : IOException(message)

/**
 * Traduce excepciones de red/servidor a mensajes claros para el usuario y expone
 * un chequeo de conectividad. Centraliza aquí lo que antes era `e.message ?: "..."`
 * en cada ViewModel, que mostraba textos técnicos ("Unexpected JSON token...",
 * "timeout", "HTTP 504") como si fueran errores de programación.
 */
object NetworkError {

    /** true si el dispositivo tiene una red con acceso a internet validado. */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Convierte una excepción en un mensaje entendible.
     * [fallback] solo se usa para errores realmente desconocidos, así nunca se
     * muestra un stacktrace o texto técnico al usuario.
     */
    fun toUserMessage(e: Throwable, fallback: String = "Ocurrió un error inesperado"): String =
        when (e) {
            is NoConnectivityException ->
                e.message ?: "Sin conexión a internet."
            is UnknownHostException ->
                "Sin conexión o el servidor no está disponible. Revisa tu red."
            is SocketTimeoutException ->
                "El servidor tardó demasiado en responder. Inténtalo de nuevo en unos segundos."
            is IOException ->
                "Problema de conexión. Revisa tu internet e inténtalo de nuevo."
            is HttpException -> when (e.code()) {
                401  -> "Tu sesión expiró. Vuelve a iniciar sesión."
                404  -> "No se encontró la información solicitada."
                in 500..599 -> "El servidor tuvo un problema temporal. Inténtalo en unos segundos."
                else -> "No se pudo completar la operación (código ${e.code()})."
            }
            is SerializationException ->
                "El servidor devolvió una respuesta inesperada. Inténtalo de nuevo."
            else -> fallback
        }
}
