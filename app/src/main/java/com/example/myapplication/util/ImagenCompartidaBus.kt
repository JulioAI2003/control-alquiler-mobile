package com.example.myapplication.util

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Puente entre MainActivity (que recibe el Intent.ACTION_SEND de la Galería) y el
 * árbol de Compose (que decide qué hacer con la imagen). Un singleton simple basta
 * porque solo hay una Activity y como mucho una imagen "pendiente" a la vez.
 */
object ImagenCompartidaBus {
    private val _imagenPendiente = MutableStateFlow<Uri?>(null)
    val imagenPendiente: StateFlow<Uri?> = _imagenPendiente

    fun emitir(uri: Uri) { _imagenPendiente.value = uri }

    /** Se llama tras abrir/procesar la imagen, para no volver a dispararla en recomposiciones. */
    fun consumir() { _imagenPendiente.value = null }
}
