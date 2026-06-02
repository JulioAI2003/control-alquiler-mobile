// ─── data/local/SessionDataStore.kt ──────────────────────────────────────────
package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extensión de Context para obtener el DataStore (singleton por proceso). */
val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "alquiler_session")

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

    // ── Escritura ─────────────────────────────────────────────────────────────

    suspend fun guardarSesion(
        token:    String,
        userId:   String,
        nombre:   String,
        rol:      String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]   = token
            prefs[KEY_USER_ID] = userId
            prefs[KEY_NOMBRE]  = nombre
            prefs[KEY_ROL]     = rol
        }
    }

    /** Borra todos los datos de sesión (logout). */
    suspend fun limpiarSesion() {
        context.dataStore.edit { it.clear() }
    }

    // ── Claves ────────────────────────────────────────────────────────────────

    companion object {
        val KEY_TOKEN   = stringPreferencesKey("jwt_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NOMBRE  = stringPreferencesKey("nombre")
        val KEY_ROL     = stringPreferencesKey("rol")
    }
}
