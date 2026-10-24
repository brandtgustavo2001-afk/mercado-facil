package br.com.mercadofacil.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    fun decimal(raw: String): BigDecimal = try {
        val normalized = if (raw.contains(',')) raw.replace(".", "").replace(',', '.') else raw
        normalized.trim().toBigDecimal().also { require(it.signum() >= 0) { "Use um valor positivo" } }
    } catch (e: NumberFormatException) { throw IllegalArgumentException("Valor inválido", e) }
    fun parse(raw: String): Long = try { decimal(raw).movePointRight(2).longValueExact() }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Use até duas casas decimais", e) }
    fun quantity(raw: String): String = decimal(raw).also {
        require(it > BigDecimal.ZERO && it <= BigDecimal("1000000") && it.scale() <= 3) { "Quantidade: até 1.000.000 e três casas decimais" }
    }.stripTrailingZeros().toPlainString()
    fun line(quantity: String, cents: Long): Long = BigDecimal(quantity).multiply(BigDecimal(cents)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    fun format(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
    fun input(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
}
data class SemVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVersion> {
    override fun compareTo(other: SemVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    companion object {
        fun parse(raw: String): SemVersion {
            require(Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(raw)) { "Versão estável inválida" }
            val parts = raw.removePrefix("v").split('.').map { it.toInt() }
            return SemVersion(parts[0], parts[1], parts[2])
        }
    }
}
