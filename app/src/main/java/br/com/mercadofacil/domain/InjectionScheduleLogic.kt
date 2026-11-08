package br.com.mercadofacil.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun nextInjectionDueAt(fromMillis: Long, intervalValue: Int, intervalUnit: String): Long {
    require(intervalValue in 1..8760) { "O intervalo deve estar entre 1 e 8.760." }
    val date = Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault())
    val next = when (intervalUnit) {
        "HOURS" -> date.plusHours(intervalValue.toLong())
        "DAYS" -> date.plusDays(intervalValue.toLong())
        "WEEKS" -> date.plusWeeks(intervalValue.toLong())
        "MONTHS" -> date.plusMonths(intervalValue.toLong())
        else -> throw IllegalArgumentException("Escolha horas, dias, semanas ou meses.")
    }
    return next.toInstant().toEpochMilli()
}

fun injectionIntervalLabel(value: Int, unit: String): String {
    val label = when (unit) {
        "HOURS" -> if (value == 1) "hora" else "horas"
        "DAYS" -> if (value == 1) "dia" else "dias"
        "WEEKS" -> if (value == 1) "semana" else "semanas"
        "MONTHS" -> if (value == 1) "mês" else "meses"
        else -> "período"
    }
    return "a cada $value $label"
}

fun formatInjectionDateTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")))
