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
