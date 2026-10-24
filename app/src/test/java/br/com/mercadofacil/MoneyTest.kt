package br.com.mercadofacil

import br.com.mercadofacil.domain.*
import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun decimalQuantityRoundsAtLineTotal() { assertEquals(998L, Money.line("1.5", 665)) }
    @Test fun brazilianInputIsExact() { assertEquals(123456L, Money.parse("1.234,56")) }
    @Test fun zeroIsAllowed() { assertEquals(0L, Money.parse("0,00")) }
    @Test(expected = IllegalArgumentException::class) fun negativeRejected() { Money.parse("-1") }
    @Test(expected = IllegalArgumentException::class) fun fractionalCentsRejected() { Money.parse("1,001") }
    @Test fun semanticComparison() { assertTrue(SemVersion.parse("v1.10.0") > SemVersion.parse("1.9.9")) }
    @Test(expected = IllegalArgumentException::class) fun prereleaseRejected() { SemVersion.parse("1.0.0-rc1") }
}
