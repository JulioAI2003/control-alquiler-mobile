// ─── util/DescargasPdf.kt ────────────────────────────────────────────────────
package com.example.myapplication.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Guarda un PDF en el dispositivo y lo abre con el visor que tenga el usuario.
 *
 * Se usa la carpeta pública de Descargas para que el archivo quede a mano
 * (y se pueda compartir por WhatsApp, correo, etc.) sin pedir permisos:
 *  · Android 10+ (API 29): MediaStore, que no requiere permiso alguno.
 *  · Android 8–9: carpeta de Descargas propia de la app, tampoco requiere
 *    permiso, y se comparte vía FileProvider.
 */
object DescargasPdf {

    /** Deja el nombre en algo que cualquier sistema de archivos acepte. */
    private fun limpiarNombre(nombre: String): String =
        nombre.trim()
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ ]+"), " ")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "contrato" }

    /**
     * Escribe [bytes] como `<nombre>.pdf` en Descargas.
     * @return el `content://` del archivo, listo para abrirlo o compartirlo.
     */
    fun guardarEnDescargas(context: Context, nombre: String, bytes: ByteArray): Uri {
        val archivo = "${limpiarNombre(nombre)}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, archivo)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: error("No se pudo crear el archivo en Descargas")

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("No se pudo escribir el contrato")

            // IS_PENDING = 0 hace visible el archivo para el resto del sistema.
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri
        }

        // Android 8–9: carpeta privada de la app (sin permisos) + FileProvider.
        val carpeta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!carpeta.exists()) carpeta.mkdirs()
        val destino = File(carpeta, archivo)
        destino.writeBytes(bytes)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destino)
    }

    /**
     * Abre el PDF con la app que el usuario tenga instalada.
     * @return false si no hay ningún visor de PDF en el dispositivo.
     */
    fun abrir(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
