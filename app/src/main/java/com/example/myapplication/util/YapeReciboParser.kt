package com.example.myapplication.util

import java.time.LocalDate

/** Monto, asunto y fecha extraídos de una captura de Yape. Cada uno queda en null si no
 *  se pudo ubicar con certeza: nunca se inventa un valor (ni "hoy" como fecha, ni el
 *  nombre del destinatario como asunto). */
data class DatosGastoEscaneado(val monto: Double?, val asunto: String?, val fecha: String?)

/**
 * Lee una captura de pantalla de Yape a partir del texto que devuelve el OCR (ML Kit),
 * apoyándose en el orden fijo en que Yape muestra los datos del comprobante:
 *   1) "¡Yapeaste!" / "Pagaste" / etc. (encabezado)
 *   2) El monto, en letra grande — ej. "S/ 5" (con o sin decimales)
 *   3) El nombre del destinatario o remitente
 *   4) La fecha (y hora), justo debajo del nombre — ej. "30 ago. 2026 | 02:42 p. m."
 *   5) El mensaje/asunto, opcional, junto al ícono de mensaje — ej. "galleta spider".
 *      Si esa transacción no tiene mensaje, esa línea ya es un encabezado fijo del
 *      comprobante ("CÓDIGO DE SEGURIDAD", "DATOS DE LA TRANSACCIÓN"...).
 * No es infalible: por eso la pantalla de confirmación siempre exige revisar (y
 * completar a mano lo que falte) antes de guardar.
 */
object YapeReciboParser {

    // El monto no siempre trae decimales ("S/15" tanto como "S/ 25.50"), ni siempre
    // conserva la mayúscula ("s/5"). "[s5]" porque el OCR confunde con frecuencia la
    // "S" de "S/" con un "5"; IGNORE_CASE cubre "S/" y "s/" por igual.
    private val REGEX_MONTO = Regex("""[s5]/\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val MESES = mapOf(
        "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "set" to 9, "sep" to 9, "oct" to 10, "nov" to 11, "dic" to 12
    )
    private val REGEX_FECHA = Regex("""(\d{1,2})\s+([a-zA-Zé]{3,4})\.?\s+(\d{4})""")

    // Encabezados fijos del comprobante: si la línea que seguiría al mensaje es una de
    // estas, es que Yape no mostró ningún mensaje para esa transacción.
    private val ENCABEZADOS_SIN_MENSAJE = listOf(
        "código de seguridad", "codigo de seguridad", "datos de la transacción",
        "datos de la transaccion", "nro. de celular", "nro. de operación",
        "nro. de operacion", "destino"
    )

    // El ícono junto al mensaje ("🏷️ galleta spider") suele leerse como una letra suelta
    // pegada al texto ("F galleta spider"). Se recorta, salvo que sea una conjunción de
    // una letra real en español (y/o/a/e/u), para no cortar un mensaje real.
    private val CONECTORES_UNA_LETRA = setOf("y", "o", "a", "e", "u")
    private val REGEX_PREFIJO_ICONO = Regex("""^([A-Za-zÑñ])\s+(.+)$""")

    fun extraer(textoReconocido: String): DatosGastoEscaneado {
        val lineas = textoReconocido.lines().map { it.trim() }.filter { it.isNotBlank() }
        val idxMonto = lineas.indexOfFirst { REGEX_MONTO.containsMatchIn(it) }
        if (idxMonto == -1) return DatosGastoEscaneado(null, null, null)

        val monto = REGEX_MONTO.find(lineas[idxMonto])
            ?.groupValues?.get(1)
            ?.replace(",", "") // formato peruano: coma de miles, punto decimal
            ?.toDoubleOrNull()

        // La fecha va 2 líneas debajo del monto (el nombre del destinatario queda en medio).
        val fecha = lineas.getOrNull(idxMonto + 2)?.let(::parsearFecha)

        // El mensaje (asunto), si existe, va justo debajo de la fecha. Si esa línea ya es
        // un encabezado fijo del comprobante, no hay mensaje: se deja sin llenar.
        val asunto = lineas.getOrNull(idxMonto + 3)
            ?.takeIf { linea -> ENCABEZADOS_SIN_MENSAJE.none { linea.lowercase().contains(it) } }
            ?.let(::quitarPrefijoIcono)

        return DatosGastoEscaneado(monto, asunto, fecha)
    }

    private fun parsearFecha(linea: String): String? {
        val m = REGEX_FECHA.find(linea) ?: return null
        val dia = m.groupValues[1].toIntOrNull() ?: return null
        val mes = MESES[m.groupValues[2].lowercase().take(3)] ?: return null
        val anio = m.groupValues[3].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(anio, mes, dia).toString() }.getOrNull()
    }

    private fun quitarPrefijoIcono(linea: String): String {
        val m = REGEX_PREFIJO_ICONO.find(linea) ?: return linea
        val letra = m.groupValues[1].lowercase()
        return if (letra in CONECTORES_UNA_LETRA) linea else m.groupValues[2]
    }
}
