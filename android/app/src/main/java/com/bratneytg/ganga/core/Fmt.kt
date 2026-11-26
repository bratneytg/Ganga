package com.bratneytg.ganga.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** Formato de importes y fechas en español. */
object Fmt {
    private val es: Locale = Locale.forLanguageTag("es-ES")

    fun eur(n: Double): String {
        val f = NumberFormat.getCurrencyInstance(es)
        f.currency = Currency.getInstance("EUR")
        return f.format(n)
    }

    /** Número con dos decimales y coma: 1244,16 */
    fun num(n: Double): String = String.format(es, "%.2f", n)

    fun whenText(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(millis).atZone(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now(zone))
        val time = d.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (days) {
            0L -> "hoy a las $time"
            1L -> "ayer a las $time"
            -1L -> "mañana a las $time"
            else -> d.format(DateTimeFormatter.ofPattern("d MMM", es)) + " a las $time"
        }
    }

    fun dateTime(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return d.format(DateTimeFormatter.ofPattern("d MMM HH:mm", es))
    }
}
