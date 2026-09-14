package com.example.myapplication.util

/** Monto y asunto extraídos de una captura de Yape (o null si no se pudo reconocer). */
data class DatosGastoEscaneado(val monto: Double?, val asunto: String?)

/**
 * Heurística para leer una captura de pantalla de Yape a partir del texto que
 * devuelve el OCR (ML Kit). Yape no tiene una API pública para esto, así que se
 * apoya en el formato visual típico del comprobante:
 *   - El monto aparece como "S/ 25.50" (con o sin espacio, con o sin punto tras la S).
 *   - El destinatario/asunto aparece en una frase como "Le yapeaste a Juan Pérez"
 *     o "Yapeaste a ...". Si no se encuentra esa frase, se usa la segunda línea del
 *     texto reconocido, que en la mayoría de capturas es el nombre de la persona
 *     (la primera suele ser el monto o el encabezado "Yape").
 * No es infalible: por eso la pantalla de confirmación siempre deja editar antes
 * de guardar.
 */
object YapeReciboParser {

    private val REGEX_MONTO = Regex("""S/\.?\s*([0-9][0-9,]*\.[0-9]{2})""")

    private val FRASES_DESTINATARIO = listOf(
        "le yapeaste a", "yapeaste a", "le pagaste a", "pagaste a",
        "enviaste a", "recibiste de", "te yapeó", "te yapeo", "te pagó", "te pago"
    )

    fun extraer(textoReconocido: String): DatosGastoEscaneado {
        val lineas = textoReconocido.lines().map { it.trim() }.filter { it.isNotBlank() }

        val monto = REGEX_MONTO.find(textoReconocido)
            ?.groupValues?.get(1)
            ?.replace(",", "") // "1,234.56" -> "1234.56" (formato peruano: coma de miles, punto decimal)
            ?.toDoubleOrNull()

        var asunto: String? = null
        for (linea in lineas) {
            val minuscula = linea.lowercase()
            val frase = FRASES_DESTINATARIO.firstOrNull { minuscula.contains(it) } ?: continue
            val resto = linea.substring(minuscula.indexOf(frase) + frase.length).trim(':', ' ', '-')
            asunto = resto.ifBlank { null }
            break
        }
        if (asunto == null) {
            // Sin la frase esperada: la segunda línea suele ser el nombre del
            // destinatario (la primera suele ser el monto o el encabezado "Yape").
            asunto = lineas.getOrNull(1)?.takeIf { it.length in 3..60 && it.none(Char::isDigit) }
        }

        return DatosGastoEscaneado(monto, asunto)
    }
}
