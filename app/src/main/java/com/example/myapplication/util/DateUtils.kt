// ─── util/DateUtils.kt ────────────────────────────────────────────────────────
package com.example.myapplication.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * Construye una [LocalDate] sin lanzar excepción aunque el día venga fuera de
 * rango. `LocalDate.of(2026, 4, 31)` lanza DateTimeException (abril tiene 30 días);
 * eso podía tumbar toda la lista de cobros si un recibo tenía día de pago 31 en un
 * mes de 30 días. Aquí ajustamos el mes a 1..12 y el día al máximo real del mes.
 */
fun fechaSegura(anio: Int, mes: Int, dia: Int): LocalDate {
    val ym = YearMonth.of(anio, mes.coerceIn(1, 12))
    return ym.atDay(dia.coerceIn(1, ym.lengthOfMonth()))
}

/**
 * Formatea una fecha ISO del backend ("2026-07-27" o "2026-07-27T14:30:00.000Z") a
 * "dd/MM/yyyy" para mostrarla al usuario. Solo se usa la parte de la fecha (los
 * primeros 10 caracteres) para evitar confusiones de zona horaria con la hora.
 * Devuelve "" si el texto es nulo o no se puede parsear.
 */
fun formatearFecha(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val fecha = LocalDate.parse(iso.take(10))
        "%02d/%02d/%04d".format(fecha.dayOfMonth, fecha.monthValue, fecha.year)
    } catch (_: Exception) {
        ""
    }
}
