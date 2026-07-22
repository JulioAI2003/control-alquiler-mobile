package com.example.myapplication.util

/**
 * Parsea un monto escrito por el usuario aceptando punto O coma como separador
 * decimal.
 *
 * En muchos teclados (locale es-PE/es-ES) la tecla decimal de `KeyboardType.Decimal`
 * inserta una coma ("1,50"), pero `Kotlin.toDouble()/toDoubleOrNull()` solo entiende
 * el punto, así que devolvía `null` y dejaba deshabilitado el botón de pago/registro.
 * Aquí se normaliza la coma a punto (y se quitan espacios) antes de parsear.
 *
 * @return el monto como [Double], o `null` si el texto no es un número válido.
 */
fun String.aMontoOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Igual que [aMontoOrNull] pero con valor por defecto (0.0) cuando el texto no es válido. */
fun String.aMonto(porDefecto: Double = 0.0): Double = aMontoOrNull() ?: porDefecto
