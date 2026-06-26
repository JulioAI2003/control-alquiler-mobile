// ─── data/local/SessionDataStore.kt ──────────────────────────────────────────
package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extensión de Context para obtener el DataStore de sesión (se borra al cerrar sesión). */
val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "alquiler_session")

/** Store de preferencias persistentes que NO se borra al cerrar sesión
 *  (p. ej. si el usuario ya vio las indicaciones de ayuda). */
val Context.prefsStore: DataStore<Preferences>
    by preferencesDataStore(name = "alquiler_prefs")

/**
 * Capa de acceso a la sesión persistida en DataStore.
 *
 * Almacena el JWT y los datos del usuario logueado.
 * Toda la escritura/lectura es suspending o Flow — nunca bloquea el hilo principal.
 */
class SessionDataStore(private val context: Context) {

    // ── Lectura reactiva (Flow) ───────────────────────────────────────────────

    val token: Flow<String?>    = context.dataStore.data.map { it[KEY_TOKEN]    }
    val userId: Flow<String?>   = context.dataStore.data.map { it[KEY_USER_ID]  }
    val nombre: Flow<String?>   = context.dataStore.data.map { it[KEY_NOMBRE]   }
    val rol: Flow<String?>      = context.dataStore.data.map { it[KEY_ROL]      }
    val idRol: Flow<String?>    = context.dataStore.data.map { it[KEY_ID_ROL]   }

    /** "alarma" o "notificacion" (default). */
    val tipoAviso: Flow<String> = context.dataStore.data.map { it[KEY_TIPO_AVISO] ?: "notificacion" }

    /** Hora local (del dispositivo, p. ej. Perú) del aviso diario, formato "HH:mm". Default "08:00". */
    val horaNotificacion: Flow<String> = context.dataStore.data.map { it[KEY_HORA_NOTIF] ?: "08:00" }

    // ── Escritura ─────────────────────────────────────────────────────────────

    suspend fun guardarSesion(
        token:    String,
        userId:   String,
        nombre:   String,
        rol:      String,
        idRol:    String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]   = token
            prefs[KEY_USER_ID] = userId
            prefs[KEY_NOMBRE]  = nombre
            prefs[KEY_ROL]     = rol
            prefs[KEY_ID_ROL]  = idRol
        }
    }

    suspend fun guardarTipoAviso(tipo: String) {
        context.dataStore.edit { prefs -> prefs[KEY_TIPO_AVISO] = tipo }
    }

    /** Guarda la hora del aviso diario en formato "HH:mm" (hora local del dispositivo). */
    suspend fun guardarHoraNotificacion(hora: String) {
        context.dataStore.edit { prefs -> prefs[KEY_HORA_NOTIF] = hora }
    }

    /** Actualiza solo el JWT sin tocar userId/nombre/rol (usado al cambiar contraseña). */
    suspend fun actualizarToken(token: String) {
        context.dataStore.edit { prefs -> prefs[KEY_TOKEN] = token }
    }

    /** Borra todos los datos de sesión (logout). No toca las indicaciones ya vistas. */
    suspend fun limpiarSesion() {
        context.dataStore.edit { it.clear() }
    }

    // ── Onboarding / indicaciones de ayuda (persistente por usuario) ────────────

    /** true si el usuario [userId] ya vio las indicaciones de ayuda. */
    fun haVistoOnboarding(userId: String): Flow<Boolean> =
        context.prefsStore.data.map { it[booleanPreferencesKey("onboarding_$userId")] ?: false }

    /** Marca que [userId] ya vio las indicaciones (no se vuelven a mostrar solas). */
    suspend fun marcarOnboardingVisto(userId: String) {
        context.prefsStore.edit { it[booleanPreferencesKey("onboarding_$userId")] = true }
    }

    /** true si [userId] ya vio el tutorial de la vista [clave] (p. ej. "ajustes"). */
    fun haVistoTutorial(userId: String, clave: String): Flow<Boolean> =
        context.prefsStore.data.map { it[booleanPreferencesKey("tut_${userId}_$clave")] ?: false }

    /** Marca como visto el tutorial de la vista [clave] para [userId]. */
    suspend fun marcarTutorialVisto(userId: String, clave: String) {
        context.prefsStore.edit { it[booleanPreferencesKey("tut_${userId}_$clave")] = true }
    }

    // ── Claves ────────────────────────────────────────────────────────────────

    companion object {
        val KEY_TOKEN   = stringPreferencesKey("jwt_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NOMBRE  = stringPreferencesKey("nombre")
        val KEY_ROL     = stringPreferencesKey("rol")
        val KEY_ID_ROL     = stringPreferencesKey("id_rol")
        val KEY_TIPO_AVISO = stringPreferencesKey("tipo_aviso")
        val KEY_HORA_NOTIF = stringPreferencesKey("hora_notificacion")
    }
}
