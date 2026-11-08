package br.com.mercadofacil

import br.com.mercadofacil.domain.injectionIntervalLabel
import br.com.mercadofacil.domain.nextInjectionDueAt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InjectionScheduleLogicTest {
    private val zone = ZoneId.systemDefault()

    @Test fun addsDaysAndWeeksToTheScheduledLocalTime() {
        val start = LocalDateTime.of(2026, 3, 1, 9, 30).atZone(zone).toInstant().toEpochMilli()

        val afterTwoDays = Instant.ofEpochMilli(nextInjectionDueAt(start, 2, "DAYS")).atZone(zone)
        val afterTwoWeeks = Instant.ofEpochMilli(nextInjectionDueAt(start, 2, "WEEKS")).atZone(zone)

        assertEquals(LocalDateTime.of(2026, 3, 3, 9, 30), afterTwoDays.toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 3, 15, 9, 30), afterTwoWeeks.toLocalDateTime())
    }

    @Test fun addsCalendarMonthsAndClampsAtMonthEnd() {
        val start = LocalDateTime.of(2026, 1, 31, 9, 30).atZone(zone).toInstant().toEpochMilli()

        val next = Instant.ofEpochMilli(nextInjectionDueAt(start, 1, "MONTHS")).atZone(zone)

        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 30), next.toLocalDateTime())
    }

    @Test fun rejectsInvalidIntervalValuesAndUnits() {
        val start = System.currentTimeMillis()

        assertThrows(IllegalArgumentException::class.java) { nextInjectionDueAt(start, 0, "DAYS") }
        assertThrows(IllegalArgumentException::class.java) { nextInjectionDueAt(start, 8761, "DAYS") }
        assertThrows(IllegalArgumentException::class.java) { nextInjectionDueAt(start, 1, "YEARS") }
    }

    @Test fun formatsIntervalInPortuguese() {
        assertEquals("a cada 1 mês", injectionIntervalLabel(1, "MONTHS"))
        assertEquals("a cada 3 semanas", injectionIntervalLabel(3, "WEEKS"))
    }
}
