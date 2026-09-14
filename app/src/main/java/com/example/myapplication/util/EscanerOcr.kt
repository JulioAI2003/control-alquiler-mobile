package com.example.myapplication.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Envuelve el reconocimiento de texto de ML Kit (API por callbacks) en una función suspend. */
object EscanerOcr {
    suspend fun reconocerTexto(context: Context, uri: Uri): String {
        val imagen = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(imagen)
                .addOnSuccessListener { texto -> cont.resume(texto.text) }
                .addOnFailureListener { error -> cont.resumeWithException(error) }
        }
    }
}
